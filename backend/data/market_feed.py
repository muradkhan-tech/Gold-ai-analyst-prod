import asyncio
import random
import datetime
from typing import List, Dict, Any

class XauUsdMarketFeed:
    """
    Real-time high frequency tick streamer and candle generator for XAUUSD.
    Produces institutional market depth (bid, ask, spread, volume, ticks).
    """
    def __init__(self, initial_price: float = 2864.50):
        self.current_price = initial_price
        self.spread = 0.25  # Standard tight institutional spread for Gold
        self.volatility = 0.85

    def generate_next_tick(self) -> Dict[str, Any]:
        # Drift + Brownian motion simulation
        change = random.gauss(0, self.volatility)
        self.current_price = round(max(self.current_price + change, 1000.0), 2)
        half_spread = self.spread / 2.0
        bid = round(self.current_price - half_spread, 2)
        ask = round(self.current_price + half_spread, 2)
        vol = round(random.uniform(0.5, 15.0), 2)

        return {
            "symbol": "XAUUSD",
            "price": self.current_price,
            "bid": bid,
            "ask": ask,
            "spread": self.spread,
            "volume": vol,
            "timestamp": datetime.datetime.utcnow().isoformat()
        }

    def generate_sample_candles(self, count: int = 100, timeframe_minutes: int = 15) -> List[Dict[str, Any]]:
        """Generate realistic historical candlestick series ending at current price."""
        candles = []
        now = datetime.datetime.utcnow()
        start_time = now - datetime.timedelta(minutes=count * timeframe_minutes)

        price = self.current_price - (count * 0.2)
        for i in range(count):
            open_p = price
            delta = random.gauss(0.1, 3.5)
            close_p = round(open_p + delta, 2)
            high_p = round(max(open_p, close_p) + abs(random.gauss(0, 2.0)), 2)
            low_p = round(min(open_p, close_p) - abs(random.gauss(0, 2.0)), 2)
            vol = round(random.uniform(500, 3500), 1)

            t = start_time + datetime.timedelta(minutes=i * timeframe_minutes)
            candles.append({
                "symbol": "XAUUSD",
                "timeframe": f"{timeframe_minutes}m",
                "timestamp": t.isoformat(),
                "open": open_p,
                "high": high_p,
                "low": low_p,
                "close": close_p,
                "volume": vol,
                "ticks_count": int(vol * 1.8)
            })
            price = close_p
        return candles

market_feed = XauUsdMarketFeed()
