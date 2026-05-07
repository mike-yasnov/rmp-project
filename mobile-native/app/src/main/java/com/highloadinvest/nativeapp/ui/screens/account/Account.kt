package com.highloadinvest.nativeapp.ui.screens.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.domain.Portfolio
import com.highloadinvest.nativeapp.domain.Position
import com.highloadinvest.nativeapp.ui.components.*
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.MonoTextStyles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(container: AppContainer, onTickerClick: (String) -> Unit) {
    val c = AppTheme.colors
    var portfolio by remember { mutableStateOf<Portfolio?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var depositOpen by remember { mutableStateOf(false) }
    var depositAmount by remember { mutableStateOf("10000") }
    val scope = rememberCoroutineScope()

    val sessionId by container.session.sessionFlow.collectAsState(initial = null)

    LaunchedEffect(sessionId) {
        val sid = sessionId?.userId ?: return@LaunchedEffect
        while (true) {
            try { portfolio = container.api.portfolio(sid); error = null }
            catch (e: Exception) { error = e.message }
            delay(5000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.canvas).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Счёт", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
        }
        item {
            BalanceCard(portfolio, onDeposit = { depositOpen = true })
        }
        item {
            Text(
                "Позиции",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        val positions = portfolio?.positions.orEmpty()
        if (positions.isEmpty()) {
            item {
                AppCard {
                    Text(
                        "У вас пока нет открытых позиций.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Откройте «Биржу» внизу и купите первую бумагу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textMuted
                    )
                }
            }
        } else {
            items(positions, key = { it.ticker }) { p ->
                PositionRow(p, onClick = { onTickerClick(p.ticker) })
            }
        }
        if (error != null) {
            item {
                Text(
                    "Ошибка: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.accentDown,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (depositOpen) {
        ModalBottomSheet(onDismissRequest = { depositOpen = false }, containerColor = c.elevated) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Пополнить счёт", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = depositAmount,
                    onValueChange = { depositAmount = it.filter { ch -> ch.isDigit() } },
                    placeholder = "сумма, ₽",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = "Пополнить",
                    onClick = {
                        val amount = depositAmount.toDoubleOrNull() ?: return@PrimaryButton
                        val sid = sessionId?.userId ?: return@PrimaryButton
                        scope.launch {
                            try {
                                container.api.deposit(sid, amount)
                                portfolio = container.api.portfolio(sid)
                                depositOpen = false
                            } catch (e: Exception) { error = e.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BalanceCard(p: Portfolio?, onDeposit: () -> Unit) {
    val c = AppTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text("Баланс", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
        Spacer(Modifier.height(4.dp))
        NumberText(p?.balance ?: 0.0, style = MonoTextStyles.numberLarge)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ValueColumn(label = "Доступно", value = p?.availableBalance ?: p?.balance ?: 0.0, modifier = Modifier.weight(1f))
            ValueColumn(label = "Резерв", value = p?.reservedBalance ?: 0.0, modifier = Modifier.weight(1f))
        }
        if ((p?.totals?.invested ?: 0.0) > 0) {
            Spacer(Modifier.height(16.dp))
            Divider(color = c.borderSubtle)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Стоимость портфеля", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                    Spacer(Modifier.height(2.dp))
                    NumberText(p!!.totals.marketValue, style = MonoTextStyles.numberMedium)
                }
                PercentBadge(percent = p!!.totals.unrealizedPnlPercent, abs = p.totals.unrealizedPnl)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = "Пополнить", onClick = onDeposit, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ValueColumn(label: String, value: Double, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textMuted)
        Spacer(Modifier.height(2.dp))
        NumberText(value, style = MonoTextStyles.numberSmall)
    }
}

@Composable
private fun PositionRow(p: Position, onClick: () -> Unit) {
    val c = AppTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(p.ticker, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${p.lots} лот · средняя ${formatNumber(p.avgPrice)} ₽",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                NumberText(p.marketValue, style = MonoTextStyles.numberSmall)
                Spacer(Modifier.height(4.dp))
                PercentBadge(percent = p.unrealizedPnlPercent, abs = p.unrealizedPnl)
            }
        }
    }
}
