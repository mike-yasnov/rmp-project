package com.highloadinvest.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val canvas: Color,
    val surface: Color,
    val elevated: Color,
    val borderDefault: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textInverse: Color,
    val accentUp: Color,
    val accentDown: Color,
    val accentBrand: Color,
    val accentWarning: Color,
    val isDark: Boolean
)

private val DarkColors = AppColors(
    canvas = DarkPalette.canvas, surface = DarkPalette.surface, elevated = DarkPalette.elevated,
    borderDefault = DarkPalette.borderDefault, borderSubtle = DarkPalette.borderSubtle,
    textPrimary = DarkPalette.textPrimary, textSecondary = DarkPalette.textSecondary,
    textMuted = DarkPalette.textMuted, textInverse = DarkPalette.textInverse,
    accentUp = DarkPalette.accentUp, accentDown = DarkPalette.accentDown,
    accentBrand = DarkPalette.accentBrand, accentWarning = DarkPalette.accentWarning,
    isDark = true
)

private val LightColors = AppColors(
    canvas = LightPalette.canvas, surface = LightPalette.surface, elevated = LightPalette.elevated,
    borderDefault = LightPalette.borderDefault, borderSubtle = LightPalette.borderSubtle,
    textPrimary = LightPalette.textPrimary, textSecondary = LightPalette.textSecondary,
    textMuted = LightPalette.textMuted, textInverse = LightPalette.textInverse,
    accentUp = LightPalette.accentUp, accentDown = LightPalette.accentDown,
    accentBrand = LightPalette.accentBrand, accentWarning = LightPalette.accentWarning,
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

enum class ThemeMode { System, Dark, Light }

@Composable
fun HighLoadTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        ThemeMode.System -> sysDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val palette = if (isDark) DarkColors else LightColors
    val material = if (isDark) {
        darkColorScheme(
            background = palette.canvas, surface = palette.surface, surfaceVariant = palette.elevated,
            primary = palette.accentBrand, onPrimary = Color.White,
            secondary = palette.accentUp, onSecondary = Color.White,
            error = palette.accentDown, onError = Color.White,
            onBackground = palette.textPrimary, onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary, outline = palette.borderDefault
        )
    } else {
        lightColorScheme(
            background = palette.canvas, surface = palette.surface, surfaceVariant = palette.elevated,
            primary = palette.accentBrand, onPrimary = Color.White,
            secondary = palette.accentUp, onSecondary = Color.White,
            error = palette.accentDown, onError = Color.White,
            onBackground = palette.textPrimary, onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary, outline = palette.borderDefault
        )
    }
    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(colorScheme = material, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
