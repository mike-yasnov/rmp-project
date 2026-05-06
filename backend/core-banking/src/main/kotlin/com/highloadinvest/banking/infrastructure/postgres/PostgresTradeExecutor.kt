package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.application.usecases.TradeExecutor
import com.highloadinvest.banking.domain.entities.Trade
import com.highloadinvest.banking.domain.entities.TradeAction
import com.highloadinvest.banking.infrastructure.observability.BankingMetrics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresTradeExecutor(
    openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
    private val metrics: BankingMetrics? = null
) : TradeExecutor {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val tracer: Tracer = openTelemetry.getTracer("com.highloadinvest.banking.trade")

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

        val span = tracer.spanBuilder("trade.execute")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("trade.id", trade.id.toString())
            .setAttribute("trade.user_id", trade.userId.toString())
            .setAttribute("trade.ticker", trade.ticker)
            .setAttribute("trade.action", trade.action.name)
            .setAttribute("trade.lots", trade.lots.toLong())
            .setAttribute("trade.total_amount", trade.totalAmount)
            .startSpan()

        val attrs = Attributes.of(
            AttributeKey.stringKey("action"), trade.action.name,
            AttributeKey.stringKey("ticker"), trade.ticker
        )
        val start = System.currentTimeMillis()

        logger.info(
            "execute trade transaction userId={} ticker={} action={} lots={} total={}",
            trade.userId,
            trade.ticker,
            trade.action,
            trade.lots,
            trade.totalAmount
        )

        return span.makeCurrent().use { _ ->
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
                    metrics?.tradesSuccess?.add(1, attrs)
                    val elapsed = (System.currentTimeMillis() - start).toDouble()
                    metrics?.tradeDuration?.record(elapsed, attrs)
                    span.setStatus(StatusCode.OK)
                    trade
                } catch (e: Throwable) {
                    conn.rollback()
                    metrics?.tradesFailed?.add(1, attrs)
                    span.recordException(e)
                    span.setStatus(StatusCode.ERROR, e.message ?: "")
                    throw e
                } finally {
                    span.end()
                }
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
