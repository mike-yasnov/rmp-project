package com.highloadinvest.banking.application.usecases

import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderStatus
import com.highloadinvest.banking.domain.repositories.LimitOrderRepository
import java.util.UUID

class ListUserOrders(private val limitOrderRepository: LimitOrderRepository) {
    suspend fun execute(userId: UUID, status: OrderStatus? = null): List<LimitOrder> =
        limitOrderRepository.findByUserId(userId, status)
}
