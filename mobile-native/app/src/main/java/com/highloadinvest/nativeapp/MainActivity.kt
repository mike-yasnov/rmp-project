package com.highloadinvest.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<BrokerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7F9)) {
                    BrokerScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun BrokerScreen(viewModel: BrokerViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(state)
        RegistrationPanel(state, viewModel)
        QuotesPanel(state, viewModel)
        TradePanel(state, viewModel)
        PortfolioPanel(state)
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF49515A)
        )
    }
}

@Composable
private fun Header(state: BrokerUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("HighLoad Invest", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Native Android", color = Color(0xFF69727D))
        }
        if (state.loading) {
            Text("sync", color = Color(0xFF0F766E), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RegistrationPanel(state: BrokerUiState, viewModel: BrokerViewModel) {
    var username by remember { mutableStateOf("android_${System.currentTimeMillis() % 100000}") }
    var email by remember { mutableStateOf("$username@example.test") }

    SectionCard {
        Text("Аккаунт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (state.user == null) {
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    email = "$it@example.test"
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Логин") }
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") }
            )
            Button(
                onClick = { viewModel.createUser(username, email) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Зарегистрироваться")
            }
        } else {
            Text("${state.user.username} · ${state.user.id}", color = Color(0xFF49515A))
            OutlinedButton(onClick = viewModel::deposit, modifier = Modifier.fillMaxWidth()) {
                Text("Пополнить на 50 000 RUB")
            }
        }
    }
}

@Composable
private fun QuotesPanel(state: BrokerUiState, viewModel: BrokerViewModel) {
    SectionCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Котировки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${state.quotes.size} тикеров", color = Color(0xFF69727D))
        }
        LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.quotes) { quote ->
                val selected = quote.ticker == state.selectedTicker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (selected) Color(0xFFE0F2FE) else Color.White, RoundedCornerShape(8.dp))
                        .clickable { viewModel.selectTicker(quote.ticker) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(quote.ticker, fontWeight = FontWeight.Bold)
                    Text("${quote.price.money()} RUB")
                }
            }
        }
        CandleChart(state.candles)
    }
}

@Composable
private fun CandleChart(candles: List<CandleResponse>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Свечной график", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(Color(0xFF101820), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (candles.isEmpty()) return@Canvas
            val min = candles.minOf { it.low }
            val max = candles.maxOf { it.high }
            val range = (max - min).takeIf { it > 0.0 } ?: 1.0
            val step = size.width / candles.size
            candles.forEachIndexed { index, candle ->
                val x = index * step + step / 2f
                fun y(price: Double): Float = (size.height - ((price - min) / range * size.height)).toFloat()
                val color = if (candle.close >= candle.open) Color(0xFF22C55E) else Color(0xFFEF4444)
                drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), strokeWidth = 2f)
                val top = minOf(y(candle.open), y(candle.close))
                val bottom = maxOf(y(candle.open), y(candle.close))
                drawRect(
                    color = color,
                    topLeft = Offset(x - step * 0.25f, top),
                    size = Size(step * 0.5f, maxOf(2f, bottom - top))
                )
            }
            drawRect(Color(0xFF334155), topLeft = Offset.Zero, size = size, style = Stroke(width = 1f))
        }
    }
}

@Composable
private fun TradePanel(state: BrokerUiState, viewModel: BrokerViewModel) {
    val quote = state.selectedQuote
    SectionCard {
        Text("Заявка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            text = quote?.let { "${it.ticker}: ${it.price.money()} RUB" } ?: "Нет цены",
            color = Color(0xFF49515A)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.lots,
                onValueChange = viewModel::setLots,
                modifier = Modifier.weight(1f),
                label = { Text("Лоты") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.width(12.dp))
            Text("Итого ${((quote?.price ?: 0.0) * (state.lots.toIntOrNull() ?: 1)).money()}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = viewModel::buy, modifier = Modifier.weight(1f), enabled = state.user != null && quote != null) {
                Text("Купить")
            }
            OutlinedButton(onClick = viewModel::sell, modifier = Modifier.weight(1f), enabled = state.user != null && quote != null) {
                Text("Продать")
            }
        }
    }
}

@Composable
private fun PortfolioPanel(state: BrokerUiState) {
    SectionCard {
        Text("Портфель", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val portfolio = state.portfolio
        if (portfolio == null) {
            Text("Зарегистрируйтесь, чтобы увидеть портфель", color = Color(0xFF69727D))
        } else {
            Text("${portfolio.balance.money()} ${portfolio.currency}", style = MaterialTheme.typography.titleLarge)
            portfolio.positions.forEach { position ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(position.ticker, fontWeight = FontWeight.SemiBold)
                    Text("${position.lots} лот. · avg ${position.avgPrice.money()}")
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

private fun Double.money(): String = String.format(Locale.US, "%,.2f", this)
