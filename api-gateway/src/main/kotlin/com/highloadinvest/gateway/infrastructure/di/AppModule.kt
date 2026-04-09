package com.highloadinvest.gateway.infrastructure.di

import com.highloadinvest.gateway.application.usecases.GetCurrentQuotes
import com.highloadinvest.gateway.domain.repositories.QuoteRepository
import com.highloadinvest.gateway.infrastructure.clickhouse.ClickHouseQuoteRepository
import com.highloadinvest.gateway.infrastructure.redis.RedisQuotePublisher
import io.ktor.server.application.*
import org.koin.dsl.module

fun appModule(environment: ApplicationEnvironment) = module {
    single<QuoteRepository> {
        ClickHouseQuoteRepository(
            jdbcUrl = environment.config.property("clickhouse.url").getString()
        )
    }

    single {
        RedisQuotePublisher(
            host = environment.config.property("redis.host").getString(),
            port = environment.config.property("redis.port").getString().toInt()
        )
    }

    single { GetCurrentQuotes(get()) }
}
