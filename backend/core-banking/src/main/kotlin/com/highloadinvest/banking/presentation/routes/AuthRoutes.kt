package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.application.usecases.AuthenticateUser
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String)

@Serializable
data class LoginResponse(val id: String, val username: String, val email: String, val balance: Double)

@Serializable
data class ErrorResponse(val error: String)

fun Route.authRoutes(authenticateUser: AuthenticateUser) {
    route("/auth") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Username is required"))
                return@post
            }
            val result = authenticateUser.execute(request.username.trim())
            if (result == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                return@post
            }
            call.respond(
                LoginResponse(
                    id = result.id.toString(),
                    username = result.username,
                    email = result.email,
                    balance = result.balance
                )
            )
        }
    }
}
