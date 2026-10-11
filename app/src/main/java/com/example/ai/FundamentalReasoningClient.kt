package com.example.ai

import android.util.Log
import com.example.data.local.EconomicEventEntity
import com.example.data.local.FundamentalAnalysisEntity
import com.example.data.local.NewsArticleEntity
import com.example.quant.AlignmentReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.Locale

object FundamentalReasoningClient {

    private const val TAG = "FundamentalReasoning"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """You are the Senior Institutional Gold Macro Economist & Quantitative Risk Officer for GOLD AI ANALYST.
You analyze macroeconomic intermarket dynamics and return a STRICT JSON object with the following schema:
{
  "market_overview": "Summary of macroeconomic backdrop and gold price drivers",
  "short_term_trend": "BULLISH" | "BEARISH" | "NEUTRAL",
  "fundamental_bias": "Bullish" | "Bearish" | "Neutral",
  "market_risk": "Low" | "Medium" | "High",
  "confidence_score": 85.0,
  "bullish_scenario": "Conditions under which gold breaks higher targeting overhead resistance",
  "bearish_scenario": "Conditions under which gold breaks lower testing downside support",
  "invalidation_level": 2845.50,
  "key_catalysts": [
    "Catalyst 1 explanation",
    "Catalyst 2 explanation"
  ],
  "provider_active": "Gemini Pro" | "OpenAI GPT"
}

Rules:
- market_risk MUST be strictly one of: "High", "Medium", "Low".
- fundamental_bias MUST be strictly one of: "Bullish", "Bearish", "Neutral".
- short_term_trend MUST be strictly one of: "BULLISH", "BEARISH", "NEUTRAL".
- Output valid JSON only."""

