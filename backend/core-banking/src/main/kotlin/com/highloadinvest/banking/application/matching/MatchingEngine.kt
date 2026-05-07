package com.highloadinvest.banking.application.matching

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Reactive matching engine: feeds (ticker, price) ticks into per-ticker channels.
 * Different tickers match in parallel. Same ticker is serialized via mutex.
 */
class MatchingEngine(
    private val matchOrders: MatchOrders
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val channels = ConcurrentHashMap<String, Channel<Double>>()

    fun start() {
        logger.info("MatchingEngine started")
    }

    fun stop() {
        scope.cancel()
        channels.values.forEach { it.close() }
        logger.info("MatchingEngine stopped")
    }

    fun onTick(ticker: String, price: Double) {
        val key = ticker.uppercase()
        val ch = channels.computeIfAbsent(key) {
            val newCh = Channel<Double>(capacity = Channel.CONFLATED)
            scope.launch { consume(key, newCh) }
            newCh
        }
        scope.launch { ch.send(price) }
    }

    private suspend fun consume(ticker: String, ch: Channel<Double>) {
        val mutex = mutexes.computeIfAbsent(ticker) { Mutex() }
        for (price in ch) {
            try {
                mutex.withLock {
                    matchOrders.match(ticker, price)
                }
            } catch (e: Exception) {
                logger.error("match loop error for {}: {}", ticker, e.message)
            }
        }
    }
}
