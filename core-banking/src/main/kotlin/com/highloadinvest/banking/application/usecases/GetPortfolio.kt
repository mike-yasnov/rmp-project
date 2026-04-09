package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.entities.PortfolioItem
import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.PortfolioRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class GetPortfolio(
    private val portfolioRepository: PortfolioRepository,
    private val accountRepository: AccountRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class Result(
        val balance: Double,
        val currency: String,
        val positions: List<PortfolioItem>
    )

    suspend fun execute(userId: UUID): Result {
        logger.debug("GetPortfolio userId={}", userId)
        val account = accountRepository.findByUserId(userId)
            ?: throw NoSuchElementException("Account not found for user $userId")
        val positions = portfolioRepository.getByUserId(userId)
        logger.info("GetPortfolio userId={} balance={} positions={}", userId, account.balance, positions.size)
        return Result(balance = account.balance, currency = account.currency, positions = positions)
    }
}
