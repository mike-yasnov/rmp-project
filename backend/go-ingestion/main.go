package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math"
	"math/rand"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/metric"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	oteltrace "go.opentelemetry.io/otel/trace"
)

type Quote struct {
	Ticker    string  `json:"ticker"`
	Price     float64 `json:"price"`
	Volume    uint64  `json:"volume"`
	Timestamp string  `json:"timestamp"`
}

type TickerState struct {
	Ticker     string
	Price      float64
	Volatility float64
}

var fallbackTickers = []TickerState{
	{"GAZP", 162.50, 0.020},
	{"SBER", 258.70, 0.015},
	{"LKOH", 7150.00, 0.018},
	{"YNDX", 3920.00, 0.025},
	{"ROSN", 520.30, 0.020},
	{"GMKN", 15800.00, 0.022},
	{"MTSS", 310.50, 0.012},
	{"VKCO", 680.00, 0.030},
	{"TCSG", 2850.00, 0.025},
	{"PLZL", 12500.00, 0.020},
}

var (
	tracer                oteltrace.Tracer = otel.Tracer("go-ingestion")
	quotesReceivedMetric  metric.Int64Counter
	quotesInsertedMetric  metric.Int64Counter
	redisPublishedMetric  metric.Int64Counter
	clickhouseErrorMetric metric.Int64Counter
)

func main() {
	ctx := context.Background()
	devicePath := getenv("QUOTES_DEVICE", "/dev/quotes")
	clickhouseHTTP := getenv("CLICKHOUSE_HTTP", "http://localhost:8123")
	redisAddr := getenv("REDIS_ADDR", "localhost:6379")
	serviceName := getenv("OTEL_SERVICE_NAME", "go-ingestion")
	otelEndpoint := getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "")
	batchSize := getenvInt("BATCH_SIZE", 100)
	interval := time.Duration(getenvInt("INTERVAL_MS", 500)) * time.Millisecond
	requireDevice := getenvBool("QUOTES_REQUIRE_DEVICE", false)
	deviceWait := time.Duration(getenvInt("QUOTES_DEVICE_WAIT_SEC", 0)) * time.Second

	shutdownTelemetry := initTelemetry(ctx, serviceName, otelEndpoint)
	defer func() {
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := shutdownTelemetry(shutdownCtx); err != nil {
			log.Printf("otel shutdown failed: %v", err)
		}
	}()

	rdb := redis.NewClient(&redis.Options{Addr: redisAddr})
	defer rdb.Close()

	log.Printf("go-ingestion: clickhouse=%s redis=%s device=%s batch=%d interval=%s requireDevice=%t", clickhouseHTTP, redisAddr, devicePath, batchSize, interval, requireDevice)

	quotes := make(chan Quote, batchSize*4)
	if devicePath != "" && devicePath != "/dev/null" {
		if err := waitForDevice(devicePath, deviceWait); err != nil {
			if requireDevice {
				log.Fatalf("device %s is required but not available: %v", devicePath, err)
			}
			log.Printf("device %s is not available (%v), using fallback generator", devicePath, err)
			go generateFallback(interval, quotes)
		} else {
			log.Printf("reading quotes from %s", devicePath)
			go readDevice(devicePath, interval, quotes, !requireDevice)
		}
	} else {
		log.Printf("quotes device is disabled, using fallback generator")
		go generateFallback(interval, quotes)
	}

	buffer := make([]Quote, 0, batchSize)
	flushTicker := time.NewTicker(1 * time.Second)
	defer flushTicker.Stop()

	for {
		select {
		case q := <-quotes:
			buffer = append(buffer, q)
			quotesReceivedMetric.Add(ctx, 1, metric.WithAttributes(attribute.String("ticker", q.Ticker)))
			publishQuote(ctx, rdb, q)
			if len(buffer) >= batchSize {
				flush(ctx, clickhouseHTTP, &buffer)
			}
		case <-flushTicker.C:
			flush(ctx, clickhouseHTTP, &buffer)
		}
	}
}

