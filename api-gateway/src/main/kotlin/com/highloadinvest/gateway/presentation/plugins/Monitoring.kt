package com.highloadinvest.gateway.presentation.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import org.slf4j.event.Level

fun Application.configureMonitoring() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.headers["User-Agent"] != "kube-probe" }
    }
}
