package com.highloadinvest.nativeapp.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.ui.screens.account.AccountScreen
import com.highloadinvest.nativeapp.ui.screens.market.MarketScreen
import com.highloadinvest.nativeapp.ui.screens.profile.ProfileScreen
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.ThemeMode

private enum class Tab(val title: String, val icon: ImageVector) {
    Account("Счёт", Icons.Rounded.PieChart),
    Market("Биржа", Icons.Rounded.ShowChart),
    Profile("Профиль", Icons.Rounded.AccountCircle)
}

@Composable
fun HomeScaffold(
    container: AppContainer,
    onTickerClicked: (String) -> Unit,
    onLogout: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    currentTheme: ThemeMode
) {
    val c = AppTheme.colors
    var tab by remember { mutableStateOf(Tab.Account) }
    Scaffold(
        containerColor = c.canvas,
        bottomBar = {
            NavigationBar(containerColor = c.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.accentBrand,
                            selectedTextColor = c.accentBrand,
                            indicatorColor = c.accentBrand.copy(alpha = 0.12f),
                            unselectedIconColor = c.textMuted,
                            unselectedTextColor = c.textMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(c.canvas).padding(padding)) {
            when (tab) {
                Tab.Account -> AccountScreen(container, onTickerClicked)
                Tab.Market -> MarketScreen(container, onTickerClicked)
                Tab.Profile -> ProfileScreen(container, onLogout, onThemeChange, currentTheme)
            }
        }
    }
}
