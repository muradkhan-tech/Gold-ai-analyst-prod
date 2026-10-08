package com.example

import com.example.data.local.CandleEntity
import com.example.data.market.CandleAggregator
import com.example.data.market.DataValidator
import com.example.data.market.SystemHealthState
import com.example.quant.QuantBacktestEngine
import com.example.quant.QuantCalculations
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testEmaCalculation() {
        val prices = listOf(10.0, 11.0, 12.0, 13.0, 14.0, 15.0)
        val ema = QuantCalculations.calculateEma(prices, 3)
        assertEquals(prices.size, ema.size)
        assertTrue(ema.last() > 10.0)
    }

    @Test
    fun testRsiCalculation() {
        val prices = (1..30).map { 2800.0 + it * 2.0 }
        val rsi = QuantCalculations.calculateRsi(prices, 14)
        assertEquals(prices.size, rsi.size)
        assertTrue(rsi.last() >= 70.0)
    }

    @Test
    fun testQuantBacktester() {
        val candles = (1..60).map { i ->
            val p = 2800.0 + (i % 10) * 3.0
            CandleEntity(
                symbol = "XAUUSD",
                timeframe = "15m",
                timestamp = 1000L * i,
                open = p,
                high = p + 2.0,
                low = p - 2.0,
                close = p + 1.0,
                volume = 1000.0
            )
        }
        val summary = QuantBacktestEngine.runBacktest(candles, "EMA Test Strategy")
        assertNotNull(summary)
        assertEquals("EMA Test Strategy", summary.strategyName)
    }

    @Test
    fun testCandleAggregatorUtcBucketing() {
        val baseTime = 1700000000000L // UTC timestamp aligned
        val minuteMs = 60_000L

        // Generate 15 consecutive 1m candles
        val candles1m = (0 until 15).map { i ->
            CandleEntity(
                symbol = "XAUUSD",
                timeframe = "1m",
                timestamp = baseTime + (i * minuteMs),
                open = 2850.0 + i,
                high = 2852.0 + i,
                low = 2849.0 + i,
                close = 2851.0 + i,
                volume = 100.0
            )
        }

        val allTimeframes = CandleAggregator.aggregateAllTimeframes(candles1m)

        // 15 1m candles aggregated into 3m -> 5 candles
        val candles3m = allTimeframes["3m"]!!
        assertTrue(candles3m.isNotEmpty())

        // 15 1m candles aggregated into 5m -> 3 candles
        val candles5m = allTimeframes["5m"]!!
        assertTrue(candles5m.isNotEmpty())

        // Check OHLCV integrity of aggregated 5m candle
        val first5m = candles5m.first()
        assertEquals(2850.0, first5m.open, 0.01)
        assertTrue(first5m.volume >= 100.0)
    }

    @Test
    fun testDataValidatorDuplicatesAndGaps() {
        val now = System.currentTimeMillis()
        val minuteMs = 60_000L

        // Include duplicate and gap (> 60s)
        val testCandles = listOf(
            CandleEntity(symbol = "XAUUSD", timeframe = "1m", timestamp = now - (180 * 1000L), open = 2850.0, high = 2852.0, low = 2848.0, close = 2851.0, volume = 100.0),
            // Duplicate
            CandleEntity(symbol = "XAUUSD", timeframe = "1m", timestamp = now - (180 * 1000L), open = 2850.0, high = 2852.0, low = 2848.0, close = 2851.0, volume = 100.0),
            // Gap of 2 minutes
            CandleEntity(symbol = "XAUUSD", timeframe = "1m", timestamp = now - (60 * 1000L), open = 2852.0, high = 2854.0, low = 2850.0, close = 2853.0, volume = 150.0)
        )

        val report = DataValidator.validate(testCandles, isRateLimited = false)
        assertEquals(1, report.duplicatesRemoved)
        assertTrue(report.missingGapsCount >= 1)
        assertEquals(SystemHealthState.LIVE, report.state)
    }

    @Test
    fun testDataValidatorStalePrices() {
        val staleTimestamp = System.currentTimeMillis() - (600 * 1000L) // 10 minutes ago (> 5 min threshold)
        val staleCandles = listOf(
            CandleEntity(symbol = "XAUUSD", timeframe = "1m", timestamp = staleTimestamp, open = 2850.0, high = 2852.0, low = 2848.0, close = 2851.0, volume = 100.0)
        )

        val report = DataValidator.validate(staleCandles, isRateLimited = false)
        assertEquals(SystemHealthState.OFFLINE_DELAYED, report.state)
    }

    @Test
    fun testDataValidatorRateLimited() {
        val candles = listOf(
            CandleEntity(symbol = "XAUUSD", timeframe = "1m", timestamp = System.currentTimeMillis(), open = 2850.0, high = 2852.0, low = 2848.0, close = 2851.0, volume = 100.0)
        )
        val report = DataValidator.validate(candles, isRateLimited = true)
        assertEquals(SystemHealthState.RATE_LIMITED_NEED_KEY, report.state)
    }

    @Test
    fun testMultiTimeframeAlignmentScoring() {
        val bullishSnapshot = com.example.quant.TechnicalSnapshot(
            currentPrice = 2865.0,
            ema8 = 2863.0,
            ema21 = 2860.0,
            ema28 = 2857.0,
            ema50 = 2850.0,
            ema200 = 2830.0,
            rsi14 = 62.0,
            macdLine = 2.5,
            macdSignal = 1.8,
            macdHistogram = 0.7,
            bbUpper = 2875.0,
            bbMiddle = 2860.0,
            bbLower = 2845.0,
            atr14 = 11.5
        )

        val map = mapOf(
            "1m" to bullishSnapshot,
            "3m" to bullishSnapshot,
            "5m" to bullishSnapshot,
            "10m" to bullishSnapshot,
            "15m" to bullishSnapshot,
            "1h" to bullishSnapshot
        )

        val alignment = com.example.quant.MultiTimeframeAlignment.evaluate(map)
        assertNotNull(alignment)
        assertEquals("STRONG_BULLISH", alignment.overallStatus)
        assertEquals(6, alignment.bullishCount)
        assertEquals(0, alignment.bearishCount)
        assertEquals(100f, alignment.bullishPercentage, 0.1f)
        assertTrue(alignment.scalpVsMacroNote.contains("aligned", ignoreCase = true))

        alignment.perTimeframe.forEach { tfStatus ->
            assertEquals("BULLISH", tfStatus.trend)
            assertTrue(tfStatus.score >= 2)
        }
    }

    @Test
    fun testNewsFilterPillarsAndRelevance() {
        // Pillar 1: Gold / XAUUSD
        val goldResult = com.example.data.market.NewsFilter.filterAndCategorize(
            "Gold Extends Rally as Sovereign Central Banks Accumulate Bullion",
            "Physical bullion holdings rise amid global reserve diversification."
        )
        assertNotNull(goldResult)
        assertEquals("GOLD", goldResult?.category)
        assertEquals("BULLISH", goldResult?.sentiment)

        // Pillar 2: Fed policy
        val fedResult = com.example.data.market.NewsFilter.filterAndCategorize(
            "Federal Reserve FOMC Signals Dovish Stance on Future Interest Rate Cuts",
            "Jerome Powell highlights steady progress on disinflation path."
        )
        assertNotNull(fedResult)
        assertEquals("FED_POLICY", fedResult?.category)
        assertEquals("BULLISH", fedResult?.sentiment)

        // Pillar 3: Inflation
        val cpiResult = com.example.data.market.NewsFilter.filterAndCategorize(
            "US Core CPI Inflation Prints Cooler Than Forecast",
            "Consumer price index decelerates to 2.5% YoY, sparking rally."
        )
        assertNotNull(cpiResult)
        assertEquals("INFLATION", cpiResult?.category)

        // Pillar 4: Treasury yields
        val yieldResult = com.example.data.market.NewsFilter.filterAndCategorize(
            "10-Year Treasury Yields Drop Below Key Support at 4.10%",
            "Bond yields retreat as real yields compress, lowering opportunity cost."
        )
        assertNotNull(yieldResult)
        assertEquals("TREASURY_YIELDS", yieldResult?.category)

        // Pillar 5: USD / DXY
        val usdResult = com.example.data.market.NewsFilter.filterAndCategorize(
            "US Dollar Index (DXY) Weakens Following Softer Economic Data",
            "The greenback retreats against major currencies."
        )
        assertNotNull(usdResult)
        assertEquals("USD_DXY", usdResult?.category)

        // Irrelevant news discarded
        val irrelevant = com.example.data.market.NewsFilter.filterAndCategorize(
            "Tech Company Announces Breakthrough in Quantum Computer Chip",
            "New silicon architecture boosts performance by 40%."
        )
        assertNull(irrelevant)
    }

    @Test
    fun testMultiTimeframeConsensusSignalAndEntryZones() {
        val now = System.currentTimeMillis()
        val candles = (1..50).map { i ->
            val p = 2800.0 + (i * 1.5)
            CandleEntity(
                symbol = "XAUUSD",
                timeframe = "15m",
                timestamp = now - ((50 - i) * 60 * 1000L),
                open = p - 1.0,
                high = p + 2.0,
                low = p - 1.5,
                close = p,
                volume = 500.0
            )
        }

        val news = listOf(
            com.example.data.local.NewsArticleEntity(
                title = "Gold Bullion Surges on Central Bank Reserve Buying",
                source = "World Gold Council",
                url = null,
                summary = "Sovereign accumulation provides persistent floor demand.",
                category = "GOLD",
                sentiment = "BULLISH",
                relevanceScore = 0.95f,
                publishedAt = now
            )
        )

        val signal = com.example.quant.QuantSignalEngine.generateConsensusSignal(
            candles = candles,
            timeframe = "15m",
            newsArticles = news,
            enforceFailsafe = false
        )

        assertNotNull(signal)
        assertTrue(signal.signalType in listOf("STRONG BUY", "BUY", "WEAK BUY", "WAIT", "WEAK SELL", "SELL", "STRONG SELL"))
        assertTrue(signal.entryZoneLow <= signal.entryPrice)
        assertTrue(signal.entryZoneHigh >= signal.entryPrice)
        assertTrue(signal.riskRewardRatio > 0.0)
        assertTrue(signal.confidenceScore in 50.0..99.0)
        if ("BUY" in signal.signalType) {
            assertTrue(signal.stopLoss < signal.entryPrice)
            assertTrue(signal.takeProfit1 > signal.entryPrice)
            assertTrue(signal.takeProfit2 > signal.takeProfit1)
        }
    }

    @Test
    fun testStrictFailsafeOnStaleData() {
        val staleTimestamp = System.currentTimeMillis() - (10 * 60 * 1000L) // 10 minutes stale
        val staleCandles = (1..30).map { i ->
            CandleEntity(
                symbol = "XAUUSD",
                timeframe = "15m",
                timestamp = staleTimestamp - ((30 - i) * 60 * 1000L),
                open = 2850.0,
                high = 2855.0,
                low = 2845.0,
                close = 2852.0,
                volume = 200.0
            )
        }

        val failsafeSignal = com.example.quant.QuantSignalEngine.generateConsensusSignal(
            candles = staleCandles,
            timeframe = "15m",
            enforceFailsafe = true
        )

        assertEquals("NO SIGNAL", failsafeSignal.signalType)
        assertEquals(0.0, failsafeSignal.confidenceScore, 0.01)
        assertTrue(failsafeSignal.reasoning.contains("FAILSAFE", ignoreCase = true))
    }

    @Test
    fun testEventDrivenBacktesterMetricsAndEntityConversion() {
        val now = System.currentTimeMillis()
        val candles = (1..80).map { i ->
            val p = 2800.0 + (i % 8) * 4.0
            CandleEntity(
                symbol = "XAUUSD",
                timeframe = "15m",
                timestamp = now - ((80 - i) * 15 * 60 * 1000L),
                open = p - 1.0,
                high = p + 3.0,
                low = p - 2.5,
                close = p + 0.5,
                volume = 600.0
            )
        }

        val summary = QuantBacktestEngine.runBacktest(candles, "MultiTimeframeConsensusEngine")
        assertNotNull(summary)
        assertTrue(summary.lookAheadBiasPrevented)
        assertEquals("EVENT_DRIVEN_BAR_OPEN_FILL", summary.executionModel)
        assertTrue(summary.winRatePct >= 0.0 && summary.winRatePct <= 100.0)
        assertTrue(summary.maxDrawdownPct >= 0.0)
        assertTrue(summary.profitFactor >= 0.0)

        // Test database entity conversion
        val entity = summary.toEntity("15m")
        assertEquals(summary.strategyName, entity.strategyName)
        assertEquals(summary.winRatePct, entity.winRatePct, 0.01)
        assertEquals(summary.profitFactor, entity.profitFactor, 0.01)
        assertEquals(summary.maxDrawdownPct, entity.maxDrawdownPct, 0.01)
    }
}
