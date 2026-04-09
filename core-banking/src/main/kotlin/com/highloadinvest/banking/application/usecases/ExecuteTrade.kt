package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.entities.Trade
import com.highloadinvest.banking.domain.entities.TradeAction
import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.PortfolioRepository
import com.highloadinvest.banking.domain.repositories.TradeRepository
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class ExecuteTrade(
    private val accountRepository: AccountRepository,
    private val tradeRepository: TradeRepository,
    private val portfolioRepository: PortfolioRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class Request(
        val userId: UUID,
        val ticker: String,
        val action: TradeAction,
        val lots: Int,
        val pricePerLot: Double
    )

    suspend fun execute(request: Request): Trade {
        logger.info("ExecuteTrade START userId={} ticker={} action={} lots={} price={}",
            request.userId, request.ticker, request.action, request.lots, request.pricePerLot)

        require(request.lots > 0) { "Lots must be positive" }
        require(request.pricePerLot > 0) { "Price must be positive" }

        val totalAmount = request.lots * request.pricePerLot
        logger.debug("ExecuteTrade totalAmount={}", totalAmount)

        val account = accountRepository.findByUserId(request.userId)
            ?: throw NoSuchElementException("Account not found for user ${request.userId}")

        logger.debug("ExecuteTrade currentBalance={}", account.balance)

        when (request.action) {
            TradeAction.BUY -> {
                if (account.balance < totalAmount) {
                    logger.warn("ExecuteTrade INSUFFICIENT_FUNDS balance={} required={}", account.balance, totalAmount)
                    throw IllegalArgumentException("Insufficient funds: balance=${account.balance}, required=$totalAmount")
                }
                accountRepository.updateBalance(request.userId, account.balance - totalAmount)
                portfolioRepository.updatePosition(request.userId, request.ticker, request.lots, request.pricePerLot)
            }
            TradeAction.SELL -> {
                accountRepository.updateBalance(request.userId, account.balance + totalAmount)
                portfolioRepository.updatePosition(request.userId, request.ticker, -request.lots, request.pricePerLot)
            }
        }

        val trade = Trade(
            id = UUID.randomUUID(),
            userId = request.userId,
            ticker = request.ticker,
            action = request.action,
            lots = request.lots,
            pricePerLot = request.pricePerLot,
            totalAmount = totalAmount,
            createdAt = Instant.now()
        )
        val saved = tradeRepository.create(trade)
        logger.info("ExecuteTrade SUCCESS tradeId={} userId={} ticker={} action={} lots={} total={}",
            saved.id, saved.userId, saved.ticker, saved.action, saved.lots, saved.totalAmount)
        return saved
    }
}
