import json
import datetime
import logging
from typing import List, Dict, Any, Optional
from fastapi import APIRouter, Header, HTTPException, Depends, Body
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from database.session import get_db
from database.models import TradingViewAlert
from backend.core.config import api_config_service

logger = logging.getLogger("GoldAI.TradingViewWebhook")

router = APIRouter(prefix="/webhooks", tags=["Webhooks"])

class TradingViewWebhookPayload(BaseModel):
    secret: Optional[str] = Field(None, description="Secret token if provided in body")
    indicator: str = Field(..., description="Name of custom proprietary indicator, e.g. 'Scalping with Dr Hafiz V2' or '3ESRA'")
    symbol: str = Field("XAUUSD", description="Ticker symbol")
    action: str = Field(..., description="Signal action, e.g. BUY, SELL, EXIT, ALERT")
    price: float = Field(..., description="Actual execution or trigger price")
    timeframe: str = Field("15m", description="Timeframe of alert, e.g. 1m, 3m, 5m, 15m, 1h")
    timestamp: Optional[str] = Field(None, description="ISO timestamp")
    message: Optional[str] = Field(None, description="Optional custom strategy notes or parameters")
    metrics: Optional[Dict[str, Any]] = Field(None, description="Actual indicator metrics provided in alert")

# In-memory alert store for real-time streaming
received_webhook_alerts: List[Dict[str, Any]] = []

@router.post("/tradingview", summary="Secure webhook endpoint for TradingView custom alerts")
async def receive_tradingview_alert(
    payload: TradingViewWebhookPayload,
    x_webhook_secret: Optional[str] = Header(None, alias="X-Webhook-Secret"),
    db: AsyncSession = Depends(get_db)
):
    """
    Receives JSON alerts from TradingView for custom proprietary indicators
    such as 'Scalping with Dr Hafiz V2', '3ESRA', and user PineScript strategies.
    Strictly protected with TRADINGVIEW_WEBHOOK_SECRET.
    Does not fabricate indicator values; only saves verified incoming alert data.
    """
    configured_secret = await api_config_service.get_key("TRADINGVIEW_WEBHOOK_SECRET", db=db)
    
    # Authenticate via header or payload body
    provided_secret = (x_webhook_secret or payload.secret or "").strip()

    if not configured_secret:
        logger.warning("TRADINGVIEW_WEBHOOK_SECRET is not configured on server.")
        raise HTTPException(
            status_code=401,
            detail="Unauthorized: TRADINGVIEW_WEBHOOK_SECRET is not configured on the server. Please set it in Settings."
        )

    if provided_secret != configured_secret.strip():
        logger.warning(f"Unauthorized TradingView webhook attempt for indicator '{payload.indicator}'")
        raise HTTPException(
            status_code=401,
            detail="Unauthorized: Invalid TRADINGVIEW_WEBHOOK_SECRET."
        )

    utc_now = datetime.datetime.now(datetime.timezone.utc)
    utc_now_iso = utc_now.isoformat()
    
    # Parse timestamp
    alert_ts = utc_now
    if payload.timestamp:
        try:
            alert_ts = datetime.datetime.fromisoformat(payload.timestamp.replace("Z", "+00:00"))
        except Exception:
            alert_ts = utc_now

    # Persist to DB if available
    try:
        db_alert = TradingViewAlert(
            indicator=payload.indicator.strip(),
            symbol=payload.symbol.upper(),
            action=payload.action.upper(),
            price=float(payload.price),
            timeframe=payload.timeframe,
            metrics_json=json.dumps(payload.metrics or {}),
            message=payload.message,
            timestamp=alert_ts.replace(tzinfo=None),
            received_at=utc_now.replace(tzinfo=None),
            verified=True
        )
        db.add(db_alert)
        await db.commit()
        await db.refresh(db_alert)
        alert_db_id = db_alert.id
    except Exception as db_err:
        logger.warning(f"Could not persist alert to database: {db_err}")
        alert_db_id = len(received_webhook_alerts) + 1

    alert_record = {
        "id": alert_db_id,
        "indicator": payload.indicator.strip(),
        "symbol": payload.symbol.upper(),
        "action": payload.action.upper(),
        "price": payload.price,
        "timeframe": payload.timeframe,
        "timestamp": payload.timestamp or utc_now_iso,
        "received_at": utc_now_iso,
        "message": payload.message,
        "metrics": payload.metrics or {},
        "verified": True
    }

    # Store in memory (bounded to last 100 alerts)
    received_webhook_alerts.insert(0, alert_record)
    if len(received_webhook_alerts) > 100:
        received_webhook_alerts.pop()

    logger.info(f"Verified TradingView alert recorded: [{payload.indicator}] {payload.action} @ ${payload.price} ({payload.timeframe})")

    return {
        "status": "success",
        "message": f"Verified TradingView alert recorded for {payload.indicator}",
        "alert_id": alert_record["id"],
        "received_at": utc_now_iso
    }

@router.get("/tradingview", summary="List verified received TradingView proprietary alerts")
async def list_tradingview_alerts(db: AsyncSession = Depends(get_db)):
    """Retrieve historical verified alerts received from TradingView webhooks."""
    # Fetch from database if available, falling back to memory
    try:
        result = await db.execute(
            select(TradingViewAlert).order_by(TradingViewAlert.timestamp.desc()).limit(50)
        )
        db_alerts = result.scalars().all()
        if db_alerts:
            formatted = []
            for a in db_alerts:
                formatted.append({
                    "id": a.id,
                    "indicator": a.indicator,
                    "symbol": a.symbol,
                    "action": a.action,
                    "price": a.price,
                    "timeframe": a.timeframe,
                    "timestamp": a.timestamp.isoformat() if a.timestamp else None,
                    "received_at": a.received_at.isoformat() if a.received_at else None,
                    "message": a.message,
                    "metrics": json.loads(a.metrics_json) if a.metrics_json else {},
                    "verified": a.verified
                })
            return {
                "total_count": len(formatted),
                "alerts": formatted
            }
    except Exception as e:
        logger.debug(f"Falling back to in-memory alerts: {e}")

    return {
        "total_count": len(received_webhook_alerts),
        "alerts": received_webhook_alerts
    }
