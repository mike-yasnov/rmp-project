package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.domain.entities.Trade
import com.highloadinvest.banking.domain.entities.TradeAction
import com.highloadinvest.banking.domain.repositories.TradeRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class PostgresTradeRepository : TradeRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun create(trade: Trade): Trade {
        logger.info("create trade id={} userId={} ticker={}", trade.id, trade.userId, trade.ticker)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                "INSERT INTO trades (id, user_id, ticker, action, lots, price_per_lot, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setObject(1, trade.id)
                stmt.setObject(2, trade.userId)
                stmt.setString(3, trade.ticker)
                stmt.setString(4, trade.action.name)
                stmt.setInt(5, trade.lots)
                stmt.setDouble(6, trade.pricePerLot)
                stmt.setDouble(7, trade.totalAmount)
                stmt.executeUpdate()
            }
            conn.commit()
        }
        return trade
    }

    override suspend fun findByUserId(userId: UUID): List<Trade> {
        logger.debug("findByUserId userId={}", userId)
        val trades = mutableListOf<Trade>()
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("SELECT * FROM trades WHERE user_id = ? ORDER BY created_at DESC")
                .use { stmt ->
                    stmt.setObject(1, userId)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        trades.add(
                            Trade(
                                id = rs.getObject("id", UUID::class.java),
                                userId = rs.getObject("user_id", UUID::class.java),
                                ticker = rs.getString("ticker"),
                                action = TradeAction.valueOf(rs.getString("action")),
                                lots = rs.getInt("lots"),
                                pricePerLot = rs.getDouble("price_per_lot"),
                                totalAmount = rs.getDouble("total_amount"),
                                createdAt = rs.getTimestamp("created_at").toInstant()
                            )
                        )
                    }
                }
            conn.commit()
        }
        logger.debug("findByUserId userId={} count={}", userId, trades.size)
        return trades
    }
}
