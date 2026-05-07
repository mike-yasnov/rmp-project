package com.highloadinvest.gateway.infrastructure.clickhouse

import com.highloadinvest.gateway.domain.entities.Candle
import com.highloadinvest.gateway.domain.entities.CandleInterval
import com.highloadinvest.gateway.domain.entities.Quote
import com.highloadinvest.gateway.domain.repositories.QuoteRepository
import com.highloadinvest.gateway.infrastructure.observability.GatewayMetrics
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CH_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]")

class ClickHouseQuoteRepository(
    private val httpUrl: String,
    openTelemetry: OpenTelemetry,
    private val metrics: GatewayMetrics
) : QuoteRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val tracer: Tracer = openTelemetry.getTracer("com.highloadinvest.gateway.clickhouse")

    private fun <T> traced(operation: String, sql: String, block: () -> T): T {
        val span = tracer.spanBuilder("clickhouse.$operation")
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("db.system", "clickhouse")
            .setAttribute("db.statement", sql.take(500))
            .startSpan()
        val start = System.currentTimeMillis()
        return try {
            span.makeCurrent().use { _ -> block() }
        } catch (e: Exception) {
            span.recordException(e)
            span.setStatus(StatusCode.ERROR, e.message ?: "")
            throw e
        } finally {
            val elapsed = (System.currentTimeMillis() - start).toDouble()
            metrics.clickhouseQueryDuration.record(elapsed, Attributes.of(AttributeKey.stringKey("operation"), operation))
            span.end()
        }
    }

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

    override suspend fun getLatestQuotes(): List<Quote> = traced("getLatestQuotes", "SELECT ... with 24h change") {
        val start = System.currentTimeMillis()
        logger.debug("getLatestQuotes() with 24h change")
        // Latest price per ticker LEFT JOIN price 24h ago — single round-trip
        val result = query("""
            SELECT
                latest.ticker AS ticker,
                latest.price AS price,
                latest.volume AS volume,
                latest.timestamp AS timestamp,
                if(isNull(prev.price), 0, latest.price - prev.price) AS change24h,
                if(isNull(prev.price) OR prev.price = 0, 0, (latest.price - prev.price) / prev.price * 100) AS changePercent24h
            FROM
                (SELECT ticker, price, volume, timestamp FROM quotes ORDER BY timestamp DESC LIMIT 1 BY ticker) AS latest
            LEFT JOIN
                (SELECT ticker, argMin(price, timestamp) AS price FROM quotes
                 WHERE timestamp >= now() - INTERVAL 24 HOUR
                 GROUP BY ticker) AS prev
            ON latest.ticker = prev.ticker
            FORMAT JSONEachRow
        """.trimIndent())
        val quotes = parseQuotes(result)
        logger.info("getLatestQuotes() returned {} quotes in {}ms", quotes.size, System.currentTimeMillis() - start)
        quotes
    }

    override suspend fun getQuoteByTicker(ticker: String): Quote? = traced("getQuoteByTicker", "SELECT ... WHERE ticker=?") {
        val start = System.currentTimeMillis()
        val safe = ticker.replace("'", "")
        val result = query("""
            SELECT
                latest.ticker AS ticker,
                latest.price AS price,
                latest.volume AS volume,
                latest.timestamp AS timestamp,
                if(isNull(prev.price), 0, latest.price - prev.price) AS change24h,
                if(isNull(prev.price) OR prev.price = 0, 0, (latest.price - prev.price) / prev.price * 100) AS changePercent24h
            FROM
                (SELECT ticker, price, volume, timestamp FROM quotes WHERE ticker='$safe' ORDER BY timestamp DESC LIMIT 1) AS latest
            LEFT JOIN
                (SELECT ticker, argMin(price, timestamp) AS price FROM quotes
                 WHERE ticker='$safe' AND timestamp >= now() - INTERVAL 24 HOUR
                 GROUP BY ticker) AS prev
            ON latest.ticker = prev.ticker
            FORMAT JSONEachRow
        """.trimIndent())
        val quotes = parseQuotes(result)
        logger.info("getQuoteByTicker({}) found={} in {}ms", ticker, quotes.isNotEmpty(), System.currentTimeMillis() - start)
        quotes.firstOrNull()
    }

    override suspend fun getCandles(ticker: String, from: Long, to: Long, interval: CandleInterval): List<Candle> =
        traced("getCandles", "toStartOfInterval($interval) GROUP BY ts") {
            val start = System.currentTimeMillis()
            val safe = ticker.replace("'", "")
            val result = query("""
                SELECT
                    '$safe' as ticker,
                    toStartOfInterval(timestamp, ${interval.clickhouseExpr}) as ts,
                    argMin(price, timestamp) as open,
                    max(price) as high,
                    min(price) as low,
                    argMax(price, timestamp) as close,
                    sum(volume) as volume
                FROM quotes
                WHERE ticker='$safe' AND timestamp BETWEEN fromUnixTimestamp($from) AND fromUnixTimestamp($to)
                GROUP BY ts
                ORDER BY ts
                FORMAT JSONEachRow
            """.trimIndent())
            val candles = parseCandles(result)
            logger.info("getCandles({}, {}) returned {} in {}ms", ticker, interval.value, candles.size, System.currentTimeMillis() - start)
            candles
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
                    timestamp = LocalDateTime.parse(obj.getString("timestamp"), CH_TS).toInstant(ZoneOffset.UTC),
                    change24h = obj.optDouble("change24h", 0.0),
                    changePercent24h = obj.optDouble("changePercent24h", 0.0)
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
