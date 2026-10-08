package com.example.data.market

import com.example.data.local.CandleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

interface MarketDataProvider {
    val name: String
    suspend fun fetch1mCandles(symbol: String = "XAUUSD", limit: Int = 120): Result<List<CandleEntity>>
}

class YahooFinanceProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : MarketDataProvider {

    override val name: String = "YahooFinance (Free Tier)"

    override suspend fun fetch1mCandles(symbol: String, limit: Int): Result<List<CandleEntity>> = withContext(Dispatchers.IO) {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=1m&range=1d"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0)")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.code == 429) {
                return@withContext Result.failure(
                    RateLimitException("Yahoo Finance rate limited (HTTP 429). Please provide a premium API key.")
                )
            }
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Yahoo Finance HTTP error: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val root = JSONObject(body)
            val chart = root.getJSONObject("chart")
            val results = chart.getJSONArray("result")
            if (results.length() == 0) {
                return@withContext Result.failure(Exception("No chart result found"))
            }

            val resultObj = results.getJSONObject(0)
            val timestampArray = resultObj.getJSONArray("timestamp")
            val indicators = resultObj.getJSONObject("indicators")
            val quotes = indicators.getJSONArray("quote")
            val quoteObj = quotes.getJSONObject(0)

            val opens = quoteObj.getJSONArray("open")
            val highs = quoteObj.getJSONArray("high")
            val lows = quoteObj.getJSONArray("low")
            val closes = quoteObj.getJSONArray("close")
            val volumes = quoteObj.optJSONArray("volume")

            val candles = mutableListOf<CandleEntity>()
            val len = timestampArray.length()
            val startIdx = max(0, len - limit)

            for (i in startIdx until len) {
                if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) continue

                val ts = timestampArray.getLong(i) * 1000L // UTC timestamp in milliseconds
                val o = roundPrice(opens.getDouble(i))
                val h = roundPrice(highs.getDouble(i))
                val l = roundPrice(lows.getDouble(i))
                val c = roundPrice(closes.getDouble(i))
                val v = if (volumes != null && !volumes.isNull(i)) volumes.getDouble(i) else 100.0

                candles.add(
                    CandleEntity(
                        symbol = symbol,
                        timeframe = "1m",
                        timestamp = ts,
                        open = o,
                        high = max(h, max(o, c)),
                        low = min(l, min(o, c)),
                        close = c,
                        volume = v
                    )
                )
            }

            if (candles.isEmpty()) {
                Result.failure(Exception("No valid candles parsed from Yahoo Finance response"))
            } else {
                Result.success(candles)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun roundPrice(v: Double): Double = round(v * 100.0) / 100.0
}

class AlphaVantageProvider(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) : MarketDataProvider {

    override val name: String = "AlphaVantage (Premium Tier)"

    override suspend fun fetch1mCandles(symbol: String, limit: Int): Result<List<CandleEntity>> = withContext(Dispatchers.IO) {
        val url = "https://www.alphavantage.co/query?function=FX_INTRADAY&from_symbol=XAU&to_symbol=USD&interval=1min&apikey=$apiKey"
        val request = Request.Builder().url(url).build()

        try {
            val response = client.newCall(request).execute()
            if (response.code == 429) {
                return@withContext Result.failure(RateLimitException("Alpha Vantage rate limit reached (HTTP 429)."))
            }
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
            val root = JSONObject(body)

            if (root.has("Note") || root.has("Information")) {
                val note = root.optString("Note", root.optString("Information"))
                return@withContext Result.failure(RateLimitException("Rate limit note: $note"))
            }

            val seriesKey = "Time Series FX (1min)"
            if (!root.has(seriesKey)) {
                return@withContext Result.failure(Exception("No intraday series returned from Alpha Vantage"))
            }

            val timeSeries = root.getJSONObject(seriesKey)
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")

            val candles = mutableListOf<CandleEntity>()
            val keys = timeSeries.keys()
            while (keys.hasNext()) {
                val timeStr = keys.next()
                val candleObj = timeSeries.getJSONObject(timeStr)
                val date = sdf.parse(timeStr) ?: continue
                val o = candleObj.getDouble("1. open")
                val h = candleObj.getDouble("2. high")
                val l = candleObj.getDouble("3. low")
                val c = candleObj.getDouble("4. close")

                candles.add(
                    CandleEntity(
                        symbol = symbol,
                        timeframe = "1m",
                        timestamp = date.time,
                        open = o,
                        high = h,
                        low = l,
                        close = c,
                        volume = 500.0
                    )
                )
            }

            candles.sortBy { it.timestamp }
            Result.success(candles.takeLast(limit))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SimulationMarketProvider(
    private var basePrice: Double = 2864.50
) : MarketDataProvider {

    override val name: String = "Realistic Simulation Fallback"
    private val random = Random()

    override suspend fun fetch1mCandles(symbol: String, limit: Int): Result<List<CandleEntity>> = withContext(Dispatchers.Default) {
        val candles = mutableListOf<CandleEntity>()
        val nowUtc = System.currentTimeMillis()
        val intervalMs = 60 * 1000L
        val startTime = nowUtc - (limit * intervalMs)

        var currPrice = basePrice - (limit * 0.12)
        for (i in 0 until limit) {
            val o = currPrice
            val delta = random.nextGaussian() * 0.75
            val c = round((o + delta) * 100.0) / 100.0
            val h = round((max(o, c) + abs(random.nextGaussian() * 0.6)) * 100.0) / 100.0
            val l = round((min(o, c) - abs(random.nextGaussian() * 0.6)) * 100.0) / 100.0
            val v = round((200.0 + (random.nextDouble() * 1200.0)) * 10.0) / 10.0

            candles.add(
                CandleEntity(
                    symbol = symbol,
                    timeframe = "1m",
                    timestamp = startTime + (i * intervalMs),
                    open = o,
                    high = h,
                    low = l,
                    close = c,
                    volume = v
                )
            )
            currPrice = c
        }
        basePrice = currPrice
        Result.success(candles)
    }
}

class RateLimitException(message: String) : Exception(message)
