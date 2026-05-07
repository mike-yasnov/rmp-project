package com.highloadinvest.nativeapp

import android.content.Context
import com.highloadinvest.nativeapp.data.local.SessionStore
import com.highloadinvest.nativeapp.data.remote.ApiService
import com.highloadinvest.nativeapp.data.remote.MarketSocket

/**
 * Tiny manual DI container. Created once in HighLoadApp.onCreate, accessed via LocalAppContainer.
 */
class AppContainer(context: Context) {
    val baseUrl: String = BuildConfig.API_BASE_URL
    private val baseWsUrl: String = baseUrl.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
    val api: ApiService = ApiService(baseUrl)
    val session: SessionStore = SessionStore(context.applicationContext)
    val market: MarketSocket = MarketSocket(baseWsUrl)
    fun orderSocketFor(userId: String) =
        com.highloadinvest.nativeapp.data.remote.OrderSocket(baseWsUrl, userId)
}
