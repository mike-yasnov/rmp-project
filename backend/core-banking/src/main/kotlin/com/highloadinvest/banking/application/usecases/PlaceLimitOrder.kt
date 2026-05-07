package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderSide
import com.highloadinvest.banking.domain.entities.OrderStatus
import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.LimitOrderRepository
import com.highloadinvest.banking.domain.repositories.PortfolioRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class PlaceLimitOrder(
    private val limitOrderRepository: LimitOrderRepository,
    private val accountRepository: AccountRepository,
    private val portfolioRepository: PortfolioRepository,
    private val onPlaced: suspend (LimitOrder) -> Unit = {}
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class Request(
        val userId: UUID,
        val ticker: String,
        val side: OrderSide,
        val lots: Int,
        val limitPrice: Double
    )

    suspend fun execute(request: Request): LimitOrder {
        require(request.lots > 0) { "Lots must be positive" }
        require(request.limitPrice > 0) { "Limit price must be positive" }

        val ticker = request.ticker.uppercase()
        val total = request.lots * request.limitPrice

        // SELL — reserve lots semantically (no DB column; checked at fill time too)
        if (request.side == OrderSide.SELL) {
            val held = portfolioRepository.getLots(request.userId, ticker)
            val pendingSell = portfolioRepository.pendingSellLots(request.userId, ticker)
            if (held - pendingSell < request.lots) {
                throw IllegalArgumentException("Insufficient lots to reserve: have=${held - pendingSell}, requested=${request.lots}")
            }
        }

        val reservedAmount = if (request.side == OrderSide.BUY) total else 0.0
        if (reservedAmount > 0) {
            accountRepository.reserveFunds(request.userId, reservedAmount)
        }

        val order = LimitOrder(
            id = UUID.randomUUID(),
            userId = request.userId,
            ticker = ticker,
            side = request.side,
            lots = request.lots,
            limitPrice = request.limitPrice,
            status = OrderStatus.PENDING,
            reservedAmount = reservedAmount,
            createdAt = Instant.now()
        )
        val saved = limitOrderRepository.create(order)
        logger.info("placed orderId={} userId={} {} {} lots={} @ {}",
            saved.id, saved.userId, saved.side, saved.ticker, saved.lots, saved.limitPrice)
        onPlaced(saved)
        return saved
    }
}
