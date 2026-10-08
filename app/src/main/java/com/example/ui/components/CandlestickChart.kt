package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CandleEntity
import com.example.quant.QuantCalculations
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun CandlestickChart(
    candles: List<CandleEntity>,
    selectedTimeframe: String,
    onSelectTimeframe: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val timeframes = listOf("1m", "3m", "5m", "10m", "15m", "1h")

    val visibleCandles = remember(candles) {
        candles.takeLast(40)
    }

    val selectedCandle = selectedIndex?.let { idx ->
        if (idx in visibleCandles.indices) visibleCandles[idx] else null
    } ?: visibleCandles.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("candlestick_chart_container")
    ) {
        // Timeframe selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "XAUUSD SPOT CHART",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                timeframes.forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectTimeframe(tf) },
                        label = {
                            Text(
                                text = tf.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = GoldPrimary
                        ),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("tf_chip_$tf")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selected / Current Candle HUD Header
        if (selectedCandle != null) {
            val isBullish = selectedCandle.close >= selectedCandle.open
            val delta = selectedCandle.close - selectedCandle.open
            val deltaPct = (delta / selectedCandle.open) * 100.0
            val color = if (isBullish) BullishGreen else BearishRed
            val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "O: ${String.format("%.2f", selectedCandle.open)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "H: ${String.format("%.2f", selectedCandle.high)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "L: ${String.format("%.2f", selectedCandle.low)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "C: ${String.format("%.2f", selectedCandle.close)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                }

                Text(
                    text = "${if (delta >= 0) "+" else ""}${String.format("%.2f", delta)} (${String.format("%.2f", deltaPct)}%)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = color
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Candlestick Drawing Canvas
        val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        val emaLineColor = GoldPrimary.copy(alpha = 0.85f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .pointerInput(visibleCandles) {
                        detectTapGestures { offset ->
                            if (visibleCandles.isNotEmpty()) {
                                val candleWidth = size.width / visibleCandles.size
                                val idx = (offset.x / candleWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                selectedIndex = idx
                            }
                        }
                    }
                    .pointerInput(visibleCandles) {
                        detectDragGestures { change, _ ->
                            if (visibleCandles.isNotEmpty()) {
                                val candleWidth = size.width / visibleCandles.size
                                val idx = (change.position.x / candleWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                selectedIndex = idx
                            }
                        }
                    }
                    .testTag("candlestick_canvas")
            ) {
                if (visibleCandles.isEmpty()) return@Canvas

                val minPrice = visibleCandles.minOf { it.low } - 1.5
                val maxPrice = visibleCandles.maxOf { it.high } + 1.5
                val priceRange = max(maxPrice - minPrice, 1.0)

                val w = size.width
                val h = size.height
                val count = visibleCandles.size
                val stepX = w / count
                val candleBodyWidth = max(stepX * 0.65f, 3f)

                fun priceToY(price: Double): Float {
                    return (h - ((price - minPrice) / priceRange * h)).toFloat()
                }

                // Draw Horizontal Price Grid lines
                val gridSteps = 4
                for (g in 0..gridSteps) {
                    val gy = h * (g.toFloat() / gridSteps)
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 1f
                    )
                }

                // Draw EMA 20 line overlay
                val closes = visibleCandles.map { it.close }
                val ema20 = QuantCalculations.calculateEma(closes, 12)
                if (ema20.size == visibleCandles.size) {
                    val emaPath = Path()
                    for (i in ema20.indices) {
                        val ex = (i * stepX) + (stepX / 2f)
                        val ey = priceToY(ema20[i])
                        if (i == 0) emaPath.moveTo(ex, ey) else emaPath.lineTo(ex, ey)
                    }
                    drawPath(
                        path = emaPath,
                        color = emaLineColor,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Draw Candlesticks (Wick & Body)
                for (i in visibleCandles.indices) {
                    val c = visibleCandles[i]
                    val cx = (i * stepX) + (stepX / 2f)
                    val isBullish = c.close >= c.open
                    val barColor = if (isBullish) BullishGreen else BearishRed

                    val highY = priceToY(c.high)
                    val lowY = priceToY(c.low)
                    val openY = priceToY(c.open)
                    val closeY = priceToY(c.close)

                    // Draw High/Low Wick
                    drawLine(
                        color = barColor,
                        start = Offset(cx, highY),
                        end = Offset(cx, lowY),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Draw Body Box
                    val bodyTop = min(openY, closeY)
                    val bodyBottom = max(openY, closeY)
                    val bodyHeight = max(bodyBottom - bodyTop, 2f)

                    drawRect(
                        color = barColor,
                        topLeft = Offset(cx - (candleBodyWidth / 2f), bodyTop),
                        size = Size(candleBodyWidth, bodyHeight)
                    )

                    // Touch Scrubber Crosshair Indicator
                    if (selectedIndex == i) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.6f),
                            start = Offset(0f, closeY),
                            end = Offset(w, closeY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }

        // Legend row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(12.dp).height(2.dp).background(GoldPrimary))
                Spacer(modifier = Modifier.width(4.dp))
                Text("EMA 20", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.width(8.dp).height(8.dp).background(BullishGreen))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bullish", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.width(12.dp))

                Box(modifier = Modifier.width(8.dp).height(8.dp).background(BearishRed))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bearish", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = "Tap or drag chart to inspect OHLCV",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
