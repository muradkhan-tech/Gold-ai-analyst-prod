package com.example.data.market

import com.example.data.local.CandleEntity

enum class SystemHealthState(val label: String) {
    LIVE("LIVE (UTC)"),
    OFFLINE_DELAYED("OFFLINE/DELAYED"),
    RATE_LIMITED_NEED_KEY("RATE LIMITED - API KEY REQUIRED"),
    MARKET_CLOSED("MARKET CLOSED (WEEKEND HALT)")
}

data class ValidationReport(
    val state: SystemHealthState,
    val duplicatesRemoved: Int,
    val missingGapsCount: Int,
    val latencySeconds: Long,
    val issues: List<String>,
    val validatedCandles: List<CandleEntity>
)

object DataValidator {

    private const val MAX_STALE_LATENCY_SECONDS = 300L // 5 minutes threshold

    /**
     * Strict Market Hours Validation (Weekend Halt):
     * XAUUSD trades from Sunday 22:00 UTC to Friday 22:00 UTC.
     * Closed from Friday 22:00 UTC to Sunday 22:00 UTC, and during daily 21:00-22:00 UTC roll.
     */
    fun isMarketOpen(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = nowEpochMs
        val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK) // 1=Sunday, 6=Friday, 7=Saturday
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY) // 0..23

        return when (dayOfWeek) {
            java.util.Calendar.SATURDAY -> false
            java.util.Calendar.SUNDAY -> hour >= 22
            java.util.Calendar.FRIDAY -> hour < 22
            else -> hour != 21 // Daily rollover break 21:00-22:00 UTC
        }
    }

    fun validate(
        rawCandles: List<CandleEntity>,
        isRateLimited: Boolean,
        providerFailure: Boolean = false
    ): ValidationReport {
        if (isRateLimited) {
            return ValidationReport(
                state = SystemHealthState.RATE_LIMITED_NEED_KEY,
                duplicatesRemoved = 0,
                missingGapsCount = 0,
                latencySeconds = 9999L,
                issues = listOf("Public market data quota reached. Please provide a premium API key in Settings to resume real-time feeds."),
                validatedCandles = rawCandles
            )
        }

        if (rawCandles.isEmpty() || providerFailure) {
            return ValidationReport(
                state = SystemHealthState.OFFLINE_DELAYED,
                duplicatesRemoved = 0,
                missingGapsCount = 0,
                latencySeconds = 9999L,
                issues = listOf("Market feed connection interrupted. System state set to OFFLINE/DELAYED."),
                validatedCandles = rawCandles
            )
        }

        val issues = mutableListOf<String>()

        // 1. Remove duplicate timestamps
        val seen = HashSet<Long>()
        val deduped = mutableListOf<CandleEntity>()
        var duplicatesCount = 0

        for (candle in rawCandles.sortedBy { it.timestamp }) {
            if (seen.add(candle.timestamp)) {
                deduped.add(candle)
            } else {
                duplicatesCount++
            }
        }
        if (duplicatesCount > 0) {
            issues.add("Detected and removed $duplicatesCount duplicate timestamp candles.")
        }

        // 2. Detect missing candle gaps (expected step = 60,000 ms)
        var missingGapsCount = 0
        for (i in 1 until deduped.size) {
            val prev = deduped[i - 1].timestamp
            val curr = deduped[i].timestamp
            val diffMs = curr - prev
            if (diffMs > 65_000L) { // Allow slight jitter
                val missing = (diffMs / 60_000L).toInt() - 1
                missingGapsCount += kotlin.math.max(0, missing)
            }
        }
        if (missingGapsCount > 0) {
            issues.add("Detected $missingGapsCount missing 1-minute intervals in time-series sequence.")
        }

        // 3. Detect stale prices
        val nowUtc = System.currentTimeMillis()
        val latestCandle = deduped.lastOrNull()
        val latencySeconds = if (latestCandle != null) {
            kotlin.math.max(0L, (nowUtc - latestCandle.timestamp) / 1000L)
        } else {
            9999L
        }

        val isStale = latencySeconds > MAX_STALE_LATENCY_SECONDS
        val state = if (isStale) {
            issues.add("Market data latency (${latencySeconds}s) exceeds ${MAX_STALE_LATENCY_SECONDS}s threshold. System state set to OFFLINE/DELAYED.")
            SystemHealthState.OFFLINE_DELAYED
        } else {
            SystemHealthState.LIVE
        }

        return ValidationReport(
            state = state,
            duplicatesRemoved = duplicatesCount,
            missingGapsCount = missingGapsCount,
            latencySeconds = latencySeconds,
            issues = issues,
            validatedCandles = deduped
        )
    }
}
