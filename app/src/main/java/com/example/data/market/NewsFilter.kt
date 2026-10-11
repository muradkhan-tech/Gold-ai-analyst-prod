package com.example.data.market

import java.util.Locale
import java.util.regex.Pattern

data class FilteredNewsResult(
    val category: String, // GOLD, FED_POLICY, INFLATION, TREASURY_YIELDS, USD_DXY
    val sentiment: String, // BULLISH, BEARISH, NEUTRAL
    val relevanceScore: Float,
    val normalizedScore: Float = 0.0f // -1.0 to +1.0
)

object NewsFilter {

    // Regex keywords strictly matching macro criteria:
    // ["gold", "xau", "fed", "federal reserve", "powell", "inflation", "cpi", "treasury", "yields", "dollar", "dxy"]
    private val STRICT_MACRO_KEYWORDS = listOf(
        "gold", "xau", "fed", "federal reserve", "powell", "inflation", "cpi", "treasury", "yields", "dollar", "dxy"
    )

    private val STRICT_KEYWORD_REGEX = Pattern.compile(
        "\\b(gold|xauusd|xau|fed|federal reserve|powell|inflation|cpi|treasury|yields?|dollar|dxy)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val GOLD_REGEX = Pattern.compile("\\b(gold|xauusd|xau|bullion|precious metals?|sovereign reserves?)\\b", Pattern.CASE_INSENSITIVE)
    private val FED_REGEX = Pattern.compile("\\b(federal reserve|fed|fomc|jerome powell|powell|interest rates?|rate cut|rate hike)\\b", Pattern.CASE_INSENSITIVE)
    private val INFLATION_REGEX = Pattern.compile("\\b(inflation|cpi|pce|stagflation|cost of living|producer prices?|ppi)\\b", Pattern.CASE_INSENSITIVE)
    private val YIELDS_REGEX = Pattern.compile("\\b(treasury yields?|treasury|10-year yield|t-note|real yields?|bond yields?|yield curve|yields)\\b", Pattern.CASE_INSENSITIVE)
    private val USD_REGEX = Pattern.compile("\\b(us dollar|dollar|dxy|greenback|usd|fiat currency)\\b", Pattern.CASE_INSENSITIVE)

    private val BULLISH_WORDS = listOf(
        "rate cut", "rate cuts", "dovish", "soft landing", "recession", "safe haven",
        "inflation hedge", "stimulus", "weak dollar", "dollar falls", "dollar drops",
        "yields fall", "yields slip", "central bank buying", "bullion demand", "geopolitical",
        "crisis", "soars", "rally", "gains", "reserve diversification", "easing", "sovereign reserve"
    )

    private val BEARISH_WORDS = listOf(
        "rate hike", "rate hikes", "hawkish", "higher for longer", "strong dollar",
        "dollar rallies", "dollar surges", "yields rise", "yields spike", "hot cpi",
        "sticky inflation", "economic boom", "tightening", "selloff", "plunge", "losses",
        "rate pause"
    )

    fun matchesStrictMacroKeywords(title: String, summary: String): Boolean {
        val text = "$title $summary".lowercase(Locale.ROOT)
        return STRICT_KEYWORD_REGEX.matcher(text).find()
    }

    fun filterAndCategorize(title: String, summary: String): FilteredNewsResult? {
        val text = "$title $summary".lowercase(Locale.ROOT)

        var totalKeywordHits = 0
        for (kw in STRICT_MACRO_KEYWORDS) {
            if (text.contains(kw)) totalKeywordHits++
        }

        // Strict filter: must match at least one required keyword
        if (totalKeywordHits == 0 && !STRICT_KEYWORD_REGEX.matcher(text).find()) {
            return null
        }

        var goldMatches = 0
        var fedMatches = 0
        var infMatches = 0
        var yieldMatches = 0
        var usdMatches = 0

        val goldMatcher = GOLD_REGEX.matcher(text)
        while (goldMatcher.find()) goldMatches++

        val fedMatcher = FED_REGEX.matcher(text)
        while (fedMatcher.find()) fedMatches++

        val infMatcher = INFLATION_REGEX.matcher(text)
        while (infMatcher.find()) infMatches++

        val yieldMatcher = YIELDS_REGEX.matcher(text)
        while (yieldMatcher.find()) yieldMatches++

        val usdMatcher = USD_REGEX.matcher(text)
        while (usdMatcher.find()) usdMatches++

        // Determine dominant category
        val category = when {
            goldMatches >= fedMatches && goldMatches >= infMatches && goldMatches >= yieldMatches && goldMatches >= usdMatches -> "GOLD"
            fedMatches >= infMatches && fedMatches >= yieldMatches && fedMatches >= usdMatches -> "FED_POLICY"
            infMatches >= yieldMatches && infMatches >= usdMatches -> "INFLATION"
            yieldMatches >= usdMatches -> "TREASURY_YIELDS"
            else -> "USD_DXY"
        }

        val relevance = (0.4f + (totalKeywordHits * 0.15f)).coerceIn(0.4f, 1.0f)
        val normalizedScore = calculateNormalizedSentimentScore(text)
        val sentiment = when {
            normalizedScore > 0.1f -> "BULLISH"
            normalizedScore < -0.1f -> "BEARISH"
            else -> "NEUTRAL"
        }

        return FilteredNewsResult(
            category = category,
            sentiment = sentiment,
            relevanceScore = relevance,
            normalizedScore = normalizedScore
        )
    }

    /**
     * Dictionary-based keyword counter producing a normalized score (-1.0 to +1.0)
     * without spending external LLM tokens.
     */
    fun calculateNormalizedSentimentScore(text: String): Float {
        val lower = text.lowercase(Locale.ROOT)
        var bullCount = 0
        var bearCount = 0

        for (phrase in BULLISH_WORDS) {
            if (lower.contains(phrase)) bullCount++
        }
        for (phrase in BEARISH_WORDS) {
            if (lower.contains(phrase)) bearCount++
        }

        val total = bullCount + bearCount
        if (total == 0) return 0.0f
        return ((bullCount - bearCount).toFloat() / total.toFloat()).coerceIn(-1.0f, 1.0f)
    }
}
