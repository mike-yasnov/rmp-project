package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.UserRepository
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(val username: String, val email: String, val initialBalance: Double = 100000.0)

@Serializable
data class UserResponse(val id: String, val username: String, val email: String, val balance: Double)

fun Route.userRoutes(userRepository: UserRepository, accountRepository: AccountRepository) {
    route("/users") {
        post {
            val request = call.receive<CreateUserRequest>()
            val user = userRepository.create(request.username, request.email)
            val account = accountRepository.create(user.id, request.initialBalance)
            call.respond(
                HttpStatusCode.Created,
                UserResponse(
                    id = user.id.toString(),
                    username = user.username,
                    email = user.email,
                    balance = account.balance
                )
            )
        }
    }
}
