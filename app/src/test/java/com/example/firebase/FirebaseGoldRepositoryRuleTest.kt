package com.example.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firebase.FirebaseGoldRepository
import com.example.data.firebase.FirebasePriceAlert
import com.example.data.firebase.FirebaseSimulatedTrade
import com.example.data.firebase.FirebaseUserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirebaseGoldRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun unauthenticated_user_cannot_save_profile() = runBlocking {
        auth.signOut()
        val repo = FirebaseGoldRepository(firestore, auth)

        try {
            repo.saveUserProfile(
                FirebaseUserProfile(
                    userId = "unauth_user",
                    email = "test@example.com",
                    role = "VIEWER"
                )
            )
            fail("Expected IllegalStateException for unauthenticated write")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Operation requires authenticated user") == true)
        }
    }

    @Test
    fun authenticated_user_can_save_and_observe_profile() = runBlocking {
        val uid = signInTestUser("trader_alice@test.com")
        val repo = FirebaseGoldRepository(firestore, auth)

        val profile = FirebaseUserProfile(
            userId = uid,
            email = "trader_alice@test.com",
            displayName = "Alice Gold",
            role = "SUPER_ADMIN",
            riskTolerance = "INSTITUTIONAL",
            alertSoundEnabled = true,
            createdAt = "2026-10-08T00:00:00Z"
        )
        repo.saveUserProfile(profile)

        val retrieved = repo.observeUserProfile(uid).first()
        assertNotNull(retrieved)
        assertEquals(uid, retrieved?.userId)
        assertEquals("SUPER_ADMIN", retrieved?.role)
        assertEquals("trader_alice@test.com", retrieved?.email)
    }

    @Test
    fun authenticated_user_can_manage_price_alerts() = runBlocking {
        val uid = signInTestUser("alert_trader@test.com")
        val repo = FirebaseGoldRepository(firestore, auth)

        val alert = FirebasePriceAlert(
            alertId = "alert_xau_1",
            userId = uid,
            targetPrice = 2880.00,
            condition = "ABOVE",
            type = "SPOT",
            note = "High volatility barrier",
            triggered = false,
            createdAt = System.currentTimeMillis()
        )
        repo.savePriceAlert(alert)

        val alerts = repo.observePriceAlerts(uid).first()
        assertEquals(1, alerts.size)
        assertEquals("alert_xau_1", alerts[0].alertId)
        assertEquals(2880.00, alerts[0].targetPrice, 0.001)

        repo.deletePriceAlert(uid, "alert_xau_1")
        val emptyAlerts = repo.observePriceAlerts(uid).first()
        assertEquals(0, emptyAlerts.size)
    }

    @Test
    fun authenticated_user_can_log_simulated_trades() = runBlocking {
        val uid = signInTestUser("sim_trader@test.com")
        val repo = FirebaseGoldRepository(firestore, auth)

        val trade = FirebaseSimulatedTrade(
            tradeId = "trade_xau_1",
            userId = uid,
            timeframe = "15m",
            direction = "BUY",
            lots = 1.0,
            entryPrice = 2864.50,
            stopLoss = 2855.00,
            takeProfit1 = 2875.00,
            takeProfit2 = 2888.00,
            status = "OPEN",
            pnlDollar = 0.0,
            pnlPips = 0.0,
            signalReason = "Consensus Volume Absorption",
            entryTime = System.currentTimeMillis()
        )
        repo.saveTrade(trade)

        val trades = repo.observeTrades(uid).first()
        assertEquals(1, trades.size)
        assertEquals("trade_xau_1", trades[0].tradeId)
        assertEquals("BUY", trades[0].direction)
    }
}
