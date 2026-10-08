package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SignalEntity
import com.example.data.local.TradingViewAlertEntity
import com.example.quant.AlignmentReport
import com.example.quant.TimeframeTrendStatus
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldPrimary

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.data.alert.AlertCondition
import com.example.data.alert.PriceAlertEntity

@Composable
fun QuantSignalsScreen(
    signals: List<SignalEntity>,
    alignmentReport: AlignmentReport? = null,
    silentLiquidityReport: com.example.quant.SilentLiquidityReport? = null,
    tradingViewAlerts: List<TradingViewAlertEntity> = emptyList(),
    priceAlerts: List<PriceAlertEntity> = emptyList(),
    currentBid: Double = 2864.38,
    currentAsk: Double = 2864.63,
    onRefreshSignals: () -> Unit,
    onSimulateTradingViewAlert: (() -> Unit)? = null,
    onCreatePriceAlert: ((Double, AlertCondition, String) -> Unit)? = null,
    onDeletePriceAlert: ((Long) -> Unit)? = null,
    onTogglePriceAlert: ((Long, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeSignal = signals.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("quant_signals_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Screen Title & Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "QUANT SIGNALS & ALIGNMENT",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Multi-timeframe XAUUSD confluence & TradingView alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onRefreshSignals,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("refresh_signals_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan Signals",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan & Align", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // 1.5 Silent Liquidity & Institutional Flow Status Section
        if (silentLiquidityReport != null) {
            item {
                InstitutionalFlowStatusWidget(report = silentLiquidityReport)
            }
        }

        // 2. Multi-Timeframe Trend Alignment Section
        if (alignmentReport != null) {
            item {
                MultiTimeframeAlignmentCard(report = alignmentReport)
            }
        }

        // 3. TradingView Custom Alerts Section
        item {
            TradingViewWebhooksCard(
                alerts = tradingViewAlerts,
                onSimulateAlert = onSimulateTradingViewAlert
            )
        }

        // 3.5 Custom Price Alert Thresholds Section (Bid / Ask trigger & browser notifications)
        item {
            PriceAlertThresholdsCard(
                alerts = priceAlerts,
                currentBid = currentBid,
                currentAsk = currentAsk,
                onCreateAlert = onCreatePriceAlert,
                onDeleteAlert = onDeletePriceAlert,
                onToggleAlert = onTogglePriceAlert
            )
        }

        // 4. Active Algorithmic Signal Card
        if (activeSignal != null) {
            item {
                Text(
                    text = "ACTIVE QUANT SIGNAL SETUP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                ActiveSignalCard(signal = activeSignal)
            }
        }

        // 5. Historical Signal Log
        item {
            Text(
                text = "HISTORICAL ALGORITHMIC SIGNALS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
        }

        if (signals.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trading signals generated yet. Click 'Scan & Align' to evaluate.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(signals) { sig ->
                SignalLogItem(signal = sig)
            }
        }
    }
}

@Composable
fun MultiTimeframeAlignmentCard(report: AlignmentReport) {
    val overallColor = when (report.overallStatus) {
        "STRONG_BULLISH", "MODERATE_BULLISH" -> BullishGreen
        "STRONG_BEARISH", "MODERATE_BEARISH" -> BearishRed
        else -> GoldPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("multi_timeframe_alignment_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Trend Alignment",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TIMEFRAME CONFLUENCE MATRIX",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(overallColor.copy(alpha = 0.15f))
                        .border(1.dp, overallColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = report.overallStatus.replace("_", " "),
                        color = overallColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scalp 1m vs Macro 1h synchronization badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "1m vs 1h",
                        tint = overallColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = report.scalpVsMacroNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Grid of 6 Timeframes: 1m, 3m, 5m, 10m, 15m, 1h
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                report.perTimeframe.forEach { tfStatus ->
                    TimeframeRowItem(status = tfStatus)
                }
            }
        }
    }
}

@Composable
fun TimeframeRowItem(status: TimeframeTrendStatus) {
    val trendColor = when (status.trend) {
        "BULLISH" -> BullishGreen
        "BEARISH" -> BearishRed
        else -> GoldPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(GoldPrimary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status.timeframe,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = GoldPrimary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(trendColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = status.trend,
                        color = trendColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = status.summary,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Text(
                text = "Score ${if (status.score > 0) "+${status.score}" else "${status.score}"}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = trendColor
            )
        }
    }
}

@Composable
fun TradingViewWebhooksCard(
    alerts: List<TradingViewAlertEntity>,
    onSimulateAlert: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("tradingview_webhooks_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Secured Webhook",
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRADINGVIEW WEBHOOK INTAKE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(
                        text = "Endpoint: /api/webhooks/tradingview",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BullishGreen.copy(alpha = 0.15f))
                        .border(1.dp, BullishGreen.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SECRET PROTECTED",
                        color = BullishGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Accepts verified JSON alerts for custom proprietary indicators ('Scalping with Dr Hafiz V2', '3ESRA'). Values are never fabricated.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No webhook alerts received yet.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.take(4).forEach { alert ->
                        TradingViewAlertItem(alert = alert)
                    }
                }
            }

            if (onSimulateAlert != null) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSimulateAlert,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulate_tradingview_alert_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAlert,
                        contentDescription = "Simulate Webhook Alert",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Test Webhook Alert (Dr Hafiz V2 / 3ESRA)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun TradingViewAlertItem(alert: TradingViewAlertEntity) {
    val isBuy = alert.action == "BUY"
    val actionColor = if (isBuy) BullishGreen else if (alert.action == "SELL") BearishRed else GoldPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .border(1.dp, actionColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(actionColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = alert.action,
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = alert.indicator,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = alert.timeframe,
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "$${String.format("%.2f", alert.price)}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!alert.message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.message,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${alert.symbol} · Alert Verified",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified Token",
                        tint = BullishGreen,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "VERIFIED SECRET",
                        color = BullishGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveSignalCard(signal: SignalEntity) {
    val isFailsafe = signal.signalType == "NO SIGNAL" || signal.reasoning.contains("FAILSAFE", ignoreCase = true)
    val badgeColor = when (signal.signalType.uppercase()) {
        "STRONG BUY" -> BullishGreen
        "BUY" -> BullishGreen.copy(alpha = 0.85f)
        "WEAK BUY" -> Color(0xFF26A69A)
        "STRONG SELL" -> BearishRed
        "SELL" -> BearishRed.copy(alpha = 0.85f)
        "WEAK SELL" -> Color(0xFFFF7043)
        "NO SIGNAL" -> Color(0xFFFF5252)
        else -> GoldPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.5.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(16.dp)
            .testTag("active_signal_card")
    ) {
        Column {
            // Failsafe Warning Banner
            if (isFailsafe) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Failsafe Alert",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FAILSAFE ACTIVE: Trading locked due to missing or stale market feed/news.",
                            color = Color(0xFFFF5252),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Signal Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = signal.signalType,
                            color = if (signal.signalType in listOf("STRONG BUY", "BUY", "WAIT")) Color.Black else Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = signal.strategyName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${signal.confidenceScore.toInt()}% CONFIDENCE",
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Entry Zone Highlight Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXACT ENTRY ZONE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = GoldPrimary
                    )
                    Text(
                        text = "$${String.format("%.2f", signal.entryZoneLow)} - $${String.format("%.2f", signal.entryZoneHigh)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Price Target Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriceLevelBox(
                    label = "ENTRY",
                    price = "$${String.format("%.2f", signal.entryPrice)}",
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                PriceLevelBox(
                    label = "STOP LOSS",
                    price = "$${String.format("%.2f", signal.stopLoss)}",
                    color = BearishRed,
                    modifier = Modifier.weight(1f)
                )
                PriceLevelBox(
                    label = "TP 1",
                    price = "$${String.format("%.2f", signal.takeProfit1)}",
                    color = BullishGreen,
                    modifier = Modifier.weight(1f)
                )
                PriceLevelBox(
                    label = "TP 2",
                    price = "$${String.format("%.2f", signal.takeProfit2)}",
                    color = BullishGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Risk:Reward & Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Risk : Reward = 1 : ${signal.riskRewardRatio}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
                Text(
                    text = "Status: ${signal.status}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { (signal.confidenceScore / 100.0).toFloat().coerceIn(0f, 1f) },
                color = badgeColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Reasoning breakdown
            Text(
                text = "CONFLUENCE REASONING",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = signal.reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PriceLevelBox(
    label: String,
    price: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = price,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}

@Composable
fun SignalLogItem(signal: SignalEntity) {
    val isBuy = signal.signalType == "BUY"
    val color = if (isBuy) BullishGreen else if (signal.signalType == "SELL") BearishRed else GoldPrimary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(color.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = signal.signalType,
                        color = color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = signal.strategyName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Entry: $${String.format("%.2f", signal.entryPrice)} | SL: $${String.format("%.2f", signal.stopLoss)} | TP1: $${String.format("%.2f", signal.takeProfit1)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "${signal.confidenceScore.toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
    }
}

@Composable
fun PriceAlertThresholdsCard(
    alerts: List<PriceAlertEntity>,
    currentBid: Double,
    currentAsk: Double,
    onCreateAlert: ((Double, AlertCondition, String) -> Unit)?,
    onDeleteAlert: ((Long) -> Unit)?,
    onToggleAlert: ((Long, Boolean) -> Unit)?
) {
    var priceInput by remember { mutableStateOf(String.format("%.2f", currentAsk + 2.50)) }
    var selectedCondition by remember { mutableStateOf(AlertCondition.ASK_ABOVE) }
    var noteInput by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("price_alert_thresholds_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddAlert,
                        contentDescription = "Price Alert Thresholds",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "CUSTOM PRICE ALERTS (XAUUSD)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Browser & sound notifications on Bid/Ask crossing",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { showForm = !showForm },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showForm) MaterialTheme.colorScheme.surfaceVariant else GoldPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("toggle_create_alert_form_button")
                ) {
                    Text(
                        text = if (showForm) "Close" else "+ Add Alert",
                        color = if (showForm) MaterialTheme.colorScheme.onSurface else Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-time Bid / Ask reference strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE BID: ", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format("%.2f", currentBid)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = BullishGreen)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LIVE ASK: ", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${String.format("%.2f", currentAsk)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = BearishRed)
                }
                Text("Spread: $${String.format("%.2f", kotlin.math.abs(currentAsk - currentBid))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Create alert form
            if (showForm && onCreateAlert != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SET NEW THRESHOLD TARGET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                        letterSpacing = 0.5.sp
                    )

                    // Condition selector chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            AlertCondition.ASK_ABOVE to "Ask ≥",
                            AlertCondition.ASK_BELOW to "Ask ≤",
                            AlertCondition.BID_ABOVE to "Bid ≥",
                            AlertCondition.BID_BELOW to "Bid ≤"
                        ).forEach { (cond, label) ->
                            val isSelected = selectedCondition == cond
                            val chipColor = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.surface
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(chipColor)
                                    .border(
                                        1.dp,
                                        if (isSelected) GoldPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 6.dp)
                                    .testTag("condition_chip_${cond.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { selectedCondition = cond },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Target price field
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Target Threshold Price ($)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_target_price_input")
                    )

                    // Note field
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Alert Note (Optional, e.g. Breakout retest)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alert_note_input")
                    )

                    Button(
                        onClick = {
                            val target = priceInput.toDoubleOrNull()
                            if (target != null && target > 0) {
                                onCreateAlert(target, selectedCondition, noteInput)
                                noteInput = ""
                                showForm = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_create_alert_button")
                    ) {
                        Text("Create & Arm Price Alert", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alerts list
            if (alerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom price alerts configured. Click '+ Add Alert' to set price trigger levels.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    alerts.forEach { alert ->
                        PriceAlertRowItem(
                            alert = alert,
                            currentBid = currentBid,
                            currentAsk = currentAsk,
                            onDelete = { onDeleteAlert?.invoke(alert.id) },
                            onToggle = { onToggleAlert?.invoke(alert.id, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceAlertRowItem(
    alert: PriceAlertEntity,
    currentBid: Double,
    currentAsk: Double,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val condLabel = when (alert.condition) {
        AlertCondition.ASK_ABOVE -> "Ask ≥ $${String.format("%.2f", alert.targetPrice)}"
        AlertCondition.ASK_BELOW -> "Ask ≤ $${String.format("%.2f", alert.targetPrice)}"
        AlertCondition.BID_ABOVE -> "Bid ≥ $${String.format("%.2f", alert.targetPrice)}"
        AlertCondition.BID_BELOW -> "Bid ≤ $${String.format("%.2f", alert.targetPrice)}"
    }

    val statusColor = when {
        alert.isTriggered -> BullishGreen
        !alert.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> GoldPrimary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(10.dp)
            .testTag("price_alert_item_${alert.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (alert.isTriggered) "TRIGGERED" else if (alert.isEnabled) "ARMED" else "PAUSED",
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = condLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (alert.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = alert.note,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alert.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldPrimary,
                        checkedTrackColor = GoldPrimary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("toggle_switch_${alert.id}")
                )

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BearishRed.copy(alpha = 0.15f),
                        contentColor = BearishRed
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("delete_alert_${alert.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Alert",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
