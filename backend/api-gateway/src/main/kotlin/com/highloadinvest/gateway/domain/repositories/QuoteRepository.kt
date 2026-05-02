package com.highloadinvest.gateway.domain.repositories

import com.highloadinvest.gateway.domain.entities.Candle
import com.highloadinvest.gateway.domain.entities.Quote

interface QuoteRepository {
    suspend fun getLatestQuotes(): List<Quote>
    suspend fun getQuoteByTicker(ticker: String): Quote?
    suspend fun getCandles(ticker: String, from: Long, to: Long): List<Candle>
}
