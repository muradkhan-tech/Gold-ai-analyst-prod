package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
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
import com.example.quant.AbsorptionType
import com.example.quant.LiquiditySweepStatus
import com.example.quant.SessionPhase
import com.example.quant.SilentLiquidityReport
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.GoldPrimary
import java.util.Locale

@Composable
fun InstitutionalFlowStatusWidget(
    report: SilentLiquidityReport?,
    modifier: Modifier = Modifier
) {
    if (report == null) return

    var isDetailsExpanded by remember { mutableStateOf(false) }

    val phaseColor = Color(report.sessionPhase.badgeColorHex)
    val isAbsorption = report.isAbsorptionActive
    val absorptionColor = when (report.absorptionType) {
        AbsorptionType.BULLISH_ABSORPTION -> BullishGreen
        AbsorptionType.BEARISH_ABSORPTION -> BearishRed
        AbsorptionType.NONE -> MaterialTheme.colorScheme.outline
    }

    val sweepStatusColor = when (report.sweepStatus) {
        LiquiditySweepStatus.HIGH_SWEPT_REVERSED -> BearishRed
        LiquiditySweepStatus.LOW_SWEPT_REVERSED -> BullishGreen
        LiquiditySweepStatus.PURGE_IN_PROGRESS -> GoldPrimary
        LiquiditySweepStatus.WITHIN_RANGE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = if (isAbsorption || report.isLbmaFixActive) GoldPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp)
            .testTag("institutional_flow_widget")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            // Header: Title & UTC Session Clock
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Institutional Flow Status",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INSTITUTIONAL FLOW STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // UTC Live Session Clock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "UTC Clock",
                        tint = GoldPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = report.utcTimeFormatted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 1. Current Session Phase Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(phaseColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(1.dp, phaseColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
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
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(phaseColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = report.sessionPhase.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = phaseColor
                        )
                    }

                    if (report.isLbmaFixActive) {
                        Box(
                            modifier = Modifier
                                .background(BearishRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "FIX REGIME ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 2. Core Grid: Institutional Absorption & Liquidity Sweep Level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Widget Item A: Institutional Absorption Alert
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (isAbsorption) absorptionColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                        .testTag("absorption_alert_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "VSA ABSORPTION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isAbsorption) absorptionColor else MaterialTheme.colorScheme.outline)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isAbsorption) {
                                    if (report.absorptionType == AbsorptionType.BULLISH_ABSORPTION) "ACTIVE (BUY)" else "ACTIVE (SELL)"
                                } else "INACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAbsorption) absorptionColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Vol: ${String.format(Locale.US, "%.1f", report.volumeRatio)}x • Sprd: ${String.format(Locale.US, "%.2f", report.spreadToAtrRatio)}x",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Widget Item B: Liquidity Sweep Level
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (report.sweepStatus != LiquiditySweepStatus.WITHIN_RANGE) sweepStatusColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                        .testTag("liquidity_sweep_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "SESSION SWEEP LEVEL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when (report.sweepStatus) {
                                LiquiditySweepStatus.HIGH_SWEPT_REVERSED -> "HIGH PURGE (REJECTED)"
                                LiquiditySweepStatus.LOW_SWEPT_REVERSED -> "LOW PURGE (REJECTED)"
                                LiquiditySweepStatus.PURGE_IN_PROGRESS -> "HUNT IN PROGRESS"
                                LiquiditySweepStatus.WITHIN_RANGE -> "WITHIN RANGE"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = sweepStatusColor
                        )
                        Text(
                            text = "Asian H: $${String.format(Locale.US, "%.1f", report.asianRange.high)} | L: $${String.format(Locale.US, "%.1f", report.asianRange.low)}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Volume Delta Pressure Meter (Tape Reading Proxy)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .testTag("volume_delta_pressure_meter")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOLUME DELTA PRESSURE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (report.deltaMetrics.buyingAggressionPct >= 50.0) {
                            "Net Buyers: +${String.format(Locale.US, "%.0f", report.deltaMetrics.buyingAggressionPct)}%"
                        } else {
                            "Net Sellers: +${String.format(Locale.US, "%.0f", report.deltaMetrics.sellingAggressionPct)}%"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (report.deltaMetrics.buyingAggressionPct >= 50.0) BullishGreen else BearishRed
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Dual-color progress gauge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(maxOf(0.01f, report.deltaMetrics.buyingAggressionPct.toFloat()))
                            .background(BullishGreen)
                    )
                    Box(
                        modifier = Modifier
                            .weight(maxOf(0.01f, report.deltaMetrics.sellingAggressionPct.toFloat()))
                            .background(BearishRed)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Buy Aggression: ${String.format(Locale.US, "%.1f", report.deltaMetrics.buyingAggressionPct)}%",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BullishGreen
                    )
                    Text(
                        text = "Sell Aggression: ${String.format(Locale.US, "%.1f", report.deltaMetrics.sellingAggressionPct)}%",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BearishRed
                    )
                }

                if (report.deltaMetrics.isExhaustionDivergence) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Divergence", tint = GoldPrimary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "EXHAUSTION DIVERGENCE: Pressure collapsed >30% on swing peak.",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                    }
                }
            }

            // Accordion toggle for in-depth institutional breakdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDetailsExpanded = !isDetailsExpanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isDetailsExpanded) "Hide Institutional Telemetry Details" else "View Institutional Telemetry Details",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldPrimary
                )
                Icon(
                    imageVector = if (isDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Details",
                    tint = GoldPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = isDetailsExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Institutional Edge Diagnostics:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• VSA Absorption: ${report.absorptionDetails}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Session Sweep: ${report.sweepDetails}",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (report.deltaMetrics.divergenceDetails != null) {
                        Text(
                            text = "• Delta Tape: ${report.deltaMetrics.divergenceDetails}",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (report.lbmaFixName != null) {
                        Text(
                            text = "• Fix Window: ${report.lbmaFixName}. Stop Loss and TP buffers expanded by +35% to prevent benchmark stop hunting.",
                            fontSize = 9.sp,
                            color = BearishRed
                        )
                    }
                }
            }
        }
    }
}
