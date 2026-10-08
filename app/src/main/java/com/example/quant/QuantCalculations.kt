package com.example.quant

import com.example.data.local.CandleEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class TechnicalSnapshot(
    val currentPrice: Double,
    val ema8: Double = 0.0,
    val ema21: Double = 0.0,
    val ema28: Double = 0.0,
    val ema20: Double = 0.0,
    val ema50: Double = 0.0,
    val ema200: Double = 0.0,
    val rsi14: Double = 50.0,
    val macdLine: Double = 0.0,
    val macdSignal: Double = 0.0,
    val macdHistogram: Double = 0.0,
    val bbUpper: Double = 0.0,
    val bbMiddle: Double = 0.0,
    val bbLower: Double = 0.0,
    val atr14: Double = 10.0
)

object QuantCalculations {

    fun calculateEma(prices: List<Double>, period: Int): List<Double> {
        if (prices.isEmpty()) return emptyList()
        val emaList = ArrayList<Double>(prices.size)
        val multiplier = 2.0 / (period + 1.0)
        var ema = prices.first()
        emaList.add(ema)

        for (i in 1 until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
            emaList.add(ema)
        }
        return emaList
    }

    fun calculateRsi(prices: List<Double>, period: Int = 14): List<Double> {
        if (prices.size <= period) return List(prices.size) { 50.0 }
        val rsiList = ArrayList<Double>(prices.size)
        for (i in 0 until period) {
            rsiList.add(50.0)
        }

        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in 1..period) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) avgGain += change else avgLoss += abs(change)
        }
        avgGain /= period
        avgLoss /= period

        val rs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
        rsiList.add(100.0 - (100.0 / (1.0 + rs)))

        for (i in (period + 1) until prices.size) {
            val change = prices[i] - prices[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0

            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period

            val currentRs = if (avgLoss == 0.0) 100.0 else avgGain / avgLoss
            val currentRsi = 100.0 - (100.0 / (1.0 + currentRs))
            rsiList.add(currentRsi)
        }
        return rsiList
    }

    fun calculateMacd(
        prices: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val fastEma = calculateEma(prices, fastPeriod)
        val slowEma = calculateEma(prices, slowPeriod)
        val macdLine = fastEma.zip(slowEma) { f, s -> f - s }
        val signalLine = calculateEma(macdLine, signalPeriod)
        val histogram = macdLine.zip(signalLine) { m, s -> m - s }
        return Triple(macdLine, signalLine, histogram)
    }

    fun calculateBollingerBands(
        prices: List<Double>,
        period: Int = 20,
        stdDevMultiplier: Double = 2.0
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val upper = ArrayList<Double>()
        val middle = ArrayList<Double>()
        val lower = ArrayList<Double>()

        for (i in prices.indices) {
            if (i < period - 1) {
                middle.add(prices[i])
                upper.add(prices[i])
                lower.add(prices[i])
            } else {
                val window = prices.subList(i - period + 1, i + 1)
                val sma = window.average()
                val variance = window.map { (it - sma) * (it - sma) }.average()
                val std = sqrt(variance)

                middle.add(sma)
                upper.add(sma + (stdDevMultiplier * std))
                lower.add(sma - (stdDevMultiplier * std))
            }
        }
        return Triple(upper, middle, lower)
    }

    fun calculateAtr(candles: List<CandleEntity>, period: Int = 14): List<Double> {
        if (candles.isEmpty()) return emptyList()
        val trList = ArrayList<Double>(candles.size)
        trList.add(candles[0].high - candles[0].low)

        for (i in 1 until candles.size) {
            val high = candles[i].high
            val low = candles[i].low
            val prevClose = candles[i - 1].close

            val tr = max(high - low, max(abs(high - prevClose), abs(low - prevClose)))
            trList.add(tr)
        }

        return calculateEma(trList, period)
    }

    fun computeSnapshot(candles: List<CandleEntity>): TechnicalSnapshot {
        if (candles.isEmpty()) {
            return TechnicalSnapshot(
                currentPrice = 2860.0,
                ema8 = 2860.0,
                ema21 = 2860.0,
                ema28 = 2860.0,
                ema20 = 2860.0,
                ema50 = 2860.0,
                ema200 = 2860.0,
                rsi14 = 50.0,
                macdLine = 0.0,
                macdSignal = 0.0,
                macdHistogram = 0.0,
                bbUpper = 2870.0,
                bbMiddle = 2860.0,
                bbLower = 2850.0,
                atr14 = 12.0
            )
        }
        val closes = candles.map { it.close }
        val ema8 = calculateEma(closes, 8).lastOrNull() ?: closes.last()
        val ema21 = calculateEma(closes, 21).lastOrNull() ?: closes.last()
        val ema28 = calculateEma(closes, 28).lastOrNull() ?: closes.last()
        val ema20 = calculateEma(closes, 20).lastOrNull() ?: closes.last()
        val ema50 = calculateEma(closes, 50).lastOrNull() ?: closes.last()
        val ema200 = calculateEma(closes, 200).lastOrNull() ?: closes.last()
        val rsi = calculateRsi(closes, 14).lastOrNull() ?: 50.0
        val (macd, signal, hist) = calculateMacd(closes)
        val (bbUp, bbMid, bbLow) = calculateBollingerBands(closes)
        val atr = calculateAtr(candles, 14).lastOrNull() ?: 12.0

        return TechnicalSnapshot(
            currentPrice = closes.last(),
            ema8 = ema8,
            ema21 = ema21,
            ema28 = ema28,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            rsi14 = rsi,
            macdLine = macd.lastOrNull() ?: 0.0,
            macdSignal = signal.lastOrNull() ?: 0.0,
            macdHistogram = hist.lastOrNull() ?: 0.0,
            bbUpper = bbUp.lastOrNull() ?: (closes.last() + 10.0),
            bbMiddle = bbMid.lastOrNull() ?: closes.last(),
            bbLower = bbLow.lastOrNull() ?: (closes.last() - 10.0),
            atr14 = atr
        )
    }
}
