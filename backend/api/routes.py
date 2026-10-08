from fastapi import APIRouter, Depends, HTTPException, Query, Body
from typing import List, Dict, Any, Optional
import pandas as pd
from sqlalchemy.ext.asyncio import AsyncSession
from database.session import get_db
from backend.core.config import api_config_service
from backend.data.market_feed import market_feed
from backend.data.subsystem import market_data_subsystem
from backend.indicators.technical import TechnicalIndicators
from backend.indicators.alignment import MultiTimeframeAlignmentEngine
from backend.signals.engine import GoldSignalEngine
from backend.news.economic_calendar import EconomicCalendarService
from backend.news.news_provider import news_service
from backend.ai.gemini_analyst import GeminiGoldAnalyst
from backend.ai.reasoning_engine import reasoning_engine
from backend.backtesting.engine import QuantBacktester
from backend.api.webhooks import router as webhooks_router

router = APIRouter()
router.include_router(webhooks_router)

# --- Market Data Subsystem & Health Status ---
@router.get("/market/status", summary="Get real-time market data subsystem health, latency, and rate-limit status")
async def get_market_status():
    return market_data_subsystem.get_system_status()

@router.post("/market/poll", summary="Trigger market data polling cycle")
async def poll_market_now(db: AsyncSession = Depends(get_db)):
    report = await market_data_subsystem.poll_market_data(db_session=db)
    return report.to_dict()

# --- Market Ticks & Candles ---
@router.get("/ticks/latest", summary="Get latest high-frequency XAUUSD tick")
async def get_latest_tick():
    if market_data_subsystem.last_tick:
        return market_data_subsystem.last_tick
    return market_feed.generate_next_tick()

@router.get("/candles", summary="Get OHLCV historical candles for XAUUSD (1m, 3m, 5m, 10m, 15m, 1h)")
async def get_candles(
    timeframe: str = Query("15m", regex="^(1m|3m|5m|10m|15m|1h|4h|1d)$"),
    limit: int = Query(60, ge=10, le=500),
    db: AsyncSession = Depends(get_db)
):
    # Check if subsystem has aggregated candles
    cached = market_data_subsystem.get_candles(timeframe)
    if cached and len(cached) >= 10:
        return cached[-limit:]

    # Fallback to simulated series
    tf_minutes = 15
    if timeframe == "1m": tf_minutes = 1
    elif timeframe == "3m": tf_minutes = 3
    elif timeframe == "5m": tf_minutes = 5
    elif timeframe == "10m": tf_minutes = 10
    elif timeframe == "1h": tf_minutes = 60
    elif timeframe == "4h": tf_minutes = 240
    elif timeframe == "1d": tf_minutes = 1440

    return market_feed.generate_sample_candles(count=limit, timeframe_minutes=tf_minutes)

# --- Technical Indicators ---
@router.get("/indicators", summary="Calculate institutional technical indicators on live candles for specified timeframe")
async def get_indicators(
    timeframe: str = Query("15m", regex="^(1m|3m|5m|10m|15m|1h|4h|1d)$")
):
    cached = market_data_subsystem.get_candles(timeframe)
    if cached and len(cached) >= 14:
        candles = cached
    else:
        tf_min = 15
        if timeframe == "1m": tf_min = 1
        elif timeframe == "3m": tf_min = 3
        elif timeframe == "5m": tf_min = 5
        elif timeframe == "10m": tf_min = 10
        elif timeframe == "1h": tf_min = 60
        candles = market_feed.generate_sample_candles(count=80, timeframe_minutes=tf_min)

    df = pd.DataFrame(candles)
    enriched = TechnicalIndicators.compute_all(df)
    last = enriched.iloc[-1].to_dict()
    return {
        "symbol": "XAUUSD",
        "timeframe": timeframe,
        "price": float(last.get("close", 0.0)),
        "ema_8": round(float(last.get("ema_8", 0.0)), 2),
        "ema_21": round(float(last.get("ema_21", 0.0)), 2),
        "ema_28": round(float(last.get("ema_28", 0.0)), 2),
        "ema_50": round(float(last.get("ema_50", 0.0)), 2),
        "ema_200": round(float(last.get("ema_200", 0.0)), 2),
        "rsi_14": round(float(last.get("rsi_14", 50.0)), 2),
        "macd": round(float(last.get("macd", 0.0)), 2),
        "macd_signal": round(float(last.get("macd_signal", 0.0)), 2),
        "macd_hist": round(float(last.get("macd_hist", 0.0)), 2),
        "bb_upper": round(float(last.get("bb_upper", 0.0)), 2),
        "bb_middle": round(float(last.get("bb_middle", 0.0)), 2),
        "bb_lower": round(float(last.get("bb_lower", 0.0)), 2),
        "bb_bandwidth": round(float(last.get("bb_bandwidth", 0.0)), 2),
        "atr_14": round(float(last.get("atr_14", 10.0)), 2)
    }

