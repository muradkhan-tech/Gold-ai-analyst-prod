package com.example.quant

import com.example.data.local.CandleEntity
import com.example.data.local.EconomicEventEntity
import com.example.data.local.FundamentalAnalysisEntity
import com.example.data.local.NewsArticleEntity
import com.example.data.local.SignalEntity
import com.example.data.local.TradingViewAlertEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object QuantSignalEngine {

    private const val MAX_STALENESS_MS = 5 * 60 * 1000L // 5 minutes strict failsafe

    /**
     * Institutional Multi-Timeframe Consensus Engine.
     * Combines:
     * 1. Multi-Timeframe Technical Confluence (1m to 1h)
     * 2. Macro Fundamental Catalysts (Filtered News & Calendar)
     * 3. AI Reasoning Synthesis (Market Risk & Bias)
     *
     * Outputs: STRONG BUY, BUY, WEAK BUY, WAIT, WEAK SELL, SELL, STRONG SELL (or NO SIGNAL on failsafe).
     * Enforces strict failsafe if market data or news is missing/stale.
     */
    fun generateConsensusSignal(
        candles: List<CandleEntity>,
        timeframe: String = "15m",
        alignment: AlignmentReport? = null,
        newsArticles: List<NewsArticleEntity> = emptyList(),
        calendarEvents: List<EconomicEventEntity> = emptyList(),
        aiAnalysis: FundamentalAnalysisEntity? = null,
        silentLiquidityReport: SilentLiquidityReport? = null,
        tradingViewAlerts: List<TradingViewAlertEntity> = emptyList(),
        latestTickTimestamp: Long? = null,
        enforceFailsafe: Boolean = true
    ): SignalEntity {
        val now = System.currentTimeMillis()

        // -------------------------------------------------------------
        // 1. STRICT FAILSAFE: Missing or Stale Market Data & News
        // -------------------------------------------------------------
        if (enforceFailsafe) {
            if (candles.size < 20) {
                val p = candles.lastOrNull()?.close ?: 2860.0
                return buildFailsafeSignal(
                    signalType = "NO SIGNAL",
                    reasoning = "FAILSAFE TRIGGERED: Insufficient candle history (${candles.size} < 20 bars). Trading halted to prevent execution on sparse data.",
                    currentPrice = p,
                    timeframe = timeframe
                )
            }

            val lastCandle = candles.last()
            val staleness = if (latestTickTimestamp != null) {
                now - latestTickTimestamp
            } else {
                val tfDurationMs = when (timeframe) {
                    "1m" -> 60_000L
                    "3m" -> 180_000L
                    "5m" -> 300_000L
                    "10m" -> 600_000L
                    "15m" -> 900_000L
                    "1h" -> 3_600_000L
                    else -> 900_000L
                }
                maxOf(0L, (now - lastCandle.timestamp) - tfDurationMs)
            }

            if (staleness > MAX_STALENESS_MS) {
                val sec = staleness / 1000
                return buildFailsafeSignal(
                    signalType = "NO SIGNAL",
                    reasoning = "FAILSAFE TRIGGERED: Market data feed is stale by ${sec}s (> 300s threshold). Automated trade generation locked.",
                    currentPrice = lastCandle.close,
                    timeframe = timeframe
                )
            }

            if (newsArticles.isEmpty() && calendarEvents.isEmpty()) {
                return buildFailsafeSignal(
                    signalType = "WAIT",
                    reasoning = "FAILSAFE TRIGGERED: Macro news intelligence is empty. Awaiting fresh fundamental data before committing capital.",
                    currentPrice = lastCandle.close,
                    timeframe = timeframe
                )
            }
        }

        // -------------------------------------------------------------
        // 2. TECHNICAL ANALYSIS LAYER (Multi-Timeframe Confluence)
        // -------------------------------------------------------------
        val snapshot = QuantCalculations.computeSnapshot(candles)
        val p = snapshot.currentPrice
        val atr = if (snapshot.atr14 > 1.0) snapshot.atr14 else 12.0

        var techScore = 0.0 // -100 to +100
        val reasons = mutableListOf<String>()

        // EMA Stack
        if (p > snapshot.ema8 && snapshot.ema8 > snapshot.ema21 && snapshot.ema21 > snapshot.ema50 && snapshot.ema50 > snapshot.ema200) {
            techScore += 40.0
            reasons.add("Perfect Bullish EMA Stack (Price > EMA8 > EMA21 > EMA50 > EMA200)")
        } else if (p < snapshot.ema8 && snapshot.ema8 < snapshot.ema21 && snapshot.ema21 < snapshot.ema50 && snapshot.ema50 < snapshot.ema200) {
            techScore -= 40.0
            reasons.add("Perfect Bearish EMA Stack (Price < EMA8 < EMA21 < EMA50 < EMA200)")
        } else if (p > snapshot.ema21 && snapshot.ema21 > snapshot.ema50) {
            techScore += 20.0
            reasons.add("Moderate Bullish EMA Structure")
        } else if (p < snapshot.ema21 && snapshot.ema21 < snapshot.ema50) {
            techScore -= 20.0
            reasons.add("Moderate Bearish EMA Structure")
        }

        // MACD Momentum
        if (snapshot.macdLine > snapshot.macdSignal && snapshot.macdHistogram > 0) {
            techScore += 25.0
            reasons.add("MACD positive expansion crossover")
        } else if (snapshot.macdLine < snapshot.macdSignal && snapshot.macdHistogram < 0) {
            techScore -= 25.0
            reasons.add("MACD negative momentum downward")
        }

        // RSI Momentum & Mean Reversion
        if (snapshot.rsi14 in 50.0..68.0) {
            techScore += 20.0
            reasons.add("RSI bullish expansion (${round1(snapshot.rsi14)})")
        } else if (snapshot.rsi14 in 32.0..50.0) {
            techScore -= 20.0
            reasons.add("RSI bearish expansion (${round1(snapshot.rsi14)})")
        } else if (snapshot.rsi14 < 32.0) {
            techScore += 15.0
            reasons.add("RSI oversold rebound zone (${round1(snapshot.rsi14)})")
        } else if (snapshot.rsi14 > 68.0) {
            techScore -= 15.0
            reasons.add("RSI overbought exhaustion zone (${round1(snapshot.rsi14)})")
        }

        // Bollinger Bands
        if (p > snapshot.bbUpper) {
            techScore += 15.0
            reasons.add("Bollinger upper band expansion breakout")
        } else if (p < snapshot.bbLower) {
            techScore -= 15.0
            reasons.add("Bollinger lower band support pressure")
        }

        // MTF Alignment Integration across M1, M3, M5, M10, M15, H1
        var isHtfBullish = false
        var isHtfBearish = false
        if (alignment != null) {
            val bullPct = alignment.bullishPercentage
            val mtfScore = (bullPct - 50.0) * 2.0 // Map 0..100% to -100..+100
            techScore = (techScore * 0.6) + (mtfScore * 0.4)
            reasons.add("MTF Confluence: ${alignment.overallStatus} (${round(bullPct)}% bullish)")

            val m15Trend = alignment.perTimeframe.firstOrNull { it.timeframe == "15m" }?.trend ?: "NEUTRAL"
            val h1Trend = alignment.perTimeframe.firstOrNull { it.timeframe == "1h" }?.trend ?: "NEUTRAL"
            isHtfBullish = (m15Trend == "BULLISH" && h1Trend == "BULLISH") || (m15Trend == "BULLISH" && h1Trend != "BEARISH")
            isHtfBearish = (m15Trend == "BEARISH" && h1Trend == "BEARISH") || (m15Trend == "BEARISH" && h1Trend != "BULLISH")
        } else {
            isHtfBullish = p > snapshot.ema50
            isHtfBearish = p < snapshot.ema50
        }

        techScore = techScore.coerceIn(-100.0, 100.0)

        // -------------------------------------------------------------
        // 3. FUNDAMENTAL INTELLIGENCE LAYER
        // -------------------------------------------------------------
        var fundScore = 0.0 // -100 to +100
        val fundReasons = mutableListOf<String>()

        newsArticles.take(8).forEach { article ->
            val rel = article.relevanceScore.toDouble()
            when (article.sentiment.uppercase()) {
                "BULLISH" -> fundScore += 12.0 * rel
                "BEARISH" -> fundScore -= 12.0 * rel
            }
        }

        calendarEvents.take(5).forEach { ev ->
            val mult = if (ev.impact.uppercase() == "HIGH") 1.5 else 1.0
            when (ev.goldBias.uppercase()) {
                "BULLISH" -> fundScore += 10.0 * mult
                "BEARISH" -> fundScore -= 10.0 * mult
            }
        }

        if (fundScore > 10.0) {
            fundReasons.add("Macro News & Catalyst Bias: Positive Bullion Demand")
        } else if (fundScore < -10.0) {
            fundReasons.add("Macro News & Catalyst Bias: Headwind from Dollar/Yields")
        }

        fundScore = fundScore.coerceIn(-100.0, 100.0)

        // -------------------------------------------------------------
        // 4. AI REASONING SYNTHESIS LAYER
        // -------------------------------------------------------------
        var aiScore = 0.0
        var marketRisk = "Medium"
        var aiConfidence = 80.0

        if (aiAnalysis != null) {
            marketRisk = aiAnalysis.marketRisk
            aiConfidence = aiAnalysis.confidenceScore.toDouble()
            when (aiAnalysis.fundamentalBias.uppercase()) {
                "BULLISH" -> aiScore = (aiConfidence / 100.0) * 100.0
                "BEARISH" -> aiScore = -(aiConfidence / 100.0) * 100.0
                else -> aiScore = 0.0
            }
            reasons.add("AI Synthesis: ${aiAnalysis.fundamentalBias} Bias (Risk: $marketRisk, Conf: ${round(aiConfidence)}%)")
        } else {
            aiScore = fundScore * 0.8
        }

        // -------------------------------------------------------------
        // 4.5 SILENT LIQUIDITY LAYER & VOLUME DELTA PRESSURE
        // -------------------------------------------------------------
        val liquidityReport = silentLiquidityReport ?: SilentLiquidityEngine.analyzeLiquidity(candles, now)
        val liquidityScoreModifier = liquidityReport.scoreModifier // -50.0 to +50.0
        reasons.addAll(liquidityReport.confluenceNotes)

        // Volume Delta Pressure contribution
        val deltaMetrics = liquidityReport.deltaMetrics
        if (deltaMetrics.dominantParty == "Net Buyers" && deltaMetrics.buyingAggressionPct > 52.0) {
            reasons.add("Volume Delta: Net Buyers (+${round(deltaMetrics.buyingAggressionPct)}% aggression)")
        } else if (deltaMetrics.dominantParty == "Net Sellers" && deltaMetrics.sellingAggressionPct > 52.0) {
            reasons.add("Volume Delta: Net Sellers (+${round(deltaMetrics.sellingAggressionPct)}% aggression)")
        }

        // -------------------------------------------------------------
        // 4.6 TRADINGVIEW PROPRIETARY ALERT CONFLUENCE BOOST (+15 pts)
        // -------------------------------------------------------------
        var tvBoost = 0.0
        val recentVerifiedAlerts = tradingViewAlerts.filter { alert ->
            val isProprietary = alert.indicator.contains("Scalping with Dr Hafiz", ignoreCase = true) ||
                    alert.indicator.contains("3ESRA", ignoreCase = true)
            val isRecent = (now - alert.timestamp) < (15 * 60 * 1000L) // active within last 15 mins
            isProprietary && alert.isVerified && isRecent
        }

        if (recentVerifiedAlerts.isNotEmpty()) {
            val latestTvAlert = recentVerifiedAlerts.first()
            if (latestTvAlert.action.contains("BUY", ignoreCase = true)) {
                tvBoost = 15.0
                reasons.add("TradingView Webhook Confluence Boost (+15 pts): Verified '${latestTvAlert.indicator}' BUY signal")
            } else if (latestTvAlert.action.contains("SELL", ignoreCase = true)) {
                tvBoost = -15.0
                reasons.add("TradingView Webhook Confluence Boost (-15 pts): Verified '${latestTvAlert.indicator}' SELL signal")
            }
        }

        // -------------------------------------------------------------
        // 5. MULTI-FACTOR CONSENSUS CALCULATION
        // Combines Technical Score, Institutional Volume Delta, and Macro Bias
        // -------------------------------------------------------------
        var compositeScore = (techScore * 0.40) + (fundScore * 0.25) + (aiScore * 0.20) + (liquidityScoreModifier * 0.30) + tvBoost

        // Risk adjustment: If High Market Risk, dampen conviction towards neutral
        if (marketRisk.equals("HIGH", ignoreCase = true)) {
            compositeScore *= 0.65
            reasons.add("High Market Risk catalyst dampens directional commitment")
        }

        compositeScore = compositeScore.coerceIn(-100.0, 100.0)

        // Confluence Thresholds:
        // Score >= +65 with M15/H1 bullish alignment: 'STRONG BUY'
        // Score between +35 and +64: 'BUY'
        // Score between -34 and +34 (or opposing HTF trend): 'WAIT' (Neutral chop)
        // Score between -35 and -64: 'SELL'
        // Score <= -65 with M15/H1 bearish alignment: 'STRONG SELL'
        val finalSignal: String = when {
            compositeScore >= 65.0 && isHtfBullish -> "STRONG BUY"
            compositeScore >= 65.0 && !isHtfBullish -> "BUY" // Opposing HTF caps at BUY or WAIT
            compositeScore in 35.0..64.9 && !isHtfBearish -> "BUY"
            compositeScore <= -65.0 && isHtfBearish -> "STRONG SELL"
            compositeScore <= -65.0 && !isHtfBearish -> "SELL"
            compositeScore in -64.9..-35.0 && !isHtfBullish -> "SELL"
            else -> "WAIT"
        }

        val confidence = min(98.0, max(50.0, 50.0 + (abs(compositeScore) * 0.48)))

        // -------------------------------------------------------------
        // 6. DYNAMIC LEVEL GENERATION & INVALIDATION
        // If status is 'WAIT' or 'NO SIGNAL': do not display misleading active trade setups.
        // Render fields as disabled dashes ("—") / 0.0 with status 'Awaiting Trend Alignment'.
        // When valid BUY/SELL:
        // Suggested Entry = Current live Close (p)
        // Stop Loss = Close ± (1.5 * ATR(14))
        // TP1 = Close ∓ (1.5 * ATR(14)) [Risk/Reward 1:1.0]
        // TP2 = Close ∓ (3.0 * ATR(14)) [Risk/Reward 1:2.0]
        // -------------------------------------------------------------
        val isTradeSetupActive = finalSignal in listOf("STRONG BUY", "BUY", "SELL", "STRONG SELL")

        val entryPrice: Double
        val entryZoneLow: Double
        val entryZoneHigh: Double
        val stopLoss: Double
        val takeProfit1: Double
        val takeProfit2: Double
        val rrRatio: Double
        val signalStatus: String

        if (isTradeSetupActive) {
            entryPrice = roundPrice(p)
            val atrOffset = roundPrice(1.5 * atr)
            val atrTp2Offset = roundPrice(3.0 * atr)

            if ("BUY" in finalSignal) {
                entryZoneLow = roundPrice(p - (atr * 0.20))
                entryZoneHigh = roundPrice(p + (atr * 0.15))
                stopLoss = roundPrice(p - atrOffset)
                takeProfit1 = roundPrice(p + atrOffset)
                takeProfit2 = roundPrice(p + atrTp2Offset)
                rrRatio = 1.0 // TP1 RR is 1:1.0, TP2 RR is 1:2.0
            } else {
                // SELL
                entryZoneLow = roundPrice(p - (atr * 0.15))
                entryZoneHigh = roundPrice(p + (atr * 0.20))
                stopLoss = roundPrice(p + atrOffset)
                takeProfit1 = roundPrice(p - atrOffset)
                takeProfit2 = roundPrice(p - atrTp2Offset)
                rrRatio = 1.0
            }
            signalStatus = "ACTIVE"
        } else {
            // Invalidation & Card Coherence: WAIT or NO SIGNAL
            entryPrice = 0.0
            entryZoneLow = 0.0
            entryZoneHigh = 0.0
            stopLoss = 0.0
            takeProfit1 = 0.0
            takeProfit2 = 0.0
            rrRatio = 0.0
            signalStatus = "Awaiting Trend Alignment"
            reasons.add("Neutral consolidation: Awaiting trend alignment across M15/H1 timeframes.")
        }

        val fullReasoning = (reasons + fundReasons).joinToString(" • ")

        return SignalEntity(
            symbol = "XAUUSD",
            strategyName = "MultiTimeframeConsensusEngine",
            signalType = finalSignal,
            timeframe = timeframe,
            entryPrice = entryPrice,
            entryZoneLow = entryZoneLow,
            entryZoneHigh = entryZoneHigh,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            takeProfit2 = takeProfit2,
            confidenceScore = round1(confidence),
            riskRewardRatio = rrRatio,
            status = signalStatus,
            reasoning = fullReasoning,
            timestamp = now
        )
    }

    private fun buildFailsafeSignal(
        signalType: String,
        reasoning: String,
        currentPrice: Double,
        timeframe: String
    ): SignalEntity {
        val p = roundPrice(currentPrice)
        return SignalEntity(
            symbol = "XAUUSD",
            strategyName = "FailsafeProtectiveEngine",
            signalType = signalType,
            timeframe = timeframe,
            entryPrice = p,
            entryZoneLow = roundPrice(p - 1.0),
            entryZoneHigh = roundPrice(p + 1.0),
            stopLoss = roundPrice(p - 10.0),
            takeProfit1 = roundPrice(p + 15.0),
            takeProfit2 = roundPrice(p + 30.0),
            confidenceScore = 0.0,
            riskRewardRatio = 1.0,
            status = "ACTIVE",
            reasoning = reasoning,
            timestamp = System.currentTimeMillis()
        )
    }

    fun generateSignal(candles: List<CandleEntity>, timeframe: String = "15m"): SignalEntity {
        return generateConsensusSignal(candles, timeframe, enforceFailsafe = false)
    }

    private fun roundPrice(v: Double): Double = round(v * 100.0) / 100.0
    private fun roundRatio(v: Double): Double = round(v * 10.0) / 10.0
    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
}
