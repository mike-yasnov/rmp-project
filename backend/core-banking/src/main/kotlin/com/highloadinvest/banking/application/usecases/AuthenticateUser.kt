package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.UserRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class AuthenticateUser(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    data class Result(
        val id: UUID,
        val username: String,
        val email: String,
        val balance: Double
    )

    suspend fun execute(username: String): Result? {
        logger.debug("AuthenticateUser username={}", username)
        val user = userRepository.findByUsername(username) ?: return null
        val account = accountRepository.findByUserId(user.id)
            ?: throw IllegalStateException("Account missing for user ${user.id}")
        logger.info("AuthenticateUser SUCCESS userId={} username={}", user.id, username)
        return Result(
            id = user.id,
            username = user.username,
            email = user.email,
            balance = account.balance
        )
    }
}
