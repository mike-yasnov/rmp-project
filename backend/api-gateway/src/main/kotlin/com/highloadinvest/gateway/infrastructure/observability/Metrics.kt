package com.highloadinvest.gateway.infrastructure.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.DoubleHistogram

/**
 * Domain-specific metrics emitted by api-gateway.
 *
 * Built on top of the OpenTelemetry MeterProvider; export goes through OTLP collector
 * → Prometheus exporter → Grafana dashboard.
 */
class GatewayMetrics(otel: OpenTelemetry) {
    private val meter = otel.getMeter("com.highloadinvest.gateway")

    val wsConnections: LongUpDownCounter = meter
        .upDownCounterBuilder("ws.active_connections")
        .setDescription("Number of active WebSocket clients subscribed to /ws/quotes")
        .setUnit("{connections}")
        .build()

    val wsMessagesBroadcast: LongCounter = meter
        .counterBuilder("ws.messages.broadcast")
        .setDescription("Quote update messages broadcast to WebSocket clients")
        .setUnit("{messages}")
        .build()

    val redisMessagesReceived: LongCounter = meter
        .counterBuilder("redis.pubsub.messages")
        .setDescription("Messages received from Redis quotes:updates channel")
        .setUnit("{messages}")
        .build()

    val clickhouseQueryDuration: DoubleHistogram = meter
        .histogramBuilder("clickhouse.query.duration")
        .setDescription("ClickHouse query latency")
        .setUnit("ms")
        .build()

    val bankingProxyDuration: DoubleHistogram = meter
        .histogramBuilder("banking.proxy.duration")
        .setDescription("Latency of api-gateway → core-banking calls")
        .setUnit("ms")
        .build()
}
