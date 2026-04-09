package com.highloadinvest.gateway.application.usecases

import com.highloadinvest.gateway.domain.entities.Candle
import com.highloadinvest.gateway.domain.entities.Quote
import com.highloadinvest.gateway.domain.repositories.QuoteRepository
import org.slf4j.LoggerFactory

class GetCurrentQuotes(private val quoteRepository: QuoteRepository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun all(): List<Quote> {
        logger.debug("GetCurrentQuotes.all() called")
        val quotes = quoteRepository.getLatestQuotes()
        logger.debug("GetCurrentQuotes.all() returned {} quotes", quotes.size)
        return quotes
    }

    suspend fun byTicker(ticker: String): Quote? {
        logger.debug("GetCurrentQuotes.byTicker() called with ticker={}", ticker)
        val quote = quoteRepository.getQuoteByTicker(ticker)
        logger.debug("GetCurrentQuotes.byTicker() result: {}", quote != null)
        return quote
    }

    suspend fun candles(ticker: String, from: Long, to: Long): List<Candle> {
        logger.debug("GetCurrentQuotes.candles() ticker={} from={} to={}", ticker, from, to)
        val candles = quoteRepository.getCandles(ticker, from, to)
        logger.debug("GetCurrentQuotes.candles() returned {} candles", candles.size)
        return candles
    }
}