@router.get("/indicators/all", summary="Calculate technical indicators across all intraday timeframes (1m to 1h)")
async def get_all_timeframe_indicators():
    timeframes = ["1m", "3m", "5m", "10m", "15m", "1h"]
    data_by_tf = {}
    for tf in timeframes:
        candles = market_data_subsystem.get_candles(tf)
        if not candles or len(candles) < 14:
            tf_min = 15
            if tf == "1m": tf_min = 1
            elif tf == "3m": tf_min = 3
            elif tf == "5m": tf_min = 5
            elif tf == "10m": tf_min = 10
            elif tf == "1h": tf_min = 60
            candles = market_feed.generate_sample_candles(count=60, timeframe_minutes=tf_min)
        data_by_tf[tf] = candles

    return TechnicalIndicators.calculate_for_timeframes(data_by_tf)

@router.get("/indicators/alignment", summary="Multi-timeframe trend alignment and scoring across 1m to 1h")
async def get_multi_timeframe_alignment():
    timeframes = ["1m", "3m", "5m", "10m", "15m", "1h"]
    snapshots = {}
    for tf in timeframes:
        candles = market_data_subsystem.get_candles(tf)
        if not candles:
            tf_min = 15
            if tf == "1m": tf_min = 1
            elif tf == "3m": tf_min = 3
            elif tf == "5m": tf_min = 5
            elif tf == "10m": tf_min = 10
            elif tf == "1h": tf_min = 60
            candles = market_feed.generate_sample_candles(count=50, timeframe_minutes=tf_min)
        df = pd.DataFrame(candles)
        enriched = TechnicalIndicators.compute_all(df)
        last = enriched.iloc[-1].to_dict()
        snapshots[tf] = {
            "price": float(last.get("close", 0.0)),
            "ema_8": float(last.get("ema_8", 0.0)),
            "ema_21": float(last.get("ema_21", 0.0)),
            "ema_28": float(last.get("ema_28", 0.0)),
            "ema_50": float(last.get("ema_50", 0.0)),
            "rsi_14": float(last.get("rsi_14", 50.0)),
            "macd_hist": float(last.get("macd_hist", 0.0))
        }
    return MultiTimeframeAlignmentEngine.evaluate_alignment(snapshots)

