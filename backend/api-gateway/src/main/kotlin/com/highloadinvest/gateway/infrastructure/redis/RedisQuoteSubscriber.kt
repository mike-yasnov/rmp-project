package com.highloadinvest.gateway.infrastructure.redis

import com.highloadinvest.gateway.infrastructure.observability.GatewayMetrics
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

class RedisQuoteSubscriber(host: String, port: Int, private val metrics: GatewayMetrics? = null) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pool: JedisPool
    private var subscriber: JedisPubSub? = null

    companion object {
        const val QUOTES_CHANNEL = "quotes:updates"
    }

    init {
        logger.info("Redis subscriber connecting to {}:{}", host, port)
        val config = JedisPoolConfig().apply { maxTotal = 4; maxIdle = 2 }
        pool = JedisPool(config, host, port)
    }

    fun subscribe(onMessage: (String) -> Unit) {
        logger.info("Subscribing to channel: {}", QUOTES_CHANNEL)
        subscriber = object : JedisPubSub() {
            override fun onMessage(channel: String, message: String) {
                logger.debug("Received quote update: {} bytes", message.length)
                metrics?.redisMessagesReceived?.add(1)
                onMessage(message)
            }

            override fun onSubscribe(channel: String, subscribedChannels: Int) {
                logger.info("Subscribed to {}, total channels: {}", channel, subscribedChannels)
            }
        }
        Thread({
            try {
                pool.resource.use { jedis -> jedis.subscribe(subscriber, QUOTES_CHANNEL) }
            } catch (e: Exception) {
                logger.error("Redis subscriber error: {}", e.message)
            }
        }, "redis-quote-subscriber").apply { isDaemon = true; start() }
    }

    fun close() {
        subscriber?.unsubscribe()
        pool.close()
    }
}
