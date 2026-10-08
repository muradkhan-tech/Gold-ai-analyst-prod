package com.example.data.alert

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceAlertDao {

    @Query("SELECT * FROM price_alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<PriceAlertEntity>>

    @Query("SELECT * FROM price_alerts WHERE isEnabled = 1 AND isTriggered = 0")
    suspend fun getActivePendingAlerts(): List<PriceAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: PriceAlertEntity): Long

    @Update
    suspend fun updateAlert(alert: PriceAlertEntity)

    @Delete
    suspend fun deleteAlert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET isTriggered = 1, triggeredAt = :timestamp WHERE id = :id")
    suspend fun markTriggered(id: Long, timestamp: Long)

    @Query("UPDATE price_alerts SET isEnabled = :enabled, isTriggered = 0 WHERE id = :id")
    suspend fun toggleEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
