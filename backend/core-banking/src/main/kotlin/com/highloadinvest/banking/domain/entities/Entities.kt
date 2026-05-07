package com.highloadinvest.banking.domain.entities

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val createdAt: Instant
)

data class Account(
    val id: UUID,
    val userId: UUID,
    val balance: Double,
    val currency: String = "RUB"
)

data class Trade(
    val id: UUID,
    val userId: UUID,
    val ticker: String,
    val action: TradeAction,
    val lots: Int,
    val pricePerLot: Double,
    val totalAmount: Double,
    val createdAt: Instant
)

enum class TradeAction { BUY, SELL }

data class PortfolioItem(
    val ticker: String,
    val lots: Int,
    val avgPrice: Double
)

enum class OrderSide { BUY, SELL }

enum class OrderStatus { PENDING, FILLED, CANCELLED, INSUFFICIENT_FUNDS, INSUFFICIENT_LOTS }

data class LimitOrder(
    val id: UUID,
    val userId: UUID,
    val ticker: String,
    val side: OrderSide,
    val lots: Int,
    val limitPrice: Double,
    val status: OrderStatus,
    val reservedAmount: Double,
    val createdAt: Instant,
    val filledAt: Instant? = null,
    val cancelledAt: Instant? = null,
    val fillTradeId: UUID? = null,
    val fillPrice: Double? = null
)
