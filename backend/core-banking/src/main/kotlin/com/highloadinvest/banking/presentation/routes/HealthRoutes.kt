package com.highloadinvest.banking.presentation.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val service: String)

fun Route.healthRoutes(serviceName: String = "core-banking") {
    get("/health") {
        call.respond(HealthResponse(status = "ok", service = serviceName))
    }
}
