package com.example.data.market

import com.example.data.local.CandleEntity

object CandleAggregator {

    /**
     * Safely resamples 1-minute OHLCV candles into institutional timeframes (3m, 5m, 10m, 15m, 1h).
     * Strictly aligns buckets to UTC epoch boundaries to avoid timestamp leakage.
     */
    fun aggregate(
        candles1m: List<CandleEntity>,
        targetMinutes: Int,
        timeframeLabel: String
    ): List<CandleEntity> {
        if (targetMinutes == 1 || candles1m.isEmpty()) {
            return candles1m
        }

        val bucketMs = targetMinutes * 60 * 1000L
        val buckets = LinkedHashMap<Long, MutableList<CandleEntity>>()

        for (candle in candles1m.sortedBy { it.timestamp }) {
            val bucketStart = (candle.timestamp / bucketMs) * bucketMs
            buckets.getOrPut(bucketStart) { mutableListOf() }.add(candle)
        }

        val aggregated = mutableListOf<CandleEntity>()
        for ((bucketStart, items) in buckets) {
            val first = items.first()
            val last = items.last()

            val open = first.open
            var high = first.high
            var low = first.low
            var totalVolume = 0.0

            for (item in items) {
                if (item.high > high) high = item.high
                if (item.low < low) low = item.low
                totalVolume += item.volume
            }
            val close = last.close

            aggregated.add(
                CandleEntity(
                    symbol = first.symbol,
                    timeframe = timeframeLabel,
                    timestamp = bucketStart,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = totalVolume
                )
            )
        }

        return aggregated
    }

    fun aggregateAllTimeframes(candles1m: List<CandleEntity>): Map<String, List<CandleEntity>> {
        return mapOf(
            "1m" to candles1m,
            "3m" to aggregate(candles1m, 3, "3m"),
            "5m" to aggregate(candles1m, 5, "5m"),
            "10m" to aggregate(candles1m, 10, "10m"),
            "15m" to aggregate(candles1m, 15, "15m"),
            "1h" to aggregate(candles1m, 60, "1h")
        )
    }
}
