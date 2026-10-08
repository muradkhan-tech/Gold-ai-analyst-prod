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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.BacktestOutcomeEntity
import com.example.quant.BacktestSummary
import com.example.quant.TradeResult
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BacktestScreen(
    summary: BacktestSummary?,
    savedOutcomes: List<BacktestOutcomeEntity> = emptyList(),
    onRunBacktest: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strategies = listOf(
        "MultiTimeframeConsensusEngine",
        "EMA Trend Crossover",
        "Bollinger Mean Reversion",
        "RSI Momentum Breakout",
        "Gold ATR Volatility Pullback"
    )
    var selectedStrategy by remember { mutableStateOf(strategies[0]) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("backtest_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "QUANTITATIVE BACKTESTER",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Event-driven backtesting engine with zero look-ahead bias & database calibration",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Zero Look-Ahead Bias Guarantee Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, BullishGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Look-Ahead Safe",
                        tint = BullishGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "EVENT-DRIVEN: ZERO LOOK-AHEAD BIAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = BullishGreen
                        )
                        Text(
                            text = "Signals fire on bar [t] close; order execution fills at bar [t+1] open. Intrabar conservative stop resolution.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Strategy Selector Chips
        item {
            Column {
                Text(
                    text = "SELECT STRATEGY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(strategies) { strat ->
                        val isSelected = strat == selectedStrategy
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedStrategy = strat
                                onRunBacktest(strat)
                            },
                            label = { Text(strat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = GoldPrimary
                            ),
                            modifier = Modifier.testTag("strategy_chip_${strat.take(8)}")
                        )
                    }
                }
            }
        }

        // Run button
        item {
            Button(
                onClick = { onRunBacktest(selectedStrategy) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("run_backtest_button")
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run Backtest", tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Execute Event-Driven Backtest", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // Metrics Grid
        if (summary != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "WIN RATE",
                            value = "${summary.winRatePct}%",
                            subtitle = "${summary.winningTrades}W / ${summary.losingTrades}L",
                            accentColor = if (summary.winRatePct >= 50) BullishGreen else BearishRed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "PROFIT FACTOR",
                            value = "${summary.profitFactor}",
                            subtitle = "Gross W / L Ratio",
                            accentColor = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "MAX DRAWDOWN",
                            value = "${summary.maxDrawdownPct}%",
                            subtitle = "Peak-to-Trough",
                            accentColor = BearishRed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "SHARPE RATIO",
                            value = "${summary.sharpeRatio}",
                            subtitle = "Annualized Return/Risk",
                            accentColor = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Net profit banner
                    val netProfit = summary.netProfitDollars
                    val isProfit = netProfit >= 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("NET SIMULATED PROFIT", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${if (isProfit) "+$" else "-$"}${String.format("%.2f", kotlin.math.abs(netProfit))}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isProfit) BullishGreen else BearishRed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TOTAL TRADES", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${summary.totalTrades} Executions",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Recent Trade Executions
            item {
                Text(
                    text = "EVENT-DRIVEN EXECUTIONS (LAST 8 TRADES)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )
            }

            items(summary.recentTrades) { trade ->
                TradeLogCard(trade = trade)
            }
        }

        // Historical Database Calibration Runs
        if (savedOutcomes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAVED CALIBRATIONS (DATABASE)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${savedOutcomes.size} records",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GoldPrimary
                    )
                }
            }

            items(savedOutcomes.take(5)) { outcome ->
                SavedCalibrationCard(outcome = outcome)
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TradeLogCard(trade: TradeResult) {
    val isWin = trade.isWin
    val accent = if (isWin) BullishGreen else BearishRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("trade_log_item")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (trade.type == "BUY") BullishGreen else BearishRed)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = trade.type,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Entry: $${trade.entryPrice} → Exit: $${trade.exitPrice}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${trade.exitReason}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isWin) "+$" else "-$"}${String.format("%.2f", kotlin.math.abs(trade.pnlDollars))}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accent
                )
                Text(
                    text = "${if (trade.pnlPoints >= 0) "+" else ""}${trade.pnlPoints} pts",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SavedCalibrationCard(outcome: BacktestOutcomeEntity) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val isProfit = outcome.netProfitDollars >= 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "Calibration",
                        tint = GoldPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = outcome.strategyName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "WR: ${outcome.winRatePct}% · PF: ${outcome.profitFactor} · DD: ${outcome.maxDrawdownPct}% · ${sdf.format(Date(outcome.timestamp))}",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${if (isProfit) "+$" else "-$"}${String.format("%.2f", kotlin.math.abs(outcome.netProfitDollars))}",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = if (isProfit) BullishGreen else BearishRed
            )
        }
    }
}
