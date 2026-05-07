package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderSide
import com.highloadinvest.banking.domain.entities.OrderStatus
import com.highloadinvest.banking.domain.repositories.LimitOrderRepository
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

class PostgresLimitOrderRepository : LimitOrderRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun create(order: LimitOrder): LimitOrder {
        logger.info("create order id={} userId={} {} {} lots={} @ {}",
            order.id, order.userId, order.side, order.ticker, order.lots, order.limitPrice)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO limit_orders
                    (id, user_id, ticker, side, lots, limit_price, status, reserved_amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, order.id)
                stmt.setObject(2, order.userId)
                stmt.setString(3, order.ticker)
                stmt.setString(4, order.side.name)
                stmt.setInt(5, order.lots)
                stmt.setDouble(6, order.limitPrice)
                stmt.setString(7, order.status.name)
                stmt.setDouble(8, order.reservedAmount)
                stmt.setTimestamp(9, Timestamp.from(order.createdAt))
                stmt.executeUpdate()
            }
            conn.commit()
        }
        return order
    }

    override suspend fun findById(id: UUID): LimitOrder? {
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("SELECT * FROM limit_orders WHERE id = ?").use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                val result = if (rs.next()) mapRow(rs) else null
                conn.commit()
                return result
            }
        }
    }

    override suspend fun findByUserId(userId: UUID, status: OrderStatus?): List<LimitOrder> {
        val sql = if (status == null)
            "SELECT * FROM limit_orders WHERE user_id = ? ORDER BY created_at DESC"
        else
            "SELECT * FROM limit_orders WHERE user_id = ? AND status = ? ORDER BY created_at DESC"
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, userId)
                if (status != null) stmt.setString(2, status.name)
                val rs = stmt.executeQuery()
                val list = mutableListOf<LimitOrder>()
                while (rs.next()) list.add(mapRow(rs))
                conn.commit()
                return list
            }
        }
    }

    override suspend fun findPendingForMatching(ticker: String, marketPrice: Double): List<LimitOrder> {
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                """
                SELECT * FROM limit_orders
                WHERE ticker = ? AND status = 'PENDING'
                  AND ((side = 'BUY'  AND limit_price >= ?) OR
                       (side = 'SELL' AND limit_price <= ?))
                ORDER BY created_at ASC
                FOR UPDATE SKIP LOCKED
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, ticker)
                stmt.setDouble(2, marketPrice)
                stmt.setDouble(3, marketPrice)
                val rs = stmt.executeQuery()
                val list = mutableListOf<LimitOrder>()
                while (rs.next()) list.add(mapRow(rs))
                conn.commit()
                return list
            }
        }
    }

    override suspend fun markFilled(id: UUID, tradeId: UUID, fillPrice: Double): LimitOrder? {
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                """
                UPDATE limit_orders
                SET status = 'FILLED', filled_at = NOW(), fill_trade_id = ?, fill_price = ?
                WHERE id = ? AND status = 'PENDING'
                RETURNING *
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, tradeId)
                stmt.setDouble(2, fillPrice)
                stmt.setObject(3, id)
                val rs = stmt.executeQuery()
                val result = if (rs.next()) mapRow(rs) else null
                conn.commit()
                return result
            }
        }
    }

    override suspend fun markCancelled(id: UUID): LimitOrder? {
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                """
                UPDATE limit_orders
                SET status = 'CANCELLED', cancelled_at = NOW()
                WHERE id = ? AND status = 'PENDING'
                RETURNING *
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, id)
                val rs = stmt.executeQuery()
                val result = if (rs.next()) mapRow(rs) else null
                conn.commit()
                return result
            }
        }
    }

    override suspend fun markRejected(id: UUID, status: OrderStatus): LimitOrder? {
        require(status == OrderStatus.INSUFFICIENT_FUNDS || status == OrderStatus.INSUFFICIENT_LOTS)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                """
                UPDATE limit_orders
                SET status = ?, cancelled_at = NOW()
                WHERE id = ? AND status = 'PENDING'
                RETURNING *
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, status.name)
                stmt.setObject(2, id)
                val rs = stmt.executeQuery()
                val result = if (rs.next()) mapRow(rs) else null
                conn.commit()
                return result
            }
        }
    }

    override suspend fun reconcileReservedBalance(): Int {
        DatabaseFactory.connection().use { conn ->
            try {
                val updated = conn.prepareStatement(
                    """
                    UPDATE accounts a
                    SET reserved_balance = COALESCE(t.total, 0)
                    FROM (
                        SELECT user_id, SUM(reserved_amount) AS total
                        FROM limit_orders
                        WHERE status = 'PENDING'
                        GROUP BY user_id
                    ) t
                    WHERE a.user_id = t.user_id
                    """.trimIndent()
                ).use { stmt -> stmt.executeUpdate() }
                // Also zero accounts that have no pending orders but stale reserved balance
                conn.prepareStatement(
                    """
                    UPDATE accounts
                    SET reserved_balance = 0
                    WHERE reserved_balance > 0
                      AND user_id NOT IN (SELECT user_id FROM limit_orders WHERE status = 'PENDING')
                    """.trimIndent()
                ).use { stmt -> stmt.executeUpdate() }
                conn.commit()
                logger.info("reconciled reserved_balance: {} rows updated", updated)
                return updated
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    private fun mapRow(rs: ResultSet): LimitOrder = LimitOrder(
        id = rs.getObject("id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        ticker = rs.getString("ticker"),
        side = OrderSide.valueOf(rs.getString("side")),
        lots = rs.getInt("lots"),
        limitPrice = rs.getDouble("limit_price"),
        status = OrderStatus.valueOf(rs.getString("status")),
        reservedAmount = rs.getDouble("reserved_amount"),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        filledAt = rs.getTimestamp("filled_at")?.toInstant(),
        cancelledAt = rs.getTimestamp("cancelled_at")?.toInstant(),
        fillTradeId = rs.getObject("fill_trade_id", UUID::class.java),
        fillPrice = rs.getObject("fill_price")?.let { (it as Number).toDouble() }
    )
}
