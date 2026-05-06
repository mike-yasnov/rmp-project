package com.highloadinvest.banking.infrastructure.postgres

import com.highloadinvest.banking.domain.entities.Account
import com.highloadinvest.banking.domain.repositories.AccountRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class PostgresAccountRepository : AccountRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun findByUserId(userId: UUID): Account? {
        val start = System.currentTimeMillis()
        logger.debug("findByUserId userId={}", userId)
        var account: Account? = null
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("SELECT id, user_id, balance, currency FROM accounts WHERE user_id = ?")
                .use { stmt ->
                    stmt.setObject(1, userId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        account = Account(
                            id = rs.getObject("id", UUID::class.java),
                            userId = rs.getObject("user_id", UUID::class.java),
                            balance = rs.getDouble("balance"),
                            currency = rs.getString("currency")
                        )
                    }
                }
            conn.commit()
        }
        val elapsed = System.currentTimeMillis() - start
        logger.debug("findByUserId userId={} found={} in {}ms", userId, account != null, elapsed)
        return account
    }

    override suspend fun updateBalance(userId: UUID, newBalance: Double) {
        val start = System.currentTimeMillis()
        logger.info("updateBalance userId={} newBalance={}", userId, newBalance)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("UPDATE accounts SET balance = ? WHERE user_id = ?")
                .use { stmt ->
                    stmt.setDouble(1, newBalance)
                    stmt.setObject(2, userId)
                    val rows = stmt.executeUpdate()
                    logger.debug("updateBalance rows={}", rows)
                }
            conn.commit()
        }
        val elapsed = System.currentTimeMillis() - start
        logger.info("updateBalance userId={} completed in {}ms", userId, elapsed)
    }

    override suspend fun deposit(userId: UUID, amount: Double): Account {
        require(amount > 0) { "Deposit amount must be positive" }
        logger.info("deposit userId={} amount={}", userId, amount)
        DatabaseFactory.connection().use { conn ->
            try {
                conn.prepareStatement(
                    """
                    UPDATE accounts
                    SET balance = balance + ?
                    WHERE user_id = ?
                    RETURNING id, user_id, balance, currency
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setDouble(1, amount)
                    stmt.setObject(2, userId)
                    val rs = stmt.executeQuery()
                    if (!rs.next()) {
                        throw NoSuchElementException("Account not found for user $userId")
                    }
                    val account = Account(
                        id = rs.getObject("id", UUID::class.java),
                        userId = rs.getObject("user_id", UUID::class.java),
                        balance = rs.getDouble("balance"),
                        currency = rs.getString("currency")
                    )
                    conn.commit()
                    return account
                }
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    override suspend fun create(userId: UUID, initialBalance: Double): Account {
        val id = UUID.randomUUID()
        logger.info("create account userId={} initialBalance={}", userId, initialBalance)
        DatabaseFactory.connection().use { conn ->
            conn.prepareStatement("INSERT INTO accounts (id, user_id, balance) VALUES (?, ?, ?)")
                .use { stmt ->
                    stmt.setObject(1, id)
                    stmt.setObject(2, userId)
                    stmt.setDouble(3, initialBalance)
                    stmt.executeUpdate()
                }
            conn.commit()
        }
        return Account(id = id, userId = userId, balance = initialBalance)
    }
}
