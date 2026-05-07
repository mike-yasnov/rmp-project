package com.highloadinvest.nativeapp.data.remote

import com.highloadinvest.nativeapp.domain.Quote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

abstract class WsManager(private val url: String) {
    private val client = OkHttpClient()
    private var ws: WebSocket? = null

    protected abstract fun onText(text: String)

    fun connect() {
        if (ws != null) return
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = onText(text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ws = null }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { ws = null }
        })
    }

    fun disconnect() {
        ws?.close(1000, null)
        ws = null
    }
}

class MarketSocket(baseWsUrl: String) : WsManager("$baseWsUrl/ws/quotes") {
    private val _quotes = MutableSharedFlow<Quote>(replay = 0, extraBufferCapacity = 64)
    val quotes: SharedFlow<Quote> = _quotes
    private val json = Json { ignoreUnknownKeys = true }

    override fun onText(text: String) {
        try {
            val q = json.decodeFromString<Quote>(text)
            _quotes.tryEmit(q)
        } catch (_: Exception) { /* ignore non-quote payloads like {"type":"connected"} */ }
    }
}

class OrderSocket(baseWsUrl: String, userId: String) : WsManager("$baseWsUrl/ws/orders/$userId") {
    private val _events = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events
    override fun onText(text: String) { _events.tryEmit(text) }
}
