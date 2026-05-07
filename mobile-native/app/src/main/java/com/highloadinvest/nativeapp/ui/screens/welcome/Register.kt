package com.highloadinvest.nativeapp.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.AppContainer
import com.highloadinvest.nativeapp.data.remote.ApiException
import com.highloadinvest.nativeapp.ui.components.AppTextField
import com.highloadinvest.nativeapp.ui.components.PrimaryButton
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(container: AppContainer, onBack: () -> Unit, onSuccess: () -> Unit) {
    val c = AppTheme.colors
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var initial by remember { mutableStateOf("100000") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Регистрация", color = c.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "back", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.canvas)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.canvas)
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Создайте аккаунт", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Получите стартовый депозит и попробуйте торговлю без рисков.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
            Spacer(Modifier.height(24.dp))

            AppTextField(value = username, onValueChange = { username = it; error = null },
                placeholder = "username", isError = error != null, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            AppTextField(value = email, onValueChange = { email = it; error = null },
                placeholder = "email", keyboardType = KeyboardType.Email, isError = error != null,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            AppTextField(value = initial, onValueChange = { initial = it.filter { ch -> ch.isDigit() } },
                placeholder = "стартовый депозит, ₽", keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth())
            Text(
                "Минимум 1 ₽. По умолчанию — 100 000 ₽.",
                style = MaterialTheme.typography.labelSmall,
                color = c.textMuted,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp)
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.accentDown)
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = if (loading) "..." else "Создать",
                onClick = {
                    if (username.isBlank() || email.isBlank() || loading) return@PrimaryButton
                    val deposit = initial.toDoubleOrNull() ?: 100000.0
                    loading = true; error = null
                    scope.launch {
                        try {
                            val user = container.api.register(username.trim(), email.trim(), deposit)
                            container.session.setSession(user.id, user.username, user.email)
                            onSuccess()
                        } catch (e: ApiException) {
                            error = "Ошибка ${e.code}: ${e.message?.take(80)}"
                        } catch (e: Exception) {
                            error = "Ошибка: ${e.message}"
                        } finally { loading = false }
                    }
                },
                enabled = username.isNotBlank() && email.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
