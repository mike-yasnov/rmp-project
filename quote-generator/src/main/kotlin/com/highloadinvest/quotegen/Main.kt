package com.highloadinvest.quotegen

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.random.Random

val CH_TS_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC)

private val logger = LoggerFactory.getLogger("QuoteGenerator")

@Serializable
data class QuoteUpdate(val ticker: String, val price: Double, val volume: Long, val timestamp: String)

data class TickerState(val ticker: String, var price: Double, val volatility: Double)

val TICKERS = listOf(
    TickerState("GAZP", 162.50, 0.02),
    TickerState("SBER", 258.70, 0.015),
    TickerState("LKOH", 7150.00, 0.018),
    TickerState("YNDX", 3920.00, 0.025),
    TickerState("ROSN", 520.30, 0.02),
    TickerState("GMKN", 15800.00, 0.022),
    TickerState("MTSS", 310.50, 0.012),
    TickerState("VKCO", 680.00, 0.03),
    TickerState("TCSG", 2850.00, 0.025),
    TickerState("PLZL", 12500.00, 0.02)
)

fun clickhouseInsert(httpUrl: String, rows: List<QuoteUpdate>) {
    val tsv = rows.joinToString("\n") { q ->
        "${q.ticker}\t${q.price}\t${q.volume}\t${CH_TS_FMT.format(Instant.parse(q.timestamp))}"
    }
    val url = URI("$httpUrl/?query=INSERT+INTO+quotes+(ticker,price,volume,timestamp)+FORMAT+TabSeparated").toURL()
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.outputStream.use { it.write(tsv.toByteArray()) }
    val code = conn.responseCode
    if (code != 200) {
        val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
        throw RuntimeException("ClickHouse HTTP $code: $err")
    }
}

fun main() {
    val chHttp = System.getenv("CLICKHOUSE_HTTP") ?: "http://localhost:8123"
    val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
    val redisPort = (System.getenv("REDIS_PORT") ?: "6379").toInt()
    val intervalMs = (System.getenv("INTERVAL_MS") ?: "500").toLong()
    val batchSize = (System.getenv("BATCH_SIZE") ?: "10").toInt()

    logger.info("Quote Generator: clickhouse={} redis={}:{} interval={}ms batch={}", chHttp, redisHost, redisPort, intervalMs, batchSize)

    val redis = JedisPool(redisHost, redisPort)
    val json = Json { prettyPrint = false }
    var totalInserted = 0L
    val buffer = mutableListOf<QuoteUpdate>()

    while (true) {
        for (t in TICKERS) {
            t.price = maxOf(0.01, t.price + t.price * t.volatility * (Random.nextDouble() * 2 - 1))
            val q = QuoteUpdate(t.ticker, "%.2f".format(t.price).toDouble(), Random.nextLong(100, 50000), Instant.now().toString())
            buffer.add(q)
            redis.resource.use { it.publish("quotes:updates", json.encodeToString(q)) }
        }

        if (buffer.size >= batchSize) {
            try {
                clickhouseInsert(chHttp, buffer)
                totalInserted += buffer.size
                if (totalInserted % 100 == 0L) logger.info("Inserted {} quotes total", totalInserted)
            } catch (e: Exception) {
                logger.error("ClickHouse insert failed: {}", e.message)
            }
            buffer.clear()
        }
        Thread.sleep(intervalMs)
    }
}
