package com.highloadinvest.gateway.presentation.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String)

fun Route.healthRoutes() {
    get("/health") {
        call.respond(HealthResponse(status = "ok", service = "api-gateway"))
    }
}
