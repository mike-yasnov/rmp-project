package com.highloadinvest.nativeapp.domain

import kotlinx.serialization.Serializable

@Serializable
data class User(val id: String, val username: String, val email: String, val balance: Double)

@Serializable
data class Quote(
    val ticker: String, val price: Double, val volume: Long = 0,
    val timestamp: String = "", val change24h: Double = 0.0, val changePercent24h: Double = 0.0
)

@Serializable
data class Candle(
    val ticker: String, val open: Double, val high: Double, val low: Double,
    val close: Double, val volume: Long, val timestamp: String
)

@Serializable
data class Position(
    val ticker: String, val lots: Int, val avgPrice: Double,
    val currentPrice: Double = 0.0, val marketValue: Double = 0.0,
    val unrealizedPnl: Double = 0.0, val unrealizedPnlPercent: Double = 0.0
)

@Serializable
data class PortfolioTotals(
    val invested: Double, val marketValue: Double,
    val unrealizedPnl: Double, val unrealizedPnlPercent: Double
)

@Serializable
data class Portfolio(
    val balance: Double, val reservedBalance: Double = 0.0, val availableBalance: Double = 0.0,
    val currency: String, val positions: List<Position>,
    val totals: PortfolioTotals = PortfolioTotals(0.0, 0.0, 0.0, 0.0)
)

@Serializable
data class Order(
    val id: String, val userId: String, val ticker: String, val side: String,
    val lots: Int, val limitPrice: Double, val status: String, val reservedAmount: Double,
    val createdAt: String, val filledAt: String? = null, val cancelledAt: String? = null,
    val fillTradeId: String? = null, val fillPrice: Double? = null
)

enum class Timeframe(val label: String, val interval: String, val seconds: Long) {
    Week("1Н", "5m", 7L * 24 * 3600),
    Month("1М", "15m", 30L * 24 * 3600),
    HalfYear("6М", "1h", 180L * 24 * 3600),
    Year("1Г", "1d", 365L * 24 * 3600),
    All("ВСЁ", "1d", 5L * 365 * 24 * 3600)
}

enum class ChartType { Line, Candle }
