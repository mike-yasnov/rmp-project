package com.highloadinvest.nativeapp.ui.screens.ticker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.domain.*
import com.highloadinvest.nativeapp.ui.components.*
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.MonoTextStyles
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickerDetailScreen(
    container: AppContainer,
    ticker: String,
    onBack: () -> Unit,
    onTrade: (String, String) -> Unit
) {
    val c = AppTheme.colors
    var quote by remember { mutableStateOf<Quote?>(null) }
    var portfolio by remember { mutableStateOf<Portfolio?>(null) }
    var orders by remember { mutableStateOf<List<Order>>(emptyList()) }
    var candles by remember { mutableStateOf<List<Candle>>(emptyList()) }
    var timeframe by remember { mutableStateOf(Timeframe.Week) }
    var chartType by remember { mutableStateOf(ChartType.Candle) }
    val session by container.session.sessionFlow.collectAsState(initial = null)

    LaunchedEffect(ticker) {
        while (true) {
            try { quote = container.api.quote(ticker) } catch (_: Exception) {}
            session?.userId?.let { uid ->
                try {
                    portfolio = container.api.portfolio(uid)
                    orders = container.api.listOrders(uid, "PENDING").filter { it.ticker == ticker }
                } catch (_: Exception) {}
            }
            delay(5000)
        }
    }

    LaunchedEffect(ticker, timeframe) {
        try {
            val now = System.currentTimeMillis() / 1000
            val from = now - timeframe.seconds
            candles = container.api.candles(ticker, from, now, timeframe.interval)
        } catch (_: Exception) {}
    }

    val position = portfolio?.positions?.firstOrNull { it.ticker == ticker }
    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(ticker, color = c.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        quote?.let {
                            Text(formatNumber(it.price) + " ₽", color = c.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "back", tint = c.textPrimary) }
                },
                actions = {
                    quote?.let { PercentBadge(percent = it.changePercent24h, abs = it.change24h, modifier = Modifier.padding(end = 12.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(c.canvas).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton(
                    text = "Купить",
                    color = c.accentUp,
                    onClick = { onTrade(ticker, "BUY") },
                    modifier = Modifier.weight(1f)
                )
                PrimaryButton(
                    text = "Продать",
                    color = c.accentDown,
                    onClick = { onTrade(ticker, "SELL") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(c.canvas).padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chart card
            AppCard(modifier = Modifier.fillMaxWidth()) {
                SegmentedControl(
                    options = listOf("Линия", "Свечи"),
                    selectedIndex = if (chartType == ChartType.Line) 0 else 1,
                    onSelect = { chartType = if (it == 0) ChartType.Line else ChartType.Candle },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PriceChart(candles = candles, type = chartType, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    options = Timeframe.entries.map { it.label },
                    selectedIndex = Timeframe.entries.indexOf(timeframe),
                    onSelect = { timeframe = Timeframe.entries[it] },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (position != null) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Ваша позиция", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${position.lots} лот", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                            Spacer(Modifier.height(2.dp))
                            Text("Средняя ${formatNumber(position.avgPrice)} ₽", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            NumberText(position.marketValue, style = MonoTextStyles.numberSmall)
                            Spacer(Modifier.height(2.dp))
                            PercentBadge(percent = position.unrealizedPnlPercent, abs = position.unrealizedPnl)
                        }
                    }
                }
            }

            if (orders.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Открытые заявки", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    orders.forEach { o ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sideColor = if (o.side == "BUY") c.accentUp else c.accentDown
                            Surface(
                                color = sideColor.copy(alpha = 0.15f), contentColor = sideColor,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(o.side, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${o.lots} × ${formatNumber(o.limitPrice)} ₽",
                                style = MaterialTheme.typography.bodyMedium, color = c.textPrimary,
                                modifier = Modifier.weight(1f))
                            Text(o.status, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
