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
    val divergenceDetails: String? = null,
    val dominantParty: String = if (buyingAggressionPct >= sellingAggressionPct) "Net Buyers" else "Net Sellers"
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
        val lookback20 = candles.takeLast(min(20, candles.size))
        val avgVolume20 = if (lookback20.isNotEmpty()) lookback20.map { it.volume }.average() else 100.0
        val atr14List = QuantCalculations.calculateAtr(candles, 14)
        val atr14 = atr14List.lastOrNull() ?: 8.0

        val lastCandle = candles.last()
        val currentSpread = lastCandle.high - lastCandle.low
        val volumeRatio = if (avgVolume20 > 0.0) lastCandle.volume / avgVolume20 else 1.0
        val spreadToAtrRatio = if (atr14 > 0.0) currentSpread / atr14 else 1.0

        // Institutional Flow: VSA Institutional Absorption
        // Formula: If tick volume > 2.0x 20-period volume SMA AND candle spread (High - Low) < 0.5x 20-period ATR(14), flag as 'INSTITUTIONAL_ABSORPTION' (Status: ACTIVE). Otherwise, 'INACTIVE'.
        val isAbsorption = (volumeRatio > 2.0 && currentSpread < (0.5 * atr14) && currentSpread > 0.0)

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
                absorptionDetails = "INSTITUTIONAL_ABSORPTION (Status: ACTIVE): Institutional limit sell orders trapped retail breakout buyers at highs. Vol: ${round1(volumeRatio)}x avg (> 2.0x SMA), Spread: ${round2(currentSpread)} (< 0.5x ATR ${round2(atr14)})."
            } else if (relativePosition <= 0.40) {
                absorptionType = AbsorptionType.BULLISH_ABSORPTION
                absorptionDetails = "INSTITUTIONAL_ABSORPTION (Status: ACTIVE): Institutional limit buy orders absorbed panic sellers at lows. Vol: ${round1(volumeRatio)}x avg (> 2.0x SMA), Spread: ${round2(currentSpread)} (< 0.5x ATR ${round2(atr14)})."
            } else {
                absorptionType = if (lastCandle.close >= lastCandle.open) AbsorptionType.BEARISH_ABSORPTION else AbsorptionType.BULLISH_ABSORPTION
                absorptionDetails = "INSTITUTIONAL_ABSORPTION (Status: ACTIVE): High volume absorption (${round1(volumeRatio)}x > 2.0x SMA) in compression spread (${round2(currentSpread)} < 0.5x ATR)."
            }
        } else {
            absorptionType = AbsorptionType.NONE
            absorptionDetails = "INSTITUTIONAL_ABSORPTION (Status: INACTIVE): Normal volume/spread dynamics (Vol: ${round1(volumeRatio)}x, Spread: ${round2(currentSpread)})."
        }

        // ====================================================================
        // SILENT EDGE 2: TIME-OF-DAY SESSION LIQUIDITY SWEEP (ASIAN RANGE & LBMA)
        // ====================================================================
        val asianRange = computeAsianSessionRange(candles, referenceTimestamp)
        var sweepStatus = LiquiditySweepStatus.WITHIN_RANGE
        var sweepTag: String? = null
        var sweepDetails = "WITHIN RANGE (Asian H: $${round2(asianRange.high)} | L: $${round2(asianRange.low)})"

        if (asianRange.valid) {
            val currentHigh = lastCandle.high
            val currentLow = lastCandle.low
            val currentClose = lastCandle.close

            // London Open Sweep Window: 07:00 to 09:30 UTC
            val isLondonOpenSweepWindow = (currentHour in 7..8) || (currentHour == 9 && currentMinute <= 30)

            // Pierce threshold: 5 to 30 pips ($0.50 to $3.00 on Gold)
            val pierceAbove = currentHigh - asianRange.high
            val pierceBelow = asianRange.low - currentLow

            if (isLondonOpenSweepWindow) {
                if (pierceAbove in 0.50..3.00 && currentClose <= asianRange.high) {
                    // Pierced Asian High by 5 to 30 pips and candle closes back inside range
                    sweepStatus = LiquiditySweepStatus.HIGH_SWEPT_REVERSED
                    sweepTag = "ASIAN_RANGE_LIQUIDITY_PURGE (Bull Trap)"
                    sweepDetails = "ASIAN_RANGE_LIQUIDITY_PURGE (Bull Trap): Asian High ($${round2(asianRange.high)}) pierced by +$${round2(pierceAbove)} (+${round1(pierceAbove * 10)} pips) and closed back inside range."
                } else if (pierceBelow in 0.50..3.00 && currentClose >= asianRange.low) {
                    // Swept below Asian Low by 5 to 30 pips and closes back inside range
                    sweepStatus = LiquiditySweepStatus.LOW_SWEPT_REVERSED
                    sweepTag = "ASIAN_RANGE_LIQUIDITY_PURGE (Bear Trap)"
                    sweepDetails = "ASIAN_RANGE_LIQUIDITY_PURGE (Bear Trap): Asian Low ($${round2(asianRange.low)}) swept by -$${round2(pierceBelow)} (-${round1(pierceBelow * 10)} pips) and closed back inside range."
                } else {
                    sweepStatus = LiquiditySweepStatus.WITHIN_RANGE
                    sweepDetails = "WITHIN RANGE (Asian H: $${round2(asianRange.high)} | L: $${round2(asianRange.low)})"
                }
            } else {
                sweepStatus = LiquiditySweepStatus.WITHIN_RANGE
                sweepDetails = "WITHIN RANGE (Asian H: $${round2(asianRange.high)} | L: $${round2(asianRange.low)})"
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
     * Extracts Asian Session (00:00 - 07:00 UTC) High & Low range:
     * - Restricts Asian session filtering strictly to today's UTC date between 00:00:00 UTC and 07:00:00 UTC.
     * - If current UTC time is between 00:00 and 07:00 UTC: High = max(High), Low = min(Low) from 00:00 UTC up to current candle.
     * - If current UTC time is after 07:00 UTC: High and Low represent locked 00:00–07:00 UTC range of current UTC day.
     * - If current day candles before 07:00 UTC are missing: Fall back to prior trading day's 00:00–07:00 UTC range. Never unconstrained historical data.
     * - Sanity check: Resulting Asian High and Low realistically sit within ±1.5% of current live XAUUSD price.
     */
    private fun computeAsianSessionRange(candles: List<CandleEntity>, referenceTimestamp: Long): AsianSessionRange {
        if (candles.isEmpty()) {
            return AsianSessionRange(2864.0, 2854.0, 10.0, false)
        }

        val currentPrice = candles.last().close
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = referenceTimestamp
        }
        val currentYear = utcCal.get(Calendar.YEAR)
        val currentDayOfYear = utcCal.get(Calendar.DAY_OF_YEAR)
        val currentHour = utcCal.get(Calendar.HOUR_OF_DAY)

        val candleCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        fun isAsianCandle(c: CandleEntity, targetYear: Int, targetDayOfYear: Int, maxTimestamp: Long = Long.MAX_VALUE): Boolean {
            if (c.timestamp > maxTimestamp) return false
            candleCal.timeInMillis = c.timestamp
            val y = candleCal.get(Calendar.YEAR)
            val d = candleCal.get(Calendar.DAY_OF_YEAR)
            val h = candleCal.get(Calendar.HOUR_OF_DAY)
            return y == targetYear && d == targetDayOfYear && h in 0..6
        }

        var asianCandles: List<CandleEntity> = emptyList()

        if (currentHour < 7) {
            // Case 1: If current UTC time is between 00:00 and 07:00 UTC:
            // Calculate High = max(High) and Low = min(Low) from 00:00 UTC up to current candle.
            val todayAsian = candles.filter { isAsianCandle(it, currentYear, currentDayOfYear, referenceTimestamp) }
            if (todayAsian.isNotEmpty()) {
                asianCandles = todayAsian
            }
        } else {
            // Case 2: If current UTC time is after 07:00 UTC:
            // High and Low represent the locked 00:00–07:00 UTC range of the current UTC day.
            val todayLockedAsian = candles.filter { isAsianCandle(it, currentYear, currentDayOfYear) }
            if (todayLockedAsian.isNotEmpty()) {
                asianCandles = todayLockedAsian
            }
        }

        // Case 3: If current day candles before 07:00 UTC are missing (e.g. market open / weekend rollover):
        // Fall back to prior trading day's 00:00–07:00 UTC range. Never query unconstrained historical data.
        if (asianCandles.isEmpty()) {
            val priorCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = referenceTimestamp
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val priorYear = priorCal.get(Calendar.YEAR)
            val priorDayOfYear = priorCal.get(Calendar.DAY_OF_YEAR)
            val priorDayAsian = candles.filter { isAsianCandle(it, priorYear, priorDayOfYear) }
            if (priorDayAsian.isNotEmpty()) {
                asianCandles = priorDayAsian
            }
        }

        var rawHigh: Double
        var rawLow: Double
        var isValid: Boolean

        if (asianCandles.isNotEmpty()) {
            rawHigh = roundD2(asianCandles.maxOf { it.high })
            rawLow = roundD2(asianCandles.minOf { it.low })
            isValid = true
        } else {
            // Clean baseline calibrated tightly to current spot price (~0.3% spread)
            rawHigh = roundD2(currentPrice + (currentPrice * 0.003))
            rawLow = roundD2(currentPrice - (currentPrice * 0.003))
            isValid = false
        }

        // Strict Sanity Check: Ensure Asian High and Low realistically sit within ±1.5% of current live XAUUSD price
        val maxAllowedHigh = currentPrice * 1.015
        val minAllowedLow = currentPrice * 0.985

        val high = roundD2(rawHigh.coerceIn(currentPrice, maxAllowedHigh))
        val low = roundD2(rawLow.coerceIn(minAllowedLow, currentPrice))
        val rangeSpread = roundD2(max(0.1, high - low))

        return AsianSessionRange(
            high = high,
            low = low,
            rangeSpread = rangeSpread,
            valid = isValid
        )
    }

    /**
     * Calculates Volume Delta Pressure (Tape Proxy) & Exhaustion Divergence:
     * - Buy Aggression % = ((Close - Low) / max(High - Low, 0.01)) * 100
     * - Sell Aggression % = ((High - Close) / max(High - Low, 0.01)) * 100
     * - Output 'Net Buyers' or 'Net Sellers' based on the dominant percentage.
     */
    private fun computeDeltaPressureMetrics(candles: List<CandleEntity>): DeltaPressureMetrics {
        if (candles.isEmpty()) {
            return DeltaPressureMetrics(
                buyingPressure = 50.0,
                sellingPressure = 50.0,
                buyingAggressionPct = 50.0,
                sellingAggressionPct = 50.0,
                isExhaustionDivergence = false,
                divergenceType = null,
                divergenceDetails = null,
                dominantParty = "Net Buyers"
            )
        }

        val lastCandle = candles.last()
        val currentRange = max(0.01, lastCandle.high - lastCandle.low)
        val currentBuyAggression = ((lastCandle.close - lastCandle.low) / currentRange) * 100.0
        val currentSellAggression = ((lastCandle.high - lastCandle.close) / currentRange) * 100.0

        val window = candles.takeLast(min(20, candles.size))
        var totalBuying = 0.0
        var totalSelling = 0.0
        var totalVol = 0.0

        val candleDeltas = mutableListOf<Pair<Double, Double>>() // (Price, BuyingPressureRatio)

        for (c in window) {
            val range = max(0.01, c.high - c.low)
            val buyPressure = ((c.close - c.low) / range) * c.volume
            val sellPressure = ((c.high - c.close) / range) * c.volume

            totalBuying += buyPressure
            totalSelling += sellPressure
            totalVol += c.volume

            val buyRatio = buyPressure / max(1.0, c.volume)
            candleDeltas.add(Pair(c.high, buyRatio))
        }

        // Weighted aggression blending the latest tick candle with the 20-period volume profile
        val windowBuyAggression = if (totalVol > 0.0) (totalBuying / totalVol) * 100.0 else currentBuyAggression
        val windowSellAggression = if (totalVol > 0.0) (totalSelling / totalVol) * 100.0 else currentSellAggression

        val buyingAggressionPct = roundD1((currentBuyAggression * 0.6) + (windowBuyAggression * 0.4))
        val sellingAggressionPct = roundD1(100.0 - buyingAggressionPct)
        val dominantParty = if (buyingAggressionPct >= sellingAggressionPct) "Net Buyers" else "Net Sellers"

        // Exhaustion Divergence Detection
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

            val priorRange = max(0.01, priorSwingCandle.high - priorSwingCandle.low)
            val secondRange = max(0.01, currentSwingCandle.high - currentSwingCandle.low)

            val priorBuyRatio = (priorSwingCandle.close - priorSwingCandle.low) / priorRange
            val currentBuyRatio = (currentSwingCandle.close - currentSwingCandle.low) / secondRange

            if (currentHigh > priorHigh && priorBuyRatio > 0.35) {
                val drop = (priorBuyRatio - currentBuyRatio) / priorBuyRatio
                if (drop >= 0.30) {
                    isDivergence = true
                    divergenceType = "BEARISH_EXHAUSTION"
                    divergenceDetails = "EXHAUSTION_DIVERGENCE: Price formed higher high ($${round2(currentHigh)} > $${round2(priorHigh)}), but Buying Pressure collapsed by ${round(drop * 100)}% (Ratio ${round1(priorBuyRatio * 100)}% -> ${round1(currentBuyRatio * 100)}%)."
                }
            }

            if (!isDivergence) {
                val priorLow = firstHalf.minOf { it.low }
                val currentLow = secondHalf.minOf { it.low }

                val priorLowCandle = firstHalf.minByOrNull { it.low } ?: firstHalf.first()
                val currentLowCandle = secondHalf.minByOrNull { it.low } ?: secondHalf.last()

                val priorLowRange = max(0.01, priorLowCandle.high - priorLowCandle.low)
                val secondLowRange = max(0.01, currentLowCandle.high - currentLowCandle.low)

                val priorSellRatio = (priorLowCandle.high - priorLowCandle.close) / priorLowRange
                val currentSellRatio = (currentLowCandle.high - currentLowCandle.close) / secondLowRange

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
            divergenceDetails = divergenceDetails,
            dominantParty = dominantParty
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

    private fun roundD1(v: Double): Double = round(v * 10.0) / 10.0
    private fun roundD2(v: Double): Double = round(v * 100.0) / 100.0
    private fun round1(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun round2(v: Double): String = String.format(Locale.US, "%.2f", v)
}
