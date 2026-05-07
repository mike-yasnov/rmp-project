package com.highloadinvest.banking.domain.repositories

import com.highloadinvest.banking.domain.entities.*
import java.util.UUID

interface UserRepository {
    suspend fun findById(id: UUID): User?
    suspend fun findByUsername(username: String): User?
    suspend fun create(username: String, email: String): User
}

interface AccountRepository {
    suspend fun findByUserId(userId: UUID): Account?
    suspend fun updateBalance(userId: UUID, newBalance: Double)
    suspend fun deposit(userId: UUID, amount: Double): Account
    suspend fun create(userId: UUID, initialBalance: Double): Account
    suspend fun reservedBalance(userId: UUID): Double
    suspend fun reserveFunds(userId: UUID, amount: Double)
    suspend fun releaseReservation(userId: UUID, amount: Double)
}

interface TradeRepository {
    suspend fun create(trade: Trade): Trade
    suspend fun findByUserId(userId: UUID): List<Trade>
}

interface PortfolioRepository {
    suspend fun getByUserId(userId: UUID): List<PortfolioItem>
    suspend fun updatePosition(userId: UUID, ticker: String, lotsDelta: Int, price: Double)
    suspend fun getLots(userId: UUID, ticker: String): Int
    suspend fun pendingSellLots(userId: UUID, ticker: String): Int
}

interface LimitOrderRepository {
    suspend fun create(order: LimitOrder): LimitOrder
    suspend fun findById(id: UUID): LimitOrder?
    suspend fun findByUserId(userId: UUID, status: OrderStatus? = null): List<LimitOrder>
    suspend fun findPendingForMatching(ticker: String, marketPrice: Double): List<LimitOrder>
    suspend fun markFilled(id: UUID, tradeId: UUID, fillPrice: Double): LimitOrder?
    suspend fun markCancelled(id: UUID): LimitOrder?
    suspend fun markRejected(id: UUID, status: OrderStatus): LimitOrder?
    suspend fun reconcileReservedBalance(): Int
}
