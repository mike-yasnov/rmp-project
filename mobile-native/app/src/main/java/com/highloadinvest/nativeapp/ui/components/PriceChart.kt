package com.highloadinvest.nativeapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.highloadinvest.nativeapp.domain.Candle
import com.highloadinvest.nativeapp.domain.ChartType
import com.highloadinvest.nativeapp.ui.theme.AppTheme
import kotlin.math.max
import kotlin.math.min

@Composable
fun PriceChart(
    candles: List<Candle>,
    type: ChartType,
    modifier: Modifier = Modifier
) {
    val c = AppTheme.colors
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }

    LaunchedEffect(candles.size) { zoom = 1f; panX = 0f }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(candles.size) {
                detectTransformGestures(panZoomLock = true) { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(0.5f, 4f)
                    panX += pan.x
                }
            }
    ) {
        if (candles.isEmpty()) return@Canvas
        val minP = candles.minOf { it.low }
        val maxP = candles.maxOf { it.high }
        val rng = (maxP - minP).takeIf { it > 0 } ?: 1.0
        val visibleCount = (candles.size / zoom).toInt().coerceAtLeast(2)
        val firstIdx = (((candles.size - visibleCount) / 2) - (panX / size.width * candles.size).toInt())
            .coerceIn(0, max(0, candles.size - visibleCount))
        val visible = candles.subList(firstIdx, min(candles.size, firstIdx + visibleCount))

        val padX = 8f
        val padY = 12f
        val width = size.width - 2 * padX
        val height = size.height - 2 * padY

        // Grid (4 horizontal lines)
        for (i in 0..4) {
            val y = padY + height * i / 4
            drawLine(
                color = c.borderSubtle,
                start = Offset(padX, y),
                end = Offset(padX + width, y),
                strokeWidth = 1f
            )
        }

        if (visible.isEmpty()) return@Canvas
        val n = visible.size
        val slot = width / n

        when (type) {
            ChartType.Line -> {
                val path = Path()
                visible.forEachIndexed { i, candle ->
                    val x = padX + slot * (i + 0.5f)
                    val y = padY + height * (1 - ((candle.close - minP) / rng).toFloat())
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = c.accentBrand, style = Stroke(width = 2.5f))
            }
            ChartType.Candle -> {
                val bodyW = (slot * 0.6f).coerceAtLeast(2f)
                visible.forEach { candle ->
                    val i = visible.indexOf(candle)
                    val cx = padX + slot * (i + 0.5f)
                    val isUp = candle.close >= candle.open
                    val color = if (isUp) c.accentUp else c.accentDown
                    val yHigh = padY + height * (1 - ((candle.high - minP) / rng).toFloat())
                    val yLow = padY + height * (1 - ((candle.low - minP) / rng).toFloat())
                    val yOpen = padY + height * (1 - ((candle.open - minP) / rng).toFloat())
                    val yClose = padY + height * (1 - ((candle.close - minP) / rng).toFloat())
                    drawLine(color = color, start = Offset(cx, yHigh), end = Offset(cx, yLow), strokeWidth = 1.5f)
                    val top = min(yOpen, yClose)
                    val bottom = max(yOpen, yClose)
                    drawRect(
                        color = color,
                        topLeft = Offset(cx - bodyW / 2, top),
                        size = androidx.compose.ui.geometry.Size(bodyW, max(1f, bottom - top))
                    )
                }
            }
        }
    }
}
