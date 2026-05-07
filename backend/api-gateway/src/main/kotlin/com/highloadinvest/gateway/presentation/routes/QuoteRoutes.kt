package com.highloadinvest.gateway.presentation.routes

import com.highloadinvest.gateway.application.usecases.GetCurrentQuotes
import com.highloadinvest.gateway.domain.entities.CandleInterval
import com.highloadinvest.gateway.presentation.dto.CandleResponse
import com.highloadinvest.gateway.presentation.dto.QuoteResponse
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.quoteRoutes(getCurrentQuotes: GetCurrentQuotes) {
    route("/api/quotes") {
        get {
            val quotes = getCurrentQuotes.all()
            call.respond(quotes.map {
                QuoteResponse(
                    ticker = it.ticker,
                    price = it.price,
                    volume = it.volume,
                    timestamp = it.timestamp.toString(),
                    change24h = it.change24h,
                    changePercent24h = it.changePercent24h
                )
            })
        }

        get("/{ticker}") {
            val ticker = call.parameters["ticker"] ?: throw IllegalArgumentException("Ticker is required")
            val quote = getCurrentQuotes.byTicker(ticker)
                ?: throw NoSuchElementException("Quote not found for ticker: $ticker")
            call.respond(
                QuoteResponse(
                    ticker = quote.ticker,
                    price = quote.price,
                    volume = quote.volume,
                    timestamp = quote.timestamp.toString(),
                    change24h = quote.change24h,
                    changePercent24h = quote.changePercent24h
                )
            )
        }

        get("/{ticker}/candles") {
            val ticker = call.parameters["ticker"] ?: throw IllegalArgumentException("Ticker is required")
            val from = call.parameters["from"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000 - 3600)
            val to = call.parameters["to"]?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)
            val intervalParam = call.parameters["interval"]
            val interval = CandleInterval.fromString(intervalParam)
            if (interval == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown interval: $intervalParam. Allowed: 1m, 5m, 15m, 1h, 1d"))
                return@get
            }
            val candles = getCurrentQuotes.candles(ticker, from, to, interval)
            call.respond(candles.map {
                CandleResponse(
                    ticker = it.ticker,
                    open = it.open,
                    high = it.high,
                    low = it.low,
                    close = it.close,
                    volume = it.volume,
                    timestamp = it.timestamp.toString()
                )
            })
        }
    }
}
