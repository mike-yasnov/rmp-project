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
}

interface TradeRepository {
    suspend fun create(trade: Trade): Trade
    suspend fun findByUserId(userId: UUID): List<Trade>
}

interface PortfolioRepository {
    suspend fun getByUserId(userId: UUID): List<PortfolioItem>
    suspend fun updatePosition(userId: UUID, ticker: String, lotsDelta: Int, price: Double)
}
