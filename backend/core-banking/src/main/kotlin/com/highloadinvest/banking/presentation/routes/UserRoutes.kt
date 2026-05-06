package com.highloadinvest.banking.presentation.routes

import com.highloadinvest.banking.domain.repositories.AccountRepository
import com.highloadinvest.banking.domain.repositories.UserRepository
import com.highloadinvest.banking.infrastructure.observability.BankingMetrics
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(val username: String, val email: String, val initialBalance: Double = 100000.0)

@Serializable
data class UserResponse(val id: String, val username: String, val email: String, val balance: Double)

@Serializable
data class DepositRequest(val amount: Double)

@Serializable
data class AccountResponse(val userId: String, val balance: Double, val currency: String)

fun Route.userRoutes(
    userRepository: UserRepository,
    accountRepository: AccountRepository,
    metrics: BankingMetrics? = null
) {
    route("/users") {
        post {
            val request = call.receive<CreateUserRequest>()
            val user = userRepository.create(request.username, request.email)
            val account = accountRepository.create(user.id, request.initialBalance)
            metrics?.usersCreated?.add(1)
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

        get("/{id}") {
            val id = java.util.UUID.fromString(call.parameters["id"])
            val user = userRepository.findById(id) ?: throw NoSuchElementException("User not found: $id")
            val account = accountRepository.findByUserId(id) ?: throw NoSuchElementException("Account not found: $id")
            call.respond(
                UserResponse(
                    id = user.id.toString(),
                    username = user.username,
                    email = user.email,
                    balance = account.balance
                )
            )
        }
    }

    route("/accounts") {
        post("/{userId}/deposit") {
            val userId = java.util.UUID.fromString(call.parameters["userId"])
            val request = call.receive<DepositRequest>()
            val account = accountRepository.deposit(userId, request.amount)
            call.respond(
                AccountResponse(
                    userId = account.userId.toString(),
                    balance = account.balance,
                    currency = account.currency
                )
            )
        }
    }
}
