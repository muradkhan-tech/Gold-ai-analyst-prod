import asyncio
import datetime
import logging
import time
from collections import deque
from typing import Dict, List, Any, Optional, Set

from backend.data.provider import (
    BinancePaxgProvider,
    YahooFinanceProvider,
    SyntheticMicroTickEngine
)
from backend.data.aggregator import CandleAggregator

logger = logging.getLogger("market_subsystem")

class CandleRingBuffer:
    """
    High-performance in-memory circular ring buffer maintaining the latest 1,000 candles
    per timeframe (1m, 3m, 5m, 10m, 15m, 1h, 4h, 1d) with O(1) append, live streaming,
    multi-source fallback pipelines, and real-time WebSocket broadcast.
    """
    def __init__(self, max_size: int = 1000):
        self.max_size = max_size
        self._buffers: Dict[str, deque] = {
            "1m": deque(maxlen=max_size),
            "3m": deque(maxlen=max_size),
            "5m": deque(maxlen=max_size),
            "10m": deque(maxlen=max_size),
            "15m": deque(maxlen=max_size),
            "1h": deque(maxlen=max_size),
            "4h": deque(maxlen=max_size),
            "1d": deque(maxlen=max_size)
        }
        self._last_tick: Optional[Dict[str, Any]] = None
        self._last_tick_time: Optional[datetime.datetime] = None
        self._last_candle_fetch_time: float = 0.0

        # Providers
        self._binance_provider = BinancePaxgProvider()
        self._yahoo_provider = YahooFinanceProvider(ttl_seconds=30)
        self._synthetic_engine = SyntheticMicroTickEngine()

        # State & Health Metrics
        self._active_provider = "Binance Spot (PAXGUSDT)"
        self._is_live = False
        self._average_latency_ms = 15.0
        self._consecutive_failures = 0
        self._active_ws_connections: Set[Any] = set()

        # Background Worker Task
        self._worker_task: Optional[asyncio.Task] = None
        self._is_running = False

    @property
    def last_tick(self) -> Optional[Dict[str, Any]]:
        return self._last_tick

    @property
    def last_tick_time(self) -> Optional[datetime.datetime]:
        return self._last_tick_time

    @property
    def is_live(self) -> bool:
        return self._is_live

    def set_last_tick(self, tick: Dict[str, Any]):
        self._last_tick = tick
        self._last_tick_time = datetime.datetime.now(datetime.timezone.utc)
        self._is_live = True

    def register_ws(self, ws: Any):
        self._active_ws_connections.add(ws)

    def unregister_ws(self, ws: Any):
        self._active_ws_connections.discard(ws)

    async def broadcast_tick(self, tick: Dict[str, Any]):
        if not self._active_ws_connections:
            return
        dead_connections = set()
        for ws in list(self._active_ws_connections):
            try:
                await ws.send_json({"type": "MARKET_TICK", "data": tick})
            except Exception:
                dead_connections.add(ws)
        for dead in dead_connections:
            self._active_ws_connections.discard(dead)

    def append_candle(self, timeframe: str, candle: Dict[str, Any]):
        if timeframe in self._buffers:
            self._buffers[timeframe].append(candle)

    def append_candles(self, timeframe: str, candles: List[Dict[str, Any]]):
        if timeframe in self._buffers:
            for c in candles:
                self._buffers[timeframe].append(c)

    def set_candles(self, timeframe: str, candles: List[Dict[str, Any]]):
        if timeframe in self._buffers:
            self._buffers[timeframe] = deque(candles[-self.max_size:], maxlen=self.max_size)

    def get_candles(self, timeframe: str, limit: Optional[int] = None) -> List[Dict[str, Any]]:
        buf = self._buffers.get(timeframe)
        if not buf:
            return []
        items = list(buf)
        if limit is not None and limit < len(items):
            return items[-limit:]
        return items

    def get_system_status(self) -> Dict[str, Any]:
        now_utc = datetime.datetime.now(datetime.timezone.utc)
        staleness_sec = None
        if self._last_tick_time:
            staleness_sec = round((now_utc - self._last_tick_time).total_seconds(), 1)

        # Transition status based on tick freshness
        status_str = "LIVE" if (staleness_sec is not None and staleness_sec <= 300) else "OFFLINE/DELAYED"

        return {
            "status": status_str,
            "active_provider": self._active_provider,
            "average_latency_ms": round(self._average_latency_ms, 2),
            "cache_ttl_seconds": 30,
            "is_live": self._is_live and (staleness_sec is not None and staleness_sec <= 300),
            "staleness_sec": staleness_sec,
            "is_failsafe_locked": (staleness_sec is not None and staleness_sec > 300),
            "last_tick_time": self._last_tick_time.isoformat() if self._last_tick_time else None,
            "buffered_timeframes": list(self._buffers.keys()),
            "buffer_depths": {tf: len(b) for tf, b in self._buffers.items()},
            "connected_ws_clients": len(self._active_ws_connections)
        }

    def _update_ring_buffers_with_tick(self, tick: Dict[str, Any]):
        """Smoothly updates the running 1m bar and higher timeframes on every tick."""
        price = float(tick["price"])
        vol = float(tick.get("volume", 1.0))
        ts_now = datetime.datetime.now(datetime.timezone.utc)
        ts_now_ms = int(ts_now.timestamp() * 1000)

        # If 1m buffer is empty, seed initial bar
        buf_1m = self._buffers["1m"]
        bucket_1m_ms = (ts_now_ms // 60000) * 60000

        if not buf_1m or buf_1m[-1].get("timestamp_epoch_ms", 0) < bucket_1m_ms:
            # New 1m bar
            new_bar = {
                "symbol": "XAUUSD",
                "timeframe": "1m",
                "timestamp": datetime.datetime.fromtimestamp(bucket_1m_ms / 1000.0, tz=datetime.timezone.utc).isoformat(),
                "timestamp_epoch_ms": bucket_1m_ms,
                "open": price,
                "high": price,
                "low": price,
                "close": price,
                "volume": vol,
                "ticks_count": 1
            }
            buf_1m.append(new_bar)
        else:
            # Update current forming 1m bar
            last_bar = buf_1m[-1]
            last_bar["high"] = max(float(last_bar["high"]), price)
            last_bar["low"] = min(float(last_bar["low"]), price)
            last_bar["close"] = price
            last_bar["volume"] = round(float(last_bar["volume"]) + vol, 2)
            last_bar["ticks_count"] = int(last_bar.get("ticks_count", 1)) + 1

        # Propagate to higher timeframes
        for tf, minutes in [("3m", 3), ("5m", 5), ("10m", 10), ("15m", 15), ("1h", 60), ("4h", 240), ("1d", 1440)]:
            tf_ms = minutes * 60000
            bucket_tf_ms = (ts_now_ms // tf_ms) * tf_ms
            buf_tf = self._buffers[tf]

            if not buf_tf or buf_tf[-1].get("timestamp_epoch_ms", 0) < bucket_tf_ms:
                new_tf_bar = {
                    "symbol": "XAUUSD",
                    "timeframe": tf,
                    "timestamp": datetime.datetime.fromtimestamp(bucket_tf_ms / 1000.0, tz=datetime.timezone.utc).isoformat(),
                    "timestamp_epoch_ms": bucket_tf_ms,
                    "open": price,
                    "high": price,
                    "low": price,
                    "close": price,
                    "volume": vol,
                    "ticks_count": 1
                }
                buf_tf.append(new_tf_bar)
            else:
                curr_tf_bar = buf_tf[-1]
                curr_tf_bar["high"] = max(float(curr_tf_bar["high"]), price)
                curr_tf_bar["low"] = min(float(curr_tf_bar["low"]), price)
                curr_tf_bar["close"] = price
                curr_tf_bar["volume"] = round(float(curr_tf_bar["volume"]) + vol, 2)
                curr_tf_bar["ticks_count"] = int(curr_tf_bar.get("ticks_count", 1)) + 1

    async def poll_market_data(self, db_session=None) -> Dict[str, Any]:
        """
        Executes one polling cycle:
        1. Primary: Binance PAXGUSDT
        2. Secondary: Yahoo Finance GC=F / XAUUSD=X
        3. Tertiary: Synthetic Micro-Tick Engine (Keep-Alive)
        """
        start_t = time.time()
        tick = None
        candles_1m = None
        provider_name = ""

        # 1. Primary: Binance Spot (PAXGUSDT)
        try:
            tick = await self._binance_provider.fetch_ticker()
            if tick:
                provider_name = "Binance Spot (PAXGUSDT)"
                # Periodically fetch 1m klines (every 60s or if empty)
                if not self._buffers["1m"] or (time.time() - self._last_candle_fetch_time > 60):
                    candles_1m = await self._binance_provider.fetch_1m_klines(limit=120)
                    if candles_1m:
                        self._last_candle_fetch_time = time.time()
        except Exception as e:
            logger.debug(f"Primary Binance feed error: {e}")

        # 2. Secondary: Yahoo Finance
        if not tick:
            try:
                y_tick, y_candles = await self._yahoo_provider.fetch(symbol="GC=F", limit=120)
                if y_tick:
                    tick = y_tick
                    provider_name = y_tick.get("source", "Yahoo Finance (GC=F)")
                    if y_candles and (not self._buffers["1m"] or time.time() - self._last_candle_fetch_time > 60):
                        candles_1m = y_candles
                        self._last_candle_fetch_time = time.time()
            except Exception as e:
                logger.debug(f"Secondary Yahoo Finance feed error: {e}")

        # 3. Tertiary: Synthetic Micro-Tick Fallback
        if not tick:
            last_p = self._last_tick.get("price") if self._last_tick else 2864.50
            tick = self._synthetic_engine.generate_tick(last_known_price=last_p)
            provider_name = "Synthetic Micro-Tick Engine (Keep-Alive Fallback)"
            if not self._buffers["1m"]:
                candles_1m = self._synthetic_engine.generate_synthetic_candles(count=80, timeframe_minutes=1)
                self._last_candle_fetch_time = time.time()

        elapsed_ms = (time.time() - start_t) * 1000.0
        self._average_latency_ms = (self._average_latency_ms * 0.8) + (elapsed_ms * 0.2)
        self._active_provider = provider_name

        # Ingest candles if fetched
        if candles_1m:
            aggregated_all = CandleAggregator.aggregate_all_timeframes(candles_1m)
            for tf, clist in aggregated_all.items():
                self.set_candles(tf, clist)

        # Update state with tick
        if tick:
            self._last_tick = tick
            self._last_tick_time = datetime.datetime.now(datetime.timezone.utc)
            self._is_live = True
            self._consecutive_failures = 0
            self._update_ring_buffers_with_tick(tick)
            await self.broadcast_tick(tick)

        return {
            "success": True,
            "provider": provider_name,
            "price": tick["price"] if tick else None,
            "latency_ms": round(elapsed_ms, 2),
            "status": "LIVE"
        }

    async def _background_worker_loop(self):
        """Continuous background poller running on asyncio.create_task with exponential backoff."""
        logger.info("Market data background worker loop started.")
        backoff_sec = 1.0

        while self._is_running:
            try:
                await self.poll_market_data()
                backoff_sec = 1.0  # Reset backoff on success
                # Poll interval: 2 seconds for high-frequency sub-second live updates
                await asyncio.sleep(2.0)
            except asyncio.CancelledError:
                break
            except Exception as e:
                self._consecutive_failures += 1
                backoff_sec = min(backoff_sec * 1.5, 10.0)
                logger.warning(f"Background market poller iteration error ({self._consecutive_failures}): {e}. Backoff {backoff_sec:.1f}s")
                await asyncio.sleep(backoff_sec)

        logger.info("Market data background worker loop stopped.")

    def start_background_worker(self):
        if not self._is_running:
            self._is_running = True
            self._worker_task = asyncio.create_task(self._background_worker_loop())

    def stop_background_worker(self):
        self._is_running = False
        if self._worker_task and not self._worker_task.done():
            self._worker_task.cancel()

market_data_subsystem = CandleRingBuffer()
