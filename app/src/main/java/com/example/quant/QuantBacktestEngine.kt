package com.example.quant

import com.example.data.local.BacktestOutcomeEntity
import com.example.data.local.CandleEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

data class TradeResult(
    val type: String, // BUY, SELL
    val entryPrice: Double,
    val exitPrice: Double,
    val pnlPoints: Double,
    val pnlDollars: Double,
    val exitReason: String = "TARGET_OR_SL",
    val isWin: Boolean
)

data class BacktestSummary(
    val strategyName: String,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val sharpeRatio: Double,
    val netProfitDollars: Double,
    val finalEquity: Double,
    val lookAheadBiasPrevented: Boolean = true,
    val executionModel: String = "EVENT_DRIVEN_BAR_OPEN_FILL",
    val recentTrades: List<TradeResult>
) {
    fun toEntity(timeframe: String = "15m"): BacktestOutcomeEntity {
        return BacktestOutcomeEntity(
            strategyName = strategyName,
            timeframe = timeframe,
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            losingTrades = losingTrades,
            winRatePct = winRatePct,
            profitFactor = profitFactor,
            maxDrawdownPct = maxDrawdownPct,
            netProfitDollars = netProfitDollars,
            sharpeRatio = sharpeRatio,
            finalEquity = finalEquity,
            calibrationNotes = "Event-driven: $totalTrades trades, $winRatePct% WR, PF $profitFactor",
            timestamp = System.currentTimeMillis()
        )
    }
}

object QuantBacktestEngine {

