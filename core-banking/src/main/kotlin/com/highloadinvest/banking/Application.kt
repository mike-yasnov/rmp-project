package com.highloadinvest.banking

import com.highloadinvest.banking.infrastructure.di.appModule
import com.highloadinvest.banking.infrastructure.postgres.DatabaseFactory
import com.highloadinvest.banking.presentation.plugins.*
import com.highloadinvest.banking.presentation.routes.healthRoutes
import com.highloadinvest.banking.presentation.routes.portfolioRoutes
import com.highloadinvest.banking.presentation.routes.tradeRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.highloadinvest.banking.Application")

fun main(args: Array<String>) {
    logger.info("Starting Core Banking service...")
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    logger.info("Configuring Core Banking module")

    DatabaseFactory.init(environment.config)

    install(Koin) {
        slf4jLogger()
        modules(appModule())
    }

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureCORS()

    routing {
        healthRoutes("core-banking")
        route("/api") {
            tradeRoutes()
            portfolioRoutes()
        }
    }

    logger.info("Core Banking configured successfully")
}
