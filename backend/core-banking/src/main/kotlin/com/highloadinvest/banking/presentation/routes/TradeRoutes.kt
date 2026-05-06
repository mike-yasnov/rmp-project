package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.application.usecases.ExecuteTrade
import com.highloadinvest.banking.application.usecases.TradeExecutor
import com.highloadinvest.banking.domain.entities.TradeAction
import com.highloadinvest.banking.domain.repositories.TradeRepository
import com.highloadinvest.banking.presentation.dto.TradeRequest
import com.highloadinvest.banking.presentation.dto.TradeResponse
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.tradeRoutes(executeTrade: TradeExecutor, tradeRepository: TradeRepository) {
    route("/trades") {
        get("/{userId}") {
            val userId = UUID.fromString(call.parameters["userId"])
            val trades = tradeRepository.findByUserId(userId)
            call.respond(trades.map {
                TradeResponse(
                    id = it.id.toString(),
                    userId = it.userId.toString(),
                    ticker = it.ticker,
                    action = it.action.name,
                    lots = it.lots,
                    pricePerLot = it.pricePerLot,
                    totalAmount = it.totalAmount,
                    createdAt = it.createdAt.toString()
                )
            })
        }

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
