package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.domain.entities.TradeAction
import com.highloadinvest.banking.presentation.dto.TradeRequest
import com.highloadinvest.banking.presentation.dto.TradeResponse
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.tradeRoutes() {
    val executeTrade by inject<ExecuteTrade>()

    route("/trades") {
        post {
            val request = call.receive<TradeRequest>()
            val trade = executeTrade.execute(
                ExecuteTrade.Request(
                    userId = UUID.fromString(request.userId),
                    ticker = request.ticker,
                    action = TradeAction.valueOf(request.action.uppercase()),
                    lots = request.lots,
                    pricePerLot = request.pricePerLot
                )
            )
            call.respond(
                TradeResponse(
                    id = trade.id.toString(),
                    userId = trade.userId.toString(),
                    ticker = trade.ticker,
                    action = trade.action.name,
                    lots = trade.lots,
                    pricePerLot = trade.pricePerLot,
                    totalAmount = trade.totalAmount,
                    createdAt = trade.createdAt.toString()
                )
            )
        }
    }
}
