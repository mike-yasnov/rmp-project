package com.highloadinvest.nativeapp.data.remote

import com.highloadinvest.nativeapp.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : RuntimeException(message)

class ApiService(private val baseUrl: String) {

    @Serializable
    data class LoginReq(val username: String)

    @Serializable
    data class CreateUserReq(val username: String, val email: String, val initialBalance: Double = 100000.0)

    @Serializable
    data class DepositReq(val amount: Double)

    @Serializable
    data class TradeReq(val userId: String, val ticker: String, val action: String, val lots: Int, val pricePerLot: Double)

    @Serializable
    data class TradeResp(
        val id: String, val userId: String, val ticker: String, val action: String,
        val lots: Int, val pricePerLot: Double, val totalAmount: Double, val createdAt: String
    )

    @Serializable
    data class PlaceOrderReq(val userId: String, val ticker: String, val side: String, val lots: Int, val limitPrice: Double)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private suspend inline fun <reified T> get(path: String): T = request("GET", path, null)
    private suspend inline fun <reified Req, reified Res> post(path: String, body: Req): Res = request("POST", path, json.encodeToString(body))
    private suspend inline fun <reified T> delete(path: String): T = request("DELETE", path, null)

    private suspend inline fun <reified T> request(method: String, path: String, body: String?): T = withContext(Dispatchers.IO) {
        val mt = "application/json".toMediaType()
        val req = Request.Builder().url("$baseUrl$path")
            .method(method, body?.toRequestBody(mt))
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(resp.code, text)
            json.decodeFromString<T>(text)
        }
    }

    suspend fun login(username: String): User = post("/api/auth/login", LoginReq(username))
    suspend fun register(username: String, email: String, initialBalance: Double): User =
        post("/api/users", CreateUserReq(username, email, initialBalance))
    suspend fun getUser(id: String): User = get("/api/users/$id")
    suspend fun deposit(userId: String, amount: Double): Map<String, String> =
        post<DepositReq, Map<String, String>>("/api/accounts/$userId/deposit", DepositReq(amount))

    suspend fun quotes(): List<Quote> = get("/api/quotes")
    suspend fun quote(ticker: String): Quote = get("/api/quotes/$ticker")
    suspend fun candles(ticker: String, from: Long, to: Long, interval: String): List<Candle> =
        get("/api/quotes/$ticker/candles?from=$from&to=$to&interval=$interval")

    suspend fun portfolio(userId: String): Portfolio = get("/api/portfolio/$userId")

    suspend fun marketTrade(userId: String, ticker: String, action: String, lots: Int, price: Double): TradeResp =
        post("/api/trades", TradeReq(userId, ticker, action, lots, price))

    suspend fun placeLimitOrder(userId: String, ticker: String, side: String, lots: Int, limitPrice: Double): Order =
        post("/api/orders", PlaceOrderReq(userId, ticker, side, lots, limitPrice))

    suspend fun listOrders(userId: String, status: String? = null): List<Order> {
        val q = if (status.isNullOrBlank()) "" else "?status=$status"
        return get("/api/orders/$userId$q")
    }

    suspend fun cancelOrder(orderId: String): Order = delete("/api/orders/$orderId")
}
