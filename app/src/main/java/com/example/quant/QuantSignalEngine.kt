package com.example.quant

import com.example.data.local.CandleEntity
import com.example.data.local.EconomicEventEntity
import com.example.data.local.FundamentalAnalysisEntity
import com.example.data.local.NewsArticleEntity
import com.example.data.local.SignalEntity
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
            val staleness = now - lastCandle.timestamp
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

        // MTF Alignment Integration
        if (alignment != null) {
            val bullPct = alignment.bullishPercentage
            val mtfScore = (bullPct - 50.0) * 2.0 // Map 0..100% to -100..+100
            techScore = (techScore * 0.6) + (mtfScore * 0.4)
            reasons.add("MTF Confluence: ${alignment.overallStatus} (${round(bullPct)}% bullish)")
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
        // 4.5 SILENT LIQUIDITY LAYER (Institutional Edge Multipliers)
        // -------------------------------------------------------------
        val liquidityReport = silentLiquidityReport ?: SilentLiquidityEngine.analyzeLiquidity(candles, now)
        val liquidityScoreModifier = liquidityReport.scoreModifier // -50.0 to +50.0
        reasons.addAll(liquidityReport.confluenceNotes)

        // -------------------------------------------------------------
        // 5. MULTI-FACTOR CONSENSUS CALCULATION
        // -------------------------------------------------------------
        // Base: Technical 40%, Fundamental 25%, AI Reasoning 20%, Silent Liquidity 15%
        var compositeScore = (techScore * 0.40) + (fundScore * 0.25) + (aiScore * 0.20) + (liquidityScoreModifier * 0.30)

        // Risk adjustment: If High Market Risk, dampen conviction towards neutral
        if (marketRisk.equals("HIGH", ignoreCase = true)) {
            compositeScore *= 0.65
            reasons.add("High Market Risk catalyst dampens directional commitment")
        }

        val finalSignal = when {
            compositeScore >= 68.0 -> "STRONG BUY"
            compositeScore >= 42.0 -> "BUY"
            compositeScore >= 18.0 -> "WEAK BUY"
            compositeScore <= -68.0 -> "STRONG SELL"
            compositeScore <= -42.0 -> "SELL"
            compositeScore <= -18.0 -> "WEAK SELL"
            else -> "WAIT"
        }

        val confidence = min(98.0, max(50.0, 50.0 + (abs(compositeScore) * 0.48)))

        // -------------------------------------------------------------
        // 6. EXACT ENTRY ZONES, STOP LOSS, TAKE PROFIT, RISK/REWARD
        // -------------------------------------------------------------
        val isBullish = "BUY" in finalSignal
        val isBearish = "SELL" in finalSignal
        val isStrong = "STRONG" in finalSignal

        val widenFactor = if (liquidityReport.widenStopsBuffer) 1.35 else 1.0
        val atrSlMult = (if (isStrong) 1.4 else 1.6) * widenFactor
        val atrTp1Mult = (if (isStrong) 2.0 else 1.8) * widenFactor
        val atrTp2Mult = (if (isStrong) 3.6 else 3.2) * widenFactor

        val entryPrice = roundPrice(p)
        val entryZoneLow: Double
        val entryZoneHigh: Double
        val stopLoss: Double
        val takeProfit1: Double
        val takeProfit2: Double
        val rrRatio: Double

        if (isBullish) {
            entryZoneLow = roundPrice(p - (atr * 0.25))
            entryZoneHigh = roundPrice(p + (atr * 0.15))
            stopLoss = roundPrice(p - (atr * atrSlMult))
            takeProfit1 = roundPrice(p + (atr * atrTp1Mult))
            takeProfit2 = roundPrice(p + (atr * atrTp2Mult))
            val risk = max(0.1, entryPrice - stopLoss)
            val reward = max(0.1, takeProfit1 - entryPrice)
            rrRatio = roundRatio(reward / risk)
        } else if (isBearish) {
            entryZoneLow = roundPrice(p - (atr * 0.15))
            entryZoneHigh = roundPrice(p + (atr * 0.25))
            stopLoss = roundPrice(p + (atr * atrSlMult))
            takeProfit1 = roundPrice(p - (atr * atrTp1Mult))
            takeProfit2 = roundPrice(p - (atr * atrTp2Mult))
            val risk = max(0.1, stopLoss - entryPrice)
            val reward = max(0.1, entryPrice - takeProfit1)
            rrRatio = roundRatio(reward / risk)
        } else {
            // WAIT / Consolidation
            entryZoneLow = roundPrice(p - (atr * 0.4))
            entryZoneHigh = roundPrice(p + (atr * 0.4))
            stopLoss = roundPrice(p - atr)
            takeProfit1 = roundPrice(p + atr)
            takeProfit2 = roundPrice(p + (atr * 2.0))
            rrRatio = 1.0
            reasons.add("Consolidation mode: Awaiting directional catalyst breakout.")
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
            status = "ACTIVE",
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
