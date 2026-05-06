package com.highloadinvest.gateway.routes

import com.highloadinvest.gateway.module
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class HealthRoutesTest {

    @Test
    fun `health endpoint returns ok`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "clickhouse.url" to "http://localhost:8123",
                "redis.host" to "localhost",
                "redis.port" to "6379",
                "banking.url" to "http://localhost:8081"
            )
        }
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "ok")
        assertContains(response.bodyAsText(), "api-gateway")
    }
}
