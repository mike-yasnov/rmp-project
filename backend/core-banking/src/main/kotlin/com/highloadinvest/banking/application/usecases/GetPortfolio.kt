package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.PortfolioRepository
import com.highloadinvest.banking.domain.services.QuoteSnapshotProvider
import org.slf4j.LoggerFactory
import java.util.UUID

class GetPortfolio(
    private val portfolioRepository: PortfolioRepository,
    private val accountRepository: AccountRepository,
    private val quoteSnapshotProvider: QuoteSnapshotProvider
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class EnrichedPosition(
        val ticker: String,
        val lots: Int,
        val avgPrice: Double,
        val currentPrice: Double,
        val marketValue: Double,
        val unrealizedPnl: Double,
        val unrealizedPnlPercent: Double
    )

    data class Totals(
        val invested: Double,
        val marketValue: Double,
        val unrealizedPnl: Double,
        val unrealizedPnlPercent: Double
    )

    data class Result(
        val balance: Double,
        val reservedBalance: Double,
        val availableBalance: Double,
        val currency: String,
        val positions: List<EnrichedPosition>,
        val totals: Totals
    )

    suspend fun execute(userId: UUID): Result {
        logger.debug("GetPortfolio userId={}", userId)
        val account = accountRepository.findByUserId(userId)
            ?: throw NoSuchElementException("Account not found for user $userId")
        val rawPositions = portfolioRepository.getByUserId(userId)
        val snapshot = quoteSnapshotProvider.snapshot()

        val enriched = rawPositions.map { pos ->
            val current = snapshot[pos.ticker] ?: pos.avgPrice
            val invested = pos.lots * pos.avgPrice
            val marketValue = pos.lots * current
            val pnl = marketValue - invested
            val pnlPercent = if (invested > 0) pnl / invested * 100 else 0.0
            EnrichedPosition(
                ticker = pos.ticker,
                lots = pos.lots,
                avgPrice = pos.avgPrice,
                currentPrice = current,
                marketValue = marketValue,
                unrealizedPnl = pnl,
                unrealizedPnlPercent = pnlPercent
            )
        }

        val invested = enriched.sumOf { it.lots * it.avgPrice }
        val marketValue = enriched.sumOf { it.marketValue }
        val pnl = marketValue - invested
        val pnlPercent = if (invested > 0) pnl / invested * 100 else 0.0

        val reserved = accountRepository.reservedBalance(userId)
        val available = account.balance - reserved

        logger.info(
            "GetPortfolio userId={} balance={} reserved={} positions={} pnl={}",
            userId, account.balance, reserved, enriched.size, pnl
        )

        return Result(
            balance = account.balance,
            reservedBalance = reserved,
            availableBalance = available,
            currency = account.currency,
            positions = enriched,
            totals = Totals(invested, marketValue, pnl, pnlPercent)
        )
    }
}
