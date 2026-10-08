package com.example.data.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class FirebaseUserProfile(
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "VIEWER",
    val riskTolerance: String = "INSTITUTIONAL",
    val alertSoundEnabled: Boolean = true,
    val createdAt: String = ""
)

@IgnoreExtraProperties
data class FirebasePriceAlert(
    val alertId: String = "",
    val userId: String = "",
    val targetPrice: Double = 0.0,
    val condition: String = "ABOVE",
    val type: String = "SPOT",
    val note: String = "",
    val triggered: Boolean = false,
    val createdAt: Long = 0L
)

@IgnoreExtraProperties
data class FirebaseSimulatedTrade(
    val tradeId: String = "",
    val userId: String = "",
    val timeframe: String = "15m",
    val direction: String = "BUY",
    val lots: Double = 1.0,
    val entryPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit1: Double = 0.0,
    val takeProfit2: Double = 0.0,
    val exitPrice: Double = 0.0,
    val status: String = "OPEN",
    val pnlDollar: Double = 0.0,
    val pnlPips: Double = 0.0,
    val signalReason: String = "",
    val entryTime: Long = 0L,
    val exitTime: Long = 0L
)
