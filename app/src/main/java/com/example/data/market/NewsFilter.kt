package com.example.data.market

import java.util.regex.Pattern

data class FilteredNewsResult(
    val category: String, // GOLD, FED_POLICY, INFLATION, TREASURY_YIELDS, USD_DXY
    val sentiment: String, // BULLISH, BEARISH, NEUTRAL
    val relevanceScore: Float
)

object NewsFilter {

    private val GOLD_REGEX = Pattern.compile("\\b(gold|xauusd|bullion|precious metals?|sovereign reserves?)\\b", Pattern.CASE_INSENSITIVE)
    private val FED_REGEX = Pattern.compile("\\b(federal reserve|fed|fomc|jerome powell|interest rates?|rate cut|rate hike)\\b", Pattern.CASE_INSENSITIVE)
    private val INFLATION_REGEX = Pattern.compile("\\b(inflation|cpi|pce|stagflation|cost of living|producer prices?|ppi)\\b", Pattern.CASE_INSENSITIVE)
    private val YIELDS_REGEX = Pattern.compile("\\b(treasury yields?|10-year yield|t-note|real yields?|bond yields?|yield curve)\\b", Pattern.CASE_INSENSITIVE)
    private val USD_REGEX = Pattern.compile("\\b(us dollar|dxy|greenback|usd|fiat currency)\\b", Pattern.CASE_INSENSITIVE)

    fun filterAndCategorize(title: String, summary: String): FilteredNewsResult? {
        val text = "$title $summary".lowercase()

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

        val totalMatches = goldMatches + fedMatches + infMatches + yieldMatches + usdMatches
        if (totalMatches == 0) return null // Strict filter: discard if unrelated to 5 pillars

        // Determine dominant category
        val category = when {
            goldMatches >= fedMatches && goldMatches >= infMatches && goldMatches >= yieldMatches && goldMatches >= usdMatches -> "GOLD"
            fedMatches >= infMatches && fedMatches >= yieldMatches && fedMatches >= usdMatches -> "FED_POLICY"
            infMatches >= yieldMatches && infMatches >= usdMatches -> "INFLATION"
            yieldMatches >= usdMatches -> "TREASURY_YIELDS"
            else -> "USD_DXY"
        }

        val relevance = (0.4f + (totalMatches * 0.15f)).coerceAtMost(1.0f)
        val sentiment = evaluateGoldSentiment(text)

        return FilteredNewsResult(
            category = category,
            sentiment = sentiment,
            relevanceScore = relevance
        )
    }

    private fun evaluateGoldSentiment(text: String): String {
        val bullishTokens = listOf("rate cut", "dovish", "falling yields", "dollar weakens", "inflation hedge", "central bank buying", "safe haven", "escalating", "rally", "easing", "sovereign reserve")
        val bearishTokens = listOf("rate hike", "hawkish", "rising yields", "dollar strength", "dollar surges", "higher for longer", "hotter than expected", "selloff", "tightening")

        var bull = 0
        var bear = 0
        bullishTokens.forEach { if (text.contains(it)) bull++ }
        bearishTokens.forEach { if (text.contains(it)) bear++ }

        return when {
            bull > bear -> "BULLISH"
            bear > bull -> "BEARISH"
            else -> "NEUTRAL"
        }
    }
}
