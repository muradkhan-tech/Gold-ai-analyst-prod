package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quant.TechnicalSnapshot
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary

@Composable
fun TechnicalIndicatorsHud(
    snapshot: TechnicalSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("technical_indicators_hud")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUANT INDICATORS HUD",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Text(
                text = "INSTITUTIONAL M15",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = GoldPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid of 4 key indicator tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 1: RSI 14
            val rsi = snapshot.rsi14
            val rsiColor = when {
                rsi < 35 -> BullishGreen // oversold
                rsi > 68 -> BearishRed   // overbought
                else -> GoldPrimary
            }
            val rsiLabel = when {
                rsi < 35 -> "OVERSOLD"
                rsi > 68 -> "OVERBOUGHT"
                else -> "NEUTRAL"
            }

            IndicatorCard(
                title = "RSI (14)",
                value = String.format("%.1f", rsi),
                subtitle = rsiLabel,
                accentColor = rsiColor,
                progress = (rsi / 100.0).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f)
            )

            // Tile 2: MACD
            val macdPositive = snapshot.macdHistogram >= 0
            val macdColor = if (macdPositive) BullishGreen else BearishRed
            IndicatorCard(
                title = "MACD (12,26,9)",
                value = String.format("%.2f", snapshot.macdLine),
                subtitle = if (macdPositive) "HIST +${String.format("%.2f", snapshot.macdHistogram)}" else "HIST ${String.format("%.2f", snapshot.macdHistogram)}",
                accentColor = macdColor,
                progress = if (macdPositive) 0.75f else 0.25f,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tile 3: Bollinger Bands
            IndicatorCard(
                title = "BOLLINGER BANDS",
                value = "$${String.format("%.1f", snapshot.bbUpper)}",
                subtitle = "LOW: $${String.format("%.1f", snapshot.bbLower)}",
                accentColor = GoldPrimary,
                progress = 0.5f,
                modifier = Modifier.weight(1f)
            )

            // Tile 4: ATR (14) Volatility
            IndicatorCard(
                title = "ATR VOLATILITY",
                value = "$${String.format("%.2f", snapshot.atr14)}",
                subtitle = "EXP. MOVE / BAR",
                accentColor = BullishGreen,
                progress = 0.65f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun IndicatorCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}
