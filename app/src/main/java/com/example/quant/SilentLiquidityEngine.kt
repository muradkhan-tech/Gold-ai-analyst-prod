package com.example.quant

import com.example.data.local.CandleEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class SessionPhase(val displayName: String, val badgeColorHex: Long) {
    ASIAN_ACCUMULATION("Asian Accumulation (00:00 - 07:00 UTC)", 0xFF3B82F6),
    LONDON_MANIPULATION("London Open / Manipulation (07:00 - 10:30 UTC)", 0xFFEAB308),
    LBMA_AM_FIX("LBMA AM Fix Benchmark (10:22 - 10:38 UTC)", 0xFFEF4444),
    NY_DISTRIBUTION("NY Overlap & Distribution (12:00 - 17:00 UTC)", 0xFF10B981),
    LBMA_PM_FIX("LBMA PM Fix Benchmark (14:52 - 15:08 UTC)", 0xFFEF4444),
    LATE_NY_REBALANCING("Late NY Rebalancing (17:00 - 21:00 UTC)", 0xFF8B5CF6),
    PACIFIC_TRANSITION("Pacific Transition / Pre-Asia (21:00 - 00:00 UTC)", 0xFF64748B)
}

enum class AbsorptionType {
    NONE,
    BULLISH_ABSORPTION, // Buying absorption at swing lows (absorbing retail panic selling)
    BEARISH_ABSORPTION  // Selling absorption at swing highs (absorbing retail breakout buyers)
}

enum class LiquiditySweepStatus {
    WITHIN_RANGE,
    HIGH_SWEPT_REVERSED,  // Swept Asian High by 10-30 pips ($1.00-$3.00) & closed back inside
    LOW_SWEPT_REVERSED,   // Swept Asian Low by 10-30 pips ($1.00-$3.00) & closed back inside
    PURGE_IN_PROGRESS     // Price currently piercing outside Asian range
}

data class DeltaPressureMetrics(
    val buyingPressure: Double,
    val sellingPressure: Double,
    val buyingAggressionPct: Double, // 0 to 100%
    val sellingAggressionPct: Double, // 0 to 100%
    val isExhaustionDivergence: Boolean,
    val divergenceType: String? = null, // "BEARISH_EXHAUSTION" or "BULLISH_EXHAUSTION"
    val divergenceDetails: String? = null
)

data class AsianSessionRange(
    val high: Double,
    val low: Double,
    val rangeSpread: Double,
    val valid: Boolean
)

data class SilentLiquidityReport(
    val sessionPhase: SessionPhase,
    val utcTimeFormatted: String,
    val isLbmaFixActive: Boolean,
    val lbmaFixName: String? = null,

    // Edge 1: Volume Absorption & Spread Exhaustion
    val isAbsorptionActive: Boolean,
    val absorptionType: AbsorptionType,
    val absorptionDetails: String,
    val volumeRatio: Double,
    val spreadToAtrRatio: Double,

    // Edge 2: Session Liquidity Sweep
    val asianRange: AsianSessionRange,
    val sweepStatus: LiquiditySweepStatus,
    val sweepTag: String? = null, // e.g. "ASIAN_RANGE_LIQUIDITY_PURGE"
    val sweepDetails: String,

    // Edge 3: Delta Pressure
    val deltaMetrics: DeltaPressureMetrics,

    // Master Score Adjustments
    val scoreModifier: Double,
    val widenStopsBuffer: Boolean,
    val confluenceNotes: List<String>
)

object SilentLiquidityEngine {

