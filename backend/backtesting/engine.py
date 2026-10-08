import json
import datetime
import math
from typing import Dict, Any, List, Optional
import pandas as pd
import numpy as np
from sqlalchemy.ext.asyncio import AsyncSession
from backend.signals.engine import GoldSignalEngine
from database.models import BacktestOutcome

class QuantBacktester:
    """
    Event-Driven Backtesting Engine for XAUUSD Quantitative Strategies.
    
    Guarantees Zero Look-Ahead Bias:
    - Indicators and signals at bar t are computed strictly using data available up to bar t close.
    - Trade execution occurs on bar t+1 open (or realistic intrabar execution).
    - Conservative intrabar resolution: if both TP and SL are breached in the same bar, SL is triggered.
    - Calculates Win Rate %, Profit Factor, Maximum Drawdown %, Sharpe Ratio, and Net PnL.
    - Persists all signal calibration outcomes to the database.
    """

    @classmethod
    async def run_event_driven_backtest(
        cls,
        candles_df: pd.DataFrame,
        strategy_name: str = "MultiTimeframeConsensusEngine",
        initial_capital: float = 10000.0,
        lot_size: float = 0.5, # 0.5 lot = $50/point in XAUUSD
        warmup_bars: int = 30,
        max_holding_bars: int = 24,
        db_session: Optional[AsyncSession] = None
    ) -> Dict[str, Any]:
        if candles_df is None or len(candles_df) < (warmup_bars + 15):
            return {
                "status": "ERROR",
                "error": f"Insufficient historical candles for backtesting (requires at least {warmup_bars + 15} bars)",
                "total_trades": 0,
                "win_rate_pct": 0.0,
                "profit_factor": 0.0,
                "max_drawdown_pct": 0.0,
                "net_profit": 0.0,
                "final_equity": initial_capital,
                "trades": []
            }

        df = candles_df.copy().reset_index(drop=True)
        trades: List[Dict[str, Any]] = []
        equity = initial_capital
        peak_equity = initial_capital
        max_drawdown_pct = 0.0
        equity_curve = [initial_capital]

        in_position = False
        pos_type = None # "LONG" or "SHORT"
        entry_price = 0.0
        entry_bar_idx = 0
        entry_time = ""
        stop_loss = 0.0
        take_profit_1 = 0.0
        take_profit_2 = 0.0
        active_signal_name = ""

        # Event-driven step-by-step through historical candles
        # Bar i: signal generation strictly on df[:i+1]
        # Bar i+1: order executed on bar i+1 open (NO LOOK-AHEAD BIAS)
        n = len(df)
        pending_order = None # Store order queued at bar i close to fill at bar i+1 open

        for i in range(warmup_bars, n):
            current_bar = df.iloc[i]
            bar_open = float(current_bar["open"])
            bar_high = float(current_bar["high"])
            bar_low = float(current_bar["low"])
            bar_close = float(current_bar["close"])
            bar_ts = str(current_bar.get("timestamp", f"bar_{i}"))

            # ------------------------------------------------------------------
            # A. Process Pending Order at Bar Open (t+1 execution)
            # ------------------------------------------------------------------
            if pending_order and not in_position:
                in_position = True
                pos_type = pending_order["type"]
                entry_price = bar_open
                entry_bar_idx = i
                entry_time = bar_ts
                stop_loss = pending_order["sl"]
                take_profit_1 = pending_order["tp1"]
                take_profit_2 = pending_order["tp2"]
                active_signal_name = pending_order["signal_type"]
                pending_order = None

            # ------------------------------------------------------------------
            # B. Evaluate In-Position Trades (Intrabar price action)
            # ------------------------------------------------------------------
            if in_position:
                bars_held = i - entry_bar_idx
                exit_price = None
                exit_reason = None
                is_win = False

                if pos_type == "LONG":
                    hit_sl = bar_low <= stop_loss
                    hit_tp1 = bar_high >= take_profit_1

                    if hit_sl and hit_tp1:
                        # Conservative assumption: hit Stop Loss first
                        exit_price = stop_loss
                        exit_reason = "HIT_SL (Intrabar Conservative)"
                        is_win = False
                    elif hit_sl:
                        exit_price = stop_loss
                        exit_reason = "HIT_SL"
                        is_win = False
                    elif hit_tp1:
                        exit_price = take_profit_1
                        exit_reason = "HIT_TP1"
                        is_win = True
                    elif bars_held >= max_holding_bars:
                        exit_price = bar_close
                        exit_reason = "TIMEOUT_EXPIRY"
                        is_win = (exit_price > entry_price)

                elif pos_type == "SHORT":
                    hit_sl = bar_high >= stop_loss
                    hit_tp1 = bar_low <= take_profit_1

                    if hit_sl and hit_tp1:
                        exit_price = stop_loss
                        exit_reason = "HIT_SL (Intrabar Conservative)"
                        is_win = False
                    elif hit_sl:
                        exit_price = stop_loss
                        exit_reason = "HIT_SL"
                        is_win = False
                    elif hit_tp1:
                        exit_price = take_profit_1
                        exit_reason = "HIT_TP1"
                        is_win = True
                    elif bars_held >= max_holding_bars:
                        exit_price = bar_close
                        exit_reason = "TIMEOUT_EXPIRY"
                        is_win = (exit_price < entry_price)

                if exit_price is not None:
                    pnl_points = (exit_price - entry_price) if pos_type == "LONG" else (entry_price - exit_price)
                    dollar_pnl = pnl_points * (lot_size * 100.0)
                    equity += dollar_pnl
                    equity_curve.append(equity)

                    if equity > peak_equity:
                        peak_equity = equity
                    dd = ((peak_equity - equity) / peak_equity) * 100.0
                    if dd > max_drawdown_pct:
                        max_drawdown_pct = dd

                    trades.append({
                        "trade_num": len(trades) + 1,
                        "type": pos_type,
                        "signal_name": active_signal_name,
                        "entry_bar": entry_bar_idx,
                        "exit_bar": i,
                        "entry_time": entry_time,
                        "exit_time": bar_ts,
                        "entry_price": round(entry_price, 2),
                        "exit_price": round(exit_price, 2),
                        "stop_loss": round(stop_loss, 2),
                        "take_profit_1": round(take_profit_1, 2),
                        "pnl_points": round(pnl_points, 2),
                        "pnl_dollars": round(dollar_pnl, 2),
                        "bars_held": bars_held,
                        "exit_reason": exit_reason,
                        "is_win": is_win
                    })

                    in_position = False
                    pos_type = None

            # ------------------------------------------------------------------
            # C. Signal Generation strictly on bar 0..i (Zero Look-Ahead)
            # ------------------------------------------------------------------
            if not in_position and pending_order is None and i < (n - 1):
                historical_slice = df.iloc[: i + 1] # Strict history slice
                eval_res = GoldSignalEngine.evaluate_signals(
                    candles_df=historical_slice,
                    enforce_failsafe=False # In historical backtest, evaluate technical setups
                )

                sig_type = eval_res.get("signal_type", "WAIT")
                if "BUY" in sig_type:
                    pending_order = {
                        "type": "LONG",
                        "signal_type": sig_type,
                        "sl": eval_res["stop_loss"],
                        "tp1": eval_res["take_profit_1"],
                        "tp2": eval_res["take_profit_2"]
                    }
                elif "SELL" in sig_type:
                    pending_order = {
                        "type": "SHORT",
                        "signal_type": sig_type,
                        "sl": eval_res["stop_loss"],
                        "tp1": eval_res["take_profit_1"],
                        "tp2": eval_res["take_profit_2"]
                    }

        # ------------------------------------------------------------------
        # Compute Performance Metrics
        # ------------------------------------------------------------------
        total_trades = len(trades)
        winning_trades = [t for t in trades if t["is_win"]]
        losing_trades = [t for t in trades if not t["is_win"]]

        win_rate = (len(winning_trades) / total_trades * 100.0) if total_trades > 0 else 0.0
        gross_profit = sum(t["pnl_dollars"] for t in winning_trades)
        gross_loss = abs(sum(t["pnl_dollars"] for t in losing_trades))
        profit_factor = round(gross_profit / max(gross_loss, 1e-4), 2) if gross_loss > 0 else (9.99 if gross_profit > 0 else 0.0)
        net_profit = round(equity - initial_capital, 2)

        # Sharpe Ratio
        pnl_series = [t["pnl_dollars"] for t in trades]
        if len(pnl_series) > 1:
            mean_pnl = np.mean(pnl_series)
            std_pnl = np.std(pnl_series)
            sharpe = round(float((mean_pnl / max(std_pnl, 1e-4)) * math.sqrt(252)), 2)
        else:
            sharpe = 0.0

        outcome_result = {
            "status": "SUCCESS",
            "strategy_name": strategy_name,
            "timeframe": "15m",
            "initial_capital": initial_capital,
            "final_equity": round(equity, 2),
            "net_profit": net_profit,
            "total_trades": total_trades,
            "winning_trades": len(winning_trades),
            "losing_trades": len(losing_trades),
            "win_rate_pct": round(win_rate, 2),
            "profit_factor": profit_factor,
            "max_drawdown_pct": round(max_drawdown_pct, 2),
            "sharpe_ratio": sharpe,
            "look_ahead_bias_prevented": True,
            "execution_model": "EVENT_DRIVEN_BAR_OPEN_FILL",
            "recent_trades": trades[-8:] if trades else [],
            "timestamp": datetime.datetime.utcnow().isoformat()
        }

        # ------------------------------------------------------------------
        # Persist calibration outcome to database
        # ------------------------------------------------------------------
        if db_session:
            try:
                outcome_record = BacktestOutcome(
                    strategy_name=strategy_name,
                    timeframe="15m",
                    total_trades=total_trades,
                    winning_trades=len(winning_trades),
                    losing_trades=len(losing_trades),
                    win_rate_pct=round(win_rate, 2),
                    profit_factor=profit_factor,
                    max_drawdown_pct=round(max_drawdown_pct, 2),
                    net_profit=net_profit,
                    sharpe_ratio=sharpe,
                    initial_capital=initial_capital,
                    final_equity=round(equity, 2),
                    trades_json=json.dumps(trades[-20:]),
                    calibration_notes=f"Zero Look-Ahead Event-Driven Calibration: {total_trades} trades, {win_rate:.1f}% WR"
                )
                db_session.add(outcome_record)
                await db_session.commit()
                await db_session.refresh(outcome_record)
                outcome_result["saved_db_id"] = outcome_record.id
            except Exception as e:
                # Log and continue
                print(f"Warning: could not persist backtest outcome to database: {e}")

        return outcome_result
