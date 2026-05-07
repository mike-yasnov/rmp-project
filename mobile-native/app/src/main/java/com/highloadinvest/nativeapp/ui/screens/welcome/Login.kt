package com.highloadinvest.nativeapp.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
fun LoginScreen(container: AppContainer, onBack: () -> Unit, onSuccess: () -> Unit) {
    val c = AppTheme.colors
    var username by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = c.canvas,
        topBar = {
            TopAppBar(
                title = { Text("Вход", color = c.textPrimary) },
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
            Text(
                "Войдите по имени пользователя",
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Учебный проект — паролей нет. Введите username, который указали при регистрации.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
            Spacer(Modifier.height(24.dp))
            AppTextField(
                value = username,
                onValueChange = { username = it; error = null },
                placeholder = "username",
                keyboardType = KeyboardType.Text,
                isError = error != null,
                modifier = Modifier.fillMaxWidth()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.accentDown)
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = if (loading) "..." else "Войти",
                onClick = {
                    if (username.isBlank() || loading) return@PrimaryButton
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val user = container.api.login(username.trim())
                            container.session.setSession(user.id, user.username, user.email)
                            onSuccess()
                        } catch (e: ApiException) {
                            error = if (e.code == 404) "Пользователь не найден" else "Ошибка ${e.code}"
                            android.util.Log.e("HighLoad", "login api error code=${e.code}", e)
                        } catch (e: Exception) {
                            error = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                            android.util.Log.e("HighLoad", "login network error", e)
                        } finally {
                            loading = false
                        }
                    }
                },
                enabled = username.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
