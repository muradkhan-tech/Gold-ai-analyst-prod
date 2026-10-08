import datetime
from typing import List, Dict, Any

class EconomicCalendarService:
    """Provides high-impact gold volatility macroeconomic calendar releases."""
    
    @staticmethod
    def get_upcoming_events() -> List[Dict[str, Any]]:
        now = datetime.datetime.utcnow()
        return [
            {
                "id": 1,
                "title": "US Non-Farm Payrolls (NFP)",
                "country": "USD",
                "impact": "HIGH",
                "timestamp": (now + datetime.timedelta(hours=14)).isoformat(),
                "actual": None,
                "forecast": "175K",
                "previous": "142K",
                "gold_bias": "BEARISH_IF_BEAT",
                "notes": "Strong employment signals Fed rate pause; bearish for non-yielding bullion."
            },
            {
                "id": 2,
                "title": "US Consumer Price Index (CPI) YoY",
                "country": "USD",
                "impact": "HIGH",
                "timestamp": (now + datetime.timedelta(days=1, hours=6)).isoformat(),
                "actual": None,
                "forecast": "2.6%",
                "previous": "2.5%",
                "gold_bias": "BULLISH_IF_STICKY",
                "notes": "Persistent inflation fuels gold's classic hedge appeal against fiat devaluation."
            },
            {
                "id": 3,
                "title": "FOMC Federal Funds Rate Decision & Press Conference",
                "country": "USD",
                "impact": "HIGH",
                "timestamp": (now + datetime.timedelta(days=3)).isoformat(),
                "actual": None,
                "forecast": "4.50%",
                "previous": "4.75%",
                "gold_bias": "BULLISH_IF_DOVISH",
                "notes": "Rate cuts lower opportunity cost of holding gold bars."
            },
            {
                "id": 4,
                "title": "US Core Retail Sales MoM",
                "country": "USD",
                "impact": "MEDIUM",
                "timestamp": (now + datetime.timedelta(days=4)).isoformat(),
                "actual": None,
                "forecast": "0.3%",
                "previous": "0.1%",
                "gold_bias": "NEUTRAL",
                "notes": "Consumer spending health barometer."
            }
        ]
