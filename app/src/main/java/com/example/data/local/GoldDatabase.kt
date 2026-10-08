package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MarketTickEntity::class,
        CandleEntity::class,
        EconomicEventEntity::class,
        SignalEntity::class,
        ApiKeyConfigEntity::class,
        UserProfileEntity::class,
        TradingViewAlertEntity::class,
        NewsArticleEntity::class,
        FundamentalAnalysisEntity::class,
        BacktestOutcomeEntity::class,
        com.example.data.alert.PriceAlertEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class GoldDatabase : RoomDatabase() {
    abstract fun marketTickDao(): MarketTickDao
    abstract fun candleDao(): CandleDao
    abstract fun economicEventDao(): EconomicEventDao
    abstract fun signalDao(): SignalDao
    abstract fun apiKeyConfigDao(): ApiKeyConfigDao
    abstract fun userDao(): UserDao
    abstract fun tradingViewAlertDao(): TradingViewAlertDao
    abstract fun newsArticleDao(): NewsArticleDao
    abstract fun fundamentalAnalysisDao(): FundamentalAnalysisDao
    abstract fun backtestOutcomeDao(): BacktestOutcomeDao
    abstract fun priceAlertDao(): com.example.data.alert.PriceAlertDao

    companion object {
        @Volatile
        private var INSTANCE: GoldDatabase? = null

        fun getDatabase(context: Context): GoldDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GoldDatabase::class.java,
                    "gold_ai_analyst.db"
                )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
