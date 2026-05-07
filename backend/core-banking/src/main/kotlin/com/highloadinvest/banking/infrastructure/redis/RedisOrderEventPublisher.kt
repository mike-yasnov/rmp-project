package com.highloadinvest.banking.infrastructure.redis

import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.util.UUID

class RedisOrderEventPublisher(host: String, port: Int) {

    @Serializable
    data class OrderEvent(
        val type: String,
        val orderId: String,
        val userId: String,
        val ticker: String,
        val side: String,
        val lots: Int,
        val limitPrice: Double,
        val status: String,
        val tradeId: String? = null,
        val fillPrice: Double? = null,
        val reason: String? = null
    )

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val pool: JedisPool
    private val json = Json { encodeDefaults = true }

    init {
        val config = JedisPoolConfig().apply { maxTotal = 4; maxIdle = 2 }
        pool = JedisPool(config, host, port)
    }

    private fun channel(userId: UUID) = "orders:updates:$userId"

    fun publishPlaced(order: LimitOrder) =
        publish(order.userId, OrderEvent(
            type = "order.placed", orderId = order.id.toString(), userId = order.userId.toString(),
            ticker = order.ticker, side = order.side.name, lots = order.lots,
            limitPrice = order.limitPrice, status = order.status.name
        ))

    fun publishCancelled(order: LimitOrder) =
        publish(order.userId, OrderEvent(
            type = "order.cancelled", orderId = order.id.toString(), userId = order.userId.toString(),
            ticker = order.ticker, side = order.side.name, lots = order.lots,
            limitPrice = order.limitPrice, status = order.status.name
        ))

    fun publishFilled(order: LimitOrder, tradeId: UUID, fillPrice: Double) =
        publish(order.userId, OrderEvent(
            type = "order.filled", orderId = order.id.toString(), userId = order.userId.toString(),
            ticker = order.ticker, side = order.side.name, lots = order.lots,
            limitPrice = order.limitPrice, status = OrderStatus.FILLED.name,
            tradeId = tradeId.toString(), fillPrice = fillPrice
        ))

    fun publishRejected(order: LimitOrder, reason: OrderStatus) =
        publish(order.userId, OrderEvent(
            type = "order.rejected", orderId = order.id.toString(), userId = order.userId.toString(),
            ticker = order.ticker, side = order.side.name, lots = order.lots,
            limitPrice = order.limitPrice, status = reason.name, reason = reason.name
        ))

    private fun publish(userId: UUID, event: OrderEvent) {
        try {
            val payload = json.encodeToString(event)
            pool.resource.use { it.publish(channel(userId), payload) }
        } catch (e: Exception) {
            logger.warn("failed to publish order event: {}", e.message)
        }
    }

    fun close() = pool.close()
}
