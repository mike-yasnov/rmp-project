package com.highloadinvest.nativeapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.time.Instant

class BrokerApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun quotes(): List<QuoteResponse> =
        get("/api/quotes")

    suspend fun candles(ticker: String): List<CandleResponse> {
        val to = Instant.now().epochSecond
        val from = to - 3600
        return get("/api/quotes/${ticker.url()}/candles?from=$from&to=$to")
    }

    suspend fun createUser(username: String, email: String, initialBalance: Double): UserResponse =
        post("/api/users", CreateUserRequest(username, email, initialBalance))

    suspend fun deposit(userId: String, amount: Double): PortfolioResponse {
        postRaw("/api/accounts/${userId.url()}/deposit", DepositRequest(amount))
        return portfolio(userId)
    }

    suspend fun trade(userId: String, ticker: String, action: String, lots: Int, price: Double): TradeResponse =
        post("/api/trades", TradeRequest(userId, ticker, action, lots, price))

    suspend fun portfolio(userId: String): PortfolioResponse =
        get("/api/portfolio/${userId.url()}")

    fun subscribeQuotes(onMessage: (QuoteResponse) -> Unit): WebSocket {
        val wsUrl = baseUrl.removePrefix("http://").removePrefix("https://")
        val scheme = if (baseUrl.startsWith("https://")) "wss" else "ws"
        val request = Request.Builder().url("$scheme://$wsUrl/ws/quotes").build()
        return client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString<QuoteResponse>(text) }
                        .onSuccess(onMessage)
                }
            }
        )
    }

    private suspend inline fun <reified T> get(path: String): T =
        request(Request.Builder().url("$baseUrl$path").get().build())

    private suspend inline fun <reified Req, reified Res> post(path: String, body: Req): Res =
        request(
            Request.Builder()
                .url("$baseUrl$path")
                .post(json.encodeToString(body).toRequestBody(mediaType))
                .build()
        )

    private suspend inline fun <reified Req> postRaw(path: String, body: Req): String =
        requestText(
            Request.Builder()
                .url("$baseUrl$path")
                .post(json.encodeToString(body).toRequestBody(mediaType))
                .build()
        )

    private suspend inline fun <reified T> request(request: Request): T =
        json.decodeFromString(requestText(request))

    private suspend fun requestText(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $body")
            }
            body
        }
    }

    private fun String.url(): String = URLEncoder.encode(this, Charsets.UTF_8.name())
}
