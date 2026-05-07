package com.highloadinvest.nativeapp.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.ui.components.AppCard
import com.highloadinvest.nativeapp.ui.components.PrimaryButton
import com.highloadinvest.nativeapp.ui.components.SegmentedControl
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    container: AppContainer,
    onLogout: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    currentTheme: ThemeMode
) {
    val c = AppTheme.colors
    val session by container.session.sessionFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(c.canvas).padding(16.dp)) {
        Text("Профиль", style = MaterialTheme.typography.headlineLarge, color = c.textPrimary)
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(c.accentBrand),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = session?.username?.take(2)?.uppercase().orEmpty()
                    Text(initials, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session?.username ?: "—", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(session?.email ?: "—", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text("ID: ${session?.userId?.take(8)}…", style = MaterialTheme.typography.labelSmall, color = c.textMuted)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Text("Тема", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            SegmentedControl(
                options = listOf("Системная", "Тёмная", "Светлая"),
                selectedIndex = when (currentTheme) {
                    ThemeMode.System -> 0; ThemeMode.Dark -> 1; ThemeMode.Light -> 2
                },
                onSelect = { idx ->
                    val mode = when (idx) { 1 -> ThemeMode.Dark; 2 -> ThemeMode.Light; else -> ThemeMode.System }
                    onThemeChange(mode)
                    scope.launch { container.session.setTheme(mode) }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Выйти",
            color = c.accentDown,
            onClick = {
                scope.launch { container.session.clear(); onLogout() }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
        Text(
            "HighLoad Invest · v0.2 · РМП ИТМО 2026",
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}
