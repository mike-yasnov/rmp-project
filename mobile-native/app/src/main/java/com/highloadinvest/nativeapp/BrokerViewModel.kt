package com.highloadinvest.nativeapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.WebSocket

data class BrokerUiState(
    val user: UserResponse? = null,
    val quotes: List<QuoteResponse> = emptyList(),
    val selectedTicker: String = "SBER",
    val candles: List<CandleResponse> = emptyList(),
    val portfolio: PortfolioResponse? = null,
    val lots: String = "1",
    val message: String = "Готово",
    val loading: Boolean = false
) {
    val selectedQuote: QuoteResponse?
        get() = quotes.firstOrNull { it.ticker == selectedTicker } ?: quotes.firstOrNull()
}

class BrokerViewModel : ViewModel() {
    private val api = BrokerApi()
    private val _state = MutableStateFlow(BrokerUiState())
    val state: StateFlow<BrokerUiState> = _state

    private var pollingJob: Job? = null
    private var webSocket: WebSocket? = null

    init {
        refreshQuotes()
        pollingJob = viewModelScope.launch {
            while (true) {
                refreshQuotes(silent = true)
                delay(5_000)
            }
        }
        webSocket = api.subscribeQuotes { quote ->
            _state.update { current ->
                val nextQuotes = current.quotes
                    .filterNot { it.ticker == quote.ticker }
                    .plus(quote)
                    .sortedBy { it.ticker }
                current.copy(quotes = nextQuotes)
            }
        }
    }

    fun setLots(value: String) {
        _state.update { it.copy(lots = value.filter(Char::isDigit).ifBlank { "1" }) }
    }

    fun selectTicker(ticker: String) {
        _state.update { it.copy(selectedTicker = ticker) }
        refreshCandles()
    }

    fun createUser(username: String, email: String) = runAction("Пользователь создан") {
        val user = api.createUser(username, email, 1_000_000.0)
        val portfolio = api.portfolio(user.id)
        _state.update { it.copy(user = user, portfolio = portfolio) }
    }

    fun deposit() = runAction("Счёт пополнен") {
        val userId = requireUserId()
        val portfolio = api.deposit(userId, 50_000.0)
        _state.update { it.copy(portfolio = portfolio) }
    }

    fun buy() = trade("BUY")

    fun sell() = trade("SELL")

    fun refreshQuotes(silent: Boolean = false) = runAction(if (silent) null else "Котировки обновлены") {
        val quotes = api.quotes().sortedBy { it.ticker }
        val selected = _state.value.selectedTicker.takeIf { ticker -> quotes.any { it.ticker == ticker } }
            ?: quotes.firstOrNull()?.ticker
            ?: _state.value.selectedTicker
        _state.update { it.copy(quotes = quotes, selectedTicker = selected) }
        refreshCandles()
    }

    fun refreshPortfolio() = runAction("Портфель обновлён") {
        val portfolio = api.portfolio(requireUserId())
        _state.update { it.copy(portfolio = portfolio) }
    }

    private fun trade(action: String) = runAction("Заявка исполнена") {
        val current = _state.value
        val quote = current.selectedQuote ?: throw IllegalStateException("Нет выбранной котировки")
        val lots = current.lots.toIntOrNull()?.coerceAtLeast(1) ?: 1
        api.trade(requireUserId(), quote.ticker, action, lots, quote.price)
        val portfolio = api.portfolio(requireUserId())
        _state.update { it.copy(portfolio = portfolio) }
    }

    private fun refreshCandles() = viewModelScope.launch {
        runCatching { api.candles(_state.value.selectedTicker) }
            .onSuccess { candles -> _state.update { it.copy(candles = candles.takeLast(30)) } }
    }

    private fun runAction(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { block() }
                .onSuccess {
                    if (successMessage != null) {
                        _state.update { state -> state.copy(message = successMessage) }
                    }
                }
                .onFailure { error ->
                    _state.update { state -> state.copy(message = error.message ?: "Ошибка") }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    private fun requireUserId(): String =
        _state.value.user?.id ?: throw IllegalStateException("Сначала зарегистрируйтесь")

    override fun onCleared() {
        pollingJob?.cancel()
        webSocket?.cancel()
    }
}