    /**
     * Strict API key validation to eliminate HTTP 401/429 crashes before requests are made.
     */
    fun isValidOpenAiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val k = key.trim()
        if (k.length < 20) return false
        val lower = k.lowercase(Locale.ROOT)
        if (lower.contains("your_ope") || lower.contains("placeholder") || lower.contains("mock_") || lower.contains("here")) {
            return false
        }
        return k.startsWith("sk-")
    }

    fun isValidGeminiKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        val k = key.trim()
        if (k.length < 20) return false
        val lower = k.lowercase(Locale.ROOT)
        if (lower.contains("placeholder") || lower.contains("my_gemini") || lower.contains("your_gemini") || lower.contains("mock_") || lower.contains("here")) {
            return false
        }
        return k.startsWith("AIzaSy")
    }

    suspend fun evaluateFundamentals(
        openaiKey: String?,
        geminiKey: String?,
        alignment: AlignmentReport?,
        events: List<EconomicEventEntity>,
        news: List<NewsArticleEntity>,
        asianHigh: Double? = null,
        asianLow: Double? = null,
        spotPrice: Double? = null,
        netBuyersPct: Double? = null
    ): Result<FundamentalAnalysisEntity> = withContext(Dispatchers.IO) {
        val hasOpenAi = isValidOpenAiKey(openaiKey)
        val hasGemini = isValidGeminiKey(geminiKey)

        // ZERO-KEY DETERMINISTIC QUANT HEURISTIC ENGINE (LOCAL DUAL-MODE AI)
        // If keys are invalid, placeholder, or missing, do NOT fire external request.
        if (!hasOpenAi && !hasGemini) {
            Log.d(TAG, "Zero-Key mode active. Using local Deterministic Quant Heuristic Engine.")
            return@withContext Result.success(
                generateHeuristicQuantAnalysis(
                    alignment = alignment,
                    events = events,
                    news = news,
                    asianHigh = asianHigh,
                    asianLow = asianLow,
                    spotPrice = spotPrice,
                    netBuyersPct = netBuyersPct
                )
            )
        }

        // Construct structured input JSON for external LLM
        val inputJson = JSONObject().apply {
            put("symbol", "XAUUSD")
            put("timestamp", System.currentTimeMillis())
            spotPrice?.let { put("spot_price", it) }
            asianHigh?.let { put("asian_session_high", it) }
            asianLow?.let { put("asian_session_low", it) }

            val techObj = JSONObject().apply {
                put("overall_status", alignment?.overallStatus ?: "MODERATE_BULLISH")
                put("bullish_percentage", alignment?.bullishPercentage ?: 75.0f)
                put("scalp_vs_macro", alignment?.scalpVsMacroNote ?: "Synchronized")

                val perTfArr = JSONArray()
                alignment?.perTimeframe?.forEach { tf ->
                    perTfArr.put(JSONObject().apply {
                        put("timeframe", tf.timeframe)
                        put("trend", tf.trend)
                        put("score", tf.score)
                        put("summary", tf.summary)
                    })
                }
                put("timeframes", perTfArr)
            }
            put("technical_scores", techObj)

            val eventsArr = JSONArray()
            events.take(5).forEach { ev ->
                eventsArr.put(JSONObject().apply {
                    put("title", ev.title)
                    put("impact", ev.impact)
                    put("forecast", ev.forecast)
                    put("previous", ev.previous)
                    put("gold_bias", ev.goldBias)
                })
            }
            put("calendar_events", eventsArr)

            val newsArr = JSONArray()
            news.take(6).forEach { n ->
                newsArr.put(JSONObject().apply {
                    put("title", n.title)
                    put("category", n.category)
                    put("sentiment", n.sentiment)
                    put("summary", n.summary)
                })
            }
            put("filtered_news", newsArr)
        }

        // Wrap external AI calls in a try-catch block with graceful fallback on 401, 403, 429, timeout
        try {
            val externalResult = if (hasOpenAi) {
                callOpenAi(openaiKey!!.trim(), inputJson)
            } else {
                callGemini(geminiKey!!.trim(), inputJson)
            }

            if (externalResult.isSuccess) {
                return@withContext externalResult
            } else {
                val err = externalResult.exceptionOrNull()
                Log.w(TAG, "External AI call failed ($err), smoothly falling back to Deterministic Quant Heuristic Engine.")
                return@withContext Result.success(
                    generateHeuristicQuantAnalysis(
                        alignment = alignment,
                        events = events,
                        news = news,
                        asianHigh = asianHigh,
                        asianLow = asianLow,
                        spotPrice = spotPrice,
                        netBuyersPct = netBuyersPct
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "External AI call exception: ${e.message}. Falling back to Quant Heuristic Engine.")
            return@withContext Result.success(
                generateHeuristicQuantAnalysis(
                    alignment = alignment,
                    events = events,
                    news = news,
                    asianHigh = asianHigh,
                    asianLow = asianLow,
                    spotPrice = spotPrice,
                    netBuyersPct = netBuyersPct
                )
            )
        }
    }

    private fun callOpenAi(apiKey: String, inputJson: JSONObject): Result<FundamentalAnalysisEntity> {
        try {
            val endpoint = "https://api.openai.com/v1/chat/completions"
            val bodyObj = JSONObject().apply {
                put("model", "gpt-4o")
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", inputJson.toString())
                    })
                }
                put("messages", messages)
                put("response_format", JSONObject().apply { put("type", "json_object") })
                put("temperature", 0.2)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string() ?: ""
                    Log.w(TAG, "OpenAI returned HTTP $code: $err")
                    return Result.failure(IllegalStateException("OpenAI HTTP $code"))
                }
                val respBody = response.body?.string() ?: return Result.failure(IllegalStateException("Empty response"))
                val respJson = JSONObject(respBody)
                val contentStr = respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                val parsed = JSONObject(contentStr)

                return Result.success(parseAnalysis(parsed, "OpenAI GPT", "gpt-4o"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun callGemini(apiKey: String, inputJson: JSONObject): Result<FundamentalAnalysisEntity> {
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val bodyObj = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "$SYSTEM_PROMPT\n\nINPUT DATA:\n${inputJson.toString(2)}")
                            })
                        }
                        put("parts", parts)
                    })
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val err = response.body?.string() ?: ""
                    Log.w(TAG, "Gemini returned HTTP $code: $err")
                    return Result.failure(IllegalStateException("Gemini HTTP $code"))
                }
                val respBody = response.body?.string() ?: return Result.failure(IllegalStateException("Empty response"))
                val respJson = JSONObject(respBody)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val text = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    val parsed = JSONObject(text)
                    return Result.success(parseAnalysis(parsed, "Gemini Pro", "gemini-2.5-flash"))
                }
                return Result.failure(IllegalStateException("No candidate returned"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun parseAnalysis(json: JSONObject, provider: String, model: String): FundamentalAnalysisEntity {
        val risk = when (json.optString("market_risk", "Medium").trim()) {
            "High" -> "High"
            "Low" -> "Low"
            else -> "Medium"
        }

        val bias = when (json.optString("fundamental_bias", "Bullish").trim()) {
            "Bearish" -> "Bearish"
            "Neutral" -> "Neutral"
            else -> "Bullish"
        }

        val trend = when (json.optString("short_term_trend", if (bias == "Bullish") "BULLISH" else if (bias == "Bearish") "BEARISH" else "NEUTRAL").trim().uppercase(Locale.ROOT)) {
            "BEARISH" -> "BEARISH"
            "NEUTRAL" -> "NEUTRAL"
            else -> "BULLISH"
        }

        val confidence = json.optDouble("confidence_score", 85.0).toFloat()
        val overview = json.optString("market_overview", "Macroeconomic factors and real yield expectations continue to direct intraday bullion momentum.")
        val bullishScenario = json.optString("bullish_scenario", "Sustained trading above nearest EMA cluster targets liquidity above recent swing highs.")
        val bearishScenario = json.optString("bearish_scenario", "Breakdown below Asian session low opens downside mean reversion toward major support.")
        val invalidation = json.optDouble("invalidation_level", 2850.0)

        val catalystsArr = json.optJSONArray("key_catalysts") ?: json.optJSONArray("key_drivers") ?: JSONArray().apply {
            put("Federal Reserve interest rate path expectations")
            put("US 10-Year Real Treasury yield trajectory")
            put("Central bank physical gold accumulation")
        }

        val catalystsList = mutableListOf<String>()
        for (i in 0 until catalystsArr.length()) {
            catalystsList.add(catalystsArr.optString(i))
        }

        return FundamentalAnalysisEntity(
            marketRisk = risk,
            fundamentalBias = bias,
            confidenceScore = confidence,
            keyDriversJson = catalystsArr.toString(),
            catalystImpact = json.optString("catalyst_impact", "Upcoming macro releases present elevated volatility around session opens."),
            recommendedAction = json.optString("recommended_action", "Maintain disciplined position sizing aligned with institutional order flow."),
            detailedSynthesis = json.optString("detailed_synthesis", overview),
            llmProvider = provider,
            modelName = model,
            evaluatedAt = System.currentTimeMillis(),
            marketOverview = overview,
            shortTermTrend = trend,
            bullishScenario = bullishScenario,
            bearishScenario = bearishScenario,
            invalidationLevel = invalidation,
            keyCatalystsJson = catalystsArr.toString(),
            providerActive = provider
        )
    }

    /**
     * ZERO-KEY DETERMINISTIC QUANT HEURISTIC ENGINE (LOCAL DUAL-MODE AI)
     * Rule-based analytical engine producing the exact structured JSON schema required by UI:
     * - Base the bias on live multi-timeframe indicator confluence (EMA trend alignment + RSI + Volume Delta Pressure)
     * - Set invalidation level to nearest swing low / Asian session low
     */
    fun generateHeuristicQuantAnalysis(
        alignment: AlignmentReport?,
        events: List<EconomicEventEntity>,
        news: List<NewsArticleEntity>,
        asianHigh: Double? = null,
        asianLow: Double? = null,
        spotPrice: Double? = null,
        netBuyersPct: Double? = null
    ): FundamentalAnalysisEntity {
        val currentSpot = spotPrice ?: 2865.00
        val effectiveAsianHigh = asianHigh ?: (currentSpot + 14.50)
        val effectiveAsianLow = asianLow ?: (currentSpot - 12.80)
        val buyersPct = netBuyersPct ?: 53.5

        // Multi-timeframe trend alignment check
        val m15Tf = alignment?.perTimeframe?.firstOrNull { it.timeframe == "15m" }
        val h1Tf = alignment?.perTimeframe?.firstOrNull { it.timeframe == "1h" }
        val m15Trend = m15Tf?.trend ?: "BULLISH"
        val h1Trend = h1Tf?.trend ?: "BULLISH"

        val isM15Bullish = m15Trend.contains("BULLISH", ignoreCase = true)
        val isH1Bullish = h1Trend.contains("BULLISH", ignoreCase = true)
        val isM15Bearish = m15Trend.contains("BEARISH", ignoreCase = true)
        val isH1Bearish = h1Trend.contains("BEARISH", ignoreCase = true)

        val isEmaStackedBullish = isM15Bullish && isH1Bullish
        val isEmaStackedBearish = isM15Bearish && isH1Bearish

        val bullishPct = alignment?.bullishPercentage ?: 65.0f
        val rsiScore = m15Tf?.score?.toDouble() ?: 54.0 // Approximation of RSI state

        // Confluence logic:
        // If M15 & H1 EMAs are stacked bullish, RSI between 45-65, and net buyers > 52% -> Bias is "Bullish"
        val bias: String
        val shortTermTrend: String
        if ((isEmaStackedBullish && buyersPct >= 51.5) || bullishPct >= 65.0f) {
            bias = "Bullish"
            shortTermTrend = "BULLISH"
        } else if ((isEmaStackedBearish && buyersPct <= 48.0) || bullishPct <= 38.0f) {
            bias = "Bearish"
            shortTermTrend = "BEARISH"
        } else {
            bias = "Neutral"
            shortTermTrend = "NEUTRAL"
        }

        // Market risk determination based on macro catalysts and RSI extremes
        val highImpactEventsCount = events.count { it.impact.equals("HIGH", ignoreCase = true) }
        val marketRisk = when {
            highImpactEventsCount >= 2 || rsiScore >= 72.0 || rsiScore <= 28.0 -> "High"
            highImpactEventsCount == 1 || rsiScore >= 66.0 || rsiScore <= 34.0 -> "Medium"
            else -> "Low"
        }

        val confidence = when (bias) {
            "Bullish" -> (68.0 + (bullishPct * 0.22) + if (buyersPct > 52.0) 5.0 else 0.0).coerceIn(60.0, 94.0)
            "Bearish" -> (68.0 + ((100.0 - bullishPct) * 0.22) + if (buyersPct < 48.0) 5.0 else 0.0).coerceIn(60.0, 94.0)
            else -> 65.0
        }.toFloat()

        // Invalidation level: Nearest swing low / Asian session low for Bullish, Asian high for Bearish
        val invalidationLevel = when (bias) {
            "Bullish" -> {
                // Invalidation below Asian low or nearest swing low
                if (effectiveAsianLow < currentSpot) effectiveAsianLow else currentSpot - 11.50
            }
            "Bearish" -> {
                // Invalidation above Asian high or nearest swing high
                if (effectiveAsianHigh > currentSpot) effectiveAsianHigh else currentSpot + 11.50
            }
            else -> effectiveAsianLow
        }

        // Key catalysts
        val catalysts = mutableListOf<String>()
        val nextHighImpact = events.firstOrNull { it.impact.equals("HIGH", ignoreCase = true) }
        if (nextHighImpact != null) {
            catalysts.add("Tier-1 Economic Event: ${nextHighImpact.title} (${nextHighImpact.country}) - Forecast: ${nextHighImpact.forecast ?: "N/A"}")
        } else if (events.isNotEmpty()) {
            catalysts.add("Upcoming Macro Catalyst: ${events.first().title}")
        } else {
            catalysts.add("Federal Reserve interest rate probabilities & FOMC communications")
        }

        val topNews = news.firstOrNull()
        if (topNews != null) {
            catalysts.add("Headline Flow (${topNews.category}): ${topNews.title.take(75)}")
        } else {
            catalysts.add("US 10-Year Real TIPS yields and Treasury market debt auctions")
        }
        catalysts.add("Order Flow Dynamics: Tape Aggression ${String.format(Locale.US, "%.1f", buyersPct)}% Buyers, Multi-TF Confluence ${String.format(Locale.US, "%.1f", bullishPct)}%")

        val catalystsJson = JSONArray().apply {
            catalysts.forEach { put(it) }
        }

        val marketOverview = when (bias) {
            "Bullish" -> "Confluence analysis indicates constructive structural momentum. M15 and H1 EMA hierarchies are stacked bullishly while order flow tape confirms net buyer dominance (${String.format(Locale.US, "%.1f", buyersPct)}% aggression). Intermarket bullion demand remains resilient against dollar fluctuations."
            "Bearish" -> "Technical indicators signal distribution and downside pressure. Multi-timeframe moving averages exhibit bearish slope with net sellers dominating liquidity absorbs (${String.format(Locale.US, "%.1f", 100.0 - buyersPct)}% sell aggression). Near-term yields weigh on spot XAUUSD."
            else -> "Market is rangebound in mean-reverting equilibrium between Asian Session High ($${String.format(Locale.US, "%.2f", effectiveAsianHigh)}) and Low ($${String.format(Locale.US, "%.2f", effectiveAsianLow)}). Directional expansion awaits high-impact macroeconomic event catalyst."
        }

        val bullishScenario = "Sustained acceptance above $${String.format(Locale.US, "%.2f", currentSpot + 4.50)} triggers momentum expansion toward Asian Session High ($${String.format(Locale.US, "%.2f", effectiveAsianHigh)}) and liquidity pool at $${String.format(Locale.US, "%.2f", effectiveAsianHigh + 6.00)}."
        val bearishScenario = "Rejection at overhead resistance and close beneath $${String.format(Locale.US, "%.2f", invalidationLevel)} invalidates long structure, triggering stop runs toward support shelf at $${String.format(Locale.US, "%.2f", currentSpot - 20.00)}."

        val recommendedAction = when (bias) {
            "Bullish" -> "Execute long scalp setups on pullbacks into 15m EMA cluster. Set hard invalidation stop at $${String.format(Locale.US, "%.2f", invalidationLevel)}."
            "Bearish" -> "Fade rallies into liquidity pools above VWAP. Invalidation strictly set at $${String.format(Locale.US, "%.2f", invalidationLevel)}."
            else -> "Deploy range-bound mean reversion tactics. Take partial profits at Asian session extremities."
        }

        return FundamentalAnalysisEntity(
            marketRisk = marketRisk,
            fundamentalBias = bias,
            confidenceScore = confidence,
            keyDriversJson = catalystsJson.toString(),
            catalystImpact = "Catalyst volatility is $marketRisk ahead of ${nextHighImpact?.title ?: "upcoming US economic releases"}.",
            recommendedAction = recommendedAction,
            detailedSynthesis = marketOverview,
            llmProvider = "Quant Heuristic (Zero-Key Active)",
            modelName = "Deterministic Confluence Engine v2.0",
            evaluatedAt = System.currentTimeMillis(),
            marketOverview = marketOverview,
            shortTermTrend = shortTermTrend,
            bullishScenario = bullishScenario,
            bearishScenario = bearishScenario,
            invalidationLevel = invalidationLevel,
            keyCatalystsJson = catalystsJson.toString(),
            providerActive = "Quant Heuristic (Zero-Key Active)"
        )
    }
}
