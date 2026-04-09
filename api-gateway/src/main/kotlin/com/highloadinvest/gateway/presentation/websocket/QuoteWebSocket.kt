package com.highloadinvest.gateway.presentation.websocket

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object QuoteWebSocketHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sessions = ConcurrentHashMap<String, MutableSet<WebSocketServerSession>>()

    fun Route.quoteWebSocket() {
        webSocket("/ws/quotes") {
            val sessionId = this.hashCode().toString()
            logger.info("WebSocket connected: sessionId={}", sessionId)

            val allSessions = sessions.getOrPut("quotes") { ConcurrentHashMap.newKeySet() }
            allSessions.add(this)

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        logger.debug("WebSocket received from {}: {}", sessionId, text)
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                logger.info("WebSocket closed: sessionId={}", sessionId)
            } catch (e: Throwable) {
                logger.error("WebSocket error: sessionId={} error={}", sessionId, e.message)
            } finally {
                allSessions.remove(this)
                logger.info("WebSocket removed: sessionId={}, remaining={}", sessionId, allSessions.size)
            }
        }
    }

    suspend fun broadcast(message: String) {
        val allSessions = sessions["quotes"] ?: return
        logger.debug("Broadcasting to {} sessions: {} bytes", allSessions.size, message.length)
        allSessions.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (e: Throwable) {
                logger.warn("Failed to send to session: {}", e.message)
            }
        }
    }
}
