package com.highloadinvest.banking.infrastructure.postgres

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import org.slf4j.LoggerFactory
import java.sql.Connection

object DatabaseFactory {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private lateinit var dataSource: HikariDataSource

    fun init(config: ApplicationConfig) {
        val url = config.property("database.url").getString()
        val user = config.property("database.user").getString()
        val password = config.property("database.password").getString()
        val maxPoolSize = config.property("database.maxPoolSize").getString().toInt()

        logger.info("Initializing database connection: url={} user={} maxPoolSize={}", url, user, maxPoolSize)

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            poolName = "core-banking-pool"
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)
        logger.info("Database connection pool created: {}", dataSource.poolName)

        runMigrations()
    }

    fun connection(): Connection = dataSource.connection

    private fun runMigrations() {
        logger.info("Running database migrations...")
        val sql = this::class.java.classLoader
            .getResourceAsStream("db/migration/V1__init.sql")
            ?.bufferedReader()
            ?.readText()
            ?: run {
                logger.warn("No migration file found, skipping")
                return
            }

        connection().use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }
            conn.commit()
        }
        logger.info("Migrations completed successfully")
    }

    fun close() {
        logger.info("Closing database connection pool")
        dataSource.close()
    }
}
