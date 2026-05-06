package com.highloadinvest.loadtest

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("LoadTester")

private val tickers = listOf("AAPL", "GOOG", "MSFT", "AMZN", "TSLA", "NVDA", "META", "NFLX", "INTC", "AMD")

@Serializable
data class CreateUserReq(val username: String, val email: String, val initialBalance: Double)

@Serializable
data class UserResp(val id: String, val username: String, val email: String, val balance: Double)

@Serializable
data class QuoteResp(val ticker: String, val price: Double, val volume: Long, val timestamp: String)

@Serializable
data class TradeReq(val userId: String, val ticker: String, val action: String, val lots: Int, val pricePerLot: Double)

private val successCount = AtomicLong(0)
private val errorCount = AtomicLong(0)
private val totalLatencyMs = AtomicLong(0)
private val wsMessageCount = AtomicLong(0)

fun main() = runBlocking {
    val gatewayUrl = (System.getenv("GATEWAY_URL") ?: "http://localhost:8080").trimEnd('/')
    val botCount = (System.getenv("BOT_COUNT") ?: "100").toInt()
    val durationSec = (System.getenv("DURATION_SEC") ?: "60").toLong()
    val createParallelism = (System.getenv("CREATE_PARALLELISM") ?: "100").toInt()
    val activeRequests = System.getenv("ACTIVE_REQUESTS")?.toIntOrNull() ?: minOf(botCount, 500)
    val wsPercent = (System.getenv("WS_PERCENT") ?: "10").toInt().coerceIn(0, 100)
    val requestDelayMinMs = (System.getenv("REQUEST_DELAY_MIN_MS") ?: "50").toLong()
    val requestDelayMaxMs = (System.getenv("REQUEST_DELAY_MAX_MS") ?: "500").toLong()

    logger.info(
        "Load Tester: gateway={} bots={} duration={}s createParallelism={} activeRequests={} wsPercent={}",
        gatewayUrl,
        botCount,
        durationSec,
        createParallelism,
        activeRequests,
        wsPercent
    )

    logger.info("Creating {} bot users through API Gateway...", botCount)
    val createClient = newClient(maxConnections = createParallelism + 100, webSockets = false)
    val userIds = createUsers(createClient, gatewayUrl, botCount, createParallelism)
    createClient.close()
    logger.info("Created {} users", userIds.size)

    val client = newClient(maxConnections = activeRequests + 100, webSockets = false)
    val wsClient = newClient(maxConnections = (botCount * wsPercent / 100) + 100, webSockets = true)
    val prices = ConcurrentHashMap<String, Double>()
    refreshPrices(client, gatewayUrl, prices)

    val startedAt = System.currentTimeMillis()
    val deadline = startedAt + durationSec * 1000

    val priceRefreshJob = launch(Dispatchers.IO) {
        while (System.currentTimeMillis() < deadline) {
            runCatching { refreshPrices(client, gatewayUrl, prices) }
            delay(1000)
        }
    }

    val reporterJob = launch {
        while (System.currentTimeMillis() < deadline) {
            delay(5000)
            val elapsedSec = maxOf(1, (System.currentTimeMillis() - startedAt) / 1000)
            val success = successCount.get()
            val errors = errorCount.get()
            val avgMs = if (success > 0) totalLatencyMs.get() / success else 0
            logger.info(
                "Stats: success={} errors={} wsMessages={} avgLatency={}ms rps={}",
                success,
                errors,
                wsMessageCount.get(),
                avgMs,
                success / elapsedSec
            )
        }
    }

    val positionsByUser = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    val jobs = (0 until activeRequests).map { workerId ->
        launch(Dispatchers.IO) {
            runWorker(
                workerId,
                userIds,
                client,
                gatewayUrl,
                prices,
                positionsByUser,
                deadline,
                requestDelayMinMs,
                requestDelayMaxMs
            )
        }
    }

    val wsJobs = (1..(botCount * wsPercent / 100)).map {
        launch(Dispatchers.IO) {
            runWebSocketListener(wsClient, gatewayUrl, deadline)
        }
    }

    jobs.joinAll()
    wsJobs.forEach { it.cancelAndJoin() }
    priceRefreshJob.cancelAndJoin()
    reporterJob.cancelAndJoin()
    client.close()
    wsClient.close()

    val success = successCount.get()
    val errors = errorCount.get()
    val total = success + errors
    val avgMs = if (success > 0) totalLatencyMs.get() / success else 0
    val errorRate = if (total > 0) errors.toDouble() / total * 100 else 0.0

    logger.info("=== LOAD TEST COMPLETE ===")
    logger.info("Total requests: {}", total)
    logger.info("Successful: {}", success)
    logger.info("Errors: {}", errors)
    logger.info("WebSocket messages: {}", wsMessageCount.get())
    logger.info("Avg latency: {}ms", avgMs)
    logger.info("Error rate: {}%", "%.2f".format(errorRate))

    if (errors > 0) {
        error("Load test finished with $errors errors")
    }
}

