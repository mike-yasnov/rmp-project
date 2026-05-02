package com.highloadinvest.banking.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class TradeRequest(
    val userId: String,
    val ticker: String,
    val action: String,
    val lots: Int,
    val pricePerLot: Double
)

@Serializable
data class TradeResponse(
    val id: String,
    val userId: String,
    val ticker: String,
    val action: String,
    val lots: Int,
    val pricePerLot: Double,
    val totalAmount: Double,
    val createdAt: String
)

@Serializable
data class PortfolioResponse(
    val balance: Double,
    val currency: String,
    val positions: List<PositionResponse>
)

@Serializable
data class PositionResponse(
    val ticker: String,
    val lots: Int,
    val avgPrice: Double
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val status: Int
)