func initTelemetry(ctx context.Context, serviceName string, endpoint string) func(context.Context) error {
	meter := otel.Meter("go-ingestion")
	quotesReceivedMetric, _ = meter.Int64Counter("quotes_received_total")
	quotesInsertedMetric, _ = meter.Int64Counter("quotes_inserted_total")
	redisPublishedMetric, _ = meter.Int64Counter("redis_quotes_published_total")
	clickhouseErrorMetric, _ = meter.Int64Counter("clickhouse_insert_errors_total")

	if strings.TrimSpace(endpoint) == "" {
		log.Printf("otel disabled: OTEL_EXPORTER_OTLP_ENDPOINT is empty")
		return func(context.Context) error { return nil }
	}

	res, err := resource.New(ctx,
		resource.WithAttributes(
			attribute.String("service.name", serviceName),
			attribute.String("service.version", "0.1.0"),
		),
	)
	if err != nil {
		log.Printf("otel resource init failed: %v", err)
		return func(context.Context) error { return nil }
	}

	baseURL := strings.TrimRight(endpoint, "/")
	traceExporter, err := otlptracehttp.New(ctx, otlptracehttp.WithEndpointURL(baseURL+"/v1/traces"))
	if err != nil {
		log.Printf("otel trace exporter init failed: %v", err)
		return func(context.Context) error { return nil }
	}
	metricExporter, err := otlpmetrichttp.New(ctx, otlpmetrichttp.WithEndpointURL(baseURL+"/v1/metrics"))
	if err != nil {
		log.Printf("otel metric exporter init failed: %v", err)
		return func(context.Context) error { return traceExporter.Shutdown(context.Background()) }
	}

	traceProvider := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(traceExporter),
		sdktrace.WithResource(res),
	)
	meterProvider := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metricExporter, sdkmetric.WithInterval(10*time.Second))),
		sdkmetric.WithResource(res),
	)

	otel.SetTracerProvider(traceProvider)
	otel.SetMeterProvider(meterProvider)
	tracer = otel.Tracer("go-ingestion")
	meter = otel.Meter("go-ingestion")
	quotesReceivedMetric, _ = meter.Int64Counter("quotes_received_total")
	quotesInsertedMetric, _ = meter.Int64Counter("quotes_inserted_total")
	redisPublishedMetric, _ = meter.Int64Counter("redis_quotes_published_total")
	clickhouseErrorMetric, _ = meter.Int64Counter("clickhouse_insert_errors_total")

	log.Printf("otel enabled: service=%s endpoint=%s", serviceName, endpoint)
	return func(ctx context.Context) error {
		err1 := traceProvider.Shutdown(ctx)
		err2 := meterProvider.Shutdown(ctx)
		if err1 != nil {
			return err1
		}
		return err2
	}
}

func waitForDevice(path string, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for {
		file, err := os.Open(path)
		if err == nil {
			return file.Close()
		}
		if timeout <= 0 || time.Now().After(deadline) {
			return err
		}
		time.Sleep(500 * time.Millisecond)
	}
}

func readDevice(path string, interval time.Duration, out chan<- Quote, allowFallback bool) {
	seen := make(map[string]time.Time)
	emptyReads := 0

	for {
		lines, err := readDeviceSnapshot(path)
		if err != nil {
			if allowFallback {
				log.Printf("device read failed (%v), using fallback generator", err)
				generateFallback(interval, out)
				return
			}
			log.Fatalf("device read failed: %v", err)
		}

		emitted := 0
		for _, line := range lines {
			q, ok := parseDriverLine(line)
			if !ok {
				continue
			}
			key := q.Ticker + "|" + q.Timestamp
			if _, exists := seen[key]; exists {
				continue
			}
			seen[key] = time.Now()
			out <- q
			emitted++
		}

		if emitted == 0 {
			emptyReads++
			if allowFallback && emptyReads >= 3 {
				log.Printf("device produced no quotes, using fallback generator")
				generateFallback(interval, out)
				return
			}
			if !allowFallback && emptyReads >= 3 {
				log.Fatalf("device produced no quotes")
			}
		} else {
			emptyReads = 0
			pruneSeen(seen, 5*time.Minute)
		}

		time.Sleep(interval)
	}
}

func readDeviceSnapshot(path string) ([]string, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	buffer := make([]byte, 32*1024)
	n, err := file.Read(buffer)
	if err != nil && err != io.EOF {
		return nil, err
	}
	if n == 0 {
		return nil, nil
	}

	scanner := bufio.NewScanner(bytes.NewReader(buffer[:n]))
	lines := make([]string, 0, 32)
	for scanner.Scan() {
		lines = append(lines, scanner.Text())
	}
	return lines, scanner.Err()
}

