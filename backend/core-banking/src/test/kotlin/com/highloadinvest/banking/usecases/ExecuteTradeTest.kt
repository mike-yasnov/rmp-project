package com.highloadinvest.banking.usecases

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.domain.entities.*
import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.PortfolioRepository
import com.highloadinvest.banking.domain.repositories.TradeRepository
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecuteTradeTest {

    private val userId = UUID.randomUUID()

    private val mockAccountRepo = object : AccountRepository {
        var balance = 10000.0
        override suspend fun findByUserId(userId: UUID) = Account(UUID.randomUUID(), userId, balance)
        override suspend fun updateBalance(userId: UUID, newBalance: Double) { balance = newBalance }
        override suspend fun deposit(userId: UUID, amount: Double): Account {
            balance += amount
            return Account(UUID.randomUUID(), userId, balance)
        }
        override suspend fun create(userId: UUID, initialBalance: Double) = Account(UUID.randomUUID(), userId, initialBalance)
    }

    private val mockTradeRepo = object : TradeRepository {
        val trades = mutableListOf<Trade>()
        override suspend fun create(trade: Trade): Trade { trades.add(trade); return trade }
        override suspend fun findByUserId(userId: UUID) = trades.filter { it.userId == userId }
    }

    private val mockPortfolioRepo = object : PortfolioRepository {
        val positions = mutableMapOf<String, Int>()
        override suspend fun getByUserId(userId: UUID) = positions.map { PortfolioItem(it.key, it.value, 0.0) }
        override suspend fun updatePosition(userId: UUID, ticker: String, lotsDelta: Int, price: Double) {
            positions[ticker] = (positions[ticker] ?: 0) + lotsDelta
        }
    }

    private val executeTrade = ExecuteTrade(mockAccountRepo, mockTradeRepo, mockPortfolioRepo)

    @Test
    fun `buy trade deducts balance and adds position`() = runBlocking {
        val request = ExecuteTrade.Request(userId, "GAZP", TradeAction.BUY, 10, 150.0)
        val trade = executeTrade.execute(request)

        assertEquals("GAZP", trade.ticker)
        assertEquals(10, trade.lots)
        assertEquals(1500.0, trade.totalAmount)
        assertEquals(8500.0, mockAccountRepo.balance)
        assertEquals(10, mockPortfolioRepo.positions["GAZP"])
    }

    @Test
    fun `sell trade adds balance`() = runBlocking {
        mockAccountRepo.balance = 5000.0
        val request = ExecuteTrade.Request(userId, "SBER", TradeAction.SELL, 5, 200.0)
        val trade = executeTrade.execute(request)

        assertEquals(6000.0, mockAccountRepo.balance)
        assertEquals(-5, mockPortfolioRepo.positions["SBER"])
    }

    @Test
    fun `insufficient funds throws exception`() = runBlocking {
        mockAccountRepo.balance = 100.0
        val request = ExecuteTrade.Request(userId, "GAZP", TradeAction.BUY, 10, 150.0)

        assertFailsWith<IllegalArgumentException> {
            executeTrade.execute(request)
        }
    }

    @Test
    fun `zero lots throws exception`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            executeTrade.execute(ExecuteTrade.Request(userId, "GAZP", TradeAction.BUY, 0, 100.0))
        }
    }
}
