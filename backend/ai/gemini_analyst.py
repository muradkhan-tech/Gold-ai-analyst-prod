import aiohttp
from typing import List, Dict, Any, Optional
from backend.core.config import api_config_service

class GeminiGoldAnalyst:
    """
    Institutional AI Macro & Quant Analyst for XAUUSD powered by Gemini.
    Uses 'gemini-3.1-pro-preview' with ThinkingLevel.HIGH for deep structural analysis.
    """
    SYSTEM_INSTRUCTION = """You are the Senior Institutional Gold (XAU/USD) Quantitative and Macro Analyst for GOLD AI ANALYST terminal.
Your responsibilities:
1. Provide precise technical analysis including multi-timeframe trend alignment, liquidity pools, Fair Value Gaps (FVG), key Support/Resistance, and momentum indicators (RSI, MACD, Bollinger Bands, ATR).
2. Integrate macroeconomic catalysts: US Dollar Index (DXY) correlation, US 10-Year Real Yields, Federal Reserve interest rate probabilities, CPI/PCE inflation, Non-Farm Payrolls, and Central Bank gold reserve accumulation.
3. Formulate clear, quantitative trade scenarios:
   - Direction (Bullish / Bearish / Mean Reverting Range)
   - High probability entry zones
   - Strict Stop-Loss (in invalidation level with dollar/pip risk)
   - Staged Take-Profit targets (TP1 conservative, TP2 runner)
   - Risk/Reward ratio calculation and invalidation criteria.
4. Maintain a professional, rigorous institutional Wall Street quant tone. Never offer generic platitudes."""

    @classmethod
    async def analyze_market(
        cls,
        user_prompt: str,
        market_context: Dict[str, Any],
        conversation_history: Optional[List[Dict[str, str]]] = None,
        use_high_thinking: bool = True,
        db_session: Any = None,
        user_id: Optional[int] = None
    ) -> Dict[str, Any]:
        api_key = await api_config_service.get_key("GEMINI_API_KEY", user_id=user_id, db=db_session)
        if not api_key:
            return {
                "error": "GEMINI_API_KEY is not configured. Please add your Gemini key in the settings panel."
            }

        model_name = "gemini-3.1-pro-preview" if use_high_thinking else "gemini-3.5-flash"
        endpoint = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={api_key}"

        contents = []
        if conversation_history:
            for msg in conversation_history:
                contents.append({
                    "role": msg.get("role", "user"),
                    "parts": [{"text": msg.get("content", "")}]
                })

        # Append current message enriched with live market telemetry
        enriched_prompt = (
            f"[LIVE XAUUSD MARKET TELEMETRY]\n"
            f"Current Price: ${market_context.get('price', 2850.50):.2f}\n"
            f"24h Range: ${market_context.get('low', 2835.0):.2f} - ${market_context.get('high', 2865.0):.2f}\n"
            f"RSI(14): {market_context.get('rsi', 55.4):.1f}\n"
            f"Active Signal: {market_context.get('signal', 'BUY')}\n"
            f"ATR(14): ${market_context.get('atr', 14.2):.2f}\n\n"
            f"User Query: {user_prompt}"
        )

        contents.append({
            "role": "user",
            "parts": [{"text": enriched_prompt}]
        })

        generation_config: Dict[str, Any] = {
            "temperature": 0.3,
            "topP": 0.95
        }
        if use_high_thinking:
            generation_config["thinkingConfig"] = {"thinkingLevel": "high"}

        payload = {
            "contents": contents,
            "systemInstruction": {
                "parts": [{"text": cls.SYSTEM_INSTRUCTION}]
            },
            "generationConfig": generation_config
        }

        async with aiohttp.ClientSession() as session:
            try:
                async with session.post(endpoint, json=payload, timeout=60) as resp:
                    if resp.status != 200:
                        error_text = await resp.text()
                        return {"error": f"Gemini API returned status {resp.status}: {error_text}"}
                    
                    data = await resp.json()
                    candidates = data.get("candidates", [])
                    if candidates:
                        text = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                        return {
                            "response": text,
                            "model_used": model_name,
                            "high_thinking_enabled": use_high_thinking
                        }
                    return {"error": "No response candidate generated"}
            except Exception as e:
                return {"error": f"Connection failed: {str(e)}"}
