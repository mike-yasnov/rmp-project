package com.highloadinvest.nativeapp

import kotlinx.serialization.Serializable

@Serializable
data class QuoteResponse(
    val ticker: String,
    val price: Double,
    val volume: Long,
    val timestamp: String
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
data class CreateUserRequest(
    val username: String,
    val email: String,
    val initialBalance: Double
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    val balance: Double
)

@Serializable
data class DepositRequest(val amount: Double)

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
