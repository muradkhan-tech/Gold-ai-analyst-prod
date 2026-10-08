package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "market_ticks",
    indices = [Index(value = ["symbol", "timestamp"])]
)
data class MarketTickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String = "XAUUSD",
    val price: Double,
    val bid: Double,
    val ask: Double,
    val spread: Double,
    val volume: Double,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "candles",
    indices = [Index(value = ["symbol", "timeframe", "timestamp"], unique = true)]
)
data class CandleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String = "XAUUSD",
    val timeframe: String, // 1m, 5m, 15m, 1h, 4h, 1d
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

@Entity(
    tableName = "economic_events",
    indices = [Index(value = ["timestamp", "impact"])]
)
data class EconomicEventEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val country: String = "USD",
    val impact: String, // HIGH, MEDIUM, LOW
    val timestamp: Long,
    val actual: String? = null,
    val forecast: String? = null,
    val previous: String? = null,
    val goldBias: String, // BULLISH, BEARISH, NEUTRAL
    val notes: String? = null
)

@Entity(
    tableName = "signals",
    indices = [Index(value = ["status", "timestamp"])]
)
data class SignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String = "XAUUSD",
    val strategyName: String,
    val signalType: String, // STRONG BUY, BUY, WEAK BUY, WAIT, WEAK SELL, SELL, STRONG SELL, NO SIGNAL
    val timeframe: String = "15m",
    val entryPrice: Double,
    val entryZoneLow: Double = entryPrice - 0.75,
    val entryZoneHigh: Double = entryPrice + 0.75,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val confidenceScore: Double,
    val riskRewardRatio: Double,
    val status: String = "ACTIVE", // ACTIVE, HIT_TP1, HIT_TP2, HIT_SL
    val reasoning: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "api_configs")
data class ApiKeyConfigEntity(
    @PrimaryKey val keyName: String,
    val keyValue: String,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserProfileEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val displayName: String,
    val role: String = "SUPER_ADMIN", // SUPER_ADMIN, VIEWER
    val tier: String = "INSTITUTIONAL_PRO",
    val isGuest: Boolean = false,
    val authToken: String = "",
    val lastLogin: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "tradingview_alerts",
    indices = [Index(value = ["indicator", "timestamp"])]
)
data class TradingViewAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val indicator: String, // e.g. "Scalping with Dr Hafiz V2", "3ESRA"
    val symbol: String = "XAUUSD",
    val action: String, // BUY, SELL, EXIT, ALERT
    val price: Double,
    val timeframe: String = "15m",
    val timestamp: Long = System.currentTimeMillis(),
    val message: String? = null,
    val isVerified: Boolean = true
)

@Entity(
    tableName = "news_articles",
    indices = [Index(value = ["category", "publishedAt"])]
)
data class NewsArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val source: String,
    val url: String? = null,
    val summary: String,
    val category: String, // GOLD, FED_POLICY, INFLATION, TREASURY_YIELDS, USD_DXY
    val sentiment: String = "NEUTRAL", // BULLISH, BEARISH, NEUTRAL
    val relevanceScore: Float = 1.0f,
    val publishedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "fundamental_analyses")
data class FundamentalAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val marketRisk: String, // High, Medium, Low
    val fundamentalBias: String, // Bullish, Bearish, Neutral
    val confidenceScore: Float = 85.0f,
    val keyDriversJson: String,
    val catalystImpact: String,
    val recommendedAction: String,
    val detailedSynthesis: String,
    val llmProvider: String = "OpenAI",
    val modelName: String = "gpt-4o",
    val evaluatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backtest_outcomes")
data class BacktestOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val strategyName: String,
    val timeframe: String = "15m",
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePct: Double,
    val profitFactor: Double,
    val maxDrawdownPct: Double,
    val netProfitDollars: Double,
    val sharpeRatio: Double,
    val initialCapital: Double = 10000.0,
    val finalEquity: Double,
    val calibrationNotes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

