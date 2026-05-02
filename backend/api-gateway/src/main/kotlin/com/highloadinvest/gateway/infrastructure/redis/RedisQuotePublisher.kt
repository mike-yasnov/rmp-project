package com.highloadinvest.gateway.infrastructure.redis

import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub

class RedisQuotePublisher(host: String, port: Int) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pool: JedisPool

    init {
        logger.info("Connecting to Redis at {}:{}", host, port)
        val config = JedisPoolConfig().apply {
            maxTotal = 16
            maxIdle = 8
        }
        pool = JedisPool(config, host, port)
        logger.info("Redis connection pool created")
    }

    fun subscribe(channel: String, onMessage: (String) -> Unit): JedisPubSub {
        logger.info("Subscribing to Redis channel: {}", channel)
        val subscriber = object : JedisPubSub() {
            override fun onMessage(ch: String, message: String) {
                logger.debug("Redis message on {}: {} bytes", ch, message.length)
                onMessage(message)
            }
        }
        Thread {
            pool.resource.use { jedis ->
                jedis.subscribe(subscriber, channel)
            }
        }.apply {
            isDaemon = true
            name = "redis-subscriber-$channel"
            start()
        }
        return subscriber
    }

    fun close() {
        logger.info("Closing Redis connection pool")
        pool.close()
    }
}
