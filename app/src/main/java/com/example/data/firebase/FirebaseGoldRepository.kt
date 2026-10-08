package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirebaseGoldRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    // Secondary constructor resolving named database ID from string resources
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: error("Operation requires authenticated user")
    }

    // --- User Profile ---

    fun observeUserProfile(userId: String): Flow<FirebaseUserProfile?> = callbackFlow {
        val docRef = firestore.collection("users").document(userId)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                trySend(snapshot.toObject(FirebaseUserProfile::class.java))
            } else {
                trySend(null)
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun saveUserProfile(profile: FirebaseUserProfile) {
        val currentUid = requireUserId()
        val sanitized = profile.copy(userId = currentUid)
        firestore.collection("users").document(currentUid).set(sanitized).await()
    }

    // --- Price Alerts ---

    fun observePriceAlerts(userId: String): Flow<List<FirebasePriceAlert>> =
        firestore.collection("users")
            .document(userId)
            .collection("price_alerts")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirebasePriceAlert::class.java)
            }

    suspend fun savePriceAlert(alert: FirebasePriceAlert) {
        val currentUid = requireUserId()
        val alertId = if (alert.alertId.isNotBlank()) alert.alertId else "alert_${System.currentTimeMillis()}"
        val sanitized = alert.copy(alertId = alertId, userId = currentUid)
        firestore.collection("users")
            .document(currentUid)
            .collection("price_alerts")
            .document(alertId)
            .set(sanitized)
            .await()
    }

    suspend fun deletePriceAlert(userId: String, alertId: String) {
        val currentUid = requireUserId()
        if (currentUid != userId) error("Unauthorized access to delete price alert")
        firestore.collection("users")
            .document(currentUid)
            .collection("price_alerts")
            .document(alertId)
            .delete()
            .await()
    }

    // --- Simulated Trades ---

    fun observeTrades(userId: String): Flow<List<FirebaseSimulatedTrade>> =
        firestore.collection("users")
            .document(userId)
            .collection("trades")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(FirebaseSimulatedTrade::class.java)
            }

    suspend fun saveTrade(trade: FirebaseSimulatedTrade) {
        val currentUid = requireUserId()
        val tradeId = if (trade.tradeId.isNotBlank()) trade.tradeId else "trade_${System.currentTimeMillis()}"
        val sanitized = trade.copy(tradeId = tradeId, userId = currentUid)
        firestore.collection("users")
            .document(currentUid)
            .collection("trades")
            .document(tradeId)
            .set(sanitized)
            .await()
    }

    suspend fun deleteTrade(userId: String, tradeId: String) {
        val currentUid = requireUserId()
        if (currentUid != userId) error("Unauthorized access to delete trade")
        firestore.collection("users")
            .document(currentUid)
            .collection("trades")
            .document(tradeId)
            .delete()
            .await()
    }
}
