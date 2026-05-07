package com.highloadinvest.banking.infrastructure.redis

import com.highloadinvest.banking.application.matching.MatchingEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

class RedisQuoteListener(
    host: String,
    port: Int,
    private val engine: MatchingEngine
) {

    @Serializable
    private data class QuoteMessage(
        val ticker: String,
        val price: Double,
        val volume: Long = 0,
        val timestamp: String = ""
    )

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pool: JedisPool
    private val json = Json { ignoreUnknownKeys = true }
    private var subscriber: JedisPubSub? = null

    companion object {
        const val QUOTES_CHANNEL = "quotes:updates"
    }

    init {
        val config = JedisPoolConfig().apply { maxTotal = 4; maxIdle = 2 }
        pool = JedisPool(config, host, port)
    }

    fun start() {
        subscriber = object : JedisPubSub() {
            override fun onMessage(channel: String, message: String) {
                try {
                    val q = json.decodeFromString<QuoteMessage>(message)
                    engine.onTick(q.ticker, q.price)
                } catch (e: Exception) {
                    logger.warn("invalid quote message: {}", e.message)
                }
            }

            override fun onSubscribe(channel: String, subscribedChannels: Int) {
                logger.info("RedisQuoteListener subscribed to {}", channel)
            }
        }
        Thread({
            try {
                pool.resource.use { it.subscribe(subscriber, QUOTES_CHANNEL) }
            } catch (e: Exception) {
                logger.error("RedisQuoteListener error: {}", e.message)
            }
        }, "redis-quote-listener-banking").apply { isDaemon = true; start() }
    }

    fun stop() {
        try { subscriber?.unsubscribe() } catch (_: Exception) {}
        pool.close()
    }
}
