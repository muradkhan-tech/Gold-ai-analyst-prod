package com.example.quant

data class TimeframeTrendStatus(
    val timeframe: String,
    val trend: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val score: Int, // -3 to +3
    val price: Double,
    val summary: String
)

data class AlignmentReport(
    val overallStatus: String, // "STRONG_BULLISH", "MODERATE_BULLISH", "NEUTRAL_CHOP", "MODERATE_BEARISH", "STRONG_BEARISH"
    val bullishCount: Int,
    val bearishCount: Int,
    val neutralCount: Int,
    val totalTimeframes: Int,
    val bullishPercentage: Float,
    val scalpVsMacroNote: String,
    val perTimeframe: List<TimeframeTrendStatus>
)

object MultiTimeframeAlignment {

    private val TIMEFRAMES_ORDER = listOf("1m", "3m", "5m", "10m", "15m", "1h")

    fun evaluate(snapshotsByTf: Map<String, TechnicalSnapshot>): AlignmentReport {
        val list = mutableListOf<TimeframeTrendStatus>()
        var bullish = 0
        var bearish = 0
        var neutral = 0

        for (tf in TIMEFRAMES_ORDER) {
            val s = snapshotsByTf[tf] ?: continue
            var score = 0
            val reasons = mutableListOf<String>()

            // 1. Fast EMA ribbon (8, 21, 28)
            if (s.currentPrice > s.ema8 && s.ema8 > s.ema21 && s.ema21 > s.ema28) {
                score += 2
                reasons.add("EMA ribbon 8>21>28 Bullish")
            } else if (s.currentPrice < s.ema8 && s.ema8 < s.ema21 && s.ema21 < s.ema28) {
                score -= 2
                reasons.add("EMA ribbon 8<21<28 Bearish")
            } else {
                reasons.add("EMA ribbon mixed")
            }

            // 2. MACD Histogram
            if (s.macdHistogram > 0.05) {
                score += 1
            } else if (s.macdHistogram < -0.05) {
                score -= 1
            }

            // 3. RSI bias
            if (s.rsi14 >= 53.0) {
                score += 1
            } else if (s.rsi14 <= 47.0) {
                score -= 1
            }

            val trend = when {
                score >= 2 -> {
                    bullish++
                    "BULLISH"
                }
                score <= -2 -> {
                    bearish++
                    "BEARISH"
                }
                else -> {
                    neutral++
                    "NEUTRAL"
                }
            }

            list.add(
                TimeframeTrendStatus(
                    timeframe = tf,
                    trend = trend,
                    score = score,
                    price = s.currentPrice,
                    summary = reasons.joinToString(", ")
                )
            )
        }

        val total = list.size.coerceAtLeast(1)
        val bullPct = (bullish.toFloat() / total.toFloat()) * 100f

        val trend1m = list.firstOrNull { it.timeframe == "1m" }?.trend ?: "NEUTRAL"
        val trend1h = list.firstOrNull { it.timeframe == "1h" }?.trend ?: "NEUTRAL"

        val scalpVsMacroNote = if (trend1m == trend1h && trend1h != "NEUTRAL") {
            "Scalp (1m) aligned with Macro (1h): High-conviction $trend1h momentum."
        } else if (trend1m != trend1h && trend1m != "NEUTRAL" && trend1h != "NEUTRAL") {
            "Counter-trend scalp: 1m ($trend1m) pulling into 1h ($trend1h) resistance/support."
        } else {
            "Consolidation regime: Multi-timeframe trend consensus is transitioning."
        }

        val overall = when {
            bullish >= 4 && bullish > bearish -> "STRONG_BULLISH"
            bearish >= 4 && bearish > bullish -> "STRONG_BEARISH"
            bullish > bearish -> "MODERATE_BULLISH"
            bearish > bullish -> "MODERATE_BEARISH"
            else -> "NEUTRAL_CHOP"
        }

        return AlignmentReport(
            overallStatus = overall,
            bullishCount = bullish,
            bearishCount = bearish,
            neutralCount = neutral,
            totalTimeframes = total,
            bullishPercentage = bullPct,
            scalpVsMacroNote = scalpVsMacroNote,
            perTimeframe = list
        )
    }
}
