package com.highloadinvest.gateway.infrastructure.clickhouse

import com.highloadinvest.gateway.domain.entities.Candle
import com.highloadinvest.gateway.domain.entities.Quote
import com.highloadinvest.gateway.domain.repositories.QuoteRepository
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

class ClickHouseQuoteRepository(private val jdbcUrl: String) : QuoteRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private fun connection(): Connection {
        logger.debug("Opening ClickHouse connection to {}", jdbcUrl)
        return DriverManager.getConnection(jdbcUrl)
    }

    override suspend fun getLatestQuotes(): List<Quote> {
        val start = System.currentTimeMillis()
        logger.debug("getLatestQuotes() executing query")
        val quotes = mutableListOf<Quote>()
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(
                    """
                    SELECT ticker, price, volume, timestamp
                    FROM quotes
                    ORDER BY timestamp DESC
                    LIMIT 1 BY ticker
                    """.trimIndent()
                )
                while (rs.next()) {
                    quotes.add(
                        Quote(
                            ticker = rs.getString("ticker"),
                            price = rs.getDouble("price"),
                            volume = rs.getLong("volume"),
                            timestamp = rs.getTimestamp("timestamp").toInstant()
                        )
                    )
                }
            }
        }
        val elapsed = System.currentTimeMillis() - start
        logger.info("getLatestQuotes() returned {} quotes in {}ms", quotes.size, elapsed)
        return quotes
    }

    override suspend fun getQuoteByTicker(ticker: String): Quote? {
        val start = System.currentTimeMillis()
        logger.debug("getQuoteByTicker() ticker={}", ticker)
        var quote: Quote? = null
        connection().use { conn ->
            conn.prepareStatement(
                "SELECT ticker, price, volume, timestamp FROM quotes WHERE ticker = ? ORDER BY timestamp DESC LIMIT 1"
            ).use { stmt ->
                stmt.setString(1, ticker)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    quote = Quote(
                        ticker = rs.getString("ticker"),
                        price = rs.getDouble("price"),
                        volume = rs.getLong("volume"),
                        timestamp = rs.getTimestamp("timestamp").toInstant()
                    )
                }
            }
        }
        val elapsed = System.currentTimeMillis() - start
        logger.info("getQuoteByTicker({}) found={} in {}ms", ticker, quote != null, elapsed)
        return quote
    }

    override suspend fun getCandles(ticker: String, from: Long, to: Long): List<Candle> {
        val start = System.currentTimeMillis()
        logger.debug("getCandles() ticker={} from={} to={}", ticker, from, to)
        val candles = mutableListOf<Candle>()
        connection().use { conn ->
            conn.prepareStatement(
                """
                SELECT
                    ticker,
                    toStartOfMinute(timestamp) as ts,
                    argMin(price, timestamp) as open,
                    max(price) as high,
                    min(price) as low,
                    argMax(price, timestamp) as close,
                    sum(volume) as volume
                FROM quotes
                WHERE ticker = ? AND timestamp BETWEEN fromUnixTimestamp(?) AND fromUnixTimestamp(?)
                GROUP BY ticker, ts
                ORDER BY ts
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, ticker)
                stmt.setLong(2, from)
                stmt.setLong(3, to)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    candles.add(
                        Candle(
                            ticker = rs.getString("ticker"),
                            open = rs.getDouble("open"),
                            high = rs.getDouble("high"),
                            low = rs.getDouble("low"),
                            close = rs.getDouble("close"),
                            volume = rs.getLong("volume"),
                            timestamp = rs.getTimestamp("ts").toInstant()
                        )
                    )
                }
            }
        }
        val elapsed = System.currentTimeMillis() - start
        logger.info("getCandles({}) returned {} candles in {}ms", ticker, candles.size, elapsed)
        return candles
    }
}
