package com.highloadinvest.banking.infrastructure.di

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.application.usecases.GetPortfolio
import com.highloadinvest.banking.domain.repositories.*
import com.highloadinvest.banking.infrastructure.postgres.PostgresAccountRepository
import com.highloadinvest.banking.infrastructure.postgres.PostgresPortfolioRepository
import com.highloadinvest.banking.infrastructure.postgres.PostgresTradeRepository
import com.highloadinvest.banking.infrastructure.postgres.PostgresUserRepository
import org.koin.dsl.module

fun appModule() = module {
    single<AccountRepository> { PostgresAccountRepository() }
    single<TradeRepository> { PostgresTradeRepository() }
    single<PortfolioRepository> { PostgresPortfolioRepository() }
    single<UserRepository> { PostgresUserRepository() }

    single { ExecuteTrade(get(), get(), get()) }
    single { GetPortfolio(get(), get()) }
}
