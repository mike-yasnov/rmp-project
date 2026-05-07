package com.highloadinvest.gateway.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class QuoteResponse(
    val ticker: String,
    val price: Double,
    val volume: Long,
    val timestamp: String,
    val change24h: Double = 0.0,
    val changePercent24h: Double = 0.0
)

@Serializable
data class CandleResponse(
    val ticker: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long,
    val timestamp: String
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)
