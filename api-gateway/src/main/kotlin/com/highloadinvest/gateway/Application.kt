package com.highloadinvest.gateway

import com.highloadinvest.gateway.infrastructure.di.appModule
import com.highloadinvest.gateway.presentation.plugins.*
import com.highloadinvest.gateway.presentation.routes.healthRoutes
import com.highloadinvest.gateway.presentation.routes.quoteRoutes
import com.highloadinvest.gateway.presentation.websocket.QuoteWebSocketHandler.quoteWebSocket
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.highloadinvest.gateway.Application")

fun main(args: Array<String>) {
    logger.info("Starting API Gateway...")
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    logger.info("Configuring API Gateway module")

    install(Koin) {
        slf4jLogger()
        modules(appModule(environment))
    }

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureWebSockets()
    configureCORS()

    routing {
        healthRoutes()
        quoteRoutes()
        quoteWebSocket()
    }

    logger.info("API Gateway configured successfully")
}
