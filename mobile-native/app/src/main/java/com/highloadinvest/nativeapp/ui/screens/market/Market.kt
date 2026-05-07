package com.highloadinvest.nativeapp.ui.screens.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.domain.Quote
import com.highloadinvest.nativeapp.ui.components.AppTextField
import com.highloadinvest.nativeapp.ui.components.NumberText
import com.highloadinvest.nativeapp.ui.components.PercentBadge
import com.highloadinvest.nativeapp.ui.components.formatNumber
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.MonoTextStyles
import kotlinx.coroutines.delay

@Composable
fun MarketScreen(container: AppContainer, onTickerClick: (String) -> Unit) {
    val c = AppTheme.colors
    var quotes by remember { mutableStateOf<List<Quote>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.market.connect()
        while (true) {
            try { quotes = container.api.quotes().sortedBy { it.ticker }; error = null }
            catch (e: Exception) { error = e.message }
            delay(5000)
        }
    }

    LaunchedEffect(Unit) {
        container.market.quotes.collect { incoming ->
            quotes = quotes.map { if (it.ticker == incoming.ticker) it.copy(price = incoming.price) else it }
        }
    }

    val filtered = remember(quotes, query) {
        if (query.isBlank()) quotes
        else quotes.filter { it.ticker.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(c.canvas)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text("Биржа", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Поиск по тикеру",
                modifier = Modifier.fillMaxWidth(),
                leading = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                }
            )
        }
        if (error != null && quotes.isEmpty()) {
            Text(
                "Не удалось загрузить котировки: $error",
                style = MaterialTheme.typography.bodyMedium,
                color = c.accentDown,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(filtered, key = { it.ticker }) { q ->
                QuoteRow(q, onClick = { onTickerClick(q.ticker) })
            }
        }
    }
}

@Composable
private fun QuoteRow(q: Quote, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(q.ticker, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("Биржевой тикер", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            NumberText(q.price, style = MonoTextStyles.numberSmall, currency = "₽")
            Spacer(Modifier.height(2.dp))
            PercentBadge(percent = q.changePercent24h, abs = q.change24h)
        }
    }
}
