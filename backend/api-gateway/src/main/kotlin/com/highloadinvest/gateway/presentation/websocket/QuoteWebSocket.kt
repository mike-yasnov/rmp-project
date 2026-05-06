package com.highloadinvest.gateway.presentation.websocket

import com.highloadinvest.gateway.infrastructure.observability.GatewayMetrics
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class QuoteWebSocketHandler(private val metrics: GatewayMetrics? = null) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketServerSession>()

    fun Route.quoteWebSocket() {
        webSocket("/ws/quotes") {
            val sessionId = this.hashCode().toString(16)
            sessions.add(this)
            metrics?.wsConnections?.add(1)
            logger.info("WS connected: id={} total={}", sessionId, sessions.size)

            try {
                send(Frame.Text("""{"type":"connected","message":"Subscribed to quote updates"}"""))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        logger.debug("WS recv from {}: {}", sessionId, frame.readText())
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                logger.info("WS closed: id={}", sessionId)
            } catch (e: Throwable) {
                logger.error("WS error: id={} err={}", sessionId, e.message)
            } finally {
                sessions.remove(this)
                metrics?.wsConnections?.add(-1)
                logger.info("WS removed: id={} remaining={}", sessionId, sessions.size)
            }
        }
    }

    fun broadcast(message: String) {
        if (sessions.isEmpty()) return
        logger.debug("Broadcasting to {} sessions", sessions.size)
        val dead = mutableListOf<WebSocketServerSession>()
        var delivered = 0L
        sessions.forEach { session ->
            try {
                runBlocking { session.send(Frame.Text(message)) }
                delivered++
            } catch (e: Throwable) {
                logger.warn("Failed to send, removing session: {}", e.message)
                dead.add(session)
            }
        }
        sessions.removeAll(dead.toSet())
        if (delivered > 0) metrics?.wsMessagesBroadcast?.add(delivered)
    }

    val activeConnections: Int get() = sessions.size
}
