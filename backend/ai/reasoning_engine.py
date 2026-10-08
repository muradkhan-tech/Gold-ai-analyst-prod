import json
import logging
import datetime
from typing import List, Dict, Any, Optional
import aiohttp
from sqlalchemy.ext.asyncio import AsyncSession
from backend.core.config import api_config_service
from database.models import FundamentalAnalysis

logger = logging.getLogger("AIReasoningEngine")

SYSTEM_PROMPT = """You are the Senior Institutional Gold Macro Economist & Quantitative Risk Officer for GOLD AI ANALYST.
You will receive structured JSON input containing:
1. Technical Analysis scores and timeframe alignment across 1m to 1h.
2. Upcoming macroeconomic catalysts (NFP, CPI, FOMC, Fed interest rates).
3. Filtered financial news strictly related to Gold, Federal Reserve, inflation, Treasury yields, and the US Dollar.

You MUST analyze the macroeconomic intermarket dynamics and return a STRICT JSON object with the following schema:
{
  "market_risk": "High" | "Medium" | "Low",
  "fundamental_bias": "Bullish" | "Bearish" | "Neutral",
  "confidence_score": 85.0,
  "key_drivers": [
    "Driver 1 explanation",
    "Driver 2 explanation",
    "Driver 3 explanation"
  ],
  "catalyst_impact": "Detailed assessment of near-term volatility catalysts",
  "recommended_action": "Tactical quant guidance",
  "detailed_synthesis": "Comprehensive multi-factor fundamental reasoning connecting technical alignment, Fed expectations, Treasury real yields, and physical bullion demand."
}

Rules:
- market_risk MUST be strictly one of: "High", "Medium", "Low".
- fundamental_bias MUST be strictly one of: "Bullish", "Bearish", "Neutral".
- Output valid JSON only."""

class AIReasoningEngine:
    """
    AI Fundamental Reasoning Engine integrating OpenAI (or Gemini).
    Accepts structured news, calendar events, and technical scores.
    Initializes dynamically and provides clean missing-key errors for frontend display.
    """

    async def evaluate_fundamentals(
        self,
        technical_scores: Dict[str, Any],
        calendar_events: List[Dict[str, Any]],
        news_articles: List[Dict[str, Any]],
        db_session: Optional[AsyncSession] = None,
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        openai_key = await api_config_service.get_key("OPENAI_API_KEY", db=db_session)
        gemini_key = await api_config_service.get_key("GEMINI_API_KEY", db=db_session)

        has_openai = bool(openai_key and openai_key.strip() and openai_key != "mock_openai_key")
        has_gemini = bool(gemini_key and gemini_key.strip() and gemini_key != "MY_GEMINI_API_KEY")

        if not has_openai and not has_gemini:
            return {
                "status": "ERROR",
                "error_type": "MISSING_API_KEY",
                "message": "Missing API Key: Please configure OPENAI_API_KEY (or GEMINI_API_KEY) in the Settings dashboard to enable live AI Fundamental Reasoning.",
                "analysis": None
            }

        input_payload = {
            "symbol": "XAUUSD",
            "timestamp": datetime.datetime.utcnow().isoformat(),
            "technical_scores": technical_scores,
            "upcoming_catalysts": calendar_events[:5],
            "filtered_macro_news": news_articles[:6]
        }

        try:
            if has_openai:
                analysis = await self._call_openai(openai_key.strip(), input_payload)
                provider = "OpenAI"
                model = "gpt-4o"
            else:
                analysis = await self._call_gemini(gemini_key.strip(), input_payload)
                provider = "Gemini"
                model = "gemini-2.5-flash"

            # Persist to database if db_session is available
            if db_session:
                try:
                    record = FundamentalAnalysis(
                        market_risk=analysis.get("market_risk", "Medium"),
                        fundamental_bias=analysis.get("fundamental_bias", "Neutral"),
                        confidence_score=float(analysis.get("confidence_score", 80.0)),
                        key_drivers=json.dumps(analysis.get("key_drivers", [])),
                        catalyst_impact=analysis.get("catalyst_impact", ""),
                        recommended_action=analysis.get("recommended_action", ""),
                        detailed_synthesis=analysis.get("detailed_synthesis", ""),
                        llm_provider=provider,
                        model_name=model
                    )
                    db_session.add(record)
                    await db_session.commit()
                    await db_session.refresh(record)
                except Exception as db_err:
                    logger.error(f"Error persisting fundamental analysis: {db_err}")

            return {
                "status": "SUCCESS",
                "provider": provider,
                "model": model,
                "analysis": analysis
            }
        except Exception as e:
            logger.error(f"AI evaluation failed: {e}")
            return {
                "status": "ERROR",
                "error_type": "LLM_CALL_FAILED",
                "message": f"Reasoning engine failed: {str(e)}",
                "analysis": None
            }

    async def _call_openai(self, api_key: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        url = "https://api.openai.com/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        body = {
            "model": "gpt-4o",
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": json.dumps(payload)}
            ],
            "response_format": {"type": "json_object"},
            "temperature": 0.2
        }

        async with aiohttp.ClientSession() as session:
            async with session.post(url, headers=headers, json=body, timeout=aiohttp.ClientTimeout(total=25)) as resp:
                if resp.status != 200:
                    text = await resp.text()
                    raise Exception(f"OpenAI API returned HTTP {resp.status}: {text}")
                data = await resp.json()
                content = data["choices"][0]["message"]["content"]
                return json.loads(content)

    async def _call_gemini(self, api_key: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={api_key}"
        headers = {"Content-Type": "application/json"}
        prompt_text = f"{SYSTEM_PROMPT}\n\nINPUT DATA:\n{json.dumps(payload)}"
        body = {
            "contents": [{"parts": [{"text": prompt_text}]}],
            "generationConfig": {"responseMimeType": "application/json"}
        }

        async with aiohttp.ClientSession() as session:
            async with session.post(url, headers=headers, json=body, timeout=aiohttp.ClientTimeout(total=25)) as resp:
                if resp.status != 200:
                    text = await resp.text()
                    raise Exception(f"Gemini API returned HTTP {resp.status}: {text}")
                data = await resp.json()
                raw_text = data["candidates"][0]["content"]["parts"][0]["text"]
                return json.loads(raw_text)

reasoning_engine = AIReasoningEngine()
