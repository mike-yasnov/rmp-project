package com.highloadinvest.banking.infrastructure.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter

/**
 * Domain-specific metrics for core-banking.
 */
class BankingMetrics(otel: OpenTelemetry) {
    private val meter = otel.getMeter("com.highloadinvest.banking")

    val tradesSuccess: LongCounter = meter
        .counterBuilder("trades.success")
        .setDescription("Successful trade executions")
        .setUnit("{trades}")
        .build()

    val tradesFailed: LongCounter = meter
        .counterBuilder("trades.failed")
        .setDescription("Failed trade executions (validation, balance, etc.)")
        .setUnit("{trades}")
        .build()

    val tradeDuration: DoubleHistogram = meter
        .histogramBuilder("trade.execute.duration")
        .setDescription("Trade execution latency (incl. PG transaction)")
        .setUnit("ms")
        .build()

    val dbQueryDuration: DoubleHistogram = meter
        .histogramBuilder("db.query.duration")
        .setDescription("PostgreSQL query latency")
        .setUnit("ms")
        .build()

    val usersCreated: LongCounter = meter
        .counterBuilder("users.created")
        .setDescription("Users created via API")
        .setUnit("{users}")
        .build()
}