# --- Quantitative Signals ---
@router.get("/signals", summary="Get multi-timeframe consensus trading signal combining Technical, Fundamental, and AI")
async def get_signals(
    enforce_failsafe: bool = Query(True, description="Enforce strict failsafe on missing or stale market data"),
    db: AsyncSession = Depends(get_db)
):
    # Fetch candles from subsystem or feed
    candles = market_data_subsystem.get_candles("15m")
    if not candles:
        candles = market_feed.generate_sample_candles(count=60, timeframe_minutes=15)
    df = pd.DataFrame(candles)

    # Multi-timeframe snapshots
    timeframes = ["1m", "3m", "5m", "10m", "15m", "1h"]
    snapshots = {}
    for tf in timeframes:
        tf_candles = market_data_subsystem.get_candles(tf)
        if not tf_candles:
            tf_min = 15
            if tf == "1m": tf_min = 1
            elif tf == "3m": tf_min = 3
            elif tf == "5m": tf_min = 5
            elif tf == "10m": tf_min = 10
            elif tf == "1h": tf_min = 60
            tf_candles = market_feed.generate_sample_candles(count=40, timeframe_minutes=tf_min)
        tf_df = pd.DataFrame(tf_candles)
        enriched = TechnicalIndicators.compute_all(tf_df)
        last = enriched.iloc[-1].to_dict()
        snapshots[tf] = {
            "price": float(last.get("close", 0.0)),
            "ema_8": float(last.get("ema_8", 0.0)),
            "ema_21": float(last.get("ema_21", 0.0)),
            "ema_28": float(last.get("ema_28", 0.0)),
            "ema_50": float(last.get("ema_50", 0.0)),
            "rsi_14": float(last.get("rsi_14", 50.0)),
            "macd_hist": float(last.get("macd_hist", 0.0))
        }

    # Fetch news & calendar
    news_key = await api_config_service.get_key("NEWS_API_KEY", db=db)
    market_key = await api_config_service.get_key("MARKET_DATA_API_KEY", db=db)
    news_data = await news_service.get_filtered_news(news_api_key=news_key, market_data_api_key=market_key)
    calendar_events = EconomicCalendarService.get_upcoming_events()

    # Latest AI synthesis from DB if available
    ai_synthesis = None
    try:
        from database.models import FundamentalAnalysis
        import json
        from sqlalchemy.future import select
        res = await db.execute(select(FundamentalAnalysis).order_by(FundamentalAnalysis.created_at.desc()).limit(1))
        latest_fa = res.scalars().first()
        if latest_fa:
            ai_synthesis = {
                "analysis": {
                    "market_risk": latest_fa.market_risk,
                    "fundamental_bias": latest_fa.fundamental_bias,
                    "confidence_score": latest_fa.confidence_score,
                    "key_drivers": json.loads(latest_fa.key_drivers) if latest_fa.key_drivers else []
                }
            }
    except Exception:
        pass

    signal_res = GoldSignalEngine.evaluate_signals(
        candles_df=df,
        timeframe_snapshots=snapshots,
        fundamental_news=news_data.get("articles", []),
        calendar_events=calendar_events,
        ai_synthesis=ai_synthesis,
        latest_tick_timestamp=market_data_subsystem.last_tick_time if hasattr(market_data_subsystem, 'last_tick_time') else None,
        enforce_failsafe=enforce_failsafe
    )

    # Persist signal to database
    try:
        from database.models import Signal
        db_sig = Signal(
            symbol=signal_res["symbol"],
            strategy_name=signal_res["strategy_name"],
            signal_type=signal_res["signal_type"],
            timeframe=signal_res["timeframe"],
            entry_price=signal_res["entry_price"],
            entry_zone_low=signal_res["entry_zone_low"],
            entry_zone_high=signal_res["entry_zone_high"],
            stop_loss=signal_res["stop_loss"],
            take_profit_1=signal_res["take_profit_1"],
            take_profit_2=signal_res["take_profit_2"],
            confidence_score=signal_res["confidence_score"],
            risk_reward_ratio=signal_res["risk_reward_ratio"],
            status="ACTIVE",
            reasoning=signal_res["reasoning"]
        )
        db.add(db_sig)
        await db.commit()
        await db.refresh(db_sig)
        signal_res["db_id"] = db_sig.id
    except Exception as db_err:
        pass

    return signal_res

# --- Macroeconomic News & Events ---
@router.get("/news", summary="Get filtered macroeconomic news strictly for Gold, USD, Fed, inflation, yields")
async def get_filtered_news(
    category: Optional[str] = Query(None, description="Filter by: GOLD, FED_POLICY, INFLATION, TREASURY_YIELDS, USD_DXY"),
    limit: int = Query(20, ge=1, le=100),
    db: AsyncSession = Depends(get_db)
):
    news_key = await api_config_service.get_key("NEWS_API_KEY", db=db)
    market_key = await api_config_service.get_key("MARKET_DATA_API_KEY", db=db)
    news_data = await news_service.get_filtered_news(news_api_key=news_key, market_data_api_key=market_key)

    articles = news_data["articles"]
    if category:
        articles = [a for a in articles if a.get("category") == category.upper()]

    return {
        "provider": news_data["provider"],
        "is_live_external": news_data["is_live_external"],
        "filter_criteria": news_data["filter_criteria"],
        "total": len(articles),
        "articles": articles[:limit]
    }

