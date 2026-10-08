import datetime
import math
from typing import Dict, Any, List, Optional
import pandas as pd
from backend.indicators.technical import TechnicalIndicators
from backend.indicators.alignment import MultiTimeframeAlignmentEngine

class GoldSignalEngine:
    """
    Institutional Signal Engine for XAUUSD.
    Combines:
    1. Multi-Timeframe Technical Alignment (1m to 1h)
    2. Macroeconomic Fundamental Intelligence (News & Calendar)
    3. AI Reasoning Synthesis (Market Risk & Bias)
    
    Outputs standardized signals:
    STRONG BUY, BUY, WEAK BUY, WAIT, WEAK SELL, SELL, STRONG SELL (or NO SIGNAL on failsafe).
    Includes Entry Zones (low/high), Stop Loss, Dual Take Profits, Risk/Reward, and Confidence Score.
    """

    MAX_MARKET_DATA_STALENESS_SEC = 300  # 5 minutes strict failsafe threshold
    MAX_NEWS_STALENESS_HOURS = 24

    @classmethod
    def evaluate_signals(
        cls,
        candles_df: pd.DataFrame,
        timeframe_snapshots: Optional[Dict[str, Dict[str, Any]]] = None,
        fundamental_news: Optional[List[Dict[str, Any]]] = None,
        calendar_events: Optional[List[Dict[str, Any]]] = None,
        ai_synthesis: Optional[Dict[str, Any]] = None,
        latest_tick_timestamp: Optional[datetime.datetime] = None,
        enforce_failsafe: bool = True
    ) -> Dict[str, Any]:
        """
        Executes multi-factor consensus combining Technical, Fundamental, and AI layers.
        Enforces strict failsafe if market data or macro news is missing or stale.
        """
        now = datetime.datetime.utcnow()

        # -------------------------------------------------------------
        # 1. STRICT FAILSAFE CHECK: Missing or Stale Market Data
        # -------------------------------------------------------------
        if enforce_failsafe:
            if candles_df is None or len(candles_df) < 20:
                return cls._build_failsafe_response(
                    signal_type="NO SIGNAL",
                    reason="FAILSAFE TRIGGERED: Insufficient candle history (< 20 bars). Trading halted to prevent execution on sparse data.",
                    current_price=2860.0
                )

            # Check timestamp staleness if available
            last_ts = None
            if "timestamp" in candles_df.columns:
                try:
                    val = candles_df["timestamp"].iloc[-1]
                    if isinstance(val, (int, float)):
                        last_ts = datetime.datetime.utcfromtimestamp(val / 1000.0 if val > 1e11 else val)
                    elif isinstance(val, str):
                        last_ts = datetime.datetime.fromisoformat(val.replace("Z", "+00:00")).replace(tzinfo=None)
                    elif isinstance(val, datetime.datetime):
                        last_ts = val.replace(tzinfo=None)
                except Exception:
                    pass

            if latest_tick_timestamp:
                last_ts = latest_tick_timestamp.replace(tzinfo=None)

            if last_ts is not None:
                staleness_sec = (now - last_ts).total_seconds()
                if staleness_sec > cls.MAX_MARKET_DATA_STALENESS_SEC:
                    last_price = float(candles_df["close"].iloc[-1]) if "close" in candles_df.columns else 2860.0
                    return cls._build_failsafe_response(
                        signal_type="NO SIGNAL",
                        reason=f"FAILSAFE TRIGGERED: Market data feed is stale by {int(staleness_sec)}s (> {cls.MAX_MARKET_DATA_STALENESS_SEC}s threshold). Order generation disabled.",
                        current_price=last_price
                    )

            # Check for missing fundamental news
            if fundamental_news is not None and len(fundamental_news) == 0 and (calendar_events is None or len(calendar_events) == 0):
                last_price = float(candles_df["close"].iloc[-1]) if "close" in candles_df.columns else 2860.0
                return cls._build_failsafe_response(
                    signal_type="WAIT",
                    reason="FAILSAFE TRIGGERED: Macro news intelligence is empty. Awaiting fresh fundamental data before committing capital.",
                    current_price=last_price
                )

        # -------------------------------------------------------------
        # 2. TECHNICAL ANALYSIS LAYER (Multi-Timeframe Confluence)
        # -------------------------------------------------------------
        df_enriched = TechnicalIndicators.compute_all(candles_df.copy())
        last = df_enriched.iloc[-1]
        price = float(last["close"])
        atr = float(last.get("atr_14", 12.0))
        if pd.isna(atr) or atr <= 0.5:
            atr = 12.0

        # Base 15m technical score
        tech_score = 0.0 # Range: -100 to +100
        reasons = []

        ema8 = float(last.get("ema_8", price))
        ema21 = float(last.get("ema_21", price))
        ema28 = float(last.get("ema_28", price))
        ema50 = float(last.get("ema_50", price))
        ema200 = float(last.get("ema_200", price))
        rsi14 = float(last.get("rsi_14", 50.0))
        macd_line = float(last.get("macd", 0.0))
        macd_sig = float(last.get("macd_signal", 0.0))
        macd_hist = float(last.get("macd_hist", 0.0))
        bb_upper = float(last.get("bb_upper", price + 10))
        bb_lower = float(last.get("bb_lower", price - 10))

        # EMA Stack
        if price > ema8 > ema21 > ema50 > ema200:
            tech_score += 40.0
            reasons.append("Perfect Bullish EMA Stack (Price > EMA8 > EMA21 > EMA50 > EMA200)")
        elif price < ema8 < ema21 < ema50 < ema200:
            tech_score -= 40.0
            reasons.append("Perfect Bearish EMA Stack (Price < EMA8 < EMA21 < EMA50 < EMA200)")
        elif price > ema21 > ema50:
            tech_score += 20.0
            reasons.append("Moderate Bullish EMA Structure")
        elif price < ema21 < ema50:
            tech_score -= 20.0
            reasons.append("Moderate Bearish EMA Structure")

        # MACD Momentum
        if macd_line > macd_sig and macd_hist > 0:
            tech_score += 25.0
            reasons.append("MACD Bullish Histogram expansion")
        elif macd_line < macd_sig and macd_hist < 0:
            tech_score -= 25.0
            reasons.append("MACD Bearish Histogram contraction")

        # RSI Momentum & Mean Reversion
        if 50 < rsi14 < 68:
            tech_score += 20.0
            reasons.append(f"RSI bullish expansion at {rsi14:.1f}")
        elif 32 < rsi14 < 50:
            tech_score -= 20.0
            reasons.append(f"RSI bearish expansion at {rsi14:.1f}")
        elif rsi14 <= 32:
            tech_score += 15.0 # Oversold bounce potential
            reasons.append(f"RSI oversold rebound zone ({rsi14:.1f})")
        elif rsi14 >= 68:
            tech_score -= 15.0 # Overbought pullback risk
            reasons.append(f"RSI overbought exhaustion zone ({rsi14:.1f})")

        # Bollinger Bands Breakout
        if price > bb_upper:
            tech_score += 15.0
            reasons.append("Bollinger upper band expansion")
        elif price < bb_lower:
            tech_score -= 15.0
            reasons.append("Bollinger lower band support pressure")

        # Multi-timeframe alignment integration if snapshots provided
        mtf_weight = 0.0
        if timeframe_snapshots and len(timeframe_snapshots) >= 3:
            alignment = MultiTimeframeAlignmentEngine.evaluate_alignment(timeframe_snapshots)
            bull_pct = alignment.get("bullish_percentage", 50.0)
            # Map 0..100% to -100..+100
            mtf_score = (bull_pct - 50.0) * 2.0
            tech_score = (tech_score * 0.6) + (mtf_score * 0.4)
            reasons.append(f"MTF Confluence: {alignment.get('overall_status')} ({bull_pct:.0f}% bullish)")

        tech_score = max(-100.0, min(100.0, tech_score))

        # -------------------------------------------------------------
        # 3. FUNDAMENTAL INTELLIGENCE LAYER
        # -------------------------------------------------------------
        fund_score = 0.0 # Range: -100 to +100
        fund_reasons = []

        if fundamental_news:
            for article in fundamental_news[:8]:
                sentiment = (article.get("sentiment") or "NEUTRAL").upper()
                category = (article.get("category") or "GOLD").upper()
                relevance = float(article.get("relevance_score", 0.8))

                if sentiment == "BULLISH":
                    fund_score += 12.0 * relevance
                elif sentiment == "BEARISH":
                    fund_score -= 12.0 * relevance

            if fund_score > 10:
                fund_reasons.append(f"Macro News Bias: Positive bullion sentiment (score: +{fund_score:.1f})")
            elif fund_score < -10:
                fund_reasons.append(f"Macro News Bias: Negative bullion sentiment (score: {fund_score:.1f})")

        if calendar_events:
            for ev in calendar_events[:5]:
                bias = (ev.get("gold_bias") or ev.get("bias") or "NEUTRAL").upper()
                impact = (ev.get("impact") or "MEDIUM").upper()
                mult = 1.5 if impact == "HIGH" else 1.0

                if bias == "BULLISH":
                    fund_score += 10.0 * mult
                elif bias == "BEARISH":
                    fund_score -= 10.0 * mult

        fund_score = max(-100.0, min(100.0, fund_score))

        # -------------------------------------------------------------
        # 4. AI REASONING SYNTHESIS LAYER
        # -------------------------------------------------------------
        ai_score = 0.0 # Range: -100 to +100
        market_risk = "Medium"
        ai_confidence = 80.0

        if ai_synthesis and "analysis" in ai_synthesis and ai_synthesis["analysis"]:
            analysis = ai_synthesis["analysis"]
            bias = (analysis.get("fundamental_bias") or "Neutral").upper()
            market_risk = analysis.get("market_risk") or "Medium"
            ai_confidence = float(analysis.get("confidence_score") or 80.0)

            if bias == "BULLISH":
                ai_score = (ai_confidence / 100.0) * 100.0
            elif bias == "BEARISH":
                ai_score = -(ai_confidence / 100.0) * 100.0
            else:
                ai_score = 0.0

            reasons.append(f"AI Reasoning: {bias} Bias (Risk: {market_risk}, Conf: {ai_confidence:.0f}%)")
        else:
            # Fallback to pure fundamental layer if AI not yet initialized
            ai_score = fund_score * 0.8

        # -------------------------------------------------------------
        # 5. MULTI-FACTOR CONSENSUS CALCULATION
        # -------------------------------------------------------------
        # Weights: Technical 45%, Fundamental 30%, AI Reasoning 25%
        composite_score = (tech_score * 0.45) + (fund_score * 0.30) + (ai_score * 0.25)

        # Market risk adjustment: If risk is HIGH, dampen composite score towards neutral
        if market_risk.upper() == "HIGH":
            composite_score *= 0.65
            reasons.append("High Market Risk catalyst dampens directional commitment")

        # Map composite score (-100 to +100) to target signal classifications:
        # STRONG BUY, BUY, WEAK BUY, WAIT, WEAK SELL, SELL, STRONG SELL
        if composite_score >= 68.0:
            final_signal = "STRONG BUY"
        elif composite_score >= 42.0:
            final_signal = "BUY"
        elif composite_score >= 18.0:
            final_signal = "WEAK BUY"
        elif composite_score <= -68.0:
            final_signal = "STRONG SELL"
        elif composite_score <= -42.0:
            final_signal = "SELL"
        elif composite_score <= -18.0:
            final_signal = "WEAK SELL"
        else:
            final_signal = "WAIT"

        # Calculate confidence score (50% to 98%)
        base_confidence = 50.0 + (abs(composite_score) * 0.48)
        confidence = round(min(98.0, max(50.0, base_confidence)), 1)

        # -------------------------------------------------------------
        # 6. EXACT ENTRY ZONES, STOP LOSS, TAKE PROFIT, RISK/REWARD
        # -------------------------------------------------------------
        is_bullish = "BUY" in final_signal
        is_bearish = "SELL" in final_signal

        # Volatility multiplier based on signal conviction
        atr_sl_mult = 1.4 if "STRONG" in final_signal else 1.6
        atr_tp1_mult = 2.0 if "STRONG" in final_signal else 1.8
        atr_tp2_mult = 3.6 if "STRONG" in final_signal else 3.2

        if is_bullish:
            entry_price = round(price, 2)
            entry_zone_low = round(price - (atr * 0.25), 2)
            entry_zone_high = round(price + (atr * 0.15), 2)
            stop_loss = round(price - (atr * atr_sl_mult), 2)
            take_profit_1 = round(price + (atr * atr_tp1_mult), 2)
            take_profit_2 = round(price + (atr * atr_tp2_mult), 2)
            risk = max(0.1, entry_price - stop_loss)
            reward = max(0.1, take_profit_1 - entry_price)
            rr_ratio = round(reward / risk, 2)
        elif is_bearish:
            entry_price = round(price, 2)
            entry_zone_low = round(price - (atr * 0.15), 2)
            entry_zone_high = round(price + (atr * 0.25), 2)
            stop_loss = round(price + (atr * atr_sl_mult), 2)
            take_profit_1 = round(price - (atr * atr_tp1_mult), 2)
            take_profit_2 = round(price - (atr * atr_tp2_mult), 2)
            risk = max(0.1, stop_loss - entry_price)
            reward = max(0.1, entry_price - take_profit_1)
            rr_ratio = round(reward / risk, 2)
        else:
            # WAIT / Consolidation
            entry_price = round(price, 2)
            entry_zone_low = round(price - (atr * 0.4), 2)
            entry_zone_high = round(price + (atr * 0.4), 2)
            stop_loss = round(price - atr, 2)
            take_profit_1 = round(price + atr, 2)
            take_profit_2 = round(price + (atr * 2.0), 2)
            rr_ratio = 1.0
            reasons.append("Consolidation mode: Awaiting directional catalyst breakout.")

        all_reasoning = " • ".join(reasons + fund_reasons)

        return {
            "symbol": "XAUUSD",
            "signal_type": final_signal,
            "entry_price": entry_price,
            "entry_zone_low": entry_zone_low,
            "entry_zone_high": entry_zone_high,
            "stop_loss": stop_loss,
            "take_profit_1": take_profit_1,
            "take_profit_2": take_profit_2,
            "risk_reward_ratio": rr_ratio,
            "confidence_score": confidence,
            "composite_score": round(composite_score, 2),
            "technical_score": round(tech_score, 2),
            "fundamental_score": round(fund_score, 2),
            "ai_score": round(ai_score, 2),
            "market_risk": market_risk,
            "strategy_name": "MultiTimeframeConsensusEngine",
            "timeframe": "15m",
            "reasoning": all_reasoning,
            "timestamp": now.isoformat()
        }

    @classmethod
    def _build_failsafe_response(cls, signal_type: str, reason: str, current_price: float) -> Dict[str, Any]:
        p = round(current_price, 2)
        return {
            "symbol": "XAUUSD",
            "signal_type": signal_type,
            "entry_price": p,
            "entry_zone_low": round(p - 1.0, 2),
            "entry_zone_high": round(p + 1.0, 2),
            "stop_loss": round(p - 10.0, 2),
            "take_profit_1": round(p + 15.0, 2),
            "take_profit_2": round(p + 30.0, 2),
            "risk_reward_ratio": 1.0,
            "confidence_score": 0.0,
            "composite_score": 0.0,
            "technical_score": 0.0,
            "fundamental_score": 0.0,
            "ai_score": 0.0,
            "market_risk": "High",
            "strategy_name": "FailsafeProtectiveEngine",
            "timeframe": "15m",
            "reasoning": reason,
            "timestamp": datetime.datetime.utcnow().isoformat()
        }
