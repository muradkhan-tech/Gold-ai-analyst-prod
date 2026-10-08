package com.example.data.market

import com.example.data.local.NewsArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface NewsProvider {
    val name: String
    suspend fun fetchNews(apiKey: String?): Result<List<NewsArticleEntity>>
}

class NewsApiLiveProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) : NewsProvider {

    override val name: String = "NewsAPI (Live Cloud)"

    override suspend fun fetchNews(apiKey: String?): Result<List<NewsArticleEntity>> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank() || apiKey == "mock_gold_news_key") {
            return@withContext Result.failure(IllegalStateException("NEWS_API_KEY is not configured"))
        }

        try {
            val query = "(Gold OR XAUUSD) AND (Fed OR Inflation OR Yields OR Dollar)"
            val url = "https://newsapi.org/v2/everything?q=$query&language=en&sortBy=publishedAt&pageSize=25&apiKey=${apiKey.trim()}"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "GoldAIAnalyst/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("NewsAPI returned code ${response.code}"))
                }

                val bodyStr = response.body?.string() ?: return@withContext Result.failure(IllegalStateException("Empty response"))
                val json = JSONObject(bodyStr)
                val articlesArr = json.optJSONArray("articles") ?: return@withContext Result.success(emptyList())

                val list = mutableListOf<NewsArticleEntity>()
                val now = System.currentTimeMillis()

                for (i in 0 until articlesArr.length()) {
                    val obj = articlesArr.getJSONObject(i)
                    val title = obj.optString("title", "")
                    val summary = obj.optString("description", "")
                    val articleUrl = if (obj.has("url") && !obj.isNull("url")) obj.getString("url") else null

                    val sourceObj = obj.optJSONObject("source")
                    val sourceName = if (sourceObj != null && !sourceObj.isNull("name")) sourceObj.optString("name", "NewsAPI") else "NewsAPI"

                    val filterResult = NewsFilter.filterAndCategorize(title, summary)
                    if (filterResult != null) {
                        list.add(
                            NewsArticleEntity(
                                title = title,
                                source = sourceName,
                                url = articleUrl,
                                summary = summary,
                                category = filterResult.category,
                                sentiment = filterResult.sentiment,
                                relevanceScore = filterResult.relevanceScore,
                                publishedAt = now - (i * 3600 * 1000L)
                            )
                        )
                    }
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SeedNewsProvider : NewsProvider {
    override val name: String = "Curated Institutional Macro Feed"

    override suspend fun fetchNews(apiKey: String?): Result<List<NewsArticleEntity>> = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val rawSeed = listOf(
            Triple(
                "Federal Reserve Signals Data-Dependent Path as 10-Year Treasury Yields Soften Below 4.15%",
                "FOMC officials reiterated that sustained progress toward the 2% PCE inflation target could prompt further interest rate recalibration, supporting non-yielding bullion demand.",
                "Bloomberg Markets"
            ),
            Triple(
                "Central Banks Continue Unprecedented Gold Accumulation Amid Sovereign Reserve De-Dollarization",
                "Global central bank net gold purchases surged over 35 tons this month, establishing structural institutional support beneath spot XAUUSD as sovereign nations diversify away from the US Dollar.",
                "World Gold Council"
            ),
            Triple(
                "US Core CPI Inflation Expected at 2.6% YoY: Market Eyes Fed Rate Cut Probability",
                "Upcoming consumer price index release will test market pricing for a 25 bps rate cut. Sticky services inflation could temporarily boost the US Dollar Index (DXY) against gold.",
                "Reuters Financial"
            ),
            Triple(
                "US 10-Year Real Yields Compress: Real Rate Inversion Lifts Gold Bullion Sentiment",
                "TIPS breakeven rates indicate declining real yields, lowering the opportunity cost of holding physical bullion and fueling institutional ETF inflows.",
                "Wall Street Journal"
            ),
            Triple(
                "US Dollar Index (DXY) Consolidates at 104.20 Ahead of Non-Farm Payrolls Volatility",
                "Greenback movement remains rangebound as forex traders await labor market data. A softer payrolls print would weaken the dollar and propel gold toward new resistance levels.",
                "Financial Times"
            )
        )

        val articles = rawSeed.mapIndexed { idx, (title, summary, source) ->
            val filter = NewsFilter.filterAndCategorize(title, summary)
            NewsArticleEntity(
                title = title,
                source = source,
                url = "https://finance.yahoo.com/quote/GC=F",
                summary = summary,
                category = filter?.category ?: "GOLD",
                sentiment = filter?.sentiment ?: "BULLISH",
                relevanceScore = filter?.relevanceScore ?: 0.95f,
                publishedAt = now - (idx * 2 * 3600 * 1000L)
            )
        }

        Result.success(articles)
    }
}
