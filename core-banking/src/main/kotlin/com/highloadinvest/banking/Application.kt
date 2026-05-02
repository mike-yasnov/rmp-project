package com.highloadinvest.banking

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.application.usecases.GetPortfolio
import com.highloadinvest.banking.infrastructure.postgres.*
import com.highloadinvest.banking.presentation.plugins.*
import com.highloadinvest.banking.presentation.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.highloadinvest.banking.Application")

fun main(args: Array<String>) {
    logger.info("Starting Core Banking service...")
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    logger.info("Configuring Core Banking module")

    DatabaseFactory.init(environment.config)

    val userRepo = PostgresUserRepository()
    val accountRepo = PostgresAccountRepository()
    val tradeRepo = PostgresTradeRepository()
    val portfolioRepo = PostgresPortfolioRepository()

    val executeTrade = ExecuteTrade(accountRepo, tradeRepo, portfolioRepo)
    val getPortfolio = GetPortfolio(portfolioRepo, accountRepo)

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureCORS()

    routing {
        healthRoutes("core-banking")
        route("/api") {
            userRoutes(userRepo, accountRepo)
            tradeRoutes(executeTrade, tradeRepo)
            portfolioRoutes(getPortfolio)
        }
    }

    logger.info("Core Banking configured successfully")
}
