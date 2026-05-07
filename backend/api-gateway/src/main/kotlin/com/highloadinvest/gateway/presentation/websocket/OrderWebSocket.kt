package com.highloadinvest.gateway.presentation.websocket

import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-user WebSocket channel for order events. Path: /ws/orders/{userId}.
 * Server PSUBSCRIBEs to Redis channels orders:updates:* and routes messages
 * to all sessions registered under the matching userId.
 */
class OrderWebSocketHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketServerSession>>()

    fun Route.orderWebSocket() {
        webSocket("/ws/orders/{userId}") {
            val userIdParam = call.parameters["userId"]
            val userId = try {
                UUID.fromString(userIdParam)
            } catch (e: Exception) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid userId"))
                return@webSocket
            }
            val set = sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }
            set.add(this)
            logger.info("WS orders connected userId={} sessions={}", userId, set.size)

            try {
                send(Frame.Text("""{"type":"connected","userId":"$userId"}"""))
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        logger.debug("WS orders recv userId={}: {}", userId, frame.readText())
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                logger.info("WS orders closed userId={}", userId)
            } catch (e: Throwable) {
                logger.error("WS orders error userId={}: {}", userId, e.message)
            } finally {
                set.remove(this)
                if (set.isEmpty()) sessions.remove(userId)
            }
        }
    }

    fun deliver(userId: UUID, message: String) {
        val set = sessions[userId] ?: return
        val dead = mutableListOf<WebSocketServerSession>()
        set.forEach { session ->
            try {
                runBlocking { session.send(Frame.Text(message)) }
            } catch (e: Throwable) {
                dead.add(session)
            }
        }
        if (dead.isNotEmpty()) set.removeAll(dead.toSet())
    }
}
