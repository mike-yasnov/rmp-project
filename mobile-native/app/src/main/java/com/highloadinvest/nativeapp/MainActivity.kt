package com.highloadinvest.nativeapp

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.highloadinvest.nativeapp.ui.navigation.AppNav
import com.highloadinvest.nativeapp.ui.theme.HighLoadTheme
import com.highloadinvest.nativeapp.ui.theme.ThemeMode

class HighLoadApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HighLoadApp).container
        setContent {
            val themeMode by container.session.themeFlow.collectAsState(initial = ThemeMode.System)
            var override by remember { mutableStateOf<ThemeMode?>(null) }
            HighLoadTheme(mode = override ?: themeMode) {
                AppNav(
                    container = container,
                    themeMode = override ?: themeMode,
                    onThemeChange = { override = it }
                )
            }
        }
    }
}
