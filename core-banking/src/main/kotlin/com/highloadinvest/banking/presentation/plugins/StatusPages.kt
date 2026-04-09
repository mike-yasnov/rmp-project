package com.highloadinvest.banking.presentation.plugins

import com.highloadinvest.banking.presentation.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("StatusPages")

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            logger.warn("Bad request: {}", cause.message)
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request", cause.message ?: "Invalid request", 400))
        }
        exception<NoSuchElementException> { call, cause ->
            logger.warn("Not found: {}", cause.message)
            call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", cause.message ?: "Not found", 404))
        }
        exception<Throwable> { call, cause ->
            logger.error("Internal error: {}", cause.message, cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Internal server error", 500))
        }
    }
}
