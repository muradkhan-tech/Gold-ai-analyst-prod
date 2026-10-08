import time
import threading
from typing import Any, Optional, Dict

class InMemoryTTLCache:
    """
    Lightweight, thread-safe in-memory cache with Time-To-Live (TTL) expiration.
    Eliminates external Redis dependencies and guarantees zero-configuration operation.
    """
    def __init__(self, default_ttl_seconds: int = 60):
        self.default_ttl = default_ttl_seconds
        self._cache: Dict[str, tuple[Any, float]] = {}
        self._lock = threading.Lock()

    def get(self, key: str) -> Optional[Any]:
        with self._lock:
            item = self._cache.get(key)
            if not item:
                return None
            val, expiry = item
            if time.time() > expiry:
                del self._cache[key]
                return None
            return val

    def set(self, key: str, value: Any, ttl_seconds: Optional[int] = None):
        ttl = ttl_seconds if ttl_seconds is not None else self.default_ttl
        with self._lock:
            self._cache[key] = (value, time.time() + ttl)

    def delete(self, key: str):
        with self._lock:
            self._cache.pop(key, None)

    def clear(self):
        with self._lock:
            self._cache.clear()

class IPRateLimiter:
    """
    In-memory rate limiter for authentication & protected routes to prevent brute-force attacks.
    Tracks request timestamps within a sliding window.
    """
    def __init__(self, max_requests: int = 5, window_seconds: int = 60):
        self.max_requests = max_requests
        self.window_seconds = window_seconds
        self._ip_history: Dict[str, list[float]] = {}
        self._lock = threading.Lock()

    def is_allowed(self, ip: str) -> bool:
        now = time.time()
        with self._lock:
            history = self._ip_history.get(ip, [])
            # Prune older than window
            history = [t for t in history if now - t < self.window_seconds]
            if len(history) >= self.max_requests:
                self._ip_history[ip] = history
                return False
            history.append(now)
            self._ip_history[ip] = history
            return True

    def reset(self, ip: str):
        with self._lock:
            self._ip_history.pop(ip, None)

# Shared global instances
market_cache = InMemoryTTLCache(default_ttl_seconds=60) # 60s TTL for market data
news_cache = InMemoryTTLCache(default_ttl_seconds=900)   # 15m (900s) TTL for news/macro
auth_rate_limiter = IPRateLimiter(max_requests=10, window_seconds=60)