    /**
     * Analyzes candle series through institutional lenses:
     * 1. Volume Spread Analysis (VSA) Absorption Trap Detection
     * 2. Time-of-Day Session Liquidity Sweeps (Asian Range & LBMA Fixes)
     * 3. Intra-Candle Delta Pressure & Exhaustion Divergence
     */
    fun analyzeLiquidity(candles: List<CandleEntity>, referenceTimestamp: Long = System.currentTimeMillis()): SilentLiquidityReport {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = referenceTimestamp
        }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        val timeFormatter = SimpleDateFormat("HH:mm:ss 'UTC'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val utcTimeStr = timeFormatter.format(Date(referenceTimestamp))

        // Determine Session Phase & LBMA Fix Window
        val isLbmaAm = (currentHour == 10 && currentMinute in 22..38)
        val isLbmaPm = (currentHour == 14 && currentMinute >= 52) || (currentHour == 15 && currentMinute <= 8)
        val isLbmaFix = isLbmaAm || isLbmaPm
        val lbmaFixName = when {
            isLbmaAm -> "LBMA AM Gold Fix (10:30 UTC Benchmark)"
            isLbmaPm -> "LBMA PM Gold Fix (15:00 UTC Benchmark)"
            else -> null
        }

        val sessionPhase = when {
            isLbmaAm -> SessionPhase.LBMA_AM_FIX
            isLbmaPm -> SessionPhase.LBMA_PM_FIX
            currentHour in 0..6 -> SessionPhase.ASIAN_ACCUMULATION
            currentHour in 7..9 || (currentHour == 10 && currentMinute < 22) -> SessionPhase.LONDON_MANIPULATION
            currentHour in 12..16 -> SessionPhase.NY_DISTRIBUTION
            currentHour in 17..20 -> SessionPhase.LATE_NY_REBALANCING
            else -> SessionPhase.PACIFIC_TRANSITION
        }

        if (candles.isEmpty()) {
            return fallbackReport(sessionPhase, utcTimeStr, isLbmaFix, lbmaFixName)
        }

        // ====================================================================
        // SILENT EDGE 1: INSTITUTIONAL VOLUME ABSORPTION & SPREAD EXHAUSTION
        // ====================================================================
        val lookback20 = candles.takeLast(20)
        val avgVolume20 = if (lookback20.isNotEmpty()) lookback20.map { it.volume }.average() else 100.0
        val atr20 = QuantCalculations.calculateAtr(candles, 20).lastOrNull() ?: 8.0

        val lastCandle = candles.last()
        val currentSpread = lastCandle.high - lastCandle.low
        val volumeRatio = if (avgVolume20 > 0.0) lastCandle.volume / avgVolume20 else 1.0
        val spreadToAtrRatio = if (atr20 > 0.0) currentSpread / atr20 else 1.0

        // Absorption Trap Condition: Volume >= 2.0x avg volume AND Spread <= 0.5x ATR
        val isAbsorption = (volumeRatio >= 2.0 && spreadToAtrRatio <= 0.5 && currentSpread > 0.0)

        // Classify absorption direction based on candle position within the recent 20-bar range
        val recentHigh = lookback20.maxOfOrNull { it.high } ?: lastCandle.high
        val recentLow = lookback20.minOfOrNull { it.low } ?: lastCandle.low
        val recentRange = max(0.1, recentHigh - recentLow)
        val relativePosition = (lastCandle.close - recentLow) / recentRange

        val absorptionType: AbsorptionType
        val absorptionDetails: String
        if (isAbsorption) {
            if (relativePosition >= 0.60) {
                absorptionType = AbsorptionType.BEARISH_ABSORPTION
                absorptionDetails = "BEARISH ABSORPTION: Institutional limit sell orders trapped retail breakout buyers. Volume ${round1(volumeRatio)}x avg into ultra-tight spread (${round1(spreadToAtrRatio)}x ATR) at swing highs."
            } else if (relativePosition <= 0.40) {
                absorptionType = AbsorptionType.BULLISH_ABSORPTION
                absorptionDetails = "BULLISH ABSORPTION: Institutional limit buy orders absorbed panic sellers. Volume ${round1(volumeRatio)}x avg into ultra-tight spread (${round1(spreadToAtrRatio)}x ATR) at swing lows."
            } else {
                absorptionType = if (lastCandle.close >= lastCandle.open) AbsorptionType.BEARISH_ABSORPTION else AbsorptionType.BULLISH_ABSORPTION
                absorptionDetails = "INSTITUTIONAL ABSORPTION: High volume (${round1(volumeRatio)}x) absorbed in tight compression (${round1(spreadToAtrRatio)}x ATR)."
            }
        } else {
            absorptionType = AbsorptionType.NONE
            absorptionDetails = "Normal volume/spread relationship (Vol: ${round1(volumeRatio)}x, Spread: ${round1(spreadToAtrRatio)}x ATR)."
        }

        // ====================================================================
        // SILENT EDGE 2: TIME-OF-DAY SESSION LIQUIDITY SWEEP (ASIAN RANGE & LBMA)
        // ====================================================================
        val asianRange = computeAsianSessionRange(candles, referenceTimestamp)
        var sweepStatus = LiquiditySweepStatus.WITHIN_RANGE
        var sweepTag: String? = null
        var sweepDetails = "Price trading safely within Asian session baseline boundaries."

        if (asianRange.valid) {
            val currentPrice = lastCandle.close
            val currentHigh = lastCandle.high
            val currentLow = lastCandle.low

            // London Open Sweep Window: 07:00 to 09:30 UTC
            val isLondonOpenSweepWindow = (currentHour in 7..8) || (currentHour == 9 && currentMinute <= 30)

            // Pierce threshold: 10 to 30 pips in gold ($1.00 to $3.00)
            val pierceAbove = currentHigh - asianRange.high
            val pierceBelow = asianRange.low - currentLow

            val candleBody = abs(lastCandle.close - lastCandle.open)
            val candleRange = max(0.01, currentHigh - currentLow)
            val upperWick = currentHigh - max(lastCandle.open, lastCandle.close)
            val lowerWick = min(lastCandle.open, lastCandle.close) - currentLow

            if (pierceAbove in 0.8..4.0 && lastCandle.close <= asianRange.high) {
                // Pierced above Asian High, rejected back inside range (pin bar or bearish reversal)
                val isBearishRejection = (upperWick / candleRange >= 0.45) || (lastCandle.close < lastCandle.open)
                if (isBearishRejection || isLondonOpenSweepWindow) {
                    sweepStatus = LiquiditySweepStatus.HIGH_SWEPT_REVERSED
                    sweepTag = "ASIAN_RANGE_LIQUIDITY_PURGE"
                    sweepDetails = "ASIAN_RANGE_LIQUIDITY_PURGE: Asian High ($${round2(asianRange.high)}) swept by +${round1(pierceAbove * 10)} pips ($${round2(pierceAbove)}). Failed to hold; violently rejected back inside range."
                }
            } else if (pierceBelow in 0.8..4.0 && lastCandle.close >= asianRange.low) {
                // Pierced below Asian Low, rejected back inside range (pin bar or bullish reversal)
                val isBullishRejection = (lowerWick / candleRange >= 0.45) || (lastCandle.close > lastCandle.open)
                if (isBullishRejection || isLondonOpenSweepWindow) {
                    sweepStatus = LiquiditySweepStatus.LOW_SWEPT_REVERSED
                    sweepTag = "ASIAN_RANGE_LIQUIDITY_PURGE"
                    sweepDetails = "ASIAN_RANGE_LIQUIDITY_PURGE: Asian Low ($${round2(asianRange.low)}) swept by -${round1(pierceBelow * 10)} pips ($${round2(pierceBelow)}). Failed to hold; violently rejected back inside range."
                }
            } else if (currentHigh > asianRange.high || currentLow < asianRange.low) {
                sweepStatus = LiquiditySweepStatus.PURGE_IN_PROGRESS
                sweepDetails = if (currentHigh > asianRange.high) {
                    "LIQUIDITY HUNT IN PROGRESS: Testing above Asian High ($${round2(asianRange.high)}), delta: +$${round2(currentHigh - asianRange.high)}."
                } else {
                    "LIQUIDITY HUNT IN PROGRESS: Testing below Asian Low ($${round2(asianRange.low)}), delta: -$${round2(asianRange.low - currentLow)}."
                }
            }
        }

        // ====================================================================
        // SILENT EDGE 3: TICK DELTA PRESSURE ESTIMATOR (TAPE READING PROXY)
        // ====================================================================
        val deltaMetrics = computeDeltaPressureMetrics(candles)

        // ====================================================================
        // CONFLUENCE SCORING MULTIPLIERS & REASONS
        // ====================================================================
        var scoreModifier = 0.0
        val notes = mutableListOf<String>()

        when (absorptionType) {
            AbsorptionType.BEARISH_ABSORPTION -> {
                scoreModifier -= 28.0
                notes.add("Institutional Absorption: Bearish trap detected at swing highs (-28 pts)")
            }
            AbsorptionType.BULLISH_ABSORPTION -> {
                scoreModifier += 28.0
                notes.add("Institutional Absorption: Bullish accumulation detected at swing lows (+28 pts)")
            }
            AbsorptionType.NONE -> {}
        }

        when (sweepStatus) {
            LiquiditySweepStatus.HIGH_SWEPT_REVERSED -> {
                scoreModifier -= 32.0
                notes.add("Session Sweep: Asian High liquidity purged & rejected (-32 pts)")
            }
            LiquiditySweepStatus.LOW_SWEPT_REVERSED -> {
                scoreModifier += 32.0
                notes.add("Session Sweep: Asian Low liquidity purged & rejected (+32 pts)")
            }
            LiquiditySweepStatus.PURGE_IN_PROGRESS -> {
                notes.add("Session Sweep: Active liquidity expansion in progress")
            }
            LiquiditySweepStatus.WITHIN_RANGE -> {}
        }

        if (deltaMetrics.isExhaustionDivergence) {
            if (deltaMetrics.divergenceType == "BEARISH_EXHAUSTION") {
                scoreModifier -= 22.0
                notes.add("Tape Pressure: Bearish exhaustion divergence on new high (-22 pts)")
            } else if (deltaMetrics.divergenceType == "BULLISH_EXHAUSTION") {
                scoreModifier += 22.0
                notes.add("Tape Pressure: Bullish exhaustion divergence on new low (+22 pts)")
            }
        }

        if (isLbmaFix) {
            notes.add("LBMA Fix Active: Widened buffers active for central bank benchmark rebalancing")
        }

        return SilentLiquidityReport(
            sessionPhase = sessionPhase,
            utcTimeFormatted = utcTimeStr,
            isLbmaFixActive = isLbmaFix,
            lbmaFixName = lbmaFixName,
            isAbsorptionActive = isAbsorption,
            absorptionType = absorptionType,
            absorptionDetails = absorptionDetails,
            volumeRatio = volumeRatio,
            spreadToAtrRatio = spreadToAtrRatio,
            asianRange = asianRange,
            sweepStatus = sweepStatus,
            sweepTag = sweepTag,
            sweepDetails = sweepDetails,
            deltaMetrics = deltaMetrics,
            scoreModifier = scoreModifier.coerceIn(-50.0, 50.0),
            widenStopsBuffer = isLbmaFix,
            confluenceNotes = notes
        )
    }

