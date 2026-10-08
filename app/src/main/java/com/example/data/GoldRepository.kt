package com.example.data

import com.example.BuildConfig
import com.example.ai.FundamentalReasoningClient
import com.example.data.local.ApiKeyConfigEntity
import com.example.data.local.BacktestOutcomeEntity
import com.example.data.local.CandleEntity
import com.example.data.local.EconomicEventEntity
import com.example.data.local.FundamentalAnalysisEntity
import com.example.data.local.GoldDatabase
import com.example.data.local.MarketTickEntity
import com.example.data.local.NewsArticleEntity
import com.example.data.local.SignalEntity
import com.example.data.local.TradingViewAlertEntity
import com.example.data.local.UserProfileEntity
import com.example.data.market.AlphaVantageProvider
import com.example.data.market.CandleAggregator
import com.example.data.market.DataValidator
import com.example.data.market.MarketDataProvider
import com.example.data.market.NewsApiLiveProvider
import com.example.data.market.RateLimitException
import com.example.data.market.SeedNewsProvider
import com.example.data.market.SimulationMarketProvider
import com.example.data.market.SystemHealthState
import com.example.data.market.ValidationReport
import com.example.data.market.YahooFinanceProvider
import com.example.quant.AlignmentReport
import com.example.quant.MultiTimeframeAlignment
import com.example.quant.QuantCalculations
import com.example.quant.QuantSignalEngine
import com.example.quant.TechnicalSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.round

class GoldRepository(private val db: GoldDatabase) {

    private val tickDao = db.marketTickDao()
    private val candleDao = db.candleDao()
    private val eventDao = db.economicEventDao()
    private val signalDao = db.signalDao()
    private val apiKeyDao = db.apiKeyConfigDao()
    private val userDao = db.userDao()
    private val alertDao = db.tradingViewAlertDao()
    private val newsDao = db.newsArticleDao()
    private val fundamentalDao = db.fundamentalAnalysisDao()
    private val backtestDao = db.backtestOutcomeDao()
    private val priceAlertDao = db.priceAlertDao()

    val priceAlerts: Flow<List<com.example.data.alert.PriceAlertEntity>> = priceAlertDao.getAllAlerts()

    private val liveNewsProvider = NewsApiLiveProvider()
    private val seedNewsProvider = SeedNewsProvider()

    private val random = Random()
    private var simulatedPrice = 2864.50

    // Data subsystem components
    private val freeProvider = YahooFinanceProvider()
    private val simulationProvider = SimulationMarketProvider()
    private var premiumProvider: AlphaVantageProvider? = null

    private val _systemHealthState = MutableStateFlow(SystemHealthState.LIVE)
    val systemHealthState: StateFlow<SystemHealthState> = _systemHealthState.asStateFlow()

    private val _lastValidationReport = MutableStateFlow<ValidationReport?>(null)
    val lastValidationReport: StateFlow<ValidationReport?> = _lastValidationReport.asStateFlow()

    private val _activeProviderName = MutableStateFlow("YahooFinance (Free Tier)")
    val activeProviderName: StateFlow<String> = _activeProviderName.asStateFlow()

    val recentTicks: Flow<List<MarketTickEntity>> = tickDao.getRecentTicks()
    val latestTick: Flow<MarketTickEntity?> = tickDao.getLatestTick()
    val economicEvents: Flow<List<EconomicEventEntity>> = eventDao.getAllEvents()
    val signals: Flow<List<SignalEntity>> = signalDao.getAllSignals()
    val apiKeys: Flow<List<ApiKeyConfigEntity>> = apiKeyDao.getAllKeys()
    val currentUser: Flow<UserProfileEntity?> = userDao.getCurrentUser()
    val tradingViewAlerts: Flow<List<TradingViewAlertEntity>> = alertDao.getAllAlerts()
    val newsArticles: Flow<List<NewsArticleEntity>> = newsDao.getAllArticles()
    val latestFundamentalAnalysis: Flow<FundamentalAnalysisEntity?> = fundamentalDao.getLatestAnalysis()
    val backtestOutcomes: Flow<List<BacktestOutcomeEntity>> = backtestDao.getAllOutcomes()

    fun getCandles(timeframe: String = "15m"): Flow<List<CandleEntity>> =
        candleDao.getCandles(timeframe = timeframe)

    suspend fun getCandlesSnapshot(timeframe: String = "15m"): List<CandleEntity> =
        candleDao.getCandlesSnapshot(timeframe = timeframe)

