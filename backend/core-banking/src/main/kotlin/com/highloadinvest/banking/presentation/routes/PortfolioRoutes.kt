package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.application.usecases.GetPortfolio
import com.highloadinvest.banking.presentation.dto.PortfolioResponse
import com.highloadinvest.banking.presentation.dto.PortfolioTotals
import com.highloadinvest.banking.presentation.dto.PositionResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.portfolioRoutes(getPortfolio: GetPortfolio) {
    route("/portfolio") {
        get("/{userId}") {
            val userId = UUID.fromString(call.parameters["userId"])
            val result = getPortfolio.execute(userId)
            call.respond(
                PortfolioResponse(
                    balance = result.balance,
                    reservedBalance = result.reservedBalance,
                    availableBalance = result.availableBalance,
                    currency = result.currency,
                    positions = result.positions.map {
                        PositionResponse(
                            ticker = it.ticker,
                            lots = it.lots,
                            avgPrice = it.avgPrice,
                            currentPrice = it.currentPrice,
                            marketValue = it.marketValue,
                            unrealizedPnl = it.unrealizedPnl,
                            unrealizedPnlPercent = it.unrealizedPnlPercent
                        )
                    },
                    totals = PortfolioTotals(
                        invested = result.totals.invested,
                        marketValue = result.totals.marketValue,
                        unrealizedPnl = result.totals.unrealizedPnl,
                        unrealizedPnlPercent = result.totals.unrealizedPnlPercent
                    )
                )
            )
        }
    }
}