    /**
     * Extracts Asian Session (00:00 - 07:00 UTC) High & Low range.
     * Looks at candles from today's Asian session or the most recent Asian session in history.
     */
    private fun computeAsianSessionRange(candles: List<CandleEntity>, referenceTimestamp: Long): AsianSessionRange {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        // Filter candles timestamped in Asian hours (hour < 7)
        val asianCandles = candles.filter { c ->
            cal.timeInMillis = c.timestamp
            cal.get(Calendar.HOUR_OF_DAY) < 7
        }

        if (asianCandles.size >= 3) {
            // Take the most recent Asian cluster (last 20 asian candles)
            val cluster = asianCandles.takeLast(min(asianCandles.size, 30))
            val high = cluster.maxOf { it.high }
            val low = cluster.minOf { it.low }
            return AsianSessionRange(
                high = high,
                low = low,
                rangeSpread = high - low,
                valid = true
            )
        }

        // Fallback: estimate from first third of available candles
        if (candles.size >= 15) {
            val sample = candles.take(min(15, candles.size / 2))
            val high = sample.maxOf { it.high }
            val low = sample.minOf { it.low }
            return AsianSessionRange(
                high = high,
                low = low,
                rangeSpread = high - low,
                valid = true
            )
        }

        val lastPrice = candles.lastOrNull()?.close ?: 2864.0
        return AsianSessionRange(
            high = lastPrice + 8.5,
            low = lastPrice - 8.5,
            rangeSpread = 17.0,
            valid = false
        )
    }