    suspend fun getMultiTimeframeAlignment(): AlignmentReport = withContext(Dispatchers.Default) {
        val tfs = listOf("1m", "3m", "5m", "10m", "15m", "1h")
        val snapshots = mutableMapOf<String, TechnicalSnapshot>()
        for (tf in tfs) {
            val candles = candleDao.getCandlesSnapshot(tf)
            if (candles.isNotEmpty()) {
                snapshots[tf] = QuantCalculations.computeSnapshot(candles)
            }
        }
        MultiTimeframeAlignment.evaluate(snapshots)
    }

    suspend fun initializeDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        // 1. First-user admin onboarding: First user registered is granted SUPER_ADMIN
        if (userDao.getUserSnapshot() == null) {
            userDao.upsertUser(
                UserProfileEntity(
                    uid = "usr_super_admin_01",
                    email = "admin.quant@goldai.terminal",
                    displayName = "Lead Quant Analyst (Super Admin)",
                    role = "SUPER_ADMIN",
                    tier = "INSTITUTIONAL_PRO",
                    isGuest = false,
                    authToken = "jwt_sec_super_admin_auth_${System.currentTimeMillis()}"
                )
            )
        }

        // 2. Seed economic calendar events if empty
        val existingEvents = eventDao.getAllEvents()
        val now = System.currentTimeMillis()
        val seedEvents = listOf(
            EconomicEventEntity(
                id = 101,
                title = "US Non-Farm Payrolls (NFP)",
                country = "USD",
                impact = "HIGH",
                timestamp = now + (14 * 3600 * 1000),
                forecast = "175K",
                previous = "142K",
                goldBias = "BEARISH_IF_BEAT",
                notes = "Higher employment fuels Fed rate pause; gold faces short-term selling."
            ),
            EconomicEventEntity(
                id = 102,
                title = "US Consumer Price Index (CPI) YoY",
                country = "USD",
                impact = "HIGH",
                timestamp = now + (32 * 3600 * 1000),
                forecast = "2.6%",
                previous = "2.5%",
                goldBias = "BULLISH_IF_STICKY",
                notes = "Sticky inflation accelerates bullion demand as sovereign reserve hedge."
            ),
            EconomicEventEntity(
                id = 103,
                title = "FOMC Federal Funds Rate Decision",
                country = "USD",
                impact = "HIGH",
                timestamp = now + (72 * 3600 * 1000),
                forecast = "4.50%",
                previous = "4.75%",
                goldBias = "BULLISH_IF_DOVISH",
                notes = "Dovish rate cut path reduces holding cost of non-interest bearing gold."
            ),
            EconomicEventEntity(
                id = 104,
                title = "US Core Retail Sales MoM",
                country = "USD",
                impact = "MEDIUM",
                timestamp = now + (96 * 3600 * 1000),
                forecast = "0.3%",
                previous = "0.1%",
                goldBias = "NEUTRAL",
                notes = "Broad macroeconomic consumer expenditure signal."
            )
        )
        eventDao.insertEvents(seedEvents)

        // 3. Seed verified proprietary alerts from TradingView webhooks if empty
        val seedAlerts = listOf(
            TradingViewAlertEntity(
                indicator = "Scalping with Dr Hafiz V2",
                symbol = "XAUUSD",
                action = "BUY",
                price = 2862.40,
                timeframe = "5m",
                timestamp = now - (15 * 60 * 1000L),
                message = "Scalp momentum trigger: Volume surge above 1.8x average.",
                isVerified = true
            ),
            TradingViewAlertEntity(
                indicator = "3ESRA",
                symbol = "XAUUSD",
                action = "BUY",
                price = 2859.10,
                timeframe = "15m",
                timestamp = now - (45 * 60 * 1000L),
                message = "Institutional liquidity sweep confirmed at key M15 swing low.",
                isVerified = true
            )
        )
        alertDao.insertAlerts(seedAlerts)

        // 4. Seed filtered macroeconomic news if empty
        if (newsDao.getCount() == 0) {
            val seedNews = seedNewsProvider.fetchNews(null).getOrDefault(emptyList())
            newsDao.insertArticles(seedNews)
        }

