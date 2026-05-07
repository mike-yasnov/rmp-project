package com.highloadinvest.gateway.infrastructure.redis

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import java.util.UUID

/**
 * PSUBSCRIBE to orders:updates:* and route each message to its userId session(s).
 */
class RedisOrderEventSubscriber(
    host: String,
    port: Int,
    private val onMessage: (UUID, String) -> Unit
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pool: JedisPool
    private var subscriber: JedisPubSub? = null

    companion object {
        const val CHANNEL_PATTERN = "orders:updates:*"
    }

    init {
        val config = JedisPoolConfig().apply { maxTotal = 4; maxIdle = 2 }
        pool = JedisPool(config, host, port)
    }

    fun start() {
        subscriber = object : JedisPubSub() {
            override fun onPMessage(pattern: String, channel: String, message: String) {
                val userIdStr = channel.substringAfterLast(':')
                val userId = try {
                    UUID.fromString(userIdStr)
                } catch (e: Exception) {
                    logger.warn("invalid userId in channel {}", channel)
                    return
                }
                onMessage(userId, message)
            }

            override fun onPSubscribe(pattern: String, subscribedChannels: Int) {
                logger.info("RedisOrderEventSubscriber psubscribed to {}", pattern)
            }
        }
        Thread({
            try {
                pool.resource.use { it.psubscribe(subscriber, CHANNEL_PATTERN) }
            } catch (e: Exception) {
                logger.error("RedisOrderEventSubscriber error: {}", e.message)
            }
        }, "redis-order-subscriber").apply { isDaemon = true; start() }
    }

    fun close() {
        try { subscriber?.punsubscribe() } catch (_: Exception) {}
        pool.close()
    }
}
