package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.application.usecases.TradeExecutor
import com.highloadinvest.banking.domain.entities.Trade
import com.highloadinvest.banking.domain.entities.TradeAction
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresTradeExecutor : TradeExecutor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun execute(request: ExecuteTrade.Request): Trade {
        require(request.lots > 0) { "Lots must be positive" }
        require(request.pricePerLot > 0) { "Price must be positive" }

        val totalAmount = request.lots * request.pricePerLot
        val trade = Trade(
            id = UUID.randomUUID(),
            userId = request.userId,
            ticker = request.ticker.uppercase(),
            action = request.action,
            lots = request.lots,
            pricePerLot = request.pricePerLot,
            totalAmount = totalAmount,
            createdAt = Instant.now()
        )

        logger.info(
            "execute trade transaction userId={} ticker={} action={} lots={} total={}",
            trade.userId,
            trade.ticker,
            trade.action,
            trade.lots,
            trade.totalAmount
        )

        DatabaseFactory.connection().use { conn ->
            try {
                conn.transactionIsolation = Connection.TRANSACTION_READ_COMMITTED

                val balance = selectBalanceForUpdate(conn, trade.userId)
                when (trade.action) {
                    TradeAction.BUY -> {
                        if (balance < totalAmount) {
                            throw IllegalArgumentException("Insufficient funds: balance=$balance, required=$totalAmount")
                        }
                        updateBalance(conn, trade.userId, balance - totalAmount)
                        upsertBoughtPosition(conn, trade.userId, trade.ticker, trade.lots, trade.pricePerLot)
                    }

                    TradeAction.SELL -> {
                        val currentLots = selectLotsForUpdate(conn, trade.userId, trade.ticker)
                        if (currentLots < trade.lots) {
                            throw IllegalArgumentException("Insufficient lots: have=$currentLots, required=${trade.lots}")
                        }
                        updateBalance(conn, trade.userId, balance + totalAmount)
                        reducePosition(conn, trade.userId, trade.ticker, trade.lots)
                    }
                }

                insertTrade(conn, trade)
                conn.commit()
                return trade
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    private fun selectBalanceForUpdate(conn: Connection, userId: UUID): Double =
        conn.prepareStatement("SELECT balance FROM accounts WHERE user_id = ? FOR UPDATE").use { stmt ->
            stmt.setObject(1, userId)
            val rs = stmt.executeQuery()
            if (!rs.next()) {
                throw NoSuchElementException("Account not found for user $userId")
            }
            rs.getDouble("balance")
        }

    private fun selectLotsForUpdate(conn: Connection, userId: UUID, ticker: String): Int =
        conn.prepareStatement("SELECT lots FROM portfolio WHERE user_id = ? AND ticker = ? FOR UPDATE").use { stmt ->
            stmt.setObject(1, userId)
            stmt.setString(2, ticker)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.getInt("lots") else 0
        }

    private fun updateBalance(conn: Connection, userId: UUID, newBalance: Double) {
        conn.prepareStatement("UPDATE accounts SET balance = ? WHERE user_id = ?").use { stmt ->
            stmt.setDouble(1, newBalance)
            stmt.setObject(2, userId)
            stmt.executeUpdate()
        }
    }

    private fun upsertBoughtPosition(conn: Connection, userId: UUID, ticker: String, lots: Int, price: Double) {
        conn.prepareStatement(
            """
            INSERT INTO portfolio (user_id, ticker, lots, avg_price)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, ticker)
            DO UPDATE SET
                lots = portfolio.lots + EXCLUDED.lots,
                avg_price =
                    (portfolio.avg_price * portfolio.lots + EXCLUDED.avg_price * EXCLUDED.lots)
                    / NULLIF(portfolio.lots + EXCLUDED.lots, 0)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, userId)
            stmt.setString(2, ticker)
            stmt.setInt(3, lots)
            stmt.setDouble(4, price)
            stmt.executeUpdate()
        }
    }

    private fun reducePosition(conn: Connection, userId: UUID, ticker: String, lots: Int) {
        conn.prepareStatement(
            """
            UPDATE portfolio
            SET lots = lots - ?
            WHERE user_id = ? AND ticker = ?
            """.trimIndent()
        ).use { stmt ->
            stmt.setInt(1, lots)
            stmt.setObject(2, userId)
            stmt.setString(3, ticker)
            stmt.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM portfolio WHERE user_id = ? AND ticker = ? AND lots = 0").use { stmt ->
            stmt.setObject(1, userId)
            stmt.setString(2, ticker)
            stmt.executeUpdate()
        }
    }

    private fun insertTrade(conn: Connection, trade: Trade) {
        conn.prepareStatement(
            """
            INSERT INTO trades (id, user_id, ticker, action, lots, price_per_lot, total_amount, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, trade.id)
            stmt.setObject(2, trade.userId)
            stmt.setString(3, trade.ticker)
            stmt.setString(4, trade.action.name)
            stmt.setInt(5, trade.lots)
            stmt.setDouble(6, trade.pricePerLot)
            stmt.setDouble(7, trade.totalAmount)
            stmt.setTimestamp(8, Timestamp.from(trade.createdAt))
            stmt.executeUpdate()
        }
    }
}
