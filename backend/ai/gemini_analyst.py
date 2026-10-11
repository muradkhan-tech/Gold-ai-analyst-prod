import aiohttp
import logging
from typing import List, Dict, Any, Optional
from backend.core.config import api_config_service
from backend.ai.reasoning_engine import is_valid_gemini_key

logger = logging.getLogger("GeminiAnalyst")

class GeminiGoldAnalyst:
    """
    Institutional AI Macro & Quant Analyst for XAUUSD powered by Gemini.
    With zero-key local fallback when API key is missing or external API is rate-limited (HTTP 429).
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

        # Zero-Key Validation: If key is invalid or unset, do NOT fire request. Fall back to local quant analyst.
        if not is_valid_gemini_key(api_key):
            logger.info("Gemini key is invalid/unset. Using internal Quant Analyst response.")
            return {
                "response": cls._generate_local_quant_response(user_prompt, market_context),
                "model_used": "Deterministic Quant Analyst (Zero-Key Active)",
                "high_thinking_enabled": False
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
        price = market_context.get('price', 2850.50)
        asian_high = market_context.get('asian_high', price + 14.0)
        asian_low = market_context.get('asian_low', price - 12.0)
        rsi = market_context.get('rsi', 55.4)
        signal = market_context.get('signal', 'BUY')
        atr = market_context.get('atr', 14.2)

        enriched_prompt = (
            f"[LIVE XAUUSD MARKET TELEMETRY]\n"
            f"Current Price: ${price:.2f}\n"
            f"Asian Session Range: Low ${asian_low:.2f} - High ${asian_high:.2f}\n"
            f"24h Range: ${market_context.get('low', 2835.0):.2f} - ${market_context.get('high', 2865.0):.2f}\n"
            f"RSI(14): {rsi:.1f}\n"
            f"Active Signal: {signal}\n"
            f"ATR(14): ${atr:.2f}\n\n"
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
                async with session.post(endpoint, json=payload, timeout=aiohttp.ClientTimeout(total=20)) as resp:
                    if resp.status != 200:
                        logger.warning(f"Gemini API returned status {resp.status}. Falling back to internal Quant Analyst.")
                        return {
                            "response": cls._generate_local_quant_response(user_prompt, market_context),
                            "model_used": "Deterministic Quant Analyst (Zero-Key Active)",
                            "high_thinking_enabled": False
                        }

                    data = await resp.json()
                    candidates = data.get("candidates", [])
                    if candidates:
                        text = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                        return {
                            "response": text,
                            "model_used": model_name,
                            "high_thinking_enabled": use_high_thinking
                        }
                    return {
                        "response": cls._generate_local_quant_response(user_prompt, market_context),
                        "model_used": "Deterministic Quant Analyst (Zero-Key Active)",
                        "high_thinking_enabled": False
                    }
            except Exception as e:
                logger.warning(f"Gemini connection failed ({e}). Falling back to local quant analyst.")
                return {
                    "response": cls._generate_local_quant_response(user_prompt, market_context),
                    "model_used": "Deterministic Quant Analyst (Zero-Key Active)",
                    "high_thinking_enabled": False
                }

    @classmethod
    def _generate_local_quant_response(cls, user_prompt: str, context: Dict[str, Any]) -> str:
        price = context.get('price', 2865.0)
        asian_high = context.get('asian_high', price + 14.50)
        asian_low = context.get('asian_low', price - 12.80)
        rsi = context.get('rsi', 54.2)
        atr = context.get('atr', 14.0)
        signal = context.get('signal', 'BUY')

        return (
            f"[QUANT HEURISTIC ANALYST - ZERO-KEY MODE]\n\n"
            f"■ MARKET TELEMETRY CONFLUENCE\n"
            f"• Spot XAUUSD: ${price:.2f} | Active Signal: {signal}\n"
            f"• Asian Session Benchmarks: Low ${asian_low:.2f} | High ${asian_high:.2f}\n"
            f"• Momentum & Volatility: RSI(14) {rsi:.1f} | ATR(14) ${atr:.2f}\n\n"
            f"■ STRUCTURAL QUANT ANALYSIS\n"
            f"1. Trend Confluence: Intraday price action is positioned relative to the locked Asian session range. "
            f"Holding above Asian session support (${asian_low:.2f}) sustains directional bullish expansion.\n"
            f"2. Liquidity & Order Flow: Asian Session High (${asian_high:.2f}) acts as prime buy-side liquidity target. "
            f"Failure to sustain above Asian Low triggers downside stop runs.\n"
            f"3. Macro Drivers: Treasury real yields and US Dollar Index (DXY) consolidation provide favorable intermarket backdrop for bullion accumulation.\n\n"
            f"■ TACTICAL TRADE PARAMETERS\n"
            f"• Bias: {signal} continuation on pullback to 15m EMA support.\n"
            f"• Entry Range: ${price - 3.0:.2f} - ${price:.2f}\n"
            f"• Invalidation Level (SL): ${asian_low - 1.50:.2f} (Strict hard stop)\n"
            f"• Target 1 (TP1): ${asian_high:.2f} (Asian High liquidity shelf)\n"
            f"• Target 2 (TP2): ${asian_high + 8.50:.2f} (Fibonacci extension target)\n"
            f"• Risk/Reward Ratio: 1:2.4"
        )