    /**
     * Calculates Intra-Candle Delta Pressure metrics and detects exhaustion divergences:
     * Buying Pressure = (Close - Low) / (High - Low) * Volume
     * Selling Pressure = (High - Close) / (High - Low) * Volume
     */
    private fun computeDeltaPressureMetrics(candles: List<CandleEntity>): DeltaPressureMetrics {
        val window = candles.takeLast(min(20, candles.size))
        var totalBuying = 0.0
        var totalSelling = 0.0
        var totalVol = 0.0

        val candleDeltas = mutableListOf<Pair<Double, Double>>() // (Price, BuyingPressureRatio)

        for (c in window) {
            val range = max(0.001, c.high - c.low)
            val buyPressure = ((c.close - c.low) / range) * c.volume
            val sellPressure = ((c.high - c.close) / range) * c.volume

            totalBuying += buyPressure
            totalSelling += sellPressure
            totalVol += c.volume

            val buyRatio = buyPressure / max(1.0, c.volume)
            candleDeltas.add(Pair(c.high, buyRatio))
        }

        val buyingAggressionPct = if (totalVol > 0.0) (totalBuying / totalVol) * 100.0 else 50.0
        val sellingAggressionPct = if (totalVol > 0.0) (totalSelling / totalVol) * 100.0 else 50.0

        // Exhaustion Divergence Detection
        // If price makes a new higher high but Buying Pressure Ratio decreases by > 30% vs prior swing high
        var isDivergence = false
        var divergenceType: String? = null
        var divergenceDetails: String? = null

        if (window.size >= 8) {
            val firstHalf = window.subList(0, window.size / 2)
            val secondHalf = window.subList(window.size / 2, window.size)

            val priorHigh = firstHalf.maxOf { it.high }
            val currentHigh = secondHalf.maxOf { it.high }

            val priorSwingCandle = firstHalf.maxByOrNull { it.high } ?: firstHalf.first()
            val currentSwingCandle = secondHalf.maxByOrNull { it.high } ?: secondHalf.last()

            val priorRange = max(0.001, priorSwingCandle.high - priorSwingCandle.low)
            val currentRange = max(0.001, currentSwingCandle.high - currentSwingCandle.low)

            val priorBuyRatio = (priorSwingCandle.close - priorSwingCandle.low) / priorRange
            val currentBuyRatio = (currentSwingCandle.close - currentSwingCandle.low) / currentRange

            // Price made higher high but buying pressure collapsed by > 30%
            if (currentHigh > priorHigh && priorBuyRatio > 0.35) {
                val drop = (priorBuyRatio - currentBuyRatio) / priorBuyRatio
                if (drop >= 0.30) {
                    isDivergence = true
                    divergenceType = "BEARISH_EXHAUSTION"
                    divergenceDetails = "EXHAUSTION_DIVERGENCE: Price formed higher high ($${round2(currentHigh)} > $${round2(priorHigh)}), but Buying Pressure collapsed by ${round(drop * 100)}% (Ratio ${round1(priorBuyRatio * 100)}% -> ${round1(currentBuyRatio * 100)}%)."
                }
            }

            // Also check Bullish Exhaustion Divergence on lower low
            if (!isDivergence) {
                val priorLow = firstHalf.minOf { it.low }
                val currentLow = secondHalf.minOf { it.low }

                val priorLowCandle = firstHalf.minByOrNull { it.low } ?: firstHalf.first()
                val currentLowCandle = secondHalf.minByOrNull { it.low } ?: secondHalf.last()

                val priorLowRange = max(0.001, priorLowCandle.high - priorLowCandle.low)
                val currentLowRange = max(0.001, currentLowCandle.high - currentLowCandle.low)

                val priorSellRatio = (priorLowCandle.high - priorLowCandle.close) / priorLowRange
                val currentSellRatio = (currentLowCandle.high - currentLowCandle.close) / currentLowRange

                if (currentLow < priorLow && priorSellRatio > 0.35) {
                    val drop = (priorSellRatio - currentSellRatio) / priorSellRatio
                    if (drop >= 0.30) {
                        isDivergence = true
                        divergenceType = "BULLISH_EXHAUSTION"
                        divergenceDetails = "EXHAUSTION_DIVERGENCE: Price formed lower low ($${round2(currentLow)} < $${round2(priorLow)}), but Selling Pressure collapsed by ${round(drop * 100)}% (Ratio ${round1(priorSellRatio * 100)}% -> ${round1(currentSellRatio * 100)}%)."
                    }
                }
            }
        }

        return DeltaPressureMetrics(
            buyingPressure = totalBuying,
            sellingPressure = totalSelling,
            buyingAggressionPct = buyingAggressionPct,
            sellingAggressionPct = sellingAggressionPct,
            isExhaustionDivergence = isDivergence,
            divergenceType = divergenceType,
            divergenceDetails = divergenceDetails
        )
    }

    private fun fallbackReport(
        phase: SessionPhase,
        timeStr: String,
        isLbma: Boolean,
        lbmaName: String?
    ): SilentLiquidityReport {
        return SilentLiquidityReport(
            sessionPhase = phase,
            utcTimeFormatted = timeStr,
            isLbmaFixActive = isLbma,
            lbmaFixName = lbmaName,
            isAbsorptionActive = false,
            absorptionType = AbsorptionType.NONE,
            absorptionDetails = "Awaiting candle telemetry for VSA absorption computation.",
            volumeRatio = 1.0,
            spreadToAtrRatio = 1.0,
            asianRange = AsianSessionRange(2870.0, 2855.0, 15.0, false),
            sweepStatus = LiquiditySweepStatus.WITHIN_RANGE,
            sweepTag = null,
            sweepDetails = "Asian session range calibrating.",
            deltaMetrics = DeltaPressureMetrics(50.0, 50.0, 50.0, 50.0, false),
            scoreModifier = 0.0,
            widenStopsBuffer = isLbma,
            confluenceNotes = emptyList()
        )
    }

    private fun round1(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun round2(v: Double): String = String.format(Locale.US, "%.2f", v)
}
