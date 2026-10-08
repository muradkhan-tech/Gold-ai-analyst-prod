package com.example.data.alert

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertCondition {
    BID_ABOVE,
    BID_BELOW,
    ASK_ABOVE,
    ASK_BELOW
}

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String = "XAUUSD",
    val targetPrice: Double,
    val condition: AlertCondition,
    val note: String = "",
    val isEnabled: Boolean = true,
    val isTriggered: Boolean = false,
    val triggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
