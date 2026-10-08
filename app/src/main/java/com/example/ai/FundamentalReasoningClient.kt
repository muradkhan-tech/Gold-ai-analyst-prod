package com.example.ai

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

class MissingApiKeyException(message: String) : Exception(message)

object FundamentalReasoningClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """You are the Senior Institutional Gold Macro Economist & Quantitative Risk Officer for GOLD AI ANALYST.
You will receive structured JSON input containing:
1. Technical Analysis scores and timeframe alignment across 1m to 1h.
2. Upcoming macroeconomic catalysts (NFP, CPI, FOMC, Fed interest rates).
3. Filtered financial news strictly related to Gold, Federal Reserve, inflation, Treasury yields, and the US Dollar.

You MUST analyze the macroeconomic intermarket dynamics and return a STRICT JSON object with the following schema:
{
  "market_risk": "High" | "Medium" | "Low",
  "fundamental_bias": "Bullish" | "Bearish" | "Neutral",
  "confidence_score": 85.0,
  "key_drivers": [
    "Driver 1 explanation",
    "Driver 2 explanation",
    "Driver 3 explanation"
  ],
  "catalyst_impact": "Detailed assessment of near-term volatility catalysts",
  "recommended_action": "Tactical quant guidance",
  "detailed_synthesis": "Comprehensive multi-factor fundamental reasoning connecting technical alignment, Fed expectations, Treasury real yields, and physical bullion demand."
}

Rules:
- market_risk MUST be strictly one of: "High", "Medium", "Low".
- fundamental_bias MUST be strictly one of: "Bullish", "Bearish", "Neutral".
- Output valid JSON only."""

    suspend fun evaluateFundamentals(
        openaiKey: String?,
        geminiKey: String?,
        alignment: AlignmentReport?,
        events: List<EconomicEventEntity>,
        news: List<NewsArticleEntity>
    ): Result<FundamentalAnalysisEntity> = withContext(Dispatchers.IO) {
        val hasOpenAi = !openaiKey.isNullOrBlank() && openaiKey != "mock_openai_key"
        val hasGemini = !geminiKey.isNullOrBlank() && geminiKey != "MY_GEMINI_API_KEY"

        if (!hasOpenAi && !hasGemini) {
            // ZERO-CONFIGURATION FALLBACK:
            // Switch dynamically to built-in Quantitative Heuristic Reasoning Engine
            // that outputs the exact same structured schema without failing or making external LLM calls.
            return@withContext Result.success(
                generateHeuristicQuantAnalysis(alignment, events, news)
            )
        }

        // Construct structured input JSON
        val inputJson = JSONObject().apply {
            put("symbol", "XAUUSD")
            put("timestamp", System.currentTimeMillis())

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

        if (hasOpenAi) {
            callOpenAi(openaiKey!!.trim(), inputJson)
        } else {
            callGemini(geminiKey!!.trim(), inputJson)
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
                    val err = response.body?.string() ?: ""
                    return Result.failure(IllegalStateException("OpenAI returned ${response.code}: $err"))
                }
                val respBody = response.body?.string() ?: return Result.failure(IllegalStateException("Empty response"))
                val respJson = JSONObject(respBody)
                val contentStr = respJson.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                val parsed = JSONObject(contentStr)

                return Result.success(parseAnalysis(parsed, "OpenAI", "gpt-4o"))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private fun callGemini(apiKey: String, inputJson: JSONObject): Result<FundamentalAnalysisEntity> {
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey"
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
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingLevel", "high")
                    })
                })
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Content-Type", "application/json")
                .post(bodyObj.toString().toRequestBody("application/json".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return Result.failure(IllegalStateException("Gemini returned ${response.code}: $err"))
                }
                val respBody = response.body?.string() ?: return Result.failure(IllegalStateException("Empty response"))
                val respJson = JSONObject(respBody)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val text = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    val parsed = JSONObject(text)
                    return Result.success(parseAnalysis(parsed, "Gemini", "gemini-3.1-pro-preview"))
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

        val confidence = json.optDouble("confidence_score", 85.0).toFloat()
        val driversArr = json.optJSONArray("key_drivers") ?: JSONArray().apply {
            put("Federal Reserve dovish recalibration expectations")
            put("Falling US 10-Year real Treasury yields")
            put("Global central bank physical gold accumulation")
        }

        return FundamentalAnalysisEntity(
            marketRisk = risk,
            fundamentalBias = bias,
            confidenceScore = confidence,
            keyDriversJson = driversArr.toString(),
            catalystImpact = json.optString("catalyst_impact", "Near term volatility risk elevated ahead of upcoming inflation and employment data releases."),
            recommendedAction = json.optString("recommended_action", "Maintain long bias above key support levels with trailing invalidation stop."),
            detailedSynthesis = json.optString("detailed_synthesis", "Structural fundamental support remains strong for bullion as real rates soften and sovereign reserves expand."),
            llmProvider = provider,
            modelName = model,
            evaluatedAt = System.currentTimeMillis()
        )
    }

    fun generateHeuristicQuantAnalysis(
        alignment: AlignmentReport?,
        events: List<EconomicEventEntity>,
        news: List<NewsArticleEntity>
    ): FundamentalAnalysisEntity {
        val bullishPct = alignment?.bullishPercentage ?: 65.0f
        val bias = when {
            bullishPct >= 60.0f -> "Bullish"
            bullishPct <= 40.0f -> "Bearish"
            else -> "Neutral"
        }
        val highImpactEventsCount = events.count { it.impact == "HIGH" }
        val marketRisk = when {
            highImpactEventsCount >= 2 -> "High"
            highImpactEventsCount == 1 -> "Medium"
            else -> "Low"
        }
        val confidence = minOf(95.0f, maxOf(65.0f, 60.0f + (bullishPct * 0.3f)))

        val driversArr = JSONArray().apply {
            if (news.isNotEmpty()) {
                put("Macro News: ${news.first().title.take(70)}")
            } else {
                put("Federal Reserve interest rate trajectory and real yield dynamics")
            }
            if (events.isNotEmpty()) {
                put("Upcoming Catalyst: ${events.first().title} (${events.first().goldBias})")
            } else {
                put("Sovereign central bank bullion reserve diversification")
            }
            put("Multi-Timeframe Alignment: ${alignment?.overallStatus ?: "MODERATE_BULLISH"} (${String.format(java.util.Locale.US, "%.1f", bullishPct)}% Bullish consensus)")
        }

        return FundamentalAnalysisEntity(
            marketRisk = marketRisk,
            fundamentalBias = bias,
            confidenceScore = confidence,
            keyDriversJson = driversArr.toString(),
            catalystImpact = "Volatility catalyst risk is $marketRisk ahead of ${events.firstOrNull()?.title ?: "Tier-1 macroeconomic calendar releases"}.",
            recommendedAction = when (bias) {
                "Bullish" -> "Execute long scalps on intraday pullbacks into EMA support zones; trail stops closely."
                "Bearish" -> "Seek short continuation patterns upon breakdown of liquidity shelves; protect capital."
                else -> "Exercise patient risk management in mean-reverting ranges awaiting breakout confirmation."
            },
            detailedSynthesis = "Quantitative Heuristic Synthesis: Technical alignment indicates ${alignment?.overallStatus ?: "BULLISH"} consensus (${String.format(java.util.Locale.US, "%.1f", bullishPct)}% trend alignment). Macro intermarket drivers and bullion safe-haven flows validate a $bias fundamental posture with $marketRisk baseline volatility risk.",
            llmProvider = "Quantitative Heuristic Engine (Zero-Config Fallback)",
            modelName = "Built-in Indicator Consensus v1.0",
            evaluatedAt = System.currentTimeMillis()
        )
    }
}
