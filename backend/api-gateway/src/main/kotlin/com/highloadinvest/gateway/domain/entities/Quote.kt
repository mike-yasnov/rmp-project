package com.highloadinvest.gateway.domain.entities

import java.time.Instant

data class Quote(
    val ticker: String,
    val price: Double,
    val volume: Long,
    val timestamp: Instant,
    val change24h: Double = 0.0,
    val changePercent24h: Double = 0.0
)

data class Candle(
    val ticker: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val timestamp: Instant
)

data class Ticker(
    val symbol: String,
    val name: String
)

enum class CandleInterval(val value: String, val clickhouseExpr: String) {
    M1("1m", "INTERVAL 1 MINUTE"),
    M5("5m", "INTERVAL 5 MINUTE"),
    M15("15m", "INTERVAL 15 MINUTE"),
    H1("1h", "INTERVAL 1 HOUR"),
    D1("1d", "INTERVAL 1 DAY");

    companion object {
        fun fromString(s: String?): CandleInterval? =
            if (s.isNullOrBlank()) M1 else values().firstOrNull { it.value.equals(s, ignoreCase = true) }
    }
}
