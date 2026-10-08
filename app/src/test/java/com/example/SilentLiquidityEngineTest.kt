package com.example

import com.example.data.local.CandleEntity
import com.example.quant.AbsorptionType
import com.example.quant.LiquiditySweepStatus
import com.example.quant.QuantSignalEngine
import com.example.quant.SessionPhase
import com.example.quant.SilentLiquidityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SilentLiquidityEngineTest {

    private fun createCandle(
        timestamp: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Double = 100.0,
        timeframe: String = "15m"
    ): CandleEntity {
        return CandleEntity(
            symbol = "XAUUSD",
            timeframe = timeframe,
            timestamp = timestamp,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume
        )
    }

    @Test
    fun testInstitutionalVolumeAbsorptionDetection() {
        val now = 1700000000000L
        val candles = mutableListOf<CandleEntity>()

        // 20 normal candles with average volume 100 and ATR ~ 10.0 (spread ~ 10)
        for (i in 0 until 20) {
            candles.add(
                createCandle(
                    timestamp = now + (i * 60_000L),
                    open = 2850.0 + i,
                    high = 2855.0 + i,
                    low = 2845.0 + i,
                    close = 2852.0 + i,
                    volume = 100.0
                )
            )
        }

        // Add 21st candle: massive tick volume (300.0 >= 2.0x 100) trapped in an ultra-narrow spread (high - low = 1.0 <= 0.5x ATR)
        candles.add(
            createCandle(
                timestamp = now + (20 * 60_000L),
                open = 2870.0,
                high = 2870.6,
                low = 2869.6,
                close = 2870.5,
                volume = 320.0 // 3.2x average
            )
        )

        val report = SilentLiquidityEngine.analyzeLiquidity(candles, now + (20 * 60_000L))

        assertTrue("Institutional absorption should be flagged", report.isAbsorptionActive)
        assertEquals("Should be flagged as Bearish Absorption at highs", AbsorptionType.BEARISH_ABSORPTION, report.absorptionType)
        assertTrue("Volume ratio should be >= 2.0x", report.volumeRatio >= 2.0)
        assertTrue("Spread to ATR ratio should be <= 0.5x", report.spreadToAtrRatio <= 0.5)
        assertTrue("Confluence score should be negative for bearish trap", report.scoreModifier < 0.0)
    }

    @Test
    fun testAsianRangeLiquidityPurgeDetection() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        val asianBase = cal.timeInMillis

        val candles = mutableListOf<CandleEntity>()

        // Asian session candles (00:00 - 07:00 UTC) with Asian High = 2860.0, Asian Low = 2840.0
        for (i in 0 until 10) {
            candles.add(
                createCandle(
                    timestamp = asianBase + (i * 300_000L),
                    open = 2845.0,
                    high = if (i == 4) 2860.0 else 2855.0,
                    low = if (i == 8) 2840.0 else 2842.0,
                    close = 2850.0,
                    volume = 100.0
                )
            )
        }

        // London Open timestamp (08:15 UTC)
        val londonCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = asianBase
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 15)
        }
        val londonTimestamp = londonCal.timeInMillis

        // London Open candle: pierces Asian High (2860.0) to 2862.0 (+20 pips), but rejects and closes back inside at 2858.0
        candles.add(
            createCandle(
                timestamp = londonTimestamp,
                open = 2857.0,
                high = 2862.0, // Pierced 20 pips above Asian High
                low = 2856.0,
                close = 2858.0, // Closed back inside Asian Range (< 2860.0)
                volume = 250.0
            )
        )

        val report = SilentLiquidityEngine.analyzeLiquidity(candles, londonTimestamp)

        assertEquals("Session phase should be London Manipulation", SessionPhase.LONDON_MANIPULATION, report.sessionPhase)
        assertEquals("Sweep status should be HIGH_SWEPT_REVERSED", LiquiditySweepStatus.HIGH_SWEPT_REVERSED, report.sweepStatus)
        assertEquals("Sweep tag should be ASIAN_RANGE_LIQUIDITY_PURGE", "ASIAN_RANGE_LIQUIDITY_PURGE", report.sweepTag)
        assertTrue("Should include liquidity purge confluence note", report.confluenceNotes.any { it.contains("Asian High liquidity purged") })
    }

    @Test
    fun testLbmaFixBenchmarkWindowRegime() {
        val calAm = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30) // Exactly on LBMA AM Fix window (10:22 - 10:38 UTC)
        }
        val amTimestamp = calAm.timeInMillis

        val dummyCandles = listOf(
            createCandle(amTimestamp, 2850.0, 2855.0, 2845.0, 2852.0)
        )

        val reportAm = SilentLiquidityEngine.analyzeLiquidity(dummyCandles, amTimestamp)

        assertEquals(SessionPhase.LBMA_AM_FIX, reportAm.sessionPhase)
        assertTrue("LBMA Fix regime should be active", reportAm.isLbmaFixActive)
        assertTrue("Widen stops buffer should be active", reportAm.widenStopsBuffer)
        assertNotNull(reportAm.lbmaFixName)

        // Test LBMA PM Fix window (15:00 UTC)
        val calPm = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 5)
        }
        val pmTimestamp = calPm.timeInMillis

        val reportPm = SilentLiquidityEngine.analyzeLiquidity(dummyCandles, pmTimestamp)

        assertEquals(SessionPhase.LBMA_PM_FIX, reportPm.sessionPhase)
        assertTrue("LBMA Fix PM regime should be active", reportPm.isLbmaFixActive)
        assertTrue("Widen stops buffer should be active", reportPm.widenStopsBuffer)
    }

    @Test
    fun testDeltaPressureAndExhaustionDivergence() {
        val now = 1700000000000L
        val candles = mutableListOf<CandleEntity>()

        // First half: strong buying push with Close at the High (high buying aggression)
        for (i in 0 until 5) {
            candles.add(
                createCandle(
                    timestamp = now + (i * 60_000L),
                    open = 2840.0 + i,
                    high = 2845.0 + i,
                    low = 2840.0 + i,
                    close = 2845.0 + i, // Close == High => Buying Pressure = 100%
                    volume = 200.0
                )
            )
        }

        // Second half: Price pushes to a NEW HIGHER HIGH (2855.0 > 2849.0), but Close collapses near the Low (exhaustion wick)
        for (i in 5 until 10) {
            candles.add(
                createCandle(
                    timestamp = now + (i * 60_000L),
                    open = 2848.0,
                    high = 2855.0, // Higher high
                    low = 2847.0,
                    close = 2847.5, // Close near Low => Buying Pressure collapsed
                    volume = 200.0
                )
            )
        }

        val report = SilentLiquidityEngine.analyzeLiquidity(candles, now + (10 * 60_000L))

        assertTrue("Should detect Exhaustion Divergence", report.deltaMetrics.isExhaustionDivergence)
        assertEquals("BEARISH_EXHAUSTION", report.deltaMetrics.divergenceType)
        assertTrue(report.deltaMetrics.divergenceDetails?.contains("EXHAUSTION_DIVERGENCE") == true)
    }

    @Test
    fun testQuantSignalEngineIntegrationWithSilentLiquidity() {
        val now = System.currentTimeMillis()
        val candles = mutableListOf<CandleEntity>()

        for (i in 0 until 25) {
            candles.add(
                createCandle(
                    timestamp = now - ((25 - i) * 60_000L),
                    open = 2850.0 + (i * 0.5),
                    high = 2852.0 + (i * 0.5),
                    low = 2849.0 + (i * 0.5),
                    close = 2851.0 + (i * 0.5),
                    volume = 120.0
                )
            )
        }

        val signal = QuantSignalEngine.generateConsensusSignal(
            candles = candles,
            timeframe = "15m",
            newsArticles = emptyList(),
            calendarEvents = emptyList(),
            enforceFailsafe = false
        )

        assertNotNull(signal)
        assertTrue("Signal should have non-empty reasoning", signal.reasoning.isNotBlank())
        assertTrue("Signal entry price should be positive", signal.entryPrice > 2000.0)
    }
}
