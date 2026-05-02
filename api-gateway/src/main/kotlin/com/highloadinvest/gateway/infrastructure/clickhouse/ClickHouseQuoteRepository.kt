package com.highloadinvest.gateway.infrastructure.clickhouse

import com.highloadinvest.gateway.domain.entities.Candle
import com.highloadinvest.gateway.domain.entities.Quote
import com.highloadinvest.gateway.domain.repositories.QuoteRepository
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CH_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]")

class ClickHouseQuoteRepository(private val httpUrl: String) : QuoteRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    private fun query(sql: String): String {
        val url = URI("$httpUrl/?query=${java.net.URLEncoder.encode(sql, "UTF-8")}").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 10000
        val code = conn.responseCode
        if (code != 200) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw RuntimeException("ClickHouse HTTP $code: $err")
        }
        return conn.inputStream.bufferedReader().readText()
    }

    override suspend fun getLatestQuotes(): List<Quote> {
        val start = System.currentTimeMillis()
        logger.debug("getLatestQuotes()")
        val result = query("""
            SELECT ticker, price, volume, timestamp
            FROM quotes
            ORDER BY timestamp DESC
            LIMIT 1 BY ticker
            FORMAT JSONEachRow
        """.trimIndent())
        val quotes = parseQuotes(result)
        logger.info("getLatestQuotes() returned {} quotes in {}ms", quotes.size, System.currentTimeMillis() - start)
        return quotes
    }

    override suspend fun getQuoteByTicker(ticker: String): Quote? {
        val start = System.currentTimeMillis()
        val result = query("SELECT ticker, price, volume, timestamp FROM quotes WHERE ticker='$ticker' ORDER BY timestamp DESC LIMIT 1 FORMAT JSONEachRow")
        val quotes = parseQuotes(result)
        logger.info("getQuoteByTicker({}) found={} in {}ms", ticker, quotes.isNotEmpty(), System.currentTimeMillis() - start)
        return quotes.firstOrNull()
    }

    override suspend fun getCandles(ticker: String, from: Long, to: Long): List<Candle> {
        val start = System.currentTimeMillis()
        val result = query("""
            SELECT
                '$ticker' as ticker,
                toStartOfMinute(timestamp) as ts,
                argMin(price, timestamp) as open,
                max(price) as high,
                min(price) as low,
                argMax(price, timestamp) as close,
                sum(volume) as volume
            FROM quotes
            WHERE ticker='$ticker' AND timestamp BETWEEN fromUnixTimestamp($from) AND fromUnixTimestamp($to)
            GROUP BY ts
            ORDER BY ts
            FORMAT JSONEachRow
        """.trimIndent())
        val candles = parseCandles(result)
        logger.info("getCandles({}) returned {} in {}ms", ticker, candles.size, System.currentTimeMillis() - start)
        return candles
    }

    private fun parseQuotes(jsonEachRow: String): List<Quote> {
        if (jsonEachRow.isBlank()) return emptyList()
        return jsonEachRow.trim().lines().mapNotNull { line ->
            try {
                val obj = org.json.JSONObject(line)
                Quote(
                    ticker = obj.getString("ticker"),
                    price = obj.getDouble("price"),
                    volume = obj.getLong("volume"),
                    timestamp = LocalDateTime.parse(obj.getString("timestamp"), CH_TS).toInstant(ZoneOffset.UTC)
                )
            } catch (e: Exception) {
                logger.warn("Failed to parse quote: {}", e.message)
                null
            }
        }
    }

    private fun parseCandles(jsonEachRow: String): List<Candle> {
        if (jsonEachRow.isBlank()) return emptyList()
        return jsonEachRow.trim().lines().mapNotNull { line ->
            try {
                val obj = org.json.JSONObject(line)
                Candle(
                    ticker = obj.getString("ticker"),
                    open = obj.getDouble("open"),
                    high = obj.getDouble("high"),
                    low = obj.getDouble("low"),
                    close = obj.getDouble("close"),
                    volume = obj.getLong("volume"),
                    timestamp = LocalDateTime.parse(obj.getString("ts"), CH_TS).toInstant(ZoneOffset.UTC)
                )
            } catch (e: Exception) {
                logger.warn("Failed to parse candle: {}", e.message)
                null
            }
        }
    }
}
