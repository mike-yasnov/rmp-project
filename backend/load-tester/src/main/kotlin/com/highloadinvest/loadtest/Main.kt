package com.highloadinvest.loadtest

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("LoadTester")

val TICKERS = listOf("GAZP", "SBER", "LKOH", "YNDX", "ROSN", "GMKN", "MTSS", "VKCO", "TCSG", "PLZL")

@Serializable
data class CreateUserReq(val username: String, val email: String, val initialBalance: Double)

@Serializable
data class UserResp(val id: String, val username: String, val email: String, val balance: Double)

@Serializable
data class TradeReq(val userId: String, val ticker: String, val action: String, val lots: Int, val pricePerLot: Double)

val successCount = AtomicLong(0)
val errorCount = AtomicLong(0)
val totalLatencyMs = AtomicLong(0)

fun main() = runBlocking {
    val gatewayUrl = System.getenv("GATEWAY_URL") ?: "http://localhost:8080"
    val bankingUrl = System.getenv("BANKING_URL") ?: "http://localhost:8081"
    val botCount = (System.getenv("BOT_COUNT") ?: "100").toInt()
    val durationSec = (System.getenv("DURATION_SEC") ?: "60").toLong()

    logger.info("Load Tester: gateway={} banking={} bots={} duration={}s", gatewayUrl, bankingUrl, botCount, durationSec)

    val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(WebSockets)
        engine { maxConnectionsCount = botCount + 100 }
    }

    // Create bot users
    logger.info("Creating {} bot users...", botCount)
    val userIds = (1..botCount).map { i ->
        val ts = System.currentTimeMillis()
        val resp = client.post("$bankingUrl/api/users") {
            contentType(ContentType.Application.Json)
            setBody(CreateUserReq("bot_${ts}_$i", "bot_${ts}_$i@load.test", 1_000_000.0))
        }
        val user = Json.decodeFromString<UserResp>(resp.bodyAsText())
        user.id
    }
    logger.info("Created {} users", userIds.size)

    val deadline = System.currentTimeMillis() + durationSec * 1000

    // Launch bot coroutines
    val jobs = userIds.mapIndexed { idx, userId ->
        launch(Dispatchers.IO) {
            val botLogger = LoggerFactory.getLogger("Bot-$idx")
            while (System.currentTimeMillis() < deadline) {
                try {
                    val action = if (Random.nextBoolean()) "BUY" else "SELL"
                    val ticker = TICKERS.random()
                    val lots = Random.nextInt(1, 20)
                    val price = Random.nextDouble(50.0, 500.0)

                    val start = System.currentTimeMillis()

                    // Random action: trade, portfolio, or quotes
                    when (Random.nextInt(4)) {
                        0, 1 -> {
                            client.post("$bankingUrl/api/trades") {
                                contentType(ContentType.Application.Json)
                                setBody(TradeReq(userId, ticker, action, lots, price))
                            }
                        }
                        2 -> {
                            client.get("$bankingUrl/api/portfolio/$userId")
                        }
                        3 -> {
                            client.get("$gatewayUrl/api/quotes")
                        }
                    }

                    val elapsed = System.currentTimeMillis() - start
                    successCount.incrementAndGet()
                    totalLatencyMs.addAndGet(elapsed)
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                    botLogger.debug("Error: {}", e.message)
                }
                delay(Random.nextLong(50, 500))
            }
        }
    }

    // Stats reporter
    launch {
        while (System.currentTimeMillis() < deadline) {
            delay(5000)
            val s = successCount.get()
            val e = errorCount.get()
            val avgMs = if (s > 0) totalLatencyMs.get() / s else 0
            logger.info("Stats: success={} errors={} avgLatency={}ms rps={}", s, e, avgMs, s / ((System.currentTimeMillis() - (deadline - durationSec * 1000)) / 1000 + 1))
        }
    }

    // WebSocket listeners (10% of bots)
    val wsJobs = (1..maxOf(1, botCount / 10)).map {
        launch(Dispatchers.IO) {
            try {
                client.webSocket("$gatewayUrl/ws/quotes") {
                    while (System.currentTimeMillis() < deadline) {
                        val frame = incoming.receive()
                        if (frame is Frame.Text) successCount.incrementAndGet()
                    }
                }
            } catch (e: Exception) {
                logger.debug("WS error: {}", e.message)
            }
        }
    }

    jobs.forEach { it.join() }
    wsJobs.forEach { it.cancelAndJoin() }
    client.close()

    val total = successCount.get()
    val errors = errorCount.get()
    val avgMs = if (total > 0) totalLatencyMs.get() / total else 0
    logger.info("=== LOAD TEST COMPLETE ===")
    logger.info("Total requests: {}", total + errors)
    logger.info("Successful: {}", total)
    logger.info("Errors: {}", errors)
    logger.info("Avg latency: {}ms", avgMs)
    val errorRate = if (total + errors > 0) errors.toDouble() / (total + errors) * 100 else 0.0
    logger.info("Error rate: {}%", "%.2f".format(errorRate))
}
