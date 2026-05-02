package com.highloadinvest.gateway.domain.entities

import kotlinx.serialization.Serializable
import java.time.Instant

data class Quote(
    val ticker: String,
    val price: Double,
    val volume: Long,
    val timestamp: Instant
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
