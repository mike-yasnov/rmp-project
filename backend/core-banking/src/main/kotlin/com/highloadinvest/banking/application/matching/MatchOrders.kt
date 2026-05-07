package com.highloadinvest.banking.application.matching

import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderSide
import com.highloadinvest.banking.domain.entities.OrderStatus
import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.LimitOrderRepository
import com.highloadinvest.banking.infrastructure.postgres.PostgresTradeExecutor
import org.slf4j.LoggerFactory

class MatchOrders(
    private val limitOrderRepository: LimitOrderRepository,
    private val tradeExecutor: PostgresTradeExecutor,
    private val accountRepository: AccountRepository,
    private val onFilled: suspend (LimitOrder, java.util.UUID, Double) -> Unit = { _, _, _ -> },
    private val onRejected: suspend (LimitOrder, OrderStatus) -> Unit = { _, _ -> }
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun match(ticker: String, marketPrice: Double): Int {
        val pending = limitOrderRepository.findPendingForMatching(ticker, marketPrice)
        if (pending.isEmpty()) return 0
        logger.debug("matching {} pending orders for {} @ {}", pending.size, ticker, marketPrice)

        var filled = 0
        for (order in pending) {
            try {
                val trade = tradeExecutor.executeFromLimit(order, marketPrice)
                limitOrderRepository.markFilled(order.id, trade.id, marketPrice)
                onFilled(order.copy(status = OrderStatus.FILLED), trade.id, marketPrice)
                filled++
            } catch (e: IllegalArgumentException) {
                val rejection = if (e.message?.contains("INSUFFICIENT_LOTS") == true)
                    OrderStatus.INSUFFICIENT_LOTS
                else
                    OrderStatus.INSUFFICIENT_FUNDS
                limitOrderRepository.markRejected(order.id, rejection)
                if (order.side == OrderSide.BUY && order.reservedAmount > 0) {
                    accountRepository.releaseReservation(order.userId, order.reservedAmount)
                }
                onRejected(order.copy(status = rejection), rejection)
                logger.warn("rejected orderId={} reason={}", order.id, rejection)
            } catch (e: Exception) {
                logger.error("failed to fill orderId={}: {}", order.id, e.message)
            }
        }
        return filled
    }
}