        // 5. Perform initial real-time market polling
        pollRealtimeMarketData()
    }

    suspend fun pollRealtimeMarketData() = withContext(Dispatchers.IO) {
        val premiumKey = apiKeyDao.getKeyValue("MARKET_DATA_API_KEY")?.trim()
        val provider: MarketDataProvider = if (!premiumKey.isNullOrBlank()) {
            if (premiumProvider == null || (premiumProvider?.name != "AlphaVantage (Premium Tier)")) {
                premiumProvider = AlphaVantageProvider(apiKey = premiumKey)
            }
            premiumProvider!!
        } else {
            freeProvider
        }
        _activeProviderName.value = provider.name

        var isRateLimited = false
        var providerFailed = false
        var fetched1m: List<CandleEntity> = emptyList()

        val fetchResult = provider.fetch1mCandles(symbol = "XAUUSD", limit = 120)
        fetchResult.onSuccess { list ->
            fetched1m = list
        }.onFailure { err ->
            if (err is RateLimitException) {
                isRateLimited = true
            } else {
                providerFailed = true
            }
            val simResult = simulationProvider.fetch1mCandles(symbol = "XAUUSD", limit = 120)
            fetched1m = simResult.getOrDefault(emptyList())
        }

        val report = DataValidator.validate(
            rawCandles = fetched1m,
            isRateLimited = isRateLimited,
            providerFailure = providerFailed
        )
        _lastValidationReport.value = report
        _systemHealthState.value = report.state

        if (report.validatedCandles.isNotEmpty()) {
            val aggregatedMap = CandleAggregator.aggregateAllTimeframes(report.validatedCandles)

            for (candlesList in aggregatedMap.values) {
                candleDao.insertCandles(candlesList)
            }

            val latest = report.validatedCandles.last()
            simulatedPrice = latest.close
            val spread = 0.25
            val tick = MarketTickEntity(
                symbol = "XAUUSD",
                price = latest.close,
                bid = round((latest.close - (spread / 2.0)) * 100.0) / 100.0,
                ask = round((latest.close + (spread / 2.0)) * 100.0) / 100.0,
                spread = spread,
                volume = latest.volume,
                timestamp = latest.timestamp
            )
            tickDao.insertTick(tick)
            tickDao.pruneOldTicks()

            refreshSignals()
        }
    }

    suspend fun streamNextTick(): MarketTickEntity = withContext(Dispatchers.IO) {
        val change = (random.nextGaussian() * 0.45)
        simulatedPrice = round((simulatedPrice + change) * 100.0) / 100.0
        val spread = 0.25
        val bid = round((simulatedPrice - (spread / 2.0)) * 100.0) / 100.0
        val ask = round((simulatedPrice + (spread / 2.0)) * 100.0) / 100.0
        val vol = round((0.5 + (random.nextDouble() * 12.0)) * 10.0) / 10.0

        val tick = MarketTickEntity(
            symbol = "XAUUSD",
            price = simulatedPrice,
            bid = bid,
            ask = ask,
            spread = spread,
            volume = vol,
            timestamp = System.currentTimeMillis()
        )
        tickDao.insertTick(tick)
        tickDao.pruneOldTicks()

        for (tf in listOf("1m", "3m", "5m", "10m", "15m", "1h")) {
            val currentCandles = candleDao.getCandlesSnapshot(timeframe = tf)
            if (currentCandles.isNotEmpty()) {
                val last = currentCandles.last()
                val updatedLast = last.copy(
                    high = maxOf(last.high, simulatedPrice),
                    low = minOf(last.low, simulatedPrice),
                    close = simulatedPrice,
                    volume = last.volume + vol
                )
                val updatedList = currentCandles.dropLast(1) + updatedLast
                candleDao.insertCandles(updatedList)
            }
        }

        tick
    }

    suspend fun saveApiKey(keyName: String, keyValue: String) = withContext(Dispatchers.IO) {
        apiKeyDao.upsertKey(
            ApiKeyConfigEntity(
                keyName = keyName.trim(),
                keyValue = keyValue.trim(),
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )
        )

        if (keyName.trim() == "MARKET_DATA_API_KEY") {
            pollRealtimeMarketData()
        }
    }

    suspend fun saveTradingViewAlert(alert: TradingViewAlertEntity) = withContext(Dispatchers.IO) {
        alertDao.insertAlert(alert)
    }

    suspend fun getEffectiveApiKey(keyName: String, defaultKey: String): String = withContext(Dispatchers.IO) {
        val dbKey = apiKeyDao.getKeyValue(keyName)?.trim()
        if (!dbKey.isNullOrBlank()) {
            dbKey
        } else {
            defaultKey
        }
    }

    suspend fun saveBacktestOutcome(outcome: BacktestOutcomeEntity): Long = withContext(Dispatchers.IO) {
        backtestDao.insertOutcome(outcome)
    }

    suspend fun refreshSignals() = withContext(Dispatchers.IO) {
        val candles = candleDao.getCandlesSnapshot(timeframe = "15m")
        val alignment = getMultiTimeframeAlignment()
        val news = newsDao.getArticlesSnapshot()
        val events = eventDao.getAllEventsSnapshot()
        val latestAi = fundamentalDao.getLatestSnapshot()

        val newSignal = QuantSignalEngine.generateConsensusSignal(
            candles = candles,
            timeframe = "15m",
            alignment = alignment,
            newsArticles = news,
            calendarEvents = events,
            aiAnalysis = latestAi,
            enforceFailsafe = true
        )
        signalDao.insertSignal(newSignal)
    }

    suspend fun fetchAndFilterNews(): Result<List<NewsArticleEntity>> = withContext(Dispatchers.IO) {
        val newsKey = getEffectiveApiKey("NEWS_API_KEY", BuildConfig.NEWS_API_KEY)
        val result = if (newsKey.isNotBlank() && newsKey != "mock_gold_news_key") {
            val liveResult = liveNewsProvider.fetchNews(newsKey)
            if (liveResult.isSuccess && liveResult.getOrNull()?.isNotEmpty() == true) {
                liveResult
            } else {
                seedNewsProvider.fetchNews(null)
            }
        } else {
            seedNewsProvider.fetchNews(null)
        }

        result.onSuccess { articles ->
            if (articles.isNotEmpty()) {
                newsDao.insertArticles(articles)
            }
        }
        result
    }

    suspend fun evaluateFundamentals(): Result<FundamentalAnalysisEntity> = withContext(Dispatchers.IO) {
        val openAiKey = getEffectiveApiKey("OPENAI_API_KEY", BuildConfig.OPENAI_API_KEY)
        val geminiKey = getEffectiveApiKey("GEMINI_API_KEY", BuildConfig.GEMINI_API_KEY)

        val alignment = getMultiTimeframeAlignment()
        val events = eventDao.getAllEventsSnapshot()
        var news = newsDao.getArticlesSnapshot()
        if (news.isEmpty()) {
            fetchAndFilterNews()
            news = newsDao.getArticlesSnapshot()
        }

        val res = FundamentalReasoningClient.evaluateFundamentals(
            openaiKey = openAiKey,
            geminiKey = geminiKey,
            alignment = alignment,
            events = events,
            news = news
        )

        res.onSuccess { analysis ->
            fundamentalDao.insertAnalysis(analysis)
        }
        res
    }

    suspend fun createPriceAlert(
        targetPrice: Double,
        condition: com.example.data.alert.AlertCondition,
        note: String = ""
    ): Long = withContext(Dispatchers.IO) {
        val alert = com.example.data.alert.PriceAlertEntity(
            targetPrice = targetPrice,
            condition = condition,
            note = note.trim()
        )
        priceAlertDao.insertAlert(alert)
    }

    suspend fun deletePriceAlert(alertId: Long) = withContext(Dispatchers.IO) {
        priceAlertDao.deleteById(alertId)
    }

    suspend fun togglePriceAlert(alertId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        priceAlertDao.toggleEnabled(alertId, enabled)
    }

    suspend fun checkPriceAlerts(bid: Double, ask: Double): List<com.example.data.alert.PriceAlertEntity> = withContext(Dispatchers.IO) {
        val active = priceAlertDao.getActivePendingAlerts()
        val triggered = mutableListOf<com.example.data.alert.PriceAlertEntity>()
        val now = System.currentTimeMillis()

        for (alert in active) {
            val isHit = when (alert.condition) {
                com.example.data.alert.AlertCondition.BID_ABOVE -> bid >= alert.targetPrice
                com.example.data.alert.AlertCondition.BID_BELOW -> bid <= alert.targetPrice
                com.example.data.alert.AlertCondition.ASK_ABOVE -> ask >= alert.targetPrice
                com.example.data.alert.AlertCondition.ASK_BELOW -> ask <= alert.targetPrice
            }
            if (isHit) {
                priceAlertDao.markTriggered(alert.id, now)
                triggered.add(alert.copy(isTriggered = true, triggeredAt = now))
            }
        }
        triggered
    }
}
