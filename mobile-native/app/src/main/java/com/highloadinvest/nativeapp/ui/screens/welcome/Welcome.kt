package com.highloadinvest.nativeapp.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.ui.components.PrimaryButton
import com.highloadinvest.nativeapp.ui.components.SecondaryButton
import com.highloadinvest.nativeapp.ui.theme.AppTheme

@Composable
fun WelcomeScreen(onLogin: () -> Unit, onRegister: () -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.canvas)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(c.accentBrand),
            contentAlignment = Alignment.Center
        ) {
            Text("HI", style = MaterialTheme.typography.headlineLarge, color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "HighLoad Invest",
            style = MaterialTheme.typography.displayLarge,
            color = c.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Симулятор биржевых торгов\nдля курса РМП ИТМО",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1.4f))
        PrimaryButton(
            text = "Создать аккаунт",
            onClick = onRegister,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            text = "У меня уже есть аккаунт",
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Учебный проект · без реальных денег",
            style = MaterialTheme.typography.labelSmall,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}