@router.get("/news/calendar", summary="Get upcoming macroeconomic calendar releases")
async def get_calendar_events():
    return EconomicCalendarService.get_upcoming_events()

# --- AI Fundamental Reasoning Engine ---
@router.post("/ai/fundamentals", summary="AI Reasoning Engine: Evaluates news, calendar, and technical scores for market risk & fundamental bias")
async def evaluate_fundamentals(
    user_id: Optional[int] = Body(None, embed=True),
    db: AsyncSession = Depends(get_db)
):
    timeframes = ["1m", "3m", "5m", "10m", "15m", "1h"]
    snapshots = {}
    for tf in timeframes:
        candles = market_data_subsystem.get_candles(tf)
        if not candles:
            tf_min = 15
            if tf == "1m": tf_min = 1
            elif tf == "3m": tf_min = 3
            elif tf == "5m": tf_min = 5
            elif tf == "10m": tf_min = 10
            elif tf == "1h": tf_min = 60
            candles = market_feed.generate_sample_candles(count=50, timeframe_minutes=tf_min)
        df = pd.DataFrame(candles)
        enriched = TechnicalIndicators.compute_all(df)
        last = enriched.iloc[-1].to_dict()
        snapshots[tf] = {
            "price": float(last.get("close", 0.0)),
            "ema_8": float(last.get("ema_8", 0.0)),
            "ema_21": float(last.get("ema_21", 0.0)),
            "ema_28": float(last.get("ema_28", 0.0)),
            "ema_50": float(last.get("ema_50", 0.0)),
            "rsi_14": float(last.get("rsi_14", 50.0)),
            "macd_hist": float(last.get("macd_hist", 0.0))
        }
    alignment = MultiTimeframeAlignmentEngine.evaluate_alignment(snapshots)

    calendar_events = EconomicCalendarService.get_upcoming_events()

    news_key = await api_config_service.get_key("NEWS_API_KEY", db=db)
    market_key = await api_config_service.get_key("MARKET_DATA_API_KEY", db=db)
    news_data = await news_service.get_filtered_news(news_api_key=news_key, market_data_api_key=market_key)

    result = await reasoning_engine.evaluate_fundamentals(
        technical_scores=alignment,
        calendar_events=calendar_events,
        news_articles=news_data.get("articles", []),
        db_session=db,
        user_id=user_id
    )
    return result

@router.get("/ai/fundamentals/latest", summary="Retrieve latest stored fundamental reasoning synthesis")
async def get_latest_fundamental_analysis(db: AsyncSession = Depends(get_db)):
    try:
        import json
        from sqlalchemy.future import select
        from database.models import FundamentalAnalysis
        res = await db.execute(
            select(FundamentalAnalysis).order_by(FundamentalAnalysis.created_at.desc()).limit(1)
        )
        item = res.scalars().first()
        if item:
            return {
                "status": "SUCCESS",
                "analysis": {
                    "market_risk": item.market_risk,
                    "fundamental_bias": item.fundamental_bias,
                    "confidence_score": item.confidence_score,
                    "key_drivers": json.loads(item.key_drivers) if item.key_drivers else [],
                    "catalyst_impact": item.catalyst_impact,
                    "recommended_action": item.recommended_action,
                    "detailed_synthesis": item.detailed_synthesis,
                    "llm_provider": item.llm_provider,
                    "model_name": item.model_name,
                    "evaluated_at": item.created_at.isoformat() if item.created_at else None
                }
            }
    except Exception as e:
        pass
    return {
        "status": "NO_ANALYSIS",
        "message": "No historical analysis stored yet."
    }

