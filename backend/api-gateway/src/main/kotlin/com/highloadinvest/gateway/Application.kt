package com.highloadinvest.gateway

import com.highloadinvest.gateway.application.usecases.GetCurrentQuotes
import com.highloadinvest.gateway.infrastructure.clickhouse.ClickHouseQuoteRepository
import com.highloadinvest.gateway.infrastructure.redis.RedisQuoteSubscriber
import com.highloadinvest.gateway.presentation.plugins.*
import com.highloadinvest.gateway.presentation.routes.healthRoutes
import com.highloadinvest.gateway.presentation.routes.bankingProxyRoutes
import com.highloadinvest.gateway.presentation.routes.quoteRoutes
import com.highloadinvest.gateway.presentation.websocket.QuoteWebSocketHandler
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.highloadinvest.gateway.Application")

fun main(args: Array<String>) {
    logger.info("Starting API Gateway...")
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    logger.info("Configuring API Gateway module")

    val clickhouseUrl = environment.config.property("clickhouse.url").getString()
    val redisHost = environment.config.property("redis.host").getString()
    val redisPort = environment.config.property("redis.port").getString().toInt()
    val bankingUrl = environment.config.property("banking.url").getString()

    val quoteRepo = ClickHouseQuoteRepository(clickhouseUrl)
    val getCurrentQuotes = GetCurrentQuotes(quoteRepo)
    val wsHandler = QuoteWebSocketHandler()
    val bankingClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    val redisSubscriber = RedisQuoteSubscriber(redisHost, redisPort)
    redisSubscriber.subscribe { message ->
        wsHandler.broadcast(message)
    }

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureWebSockets()
    configureCORS()

    routing {
        healthRoutes()
        quoteRoutes(getCurrentQuotes)
        bankingProxyRoutes(bankingClient, bankingUrl)
        with(wsHandler) { quoteWebSocket() }
    }

    @Suppress("DEPRECATION")
    environment.monitor.subscribe(ApplicationStopped) {
        logger.info("Shutting down — closing Redis subscriber")
        redisSubscriber.close()
        bankingClient.close()
    }

    logger.info("API Gateway configured successfully")
}