func pruneSeen(seen map[string]time.Time, maxAge time.Duration) {
	if len(seen) < 10000 {
		return
	}
	cutoff := time.Now().Add(-maxAge)
	for key, observedAt := range seen {
		if observedAt.Before(cutoff) {
			delete(seen, key)
		}
	}
	if len(seen) >= 10000 {
		clear(seen)
	}
}

func parseDriverLine(line string) (Quote, bool) {
	fields := strings.Fields(line)
	if len(fields) < 2 || fields[0] == "SYMBOL" || strings.HasPrefix(fields[0], "-") {
		return Quote{}, false
	}

	price, err := strconv.ParseFloat(fields[1], 64)
	if err != nil {
		return Quote{}, false
	}

	ts := time.Now().UTC()
	if len(fields) >= 4 {
		if ns, err := strconv.ParseInt(fields[3], 10, 64); err == nil && ns > 0 {
			ts = time.Unix(0, ns).UTC()
		}
	}

	return Quote{
		Ticker:    strings.ToUpper(fields[0]),
		Price:     round2(price),
		Volume:    uint64(rand.Intn(49900) + 100),
		Timestamp: ts.Format(time.RFC3339Nano),
	}, true
}

func generateFallback(interval time.Duration, out chan<- Quote) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for range ticker.C {
		now := time.Now().UTC().Format(time.RFC3339Nano)
		for i := range fallbackTickers {
			t := &fallbackTickers[i]
			delta := t.Price * t.Volatility * (rand.Float64()*2 - 1)
			t.Price = math.Max(0.01, t.Price+delta)
			out <- Quote{
				Ticker:    t.Ticker,
				Price:     round2(t.Price),
				Volume:    uint64(rand.Intn(49900) + 100),
				Timestamp: now,
			}
		}
	}
}

func flush(ctx context.Context, clickhouseHTTP string, buffer *[]Quote) {
	if len(*buffer) == 0 {
		return
	}
	rows := *buffer
	if err := insertClickHouse(ctx, clickhouseHTTP, rows); err != nil {
		clickhouseErrorMetric.Add(ctx, 1)
		log.Printf("clickhouse insert failed: %v", err)
		return
	}
	quotesInsertedMetric.Add(ctx, int64(len(rows)))
	log.Printf("inserted %d quotes", len(rows))
	*buffer = (*buffer)[:0]
}

func insertClickHouse(ctx context.Context, baseURL string, rows []Quote) error {
	ctx, span := tracer.Start(ctx, "clickhouse.insert_quotes", oteltrace.WithAttributes(attribute.Int("rows", len(rows))))
	defer span.End()

	var body bytes.Buffer
	for _, q := range rows {
		ts, err := time.Parse(time.RFC3339Nano, q.Timestamp)
		if err != nil {
			ts = time.Now().UTC()
		}
		fmt.Fprintf(&body, "%s\t%.2f\t%d\t%s\n", q.Ticker, q.Price, q.Volume, ts.UTC().Format("2006-01-02 15:04:05.000"))
	}

	url := strings.TrimRight(baseURL, "/") + "/?query=INSERT%20INTO%20quotes%20(ticker%2Cprice%2Cvolume%2Ctimestamp)%20FORMAT%20TabSeparated"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, &body)
	if err != nil {
		span.RecordError(err)
		return err
	}
	req.Header.Set("Content-Type", "text/tab-separated-values")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		span.RecordError(err)
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		msg, _ := io.ReadAll(resp.Body)
		err := fmt.Errorf("http %d: %s", resp.StatusCode, strings.TrimSpace(string(msg)))
		span.RecordError(err)
		return err
	}
	return nil
}

func publishQuote(ctx context.Context, rdb *redis.Client, q Quote) {
	payload, err := json.Marshal(q)
	if err != nil {
		log.Printf("json marshal failed: %v", err)
		return
	}
	if err := rdb.Publish(ctx, "quotes:updates", payload).Err(); err != nil {
		log.Printf("redis publish failed: %v", err)
		return
	}
	redisPublishedMetric.Add(ctx, 1, metric.WithAttributes(attribute.String("ticker", q.Ticker)))
}

func getenv(key string, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

func getenvInt(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func getenvBool(key string, fallback bool) bool {
	value := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
	if value == "" {
		return fallback
	}
	return value == "1" || value == "true" || value == "yes" || value == "on"
}

func round2(value float64) float64 {
	return math.Round(value*100) / 100
}
