package com.highloadinvest.banking

import com.highloadinvest.banking.application.usecases.GetPortfolio
import com.highloadinvest.banking.infrastructure.observability.BankingMetrics
import com.highloadinvest.banking.infrastructure.observability.Telemetry
import com.highloadinvest.banking.infrastructure.postgres.*
import com.highloadinvest.banking.presentation.plugins.*
import com.highloadinvest.banking.presentation.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.highloadinvest.banking.Application")

fun main(args: Array<String>) {
    logger.info("Starting Core Banking service...")
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    logger.info("Configuring Core Banking module")

    val openTelemetry = Telemetry.init()
    val metrics = BankingMetrics(openTelemetry)

    DatabaseFactory.init(environment.config)

    val userRepo = PostgresUserRepository()
    val accountRepo = PostgresAccountRepository()
    val tradeRepo = PostgresTradeRepository()
    val portfolioRepo = PostgresPortfolioRepository()

    val executeTrade = PostgresTradeExecutor(openTelemetry, metrics)
    val getPortfolio = GetPortfolio(portfolioRepo, accountRepo)

    install(KtorServerTelemetry) {
        setOpenTelemetry(openTelemetry)
        capturedRequestHeaders("X-Request-Id", "User-Agent", "traceparent")
    }

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureCORS()

    routing {
        healthRoutes("core-banking")
        route("/api") {
            userRoutes(userRepo, accountRepo, metrics)
            tradeRoutes(executeTrade, tradeRepo)
            portfolioRoutes(getPortfolio)
        }
    }

    logger.info("Core Banking configured successfully")
}
