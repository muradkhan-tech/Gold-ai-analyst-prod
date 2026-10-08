from typing import Dict, Any, List

class TimeframeTrendResult:
    def __init__(
        self,
        timeframe: str,
        trend: str,  # BULLISH, BEARISH, NEUTRAL
        score: int,  # -3 to +3
        price: float,
        ema_stack: str,
        rsi_state: str,
        macd_state: str,
        summary: str
    ):
        self.timeframe = timeframe
        self.trend = trend
        self.score = score
        self.price = price
        self.ema_stack = ema_stack
        self.rsi_state = rsi_state
        self.macd_state = macd_state
        self.summary = summary

    def to_dict(self) -> Dict[str, Any]:
        return {
            "timeframe": self.timeframe,
            "trend": self.trend,
            "score": self.score,
            "price": self.price,
            "ema_stack": self.ema_stack,
            "rsi_state": self.rsi_state,
            "macd_state": self.macd_state,
            "summary": self.summary
        }


class MultiTimeframeAlignmentEngine:
    """
    Evaluates multi-timeframe trend alignment for XAUUSD across 1m, 3m, 5m, 10m, 15m, and 1h.
    Outputs Bullish/Bearish/Neutral per timeframe and calculates institutional confluence alignment.
    """

    TIMEFRAMES_ORDER = ["1m", "3m", "5m", "10m", "15m", "1h"]

    @classmethod
    def evaluate_timeframe(cls, tf: str, indicators: Dict[str, Any]) -> TimeframeTrendResult:
        price = indicators.get("price", 0.0)
        ema8 = indicators.get("ema_8", 0.0)
        ema21 = indicators.get("ema_21", 0.0)
        ema28 = indicators.get("ema_28", 0.0)
        ema50 = indicators.get("ema_50", 0.0)
        rsi = indicators.get("rsi_14", 50.0)
        macd_hist = indicators.get("macd_hist", 0.0)

        score = 0
        reasons = []

        # 1. EMA 8/21/28/50 Alignment
        if price > ema8 and ema8 > ema21 and ema21 > ema28:
            score += 2
            ema_stack = "BULLISH_STACK (Price > EMA8 > EMA21 > EMA28)"
            reasons.append("Fast EMA ribbon bullish")
        elif price < ema8 and ema8 < ema21 and ema21 < ema28:
            score -= 2
            ema_stack = "BEARISH_STACK (Price < EMA8 < EMA21 < EMA28)"
            reasons.append("Fast EMA ribbon bearish")
        else:
            ema_stack = "COMPRESSED_NEUTRAL"
            reasons.append("EMA ribbon compressing/choppy")

        # 2. MACD Momentum
        if macd_hist > 0.1:
            score += 1
            macd_state = f"BULLISH (+{macd_hist:.2f})"
        elif macd_hist < -0.1:
            score -= 1
            macd_state = f"BEARISH ({macd_hist:.2f})"
        else:
            macd_state = "FLAT"

        # 3. RSI Bias
        if rsi >= 55.0:
            score += 1
            rsi_state = f"BULLISH ({rsi:.1f})"
        elif rsi <= 45.0:
            score -= 1
            rsi_state = f"BEARISH ({rsi:.1f})"
        else:
            rsi_state = f"NEUTRAL ({rsi:.1f})"

        # Trend classification
        if score >= 2:
            trend = "BULLISH"
        elif score <= -2:
            trend = "BEARISH"
        else:
            trend = "NEUTRAL"

        return TimeframeTrendResult(
            timeframe=tf,
            trend=trend,
            score=score,
            price=price,
            ema_stack=ema_stack,
            rsi_state=rsi_state,
            macd_state=macd_state,
            summary=", ".join(reasons)
        )

    @classmethod
    def evaluate_alignment(cls, snapshots_by_tf: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
        results: Dict[str, Dict[str, Any]] = {}
        bullish_count = 0
        bearish_count = 0
        neutral_count = 0

        for tf in cls.TIMEFRAMES_ORDER:
            ind = snapshots_by_tf.get(tf)
            if not ind:
                continue
            tf_result = cls.evaluate_timeframe(tf, ind)
            results[tf] = tf_result.to_dict()

            if tf_result.trend == "BULLISH":
                bullish_count += 1
            elif tf_result.trend == "BEARISH":
                bearish_count += 1
            else:
                neutral_count += 1

        total_analyzed = len(results) or 1
        bullish_pct = round((bullish_count / total_analyzed) * 100.0, 1)
        bearish_pct = round((bearish_count / total_analyzed) * 100.0, 1)

        # Multi-timeframe trend hierarchy (1m scalp vs 1h macro trend)
        trend_1m = results.get("1m", {}).get("trend", "NEUTRAL")
        trend_1h = results.get("1h", {}).get("trend", "NEUTRAL")

        if trend_1m == trend_1h and trend_1h != "NEUTRAL":
            alignment_note = f"Full scalp-to-macro trend synchronization: 1m aligns with 1h ({trend_1h})."
        elif trend_1m != trend_1h:
            alignment_note = f"Counter-trend scalp regime: 1m ({trend_1m}) moving against 1h macro ({trend_1h}). Exercise tighter stop-losses."
        else:
            alignment_note = "Consolidation regime: Lack of directional macro consensus."

        # Overall Confluence state
        if bullish_count >= 4 and bullish_count > bearish_count:
            overall_status = "STRONG_BULLISH_CONFLUENCE"
        elif bearish_count >= 4 and bearish_count > bullish_count:
            overall_status = "STRONG_BEARISH_CONFLUENCE"
        elif bullish_count > bearish_count:
            overall_status = "MODERATE_BULLISH"
        elif bearish_count > bullish_count:
            overall_status = "MODERATE_BEARISH"
        else:
            overall_status = "NEUTRAL_CHOP"

        return {
            "overall_status": overall_status,
            "bullish_percentage": bullish_pct,
            "bearish_percentage": bearish_pct,
            "bullish_count": bullish_count,
            "bearish_count": bearish_count,
            "neutral_count": neutral_count,
            "total_timeframes": total_analyzed,
            "scalp_1m_vs_macro_1h": {
                "trend_1m": trend_1m,
                "trend_1h": trend_1h,
                "synchronized": trend_1m == trend_1h
            },
            "alignment_note": alignment_note,
            "per_timeframe": results
        }
