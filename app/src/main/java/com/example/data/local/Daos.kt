package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketTickDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTick(tick: MarketTickEntity)

    @Query("SELECT * FROM market_ticks WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTicks(symbol: String = "XAUUSD", limit: Int = 50): Flow<List<MarketTickEntity>>

    @Query("SELECT * FROM market_ticks WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    fun getLatestTick(symbol: String = "XAUUSD"): Flow<MarketTickEntity?>

    @Query("DELETE FROM market_ticks WHERE id NOT IN (SELECT id FROM market_ticks ORDER BY timestamp DESC LIMIT 200)")
    suspend fun pruneOldTicks()
}

@Dao
interface CandleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<CandleEntity>)

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestamp ASC")
    fun getCandles(symbol: String = "XAUUSD", timeframe: String = "15m"): Flow<List<CandleEntity>>

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND timeframe = :timeframe ORDER BY timestamp ASC")
    suspend fun getCandlesSnapshot(symbol: String = "XAUUSD", timeframe: String = "15m"): List<CandleEntity>

    @Query("DELETE FROM candles WHERE timeframe = :timeframe")
    suspend fun clearTimeframe(timeframe: String)
}

@Dao
interface EconomicEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EconomicEventEntity>)

    @Query("SELECT * FROM economic_events ORDER BY timestamp ASC")
    fun getAllEvents(): Flow<List<EconomicEventEntity>>

    @Query("SELECT * FROM economic_events ORDER BY timestamp ASC")
    suspend fun getAllEventsSnapshot(): List<EconomicEventEntity>
}

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<SignalEntity>)

    @Query("SELECT * FROM signals ORDER BY timestamp DESC")
    fun getAllSignals(): Flow<List<SignalEntity>>

    @Query("UPDATE signals SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}

@Dao
interface ApiKeyConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertKey(config: ApiKeyConfigEntity)

    @Query("SELECT * FROM api_configs")
    fun getAllKeys(): Flow<List<ApiKeyConfigEntity>>

    @Query("SELECT keyValue FROM api_configs WHERE keyName = :keyName AND isActive = 1 LIMIT 1")
    suspend fun getKeyValue(keyName: String): String?
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUser(user: UserProfileEntity)

    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUser(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getUserSnapshot(): UserProfileEntity?
}

@Dao
interface TradingViewAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: TradingViewAlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<TradingViewAlertEntity>)

    @Query("SELECT * FROM tradingview_alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<TradingViewAlertEntity>>

    @Query("SELECT * FROM tradingview_alerts WHERE indicator = :indicator ORDER BY timestamp DESC")
    fun getAlertsByIndicator(indicator: String): Flow<List<TradingViewAlertEntity>>
}

@Dao
interface NewsArticleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<NewsArticleEntity>)

    @Query("SELECT * FROM news_articles ORDER BY publishedAt DESC")
    fun getAllArticles(): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles WHERE category = :category ORDER BY publishedAt DESC")
    fun getArticlesByCategory(category: String): Flow<List<NewsArticleEntity>>

    @Query("SELECT * FROM news_articles ORDER BY publishedAt DESC")
    suspend fun getArticlesSnapshot(): List<NewsArticleEntity>

    @Query("SELECT COUNT(*) FROM news_articles")
    suspend fun getCount(): Int
}

@Dao
interface FundamentalAnalysisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: FundamentalAnalysisEntity)

    @Query("SELECT * FROM fundamental_analyses ORDER BY evaluatedAt DESC LIMIT 1")
    fun getLatestAnalysis(): Flow<FundamentalAnalysisEntity?>

    @Query("SELECT * FROM fundamental_analyses ORDER BY evaluatedAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(): FundamentalAnalysisEntity?
}

@Dao
interface BacktestOutcomeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: BacktestOutcomeEntity): Long

    @Query("SELECT * FROM backtest_outcomes ORDER BY timestamp DESC")
    fun getAllOutcomes(): Flow<List<BacktestOutcomeEntity>>

    @Query("SELECT * FROM backtest_outcomes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentOutcomes(limit: Int): List<BacktestOutcomeEntity>

    @Query("DELETE FROM backtest_outcomes")
    suspend fun clearAll()
}

