package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.domain.entities.PortfolioItem
import com.highloadinvest.banking.domain.repositories.PortfolioRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class PostgresPortfolioRepository : PortfolioRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun getByUserId(userId: UUID): List<PortfolioItem> {
        logger.debug("getByUserId userId={}", userId)
        val items = mutableListOf<PortfolioItem>()
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("SELECT ticker, lots, avg_price FROM portfolio WHERE user_id = ? AND lots > 0")
                .use { stmt ->
                    stmt.setObject(1, userId)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        items.add(
                            PortfolioItem(
                                ticker = rs.getString("ticker"),
                                lots = rs.getInt("lots"),
                                avgPrice = rs.getDouble("avg_price")
                            )
                        )
                    }
                }
            conn.commit()
        }
        logger.debug("getByUserId userId={} positions={}", userId, items.size)
        return items
    }

    override suspend fun updatePosition(userId: UUID, ticker: String, lotsDelta: Int, price: Double) {
        logger.info("updatePosition userId={} ticker={} lotsDelta={} price={}", userId, ticker, lotsDelta, price)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO portfolio (user_id, ticker, lots, avg_price)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, ticker)
                DO UPDATE SET
                    lots = portfolio.lots + EXCLUDED.lots,
                    avg_price = CASE
                        WHEN EXCLUDED.lots > 0 THEN
                            (portfolio.avg_price * portfolio.lots + EXCLUDED.avg_price * EXCLUDED.lots)
                            / NULLIF(portfolio.lots + EXCLUDED.lots, 0)
                        ELSE portfolio.avg_price
                    END
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, userId)
                stmt.setString(2, ticker)
                stmt.setInt(3, lotsDelta)
                stmt.setDouble(4, price)
                stmt.executeUpdate()
            }
            conn.commit()
        }
    }
}
