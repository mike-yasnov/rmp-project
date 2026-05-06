package com.highloadinvest.gateway.presentation.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.bankingProxyRoutes(client: HttpClient, bankingUrl: String) {
    route("/api") {
        post("/users") {
            call.forwardJson(client, HttpMethod.Post, "$bankingUrl/api/users")
        }

        get("/users/{id}") {
            val id = call.parameters["id"] ?: throw IllegalArgumentException("User id is required")
            call.forwardJson(client, HttpMethod.Get, "$bankingUrl/api/users/$id")
        }

        post("/accounts/{userId}/deposit") {
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("User id is required")
            call.forwardJson(client, HttpMethod.Post, "$bankingUrl/api/accounts/$userId/deposit")
        }

        post("/trades") {
            call.forwardJson(client, HttpMethod.Post, "$bankingUrl/api/trades")
        }

        get("/trades/{userId}") {
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("User id is required")
            call.forwardJson(client, HttpMethod.Get, "$bankingUrl/api/trades/$userId")
        }

        get("/portfolio/{userId}") {
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("User id is required")
            call.forwardJson(client, HttpMethod.Get, "$bankingUrl/api/portfolio/$userId")
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.forwardJson(
    client: HttpClient,
    method: HttpMethod,
    url: String
) {
    val requestBody = if (method == HttpMethod.Get) null else receiveText()
    val response = client.request(url) {
        this.method = method
        accept(ContentType.Application.Json)
        requestBody?.takeIf { it.isNotBlank() }?.let {
            contentType(ContentType.Application.Json)
            setBody(TextContent(it, ContentType.Application.Json))
        }
    }
    respondText(
        text = response.bodyAsText(),
        contentType = response.contentType() ?: ContentType.Application.Json,
        status = response.status
    )
}