# --- Gemini AI Analyst (Chat + High Thinking) ---
@router.post("/ai/chat", summary="Chat with Institutional Gemini Gold Analyst")
async def chat_with_analyst(
    prompt: str = Body(..., embed=True),
    use_high_thinking: bool = Body(True, embed=True),
    history: Optional[List[Dict[str, str]]] = Body(None, embed=True),
    user_id: Optional[int] = Body(None, embed=True),
    db: AsyncSession = Depends(get_db)
):
    tick = market_feed.generate_next_tick()
    candles = market_feed.generate_sample_candles(count=40, timeframe_minutes=15)
    df = pd.DataFrame(candles)
    sig = GoldSignalEngine.evaluate_signals(df)

    context = {
        "price": tick["price"],
        "high": max(c["high"] for c in candles),
        "low": min(c["low"] for c in candles),
        "rsi": 54.2,
        "signal": sig.get("signal_type", "BUY"),
        "atr": 13.5
    }

    result = await GeminiGoldAnalyst.analyze_market(
        user_prompt=prompt,
        market_context=context,
        conversation_history=history,
        use_high_thinking=use_high_thinking,
        db_session=db,
        user_id=user_id
    )
    return result

# --- Backtesting ---
@router.post("/backtest", summary="Execute event-driven quantitative backtest with zero look-ahead bias")
async def run_backtest(
    strategy: str = Body("MultiTimeframeConsensusEngine", embed=True),
    candles_count: int = Body(150, embed=True),
    initial_capital: float = Body(10000.0, embed=True),
    db: AsyncSession = Depends(get_db)
):
    candles = market_feed.generate_sample_candles(count=max(60, candles_count), timeframe_minutes=15)
    df = pd.DataFrame(candles)
    return await QuantBacktester.run_event_driven_backtest(
        candles_df=df,
        strategy_name=strategy,
        initial_capital=initial_capital,
        db_session=db
    )

@router.get("/backtest/history", summary="Retrieve historical backtest calibrations and performance records")
async def get_backtest_history(
    limit: int = Query(10, ge=1, le=50),
    db: AsyncSession = Depends(get_db)
):
    from database.models import BacktestOutcome
    from sqlalchemy.future import select
    import json
    res = await db.execute(
        select(BacktestOutcome).order_by(BacktestOutcome.created_at.desc()).limit(limit)
    )
    records = res.scalars().all()
    return [
        {
            "id": r.id,
            "strategy_name": r.strategy_name,
            "timeframe": r.timeframe,
            "total_trades": r.total_trades,
            "winning_trades": r.winning_trades,
            "losing_trades": r.losing_trades,
            "win_rate_pct": r.win_rate_pct,
            "profit_factor": r.profit_factor,
            "max_drawdown_pct": r.max_drawdown_pct,
            "net_profit": r.net_profit,
            "sharpe_ratio": r.sharpe_ratio,
            "initial_capital": r.initial_capital,
            "final_equity": r.final_equity,
            "calibration_notes": r.calibration_notes,
            "created_at": r.created_at.isoformat() if r.created_at else None
        }
        for r in records
    ]

# --- Dynamic API Key Management (DB with .env fallback) ---
@router.get("/config/keys", summary="Check configuration status of all dynamic API keys")
async def get_key_statuses(
    user_id: Optional[int] = None,
    db: AsyncSession = Depends(get_db)
):
    return await api_config_service.get_all_statuses(user_id=user_id, db=db)

@router.post("/config/keys", summary="Save or update dynamic API key via frontend UI")
async def update_key(
    key_name: str = Body(..., embed=True),
    key_value: str = Body(..., embed=True),
    description: Optional[str] = Body(None, embed=True),
    user_id: Optional[int] = Body(None, embed=True),
    db: AsyncSession = Depends(get_db)
):
    valid_keys = [
        "MARKET_DATA_API_KEY",
        "NEWS_API_KEY",
        "OPENAI_API_KEY",
        "GEMINI_API_KEY",
        "TRADINGVIEW_WEBHOOK_SECRET"
    ]
    if key_name not in valid_keys:
        raise HTTPException(status_code=400, detail=f"Invalid key_name. Allowed: {valid_keys}")

    record = await api_config_service.set_key(
        key_name=key_name,
        key_value=key_value,
        user_id=user_id,
        db=db,
        description=description
    )
    return {
        "success": True,
        "message": f"Successfully updated key '{key_name}' in database",
        "key_name": record.key_name,
        "updated_at": record.updated_at.isoformat() if record.updated_at else None
    }
