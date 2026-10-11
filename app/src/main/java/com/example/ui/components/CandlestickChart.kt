package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.NavyBrand
import com.example.ui.theme.NavyBrandLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    var crosshairOffset by remember { mutableStateOf<Offset?>(null) }
    var showEma20 by remember { mutableStateOf(true) }
    var showEma50 by remember { mutableStateOf(true) }
    var showEma200 by remember { mutableStateOf(false) }

    // Pinch-to-zoom and pan states
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var panOffsetIndex by remember { mutableIntStateOf(0) }

    val timeframes = listOf("1m", "3m", "5m", "10m", "15m", "1h")

    // Dynamic window calculation based on zoom & pan
    val baseCount = 45
    val currentWindowSize = (baseCount / zoomScale).toInt().coerceIn(15, min(candles.size.coerceAtLeast(15), 180))

    val visibleCandles = remember(candles, currentWindowSize, panOffsetIndex) {
        if (candles.isEmpty()) emptyList()
        else {
            val maxOffset = max(0, candles.size - currentWindowSize)
            val clampedOffset = panOffsetIndex.coerceIn(0, maxOffset)
            val startIndex = max(0, candles.size - currentWindowSize - clampedOffset)
            val endIndex = min(candles.size, startIndex + currentWindowSize)
            candles.subList(startIndex, endIndex)
        }
    }

    val selectedCandle = selectedIndex?.let { idx ->
        if (idx in visibleCandles.indices) visibleCandles[idx] else null
    } ?: visibleCandles.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("candlestick_chart_container")
    ) {
        // 1. Timeframe Selector & Overlay Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BullishGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "XAU/USD SPOT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }

            // Timeframe Pills
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
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyBrand,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("tf_chip_$tf")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Crosshair Tooltip Header (UTC Time, Open, High, Low, Close, Volume)
        if (selectedCandle != null) {
            val isBullish = selectedCandle.close >= selectedCandle.open
            val delta = selectedCandle.close - selectedCandle.open
            val deltaPct = if (selectedCandle.open > 0) (delta / selectedCandle.open) * 100.0 else 0.0
            val color = if (isBullish) BullishGreen else BearishRed

            val utcSdf = remember {
                SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            }
            val formattedTime = utcSdf.format(Date(selectedCandle.timestamp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "${if (delta >= 0) "+" else ""}${String.format("%.2f", delta)} (${String.format("%.2f", deltaPct)}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "O: ${String.format("%.2f", selectedCandle.open)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "H: ${String.format("%.2f", selectedCandle.high)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "L: ${String.format("%.2f", selectedCandle.low)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "C: ${String.format("%.2f", selectedCandle.close)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = color
                    )
                    Text(
                        text = "V: ${selectedCandle.volume.toInt()}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Hardware-Accelerated Interactive Canvas
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        val textMutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        val ema20Color = Color(0xFF2563EB) // Soft Royal Blue
        val ema50Color = Color(0xFFD97706) // Amber
        val ema200Color = Color(0xFF7C3AED) // Purple

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .pointerInput(visibleCandles) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            zoomScale = (zoomScale * zoom).coerceIn(0.6f, 3.5f)
                            if (pan.x != 0f) {
                                val candleWidth = size.width / max(visibleCandles.size, 1)
                                val deltaIndex = (pan.x / candleWidth).toInt()
                                panOffsetIndex = (panOffsetIndex - deltaIndex).coerceAtLeast(0)
                            }
                        }
                    }
                    .pointerInput(visibleCandles) {
                        detectTapGestures(
                            onTap = { offset ->
                                if (visibleCandles.isNotEmpty()) {
                                    val candleWidth = size.width / visibleCandles.size
                                    val idx = (offset.x / candleWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                    selectedIndex = idx
                                    crosshairOffset = offset
                                }
                            }
                        )
                    }
                    .pointerInput(visibleCandles) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                if (visibleCandles.isNotEmpty()) {
                                    val candleWidth = size.width / visibleCandles.size
                                    val idx = (change.position.x / candleWidth).toInt().coerceIn(0, visibleCandles.size - 1)
                                    selectedIndex = idx
                                    crosshairOffset = change.position
                                }
                            },
                            onDragEnd = {
                                // keep selected candle for inspection
                            }
                        )
                    }
                    .testTag("candlestick_canvas")
            ) {
                if (visibleCandles.isEmpty()) return@Canvas

                val minPrice = visibleCandles.minOf { it.low } - 1.0
                val maxPrice = visibleCandles.maxOf { it.high } + 1.0
                val priceRange = max(maxPrice - minPrice, 0.5)

                val maxVolume = visibleCandles.maxOf { it.volume }.coerceAtLeast(1.0)

                val w = size.width
                val h = size.height
                val count = visibleCandles.size
                val stepX = w / count
                val candleBodyWidth = max(stepX * 0.65f, 2.5f)

                // Chart main area takes top 78%, mini volume strip takes bottom 22%
                val mainChartHeight = h * 0.78f
                val volumeStripHeight = h * 0.20f
                val volumeTop = h - volumeStripHeight

                fun priceToY(price: Double): Float {
                    return (mainChartHeight - ((price - minPrice) / priceRange * mainChartHeight)).toFloat()
                }

                // A. Draw Horizontal Subtle Price Grids
                val gridSteps = 4
                for (g in 0..gridSteps) {
                    val gy = mainChartHeight * (g.toFloat() / gridSteps)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gy),
                        end = Offset(w, gy),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                // Volume separator
                drawLine(
                    color = gridColor,
                    start = Offset(0f, volumeTop),
                    end = Offset(w, volumeTop),
                    strokeWidth = 0.8.dp.toPx()
                )

                // B. Draw EMA Overlays
                val fullCloses = candles.map { it.close }
                if (showEma20 && fullCloses.isNotEmpty()) {
                    val ema20Values = QuantCalculations.calculateEma(fullCloses, 20)
                    drawEmaSeries(ema20Values, candles, visibleCandles, stepX, ::priceToY, ema20Color)
                }
                if (showEma50 && fullCloses.isNotEmpty()) {
                    val ema50Values = QuantCalculations.calculateEma(fullCloses, 50)
                    drawEmaSeries(ema50Values, candles, visibleCandles, stepX, ::priceToY, ema50Color)
                }
                if (showEma200 && fullCloses.isNotEmpty()) {
                    val ema200Values = QuantCalculations.calculateEma(fullCloses, 200)
                    drawEmaSeries(ema200Values, candles, visibleCandles, stepX, ::priceToY, ema200Color)
                }

                // C. Draw Candlesticks & Mini Volume Bars
                for (i in visibleCandles.indices) {
                    val c = visibleCandles[i]
                    val cx = (i * stepX) + (stepX / 2f)
                    val isBullish = c.close >= c.open
                    val barColor = if (isBullish) BullishGreen else BearishRed

                    // Mini volume bar
                    val vHeight = (c.volume / maxVolume * (volumeStripHeight - 4f)).toFloat()
                    val vTop = h - vHeight
                    drawRect(
                        color = barColor.copy(alpha = 0.35f),
                        topLeft = Offset(cx - (candleBodyWidth / 2f), vTop),
                        size = Size(candleBodyWidth, vHeight)
                    )

                    // Price Wick
                    val highY = priceToY(c.high)
                    val lowY = priceToY(c.low)
                    val openY = priceToY(c.open)
                    val closeY = priceToY(c.close)

                    drawLine(
                        color = barColor,
                        start = Offset(cx, highY),
                        end = Offset(cx, lowY),
                        strokeWidth = 1.2.dp.toPx()
                    )

                    // Price Body
                    val bodyTop = min(openY, closeY)
                    val bodyBottom = max(openY, closeY)
                    val bodyHeight = max(bodyBottom - bodyTop, 1.5f)

                    drawRect(
                        color = barColor,
                        topLeft = Offset(cx - (candleBodyWidth / 2f), bodyTop),
                        size = Size(candleBodyWidth, bodyHeight)
                    )

                    // Crosshair on touch
                    if (selectedIndex == i) {
                        drawLine(
                            color = NavyBrandLight.copy(alpha = 0.5f),
                            start = Offset(cx, 0f),
                            end = Offset(cx, h),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = NavyBrandLight.copy(alpha = 0.5f),
                            start = Offset(0f, closeY),
                            end = Offset(w, closeY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
        }

        // 4. Interactive EMA Toggle Chips & Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // EMA 20 Toggle Chip
                OverlayTogglePill(
                    label = "EMA 20",
                    color = ema20Color,
                    isActive = showEma20,
                    onClick = { showEma20 = !showEma20 }
                )

                // EMA 50 Toggle Chip
                OverlayTogglePill(
                    label = "EMA 50",
                    color = ema50Color,
                    isActive = showEma50,
                    onClick = { showEma50 = !showEma50 }
                )

                // EMA 200 Toggle Chip
                OverlayTogglePill(
                    label = "EMA 200",
                    color = ema200Color,
                    isActive = showEma200,
                    onClick = { showEma200 = !showEma200 }
                )
            }

            Text(
                text = "Pinch to zoom • Drag to pan",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEmaSeries(
    emaValues: List<Double>,
    fullCandles: List<CandleEntity>,
    visibleCandles: List<CandleEntity>,
    stepX: Float,
    priceToY: (Double) -> Float,
    color: Color
) {
    if (emaValues.isEmpty() || visibleCandles.isEmpty()) return
    val fullMap = fullCandles.mapIndexed { idx, c -> c.timestamp to emaValues.getOrNull(idx) }.toMap()

    val path = Path()
    var started = false

    for (i in visibleCandles.indices) {
        val ts = visibleCandles[i].timestamp
        val v = fullMap[ts] ?: continue
        val ex = (i * stepX) + (stepX / 2f)
        val ey = priceToY(v)
        if (!started) {
            path.moveTo(ex, ey)
            started = true
        } else {
            path.lineTo(ex, ey)
        }
    }

    if (started) {
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
private fun OverlayTogglePill(
    label: String,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, if (isActive) color else MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures { onClick() }
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isActive) color else Color.Gray.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
