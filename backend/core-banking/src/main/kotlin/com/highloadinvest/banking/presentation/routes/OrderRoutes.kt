package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.application.usecases.CancelLimitOrder
import com.highloadinvest.banking.application.usecases.ListUserOrders
import com.highloadinvest.banking.application.usecases.PlaceLimitOrder
import com.highloadinvest.banking.domain.entities.LimitOrder
import com.highloadinvest.banking.domain.entities.OrderSide
import com.highloadinvest.banking.domain.entities.OrderStatus
import com.highloadinvest.banking.domain.repositories.LimitOrderRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PlaceOrderRequest(
    val userId: String,
    val ticker: String,
    val side: String,
    val lots: Int,
    val limitPrice: Double
)

@Serializable
data class OrderResponse(
    val id: String,
    val userId: String,
    val ticker: String,
    val side: String,
    val lots: Int,
    val limitPrice: Double,
    val status: String,
    val reservedAmount: Double,
    val createdAt: String,
    val filledAt: String? = null,
    val cancelledAt: String? = null,
    val fillTradeId: String? = null,
    val fillPrice: Double? = null
)

private fun LimitOrder.toResponse() = OrderResponse(
    id = id.toString(),
    userId = userId.toString(),
    ticker = ticker,
    side = side.name,
    lots = lots,
    limitPrice = limitPrice,
    status = status.name,
    reservedAmount = reservedAmount,
    createdAt = createdAt.toString(),
    filledAt = filledAt?.toString(),
    cancelledAt = cancelledAt?.toString(),
    fillTradeId = fillTradeId?.toString(),
    fillPrice = fillPrice
)

fun Route.orderRoutes(
    placeLimitOrder: PlaceLimitOrder,
    cancelLimitOrder: CancelLimitOrder,
    listUserOrders: ListUserOrders,
    limitOrderRepository: LimitOrderRepository
) {
    route("/orders") {
        post {
            val req = call.receive<PlaceOrderRequest>()
            val side = try {
                OrderSide.valueOf(req.side.uppercase())
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "side must be BUY or SELL"))
                return@post
            }
            val order = placeLimitOrder.execute(
                PlaceLimitOrder.Request(
                    userId = UUID.fromString(req.userId),
                    ticker = req.ticker,
                    side = side,
                    lots = req.lots,
                    limitPrice = req.limitPrice
                )
            )
            call.respond(HttpStatusCode.Created, order.toResponse())
        }

        get("/{userId}") {
            val userId = UUID.fromString(call.parameters["userId"])
            val statusParam = call.parameters["status"]?.uppercase()
            val status = if (statusParam.isNullOrBlank() || statusParam == "ALL") null
            else try { OrderStatus.valueOf(statusParam) } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Unknown status: $statusParam"))
                return@get
            }
            val orders = listUserOrders.execute(userId, status)
            call.respond(orders.map { it.toResponse() })
        }

        get("/order/{orderId}") {
            val orderId = UUID.fromString(call.parameters["orderId"])
            val order = limitOrderRepository.findById(orderId)
            if (order != null) call.respond(order.toResponse())
            else call.respond(HttpStatusCode.NotFound, mapOf("error" to "Order not found"))
        }

        delete("/{orderId}") {
            val orderId = UUID.fromString(call.parameters["orderId"])
            when (val result = cancelLimitOrder.execute(orderId)) {
                is CancelLimitOrder.Result.Cancelled -> call.respond(result.order.toResponse())
                CancelLimitOrder.Result.NotFound -> call.respond(HttpStatusCode.NotFound, mapOf("error" to "Order not found"))
                CancelLimitOrder.Result.NotPending -> call.respond(HttpStatusCode.Conflict, mapOf("error" to "Order is not pending"))
            }
        }
    }
}
