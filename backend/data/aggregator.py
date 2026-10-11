import datetime
from typing import List, Dict, Any

class CandleAggregator:
    """
    Vectorized & streaming candle aggregator that transforms high-frequency 1m
    candlestick sequences into standard institutional higher-timeframe bars:
    3m, 5m, 10m, 15m, 1h, 4h, 1d.
    """
    TF_MINUTES = {
        "1m": 1,
        "3m": 3,
        "5m": 5,
        "10m": 10,
        "15m": 15,
        "1h": 60,
        "4h": 240,
        "1d": 1440
    }

    @classmethod
    def aggregate_timeframe(cls, candles_1m: List[Dict[str, Any]], target_tf: str) -> List[Dict[str, Any]]:
        if not candles_1m or target_tf == "1m":
            return list(candles_1m)

        tf_min = cls.TF_MINUTES.get(target_tf, 15)
        bucket_size_ms = tf_min * 60 * 1000

        buckets: Dict[int, List[Dict[str, Any]]] = {}
        for c in candles_1m:
            ts_ms = c.get("timestamp_epoch_ms")
            if ts_ms is None:
                ts_str = c.get("timestamp")
                if ts_str:
                    try:
                        dt = datetime.datetime.fromisoformat(str(ts_str).replace("Z", "+00:00"))
                        ts_ms = int(dt.timestamp() * 1000)
                    except Exception:
                        continue
                else:
                    continue

            bucket_key = (ts_ms // bucket_size_ms) * bucket_size_ms
            if bucket_key not in buckets:
                buckets[bucket_key] = []
            buckets[bucket_key].append(c)

        aggregated = []
        for bucket_key in sorted(buckets.keys()):
            bars = buckets[bucket_key]
            if not bars:
                continue

            open_p = float(bars[0]["open"])
            high_p = max(float(b["high"]) for b in bars)
            low_p = min(float(b["low"]) for b in bars)
            close_p = float(bars[-1]["close"])
            vol = sum(float(b.get("volume", 0.0)) for b in bars)
            ticks = sum(int(b.get("ticks_count", 1)) for b in bars)

            bucket_dt = datetime.datetime.fromtimestamp(bucket_key / 1000.0, tz=datetime.timezone.utc)
            aggregated.append({
                "symbol": "XAUUSD",
                "timeframe": target_tf,
                "timestamp": bucket_dt.isoformat(),
                "timestamp_epoch_ms": bucket_key,
                "open": round(open_p, 2),
                "high": round(high_p, 2),
                "low": round(low_p, 2),
                "close": round(close_p, 2),
                "volume": round(vol, 2),
                "ticks_count": ticks
            })

        return aggregated

    @classmethod
    def aggregate_all_timeframes(cls, candles_1m: List[Dict[str, Any]]) -> Dict[str, List[Dict[str, Any]]]:
        results = {"1m": list(candles_1m)}
        for tf in ["3m", "5m", "10m", "15m", "1h", "4h", "1d"]:
            results[tf] = cls.aggregate_timeframe(candles_1m, tf)
        return results
