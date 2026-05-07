package com.highloadinvest.nativeapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import com.highloadinvest.nativeapp.ui.theme.MonoTextStyles
import kotlin.math.abs

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = AppTheme.colors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = c.surface,
        border = BorderStroke(1.dp, c.borderSubtle),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun NumberText(value: Double, modifier: Modifier = Modifier, currency: String = "₽", style: TextStyle = MonoTextStyles.numberMedium) {
    val txt = formatMoney(value, currency)
    Text(text = txt, modifier = modifier, color = AppTheme.colors.textPrimary, style = style)
}

fun formatMoney(value: Double, currency: String = "₽"): String {
    val abs = abs(value)
    val sign = if (value < 0) "−" else ""
    val whole = abs.toLong()
    val frac = ((abs - whole) * 100).toLong()
    val whStr = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return "$sign$whStr,${frac.toString().padStart(2, '0')} $currency"
}

fun formatNumber(value: Double, decimals: Int = 2): String {
    val abs = abs(value)
    val sign = if (value < 0) "−" else ""
    val multiplier = Math.pow(10.0, decimals.toDouble())
    val rounded = Math.round(abs * multiplier) / multiplier
    val whole = rounded.toLong()
    val frac = Math.round((rounded - whole) * multiplier).toLong()
    val whStr = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    return "$sign$whStr,${frac.toString().padStart(decimals, '0')}"
}

@Composable
fun PercentBadge(percent: Double, abs: Double? = null, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val isUp = percent >= 0
    val color = if (isUp) c.accentUp else c.accentDown
    val sign = if (isUp) "+" else "−"
    val absPct = kotlin.math.abs(percent)
    val absText = abs?.let { ", " + sign + formatNumber(kotlin.math.abs(it)) + " ₽" }.orEmpty()
    Surface(
        color = color.copy(alpha = 0.15f),
        contentColor = color,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (isUp) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$sign${"%.2f".format(absPct)}%$absText",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null
) {
    val c = AppTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color ?: c.accentBrand,
            contentColor = Color.White,
            disabledContainerColor = c.borderDefault,
            disabledContentColor = c.textMuted
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = AppTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(10.dp),
        enabled = enabled,
        border = BorderStroke(1.dp, c.borderDefault),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null
) {
    val c = AppTheme.colors
    val borderColor by animateColorAsState(
        targetValue = when { isError -> c.accentDown; !enabled -> c.borderSubtle; else -> c.borderDefault },
        label = "border"
    )
    Surface(
        modifier = modifier.heightIn(min = 52.dp),
        color = c.elevated,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(c.accentBrand),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                keyboardActions = KeyboardActions(),
                visualTransformation = visualTransformation,
                enabled = enabled,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                decorationBox = { inner ->
                    if (value.isEmpty()) Text(placeholder, color = c.textMuted, style = MaterialTheme.typography.bodyLarge)
                    inner()
                }
            )
        }
    }
}

@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Surface(
        modifier = modifier,
        color = c.elevated,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, c.borderSubtle)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            options.forEachIndexed { idx, label ->
                val active = idx == selectedIndex
                val bg by animateColorAsState(if (active) c.surface else Color.Transparent, label = "seg-bg")
                val fg by animateColorAsState(if (active) c.textPrimary else c.textSecondary, label = "seg-fg")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(bg)
                        .clickable { onSelect(idx) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
                }
            }
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { idx, label ->
            val active = idx == selectedIndex
            Surface(
                modifier = Modifier.clickable { onSelect(idx) },
                color = if (active) c.accentBrand.copy(alpha = 0.15f) else c.elevated,
                contentColor = if (active) c.accentBrand else c.textSecondary,
                border = BorderStroke(1.dp, if (active) c.accentBrand.copy(alpha = 0.4f) else c.borderSubtle),
                shape = CircleShape
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String = "", modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}

