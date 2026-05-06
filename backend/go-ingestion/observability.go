package main

import (
	"context"
	"log"
	"os"
	"strings"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/metric"
	sdkmetric "go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
	"go.opentelemetry.io/otel/trace"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// Telemetry bundles a TracerProvider, MeterProvider and the domain-level
// metric instruments used across go-ingestion.
type Telemetry struct {
	Tracer trace.Tracer

	QuotesIngested      metric.Int64Counter
	BatchSize           metric.Int64Histogram
	InsertDuration      metric.Float64Histogram
	PublishDuration     metric.Float64Histogram
	InsertFailures      metric.Int64Counter

	shutdown []func(context.Context) error
}

// initTelemetry initialises OpenTelemetry. If OTEL_EXPORTER_OTLP_ENDPOINT is unset
// or the exporter cannot be reached, it returns a no-op telemetry struct so the
// service degrades gracefully.
func initTelemetry(ctx context.Context, serviceName string) *Telemetry {
	endpoint := os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
	if strings.HasPrefix(endpoint, "http://") {
		endpoint = strings.TrimPrefix(endpoint, "http://")
	} else if strings.HasPrefix(endpoint, "https://") {
		endpoint = strings.TrimPrefix(endpoint, "https://")
	}
	if endpoint == "" {
		log.Printf("OTEL: endpoint not configured, telemetry disabled")
		return noopTelemetry(serviceName)
	}

	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceName(serviceName),
			semconv.ServiceVersion("0.1.0"),
		),
		resource.WithFromEnv(),
		resource.WithProcess(),
		resource.WithHost(),
	)
	if err != nil {
		log.Printf("OTEL: resource init failed: %v; using no-op", err)
		return noopTelemetry(serviceName)
	}

	dialOpts := []grpc.DialOption{}
	if strings.EqualFold(os.Getenv("OTEL_EXPORTER_OTLP_INSECURE"), "true") {
		dialOpts = append(dialOpts, grpc.WithTransportCredentials(insecure.NewCredentials()))
	}

	traceExp, err := otlptracegrpc.New(ctx,
		otlptracegrpc.WithEndpoint(endpoint),
		otlptracegrpc.WithDialOption(dialOpts...),
		otlptracegrpc.WithInsecure(),
	)
	if err != nil {
		log.Printf("OTEL: trace exporter init failed: %v; using no-op", err)
		return noopTelemetry(serviceName)
	}

	tp := sdktrace.NewTracerProvider(
		sdktrace.WithBatcher(traceExp, sdktrace.WithBatchTimeout(5*time.Second)),
		sdktrace.WithResource(res),
	)
	otel.SetTracerProvider(tp)

	metricExp, err := otlpmetricgrpc.New(ctx,
		otlpmetricgrpc.WithEndpoint(endpoint),
		otlpmetricgrpc.WithDialOption(dialOpts...),
		otlpmetricgrpc.WithInsecure(),
	)
	if err != nil {
		log.Printf("OTEL: metric exporter init failed: %v; using no-op", err)
		return noopTelemetry(serviceName)
	}

	mp := sdkmetric.NewMeterProvider(
		sdkmetric.WithReader(sdkmetric.NewPeriodicReader(metricExp,
			sdkmetric.WithInterval(15*time.Second),
		)),
		sdkmetric.WithResource(res),
	)
	otel.SetMeterProvider(mp)

	t := &Telemetry{Tracer: tp.Tracer("highload-invest/go-ingestion")}
	meter := mp.Meter("highload-invest/go-ingestion")

	t.QuotesIngested, _ = meter.Int64Counter("quotes.ingested",
		metric.WithDescription("Quotes received from driver/fallback and forwarded"))
	t.BatchSize, _ = meter.Int64Histogram("clickhouse.batch_size",
		metric.WithDescription("Quotes per ClickHouse insert batch"))
	t.InsertDuration, _ = meter.Float64Histogram("clickhouse.insert.duration",
		metric.WithDescription("ClickHouse insert latency"),
		metric.WithUnit("ms"))
	t.PublishDuration, _ = meter.Float64Histogram("redis.publish.duration",
		metric.WithDescription("Redis pubsub publish latency"),
		metric.WithUnit("ms"))
	t.InsertFailures, _ = meter.Int64Counter("clickhouse.insert.failures",
		metric.WithDescription("Number of failed ClickHouse inserts"))

	t.shutdown = []func(context.Context) error{
		tp.Shutdown,
		mp.Shutdown,
	}
	log.Printf("OTEL: initialized (endpoint=%s service=%s)", endpoint, serviceName)
	return t
}

func (t *Telemetry) Shutdown(ctx context.Context) {
	for _, fn := range t.shutdown {
		_ = fn(ctx)
	}
}

func noopTelemetry(serviceName string) *Telemetry {
	tp := otel.GetTracerProvider()
	mp := otel.GetMeterProvider()
	t := &Telemetry{Tracer: tp.Tracer(serviceName)}
	meter := mp.Meter(serviceName)
	t.QuotesIngested, _ = meter.Int64Counter("quotes.ingested")
	t.BatchSize, _ = meter.Int64Histogram("clickhouse.batch_size")
	t.InsertDuration, _ = meter.Float64Histogram("clickhouse.insert.duration")
	t.PublishDuration, _ = meter.Float64Histogram("redis.publish.duration")
	t.InsertFailures, _ = meter.Int64Counter("clickhouse.insert.failures")
	return t
}

// helper to add common attributes
func tickerAttr(t string) attribute.KeyValue {
	return attribute.String("ticker", t)
}
