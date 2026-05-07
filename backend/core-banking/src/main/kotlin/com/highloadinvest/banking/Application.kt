package com.highloadinvest.banking

import com.highloadinvest.banking.application.matching.MatchOrders
import com.highloadinvest.banking.application.matching.MatchingEngine
import com.highloadinvest.banking.application.usecases.AuthenticateUser
import com.highloadinvest.banking.application.usecases.CancelLimitOrder
import com.highloadinvest.banking.application.usecases.GetPortfolio
import com.highloadinvest.banking.application.usecases.ListUserOrders
import com.highloadinvest.banking.application.usecases.PlaceLimitOrder
import com.highloadinvest.banking.infrastructure.observability.BankingMetrics
import com.highloadinvest.banking.infrastructure.observability.Telemetry
import com.highloadinvest.banking.infrastructure.postgres.*
import com.highloadinvest.banking.infrastructure.quotes.HttpQuoteSnapshotProvider
import com.highloadinvest.banking.infrastructure.redis.RedisOrderEventPublisher
import com.highloadinvest.banking.infrastructure.redis.RedisQuoteListener
import com.highloadinvest.banking.presentation.plugins.*
import com.highloadinvest.banking.presentation.routes.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
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
    val limitOrderRepo = PostgresLimitOrderRepository()

    val executeTrade = PostgresTradeExecutor(openTelemetry, metrics)
    val gatewayUrl = environment.config.propertyOrNull("gateway.url")?.getString() ?: "http://localhost:8080"
    val quoteSnapshotProvider = HttpQuoteSnapshotProvider(gatewayUrl)
    val getPortfolio = GetPortfolio(portfolioRepo, accountRepo, quoteSnapshotProvider)
    val authenticateUser = AuthenticateUser(userRepo, accountRepo)

    val redisHost = environment.config.propertyOrNull("redis.host")?.getString() ?: "localhost"
    val redisPort = environment.config.propertyOrNull("redis.port")?.getString()?.toIntOrNull() ?: 6379

    val orderEventPublisher = RedisOrderEventPublisher(redisHost, redisPort)
    val placeLimitOrder = PlaceLimitOrder(
        limitOrderRepository = limitOrderRepo,
        accountRepository = accountRepo,
        portfolioRepository = portfolioRepo,
        onPlaced = { orderEventPublisher.publishPlaced(it) }
    )
    val cancelLimitOrder = CancelLimitOrder(
        limitOrderRepository = limitOrderRepo,
        accountRepository = accountRepo,
        onCancelled = { orderEventPublisher.publishCancelled(it) }
    )
    val listUserOrders = ListUserOrders(limitOrderRepo)

    val matchOrders = MatchOrders(
        limitOrderRepository = limitOrderRepo,
        tradeExecutor = executeTrade,
        accountRepository = accountRepo,
        onFilled = { order, tradeId, fillPrice -> orderEventPublisher.publishFilled(order, tradeId, fillPrice) },
        onRejected = { order, status -> orderEventPublisher.publishRejected(order, status) }
    )
    val matchingEngine = MatchingEngine(matchOrders)
    val redisQuoteListener = RedisQuoteListener(redisHost, redisPort, matchingEngine)

    // Reconcile reserved balance on startup (defensive against crashes mid-fill)
    try {
        runBlocking { limitOrderRepo.reconcileReservedBalance() }
    } catch (e: Exception) {
        logger.warn("reserved balance reconciliation skipped: {}", e.message)
    }

    matchingEngine.start()
    redisQuoteListener.start()

    monitor.subscribe(ApplicationStopped) {
        redisQuoteListener.stop()
        matchingEngine.stop()
        orderEventPublisher.close()
    }

    // KtorServerTelemetry is installed automatically by the OpenTelemetry javaagent;
    // no manual install needed (and a manual one throws DuplicatePluginException).

    configureSerialization()
    configureStatusPages()
    configureMonitoring()
    configureCORS()

    routing {
        healthRoutes("core-banking")
        route("/api") {
            authRoutes(authenticateUser)
            userRoutes(userRepo, accountRepo, metrics)
            tradeRoutes(executeTrade, tradeRepo)
            portfolioRoutes(getPortfolio)
            orderRoutes(placeLimitOrder, cancelLimitOrder, listUserOrders, limitOrderRepo)
        }
    }

    logger.info("Core Banking configured successfully")
}
