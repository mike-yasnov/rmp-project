package com.highloadinvest.gateway.presentation.websocket

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class QuoteWebSocketHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sessions = ConcurrentHashMap.newKeySet<WebSocketServerSession>()

    fun Route.quoteWebSocket() {
        webSocket("/ws/quotes") {
            val sessionId = this.hashCode().toString(16)
            logger.info("WS connected: id={} total={}", sessionId, sessions.size + 1)
            sessions.add(this)

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
                logger.info("WS removed: id={} remaining={}", sessionId, sessions.size)
            }
        }
    }

    fun broadcast(message: String) {
        if (sessions.isEmpty()) return
        logger.debug("Broadcasting to {} sessions", sessions.size)
        val dead = mutableListOf<WebSocketServerSession>()
        sessions.forEach { session ->
            try {
                runBlocking { session.send(Frame.Text(message)) }
            } catch (e: Throwable) {
                logger.warn("Failed to send, removing session: {}", e.message)
                dead.add(session)
            }
        }
        sessions.removeAll(dead.toSet())
    }

    val activeConnections: Int get() = sessions.size
}
