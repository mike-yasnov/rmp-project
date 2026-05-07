package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderSide
import com.highloadinvest.banking.domain.entities.OrderStatus
import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.LimitOrderRepository
import org.slf4j.LoggerFactory
import java.util.UUID

class CancelLimitOrder(
    private val limitOrderRepository: LimitOrderRepository,
    private val accountRepository: AccountRepository,
    private val onCancelled: suspend (LimitOrder) -> Unit = {}
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    sealed class Result {
        data class Cancelled(val order: LimitOrder) : Result()
        data object NotFound : Result()
        data object NotPending : Result()
    }

    suspend fun execute(orderId: UUID): Result {
        val existing = limitOrderRepository.findById(orderId) ?: return Result.NotFound
        if (existing.status != OrderStatus.PENDING) return Result.NotPending

        val updated = limitOrderRepository.markCancelled(orderId) ?: return Result.NotPending

        if (updated.side == OrderSide.BUY && updated.reservedAmount > 0) {
            accountRepository.releaseReservation(updated.userId, updated.reservedAmount)
        }

        logger.info("cancelled orderId={}", updated.id)
        onCancelled(updated)
        return Result.Cancelled(updated)
    }
}
