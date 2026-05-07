package com.highloadinvest.nativeapp.ui.screens.order

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.ui.components.*
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.MonoTextStyles
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitOrderScreen(
    container: AppContainer,
    ticker: String,
    side: String,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val c = AppTheme.colors
    val sideUp = side.uppercase()
    val isBuy = sideUp == "BUY"
    val sideColor = if (isBuy) c.accentUp else c.accentDown
    val title = if (isBuy) "Купить $ticker" else "Продать $ticker"

    var orderType by remember { mutableStateOf(0) } // 0 = Limit, 1 = Market
    var lots by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val session by container.session.sessionFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(ticker) {
        try { val q = container.api.quote(ticker); price = "%.2f".format(q.price) }
        catch (_: Exception) {}
    }

    val lotsInt = lots.toIntOrNull() ?: 0
    val priceVal = price.replace(",", ".").toDoubleOrNull() ?: 0.0
    val total = lotsInt * priceVal

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text(title, color = c.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "back", tint = c.textPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(c.canvas).padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SegmentedControl(
                options = listOf("Лимит", "Рынок"),
                selectedIndex = orderType,
                onSelect = { orderType = it },
                modifier = Modifier.fillMaxWidth()
            )

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text("Количество, лот", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = lots,
                    onValueChange = { lots = it.filter { ch -> ch.isDigit() } },
                    placeholder = "1",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (orderType == 0) "Лимитная цена, ₽" else "Цена исполнения (текущая)",
                    style = MaterialTheme.typography.labelMedium, color = c.textMuted
                )
                Spacer(Modifier.height(6.dp))
                AppTextField(
                    value = price,
                    onValueChange = { v -> price = v.filter { it.isDigit() || it == '.' || it == ',' } },
                    placeholder = "0,00",
                    keyboardType = KeyboardType.Decimal,
                    enabled = orderType == 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Итого", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                        Spacer(Modifier.height(4.dp))
                        NumberText(total, style = MonoTextStyles.numberMedium)
                    }
                    Text(
                        if (isBuy) "к списанию" else "к зачислению",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textMuted
                    )
                }
            }

            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.accentDown, fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = if (loading) "..." else (if (isBuy) "Подтвердить покупку" else "Подтвердить продажу"),
                color = sideColor,
                enabled = lotsInt > 0 && priceVal > 0 && !loading,
                onClick = {
                    val sid = session?.userId ?: return@PrimaryButton
                    loading = true; error = null
                    scope.launch {
                        try {
                            if (orderType == 1) {
                                container.api.marketTrade(sid, ticker, sideUp, lotsInt, priceVal)
                            } else {
                                container.api.placeLimitOrder(sid, ticker, sideUp, lotsInt, priceVal)
                            }
                            onDone()
                        } catch (e: Exception) {
                            error = "Не удалось: ${e.message?.take(120)}"
                        } finally { loading = false }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
