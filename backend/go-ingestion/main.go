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

func main() {
	ctx := context.Background()
	devicePath := getenv("QUOTES_DEVICE", "/dev/quotes")
	clickhouseHTTP := getenv("CLICKHOUSE_HTTP", "http://localhost:8123")
	redisAddr := getenv("REDIS_ADDR", "localhost:6379")
	batchSize := getenvInt("BATCH_SIZE", 100)
	interval := time.Duration(getenvInt("INTERVAL_MS", 500)) * time.Millisecond

	rdb := redis.NewClient(&redis.Options{Addr: redisAddr})
	defer rdb.Close()

	log.Printf("go-ingestion: clickhouse=%s redis=%s device=%s batch=%d interval=%s", clickhouseHTTP, redisAddr, devicePath, batchSize, interval)

	quotes := make(chan Quote, batchSize*4)
	if devicePath != "" && devicePath != "/dev/null" {
		file, err := os.Open(devicePath)
		if err != nil {
			log.Printf("device %s is not available (%v), using fallback generator", devicePath, err)
			go generateFallback(interval, quotes)
		} else {
			defer file.Close()
			log.Printf("reading quotes from %s", devicePath)
			go readDevice(file, interval, quotes)
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
			publishQuote(ctx, rdb, q)
			if len(buffer) >= batchSize {
				flush(clickhouseHTTP, &buffer)
			}
		case <-flushTicker.C:
			flush(clickhouseHTTP, &buffer)
		}
	}
}

func readDevice(reader io.Reader, fallbackInterval time.Duration, out chan<- Quote) {
	scanner := bufio.NewScanner(reader)
	readRows := 0
	for scanner.Scan() {
		if q, ok := parseDriverLine(scanner.Text()); ok {
			readRows++
			out <- q
		}
	}
	if err := scanner.Err(); err != nil {
		log.Printf("device read failed: %v", err)
	}
	if readRows == 0 {
		log.Printf("device produced no quotes, switching to fallback generator")
		generateFallback(fallbackInterval, out)
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

func flush(clickhouseHTTP string, buffer *[]Quote) {
	if len(*buffer) == 0 {
		return
	}
	rows := *buffer
	if err := insertClickHouse(clickhouseHTTP, rows); err != nil {
		log.Printf("clickhouse insert failed: %v", err)
		return
	}
	log.Printf("inserted %d quotes", len(rows))
	*buffer = (*buffer)[:0]
}

func insertClickHouse(baseURL string, rows []Quote) error {
	var body bytes.Buffer
	for _, q := range rows {
		ts, err := time.Parse(time.RFC3339Nano, q.Timestamp)
		if err != nil {
			ts = time.Now().UTC()
		}
		fmt.Fprintf(&body, "%s\t%.2f\t%d\t%s\n", q.Ticker, q.Price, q.Volume, ts.UTC().Format("2006-01-02 15:04:05.000"))
	}

	url := strings.TrimRight(baseURL, "/") + "/?query=INSERT%20INTO%20quotes%20(ticker%2Cprice%2Cvolume%2Ctimestamp)%20FORMAT%20TabSeparated"
	resp, err := http.Post(url, "text/tab-separated-values", &body)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		msg, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("http %d: %s", resp.StatusCode, strings.TrimSpace(string(msg)))
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
	}
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

func round2(value float64) float64 {
	return math.Round(value*100) / 100
}