    /**
     * Executes event-driven backtesting strictly preventing look-ahead bias:
     * - Signal at bar i is generated exclusively from historical slice candles[0..i].
     * - Order execution occurs on bar i+1 open.
     * - Intrabar resolution: if bar low touches SL and high touches TP, conservative SL fill is assumed.
     * - Computes Win Rate %, Profit Factor, Max Drawdown %, Net PnL, and Sharpe Ratio.
     */
    fun runBacktest(
        candles: List<CandleEntity>,
        strategyName: String = "MultiTimeframeConsensusEngine",
        initialCapital: Double = 10000.0,
        warmupBars: Int = 30,
        maxHoldingBars: Int = 20
    ): BacktestSummary {
        if (candles.size < (warmupBars + 15)) {
            return BacktestSummary(
                strategyName = strategyName,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePct = 0.0,
                profitFactor = 0.0,
                maxDrawdownPct = 0.0,
                sharpeRatio = 0.0,
                netProfitDollars = 0.0,
                finalEquity = initialCapital,
                recentTrades = emptyList()
            )
        }

        val trades = mutableListOf<TradeResult>()
        var equity = initialCapital
        var peakEquity = initialCapital
        var maxDrawdownPct = 0.0
        val pnlSeries = mutableListOf<Double>()

        var inPosition = false
        var posType = "BUY"
        var entryPrice = 0.0
        var stopLoss = 0.0
        var takeProfit1 = 0.0
        var barsHeld = 0

        // Zero look-ahead queue: order queued at bar i close to fill at bar i+1 open
        data class PendingOrder(val type: String, val sl: Double, val tp1: Double)
        var pendingOrder: PendingOrder? = null

        val n = candles.size

        for (i in warmupBars until n) {
            val bar = candles[i]
            val barOpen = bar.open
            val barHigh = bar.high
            val barLow = bar.low
            val barClose = bar.close

            // 1. Process pending order at bar Open (Event-driven t+1 fill)
            if (pendingOrder != null && !inPosition) {
                inPosition = true
                posType = pendingOrder.type
                entryPrice = barOpen
                stopLoss = pendingOrder.sl
                takeProfit1 = pendingOrder.tp1
                barsHeld = 0
                pendingOrder = null
            }

            // 2. Evaluate currently active position intrabar
            if (inPosition) {
                barsHeld++
                var exitPrice: Double? = null
                var exitReason = "ACTIVE"
                var isWin = false

                if (posType == "BUY") {
                    val hitSl = barLow <= stopLoss
                    val hitTp = barHigh >= takeProfit1

                    if (hitSl && hitTp) {
                        // Conservative: assume SL hit first
                        exitPrice = stopLoss
                        exitReason = "HIT_SL (Conservative)"
                        isWin = false
                    } else if (hitSl) {
                        exitPrice = stopLoss
                        exitReason = "HIT_SL"
                        isWin = false
                    } else if (hitTp) {
                        exitPrice = takeProfit1
                        exitReason = "HIT_TP1"
                        isWin = true
                    } else if (barsHeld >= maxHoldingBars) {
                        exitPrice = barClose
                        exitReason = "TIMEOUT_EXPIRY"
                        isWin = exitPrice > entryPrice
                    }
                } else {
                    val hitSl = barHigh >= stopLoss
                    val hitTp = barLow <= takeProfit1

                    if (hitSl && hitTp) {
                        exitPrice = stopLoss
                        exitReason = "HIT_SL (Conservative)"
                        isWin = false
                    } else if (hitSl) {
                        exitPrice = stopLoss
                        exitReason = "HIT_SL"
                        isWin = false
                    } else if (hitTp) {
                        exitPrice = takeProfit1
                        exitReason = "HIT_TP1"
                        isWin = true
                    } else if (barsHeld >= maxHoldingBars) {
                        exitPrice = barClose
                        exitReason = "TIMEOUT_EXPIRY"
                        isWin = exitPrice < entryPrice
                    }
                }

                if (exitPrice != null) {
                    val pnlPoints = if (posType == "BUY") exitPrice - entryPrice else entryPrice - exitPrice
                    val dollarPnl = pnlPoints * 50.0 // Standard 0.5 lot gold ($50/pt)
                    equity += dollarPnl
                    pnlSeries.add(dollarPnl)

                    if (equity > peakEquity) peakEquity = equity
                    val dd = ((peakEquity - equity) / peakEquity) * 100.0
                    if (dd > maxDrawdownPct) maxDrawdownPct = dd

                    trades.add(
                        TradeResult(
                            type = posType,
                            entryPrice = round2(entryPrice),
                            exitPrice = round2(exitPrice),
                            pnlPoints = round2(pnlPoints),
                            pnlDollars = round2(dollarPnl),
                            exitReason = exitReason,
                            isWin = isWin
                        )
                    )

                    inPosition = false
                }
            }

            // 3. Generate signal on slice candles[0..i] strictly without future bars
            if (!inPosition && pendingOrder == null && i < (n - 1)) {
                val historicalSlice = candles.subList(0, i + 1)
                val signal = QuantSignalEngine.generateConsensusSignal(
                    candles = historicalSlice,
                    timeframe = "15m",
                    enforceFailsafe = false
                )

                if ("BUY" in signal.signalType) {
                    pendingOrder = PendingOrder("BUY", signal.stopLoss, signal.takeProfit1)
                } else if ("SELL" in signal.signalType) {
                    pendingOrder = PendingOrder("SELL", signal.stopLoss, signal.takeProfit1)
                }
            }
        }

        val wins = trades.filter { it.isWin }
        val losses = trades.filter { !it.isWin }
        val winRate = if (trades.isNotEmpty()) (wins.size.toDouble() / trades.size) * 100.0 else 0.0
        val grossProfit = wins.sumOf { it.pnlDollars }
        val grossLoss = abs(losses.sumOf { it.pnlDollars })
        val profitFactor = if (grossLoss > 0.0) grossProfit / grossLoss else (if (grossProfit > 0.0) 9.99 else 0.0)

        val avgPnl = if (pnlSeries.isNotEmpty()) pnlSeries.average() else 0.0
        val stdPnl = if (pnlSeries.size > 1) {
            val variance = pnlSeries.map { (it - avgPnl) * (it - avgPnl) }.average()
            sqrt(variance)
        } else 1.0
        val sharpe = if (stdPnl > 0.0) (avgPnl / stdPnl) * sqrt(252.0) else 0.0

        return BacktestSummary(
            strategyName = strategyName,
            totalTrades = trades.size,
            winningTrades = wins.size,
            losingTrades = losses.size,
            winRatePct = round2(winRate),
            profitFactor = round2(profitFactor),
            maxDrawdownPct = round2(maxDrawdownPct),
            sharpeRatio = round2(sharpe),
            netProfitDollars = round2(equity - initialCapital),
            finalEquity = round2(equity),
            lookAheadBiasPrevented = true,
            executionModel = "EVENT_DRIVEN_BAR_OPEN_FILL",
            recentTrades = trades.takeLast(8).reversed()
        )
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