private fun newClient(maxConnections: Int, webSockets: Boolean): HttpClient =
    HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        if (webSockets) {
            install(WebSockets)
        }
        engine {
            maxConnectionsCount = maxConnections
            endpoint {
                connectTimeout = 10_000
                requestTimeout = 30_000
            }
        }
    }

private suspend fun createUsers(client: HttpClient, gatewayUrl: String, botCount: Int, parallelism: Int): List<String> {
    val semaphore = Semaphore(parallelism)
    val ts = System.currentTimeMillis()
    return coroutineScope {
        (1..botCount).map { i ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val resp = checked("create-user") {
                        client.post("$gatewayUrl/api/users") {
                            contentType(ContentType.Application.Json)
                            setBody(CreateUserReq("bot_${ts}_$i", "bot_${ts}_$i@load.test", 10_000_000.0))
                        }
                    }
                    Json.decodeFromString<UserResp>(resp.bodyAsText()).id
                }
            }
        }.awaitAll()
    }
}

private suspend fun runWorker(
    workerId: Int,
    userIds: List<String>,
    client: HttpClient,
    gatewayUrl: String,
    prices: ConcurrentHashMap<String, Double>,
    positionsByUser: ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>,
    deadline: Long,
    minDelayMs: Long,
    maxDelayMs: Long
) {
    val workerLogger = LoggerFactory.getLogger("Worker-$workerId")

    while (System.currentTimeMillis() < deadline) {
        try {
            val userId = userIds.random()
            val positions = positionsByUser.computeIfAbsent(userId) { ConcurrentHashMap() }
            val started = System.currentTimeMillis()
            when (Random.nextInt(5)) {
                0, 1 -> checked("quotes") { client.get("$gatewayUrl/api/quotes") }
                2 -> checked("portfolio") { client.get("$gatewayUrl/api/portfolio/$userId") }
                else -> executeTrade(client, gatewayUrl, userId, prices, positions)
            }
            val elapsed = System.currentTimeMillis() - started
            successCount.incrementAndGet()
            totalLatencyMs.addAndGet(elapsed)
        } catch (e: Exception) {
            errorCount.incrementAndGet()
            workerLogger.debug("Error: {}", e.message)
        }
        delay(Random.nextLong(minDelayMs, maxDelayMs + 1))
    }
}

private suspend fun executeTrade(
    client: HttpClient,
    gatewayUrl: String,
    userId: String,
    prices: ConcurrentHashMap<String, Double>,
    positions: MutableMap<String, Int>
) {
    val sellCandidates = positions.filterValues { it > 0 }.keys.toList()
    val sell = sellCandidates.isNotEmpty() && Random.nextInt(100) < 35
    val ticker = if (sell) sellCandidates.random() else tickers.random()
    val currentLots = positions[ticker] ?: 0
    val lots = if (sell) Random.nextInt(1, currentLots + 1) else Random.nextInt(1, 5)
    val action = if (sell) "SELL" else "BUY"
    val price = prices[ticker] ?: 100.0

    checked("trade-$action") {
        client.post("$gatewayUrl/api/trades") {
            contentType(ContentType.Application.Json)
            setBody(TradeReq(userId, ticker, action, lots, price))
        }
    }

    positions[ticker] = if (sell) currentLots - lots else currentLots + lots
}

private suspend fun refreshPrices(client: HttpClient, gatewayUrl: String, prices: ConcurrentHashMap<String, Double>) {
    val resp = checked("quotes-refresh") { client.get("$gatewayUrl/api/quotes") }
    resp.body<List<QuoteResp>>().forEach { quote ->
        prices[quote.ticker] = quote.price
    }
}

private suspend fun runWebSocketListener(client: HttpClient, gatewayUrl: String, deadline: Long) {
    val wsUrl = gatewayUrl.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/ws/quotes"
    try {
        client.webSocket(wsUrl) {
            while (System.currentTimeMillis() < deadline) {
                val frame = incoming.receive()
                if (frame is Frame.Text) {
                    wsMessageCount.incrementAndGet()
                }
            }
        }
    } catch (e: Exception) {
        logger.debug("WS error: {}", e.message)
    }
}

private suspend fun checked(operation: String, block: suspend () -> HttpResponse): HttpResponse {
    val response = block()
    if (!response.status.isSuccess()) {
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        throw IllegalStateException("$operation failed: ${response.status.value} ${body.take(200)}")
    }
    return response
}
