package com.highloadinvest.banking.infrastructure.quotes

import com.highloadinvest.banking.domain.services.QuoteSnapshotProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

class HttpQuoteSnapshotProvider(
    private val gatewayUrl: String,
    private val ttlMillis: Long = 1000L
) : QuoteSnapshotProvider {

    @Serializable
    private data class QuoteRow(
        val ticker: String,
        val price: Double,
        val volume: Long = 0,
        val timestamp: String = "",
        val change24h: Double = 0.0,
        val changePercent24h: Double = 0.0
    )

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = AtomicReference<Pair<Instant, Map<String, Double>>?>(null)

    override suspend fun snapshot(): Map<String, Double> {
        val now = Instant.now()
        val cached = cache.get()
        if (cached != null && now.toEpochMilli() - cached.first.toEpochMilli() < ttlMillis) {
            return cached.second
        }
        return try {
            val fresh = fetch()
            cache.set(now to fresh)
            fresh
        } catch (e: Exception) {
            logger.warn("Failed to fetch snapshot: {}", e.message)
            cached?.second ?: emptyMap()
        }
    }

    private fun fetch(): Map<String, Double> {
        val url = URI("$gatewayUrl/api/quotes").toURL()
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 2000
        conn.readTimeout = 3000
        val code = conn.responseCode
        if (code != 200) {
            throw RuntimeException("Gateway /api/quotes HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().readText()
        val rows = json.decodeFromString<List<QuoteRow>>(body)
        return rows.associate { it.ticker to it.price }
    }
}
