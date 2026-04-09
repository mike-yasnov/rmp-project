package com.highloadinvest.gateway.presentation.routes

import com.highloadinvest.gateway.application.usecases.GetCurrentQuotes
import com.highloadinvest.gateway.presentation.dto.CandleResponse
import com.highloadinvest.gateway.presentation.dto.QuoteResponse
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.quoteRoutes() {
    val getCurrentQuotes by inject<GetCurrentQuotes>()

    route("/api/quotes") {
        get {
            val quotes = getCurrentQuotes.all()
            call.respond(quotes.map {
                QuoteResponse(
                    ticker = it.ticker,
                    price = it.price,
                    volume = it.volume,
                    timestamp = it.timestamp.toString()
                )
            })
        }

        get("/{ticker}") {
            val ticker = call.parameters["ticker"]
                ?: throw IllegalArgumentException("Ticker is required")
            val quote = getCurrentQuotes.byTicker(ticker)
                ?: throw NoSuchElementException("Quote not found for ticker: $ticker")
            call.respond(
                QuoteResponse(
                    ticker = quote.ticker,
                    price = quote.price,
                    volume = quote.volume,
                    timestamp = quote.timestamp.toString()
                )
            )
        }

        get("/{ticker}/candles") {
            val ticker = call.parameters["ticker"]
                ?: throw IllegalArgumentException("Ticker is required")
            val from = call.parameters["from"]?.toLongOrNull()
                ?: (System.currentTimeMillis() / 1000 - 3600)
            val to = call.parameters["to"]?.toLongOrNull()
                ?: (System.currentTimeMillis() / 1000)
            val candles = getCurrentQuotes.candles(ticker, from, to)
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
