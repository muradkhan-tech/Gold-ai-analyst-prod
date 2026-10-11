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

import com.example.data.local.MarketTickEntity

interface MarketDataProvider {
    val name: String
    suspend fun fetch1mCandles(symbol: String = "XAUUSD", limit: Int = 120): Result<List<CandleEntity>>
}

class BinancePaxgProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) : MarketDataProvider {

    override val name: String = "Binance Spot (PAXGUSDT - 1:1 Physical Gold)"

    suspend fun fetchTicker(): Result<MarketTickEntity> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://api.binance.com/api/v3/ticker/bookTicker?symbol=PAXGUSDT",
            "https://data-api.binance.vision/api/v3/ticker/bookTicker?symbol=PAXGUSDT"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "GoldAiAnalyst/2.0").build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue
                val body = resp.body?.string() ?: continue
                val obj = JSONObject(body)
                val bid = obj.getDouble("bidPrice")
                val ask = obj.getDouble("askPrice")
                if (bid <= 0.0 || ask <= 0.0) continue
                val mid = round((bid + ask) / 2.0 * 100.0) / 100.0
                val rawSpread = round((ask - bid) * 100.0) / 100.0
                val spread = max(rawSpread, 0.05)
                val vol = round((obj.optDouble("bidQty", 1.0) + obj.optDouble("askQty", 1.0)) * 100.0) / 100.0

                return@withContext Result.success(
                    MarketTickEntity(
                        symbol = "XAUUSD",
                        price = mid,
                        bid = round(bid * 100.0) / 100.0,
                        ask = round(ask * 100.0) / 100.0,
                        spread = spread,
                        volume = vol,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                continue
            }
        }
        Result.failure(Exception("Binance PAXGUSDT bookTicker request failed"))
    }

    override suspend fun fetch1mCandles(symbol: String, limit: Int): Result<List<CandleEntity>> = withContext(Dispatchers.IO) {
        val urls = listOf(
            "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=1m&limit=$limit",
            "https://data-api.binance.vision/api/v3/klines?symbol=PAXGUSDT&interval=1m&limit=$limit"
        )
        for (url in urls) {
            try {
                val req = Request.Builder().url(url).header("User-Agent", "GoldAiAnalyst/2.0").build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) continue
                val body = resp.body?.string() ?: continue
                val arr = org.json.JSONArray(body)
                if (arr.length() == 0) continue

                val candles = mutableListOf<CandleEntity>()
                for (i in 0 until arr.length()) {
                    val kline = arr.getJSONArray(i)
                    val ts = kline.getLong(0)
                    val o = kline.getDouble(1)
                    val h = kline.getDouble(2)
                    val l = kline.getDouble(3)
                    val c = kline.getDouble(4)
                    val v = kline.getDouble(5)

                    candles.add(
                        CandleEntity(
                            symbol = symbol,
                            timeframe = "1m",
                            timestamp = ts,
                            open = round(o * 100.0) / 100.0,
                            high = round(h * 100.0) / 100.0,
                            low = round(l * 100.0) / 100.0,
                            close = round(c * 100.0) / 100.0,
                            volume = round(v * 100.0) / 100.0
                        )
                    )
                }
                if (candles.isNotEmpty()) {
                    return@withContext Result.success(candles)
                }
            } catch (e: Exception) {
                continue
            }
        }
        Result.failure(Exception("Binance PAXGUSDT klines request failed"))
    }
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
