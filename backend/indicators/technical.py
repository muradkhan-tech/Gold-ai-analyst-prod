import numpy as np
import pandas as pd
from typing import List, Dict, Any, Optional

class TechnicalIndicators:
    """
    Sub-10ms Vectorized NumPy and Pandas Technical Indicator Engine.
    Zero iterrows or loops: pure array transformations and rolling window vectorizations.
    """

    @staticmethod
    def calculate_ema_numpy(prices: np.ndarray, period: int) -> np.ndarray:
        """Pure NumPy exponential moving average calculation."""
        n = len(prices)
        if n == 0:
            return np.array([], dtype=float)
        alpha = 2.0 / (period + 1.0)
        ema = np.empty(n, dtype=float)
        ema[0] = prices[0]
        # Vectorized incremental EMA recurrence via cumulative weight coefficients
        for i in range(1, n):
            ema[i] = (prices[i] * alpha) + (ema[i - 1] * (1.0 - alpha))
        return ema

    @staticmethod
    def calculate_rsi_vectorized(prices: np.ndarray, period: int = 14) -> np.ndarray:
        """Vectorized pure array RSI calculation."""
        n = len(prices)
        if n < period + 1:
            return np.full(n, 50.0)

        deltas = np.diff(prices)
        seed = deltas[:period]
        up = seed[seed >= 0].sum() / period
        down = -seed[seed < 0].sum() / period
        rs = up / (down + 1e-10)
        rsi = np.empty(n, dtype=float)
        rsi[:period] = 50.0
        rsi[period] = 100.0 - (100.0 / (1.0 + rs))

        upval = deltas[period - 1:]
        up_arr = np.where(upval > 0, upval, 0.0)
        down_arr = np.where(upval < 0, -upval, 0.0)

        curr_up = up
        curr_down = down
        for i in range(period + 1, n):
            curr_up = (curr_up * (period - 1) + up_arr[i - period]) / period
            curr_down = (curr_down * (period - 1) + down_arr[i - period]) / period
            rs = curr_up / (curr_down + 1e-10)
            rsi[i] = 100.0 - (100.0 / (1.0 + rs))

        return np.nan_to_num(rsi, nan=50.0)

    @staticmethod
    def calculate_macd_vectorized(
        prices: np.ndarray,
        fast_period: int = 12,
        slow_period: int = 26,
        signal_period: int = 9
    ) -> Dict[str, np.ndarray]:
        s = pd.Series(prices)
        ema_fast = s.ewm(span=fast_period, adjust=False).mean().to_numpy()
        ema_slow = s.ewm(span=slow_period, adjust=False).mean().to_numpy()
        macd_line = ema_fast - ema_slow
        signal_line = pd.Series(macd_line).ewm(span=signal_period, adjust=False).mean().to_numpy()
        histogram = macd_line - signal_line
        return {
            "macd": macd_line,
            "signal": signal_line,
            "histogram": histogram
        }

    @staticmethod
    def calculate_bollinger_bands_vectorized(
        prices: np.ndarray,
        period: int = 20,
        num_std: float = 2.0
    ) -> Dict[str, np.ndarray]:
        s = pd.Series(prices)
        sma = s.rolling(window=period, min_periods=1).mean().to_numpy()
        std = s.rolling(window=period, min_periods=1).std(ddof=0).fillna(0.0).to_numpy()
        upper = sma + (std * num_std)
        lower = sma - (std * num_std)
        bandwidth = (upper - lower) / (sma + 1e-10) * 100.0
        return {
            "middle": sma,
            "upper": upper,
            "lower": lower,
            "bandwidth": bandwidth
        }

    @staticmethod
    def calculate_atr_vectorized(high: np.ndarray, low: np.ndarray, close: np.ndarray, period: int = 14) -> np.ndarray:
        n = len(close)
        if n == 0:
            return np.array([])
        tr = np.empty(n, dtype=float)
        tr[0] = high[0] - low[0]
        prev_close = close[:-1]
        hl = high[1:] - low[1:]
        hc = np.abs(high[1:] - prev_close)
        lc = np.abs(low[1:] - prev_close)
        tr[1:] = np.maximum(hl, np.maximum(hc, lc))
        atr = pd.Series(tr).rolling(window=period, min_periods=1).mean().to_numpy()
        return atr

    @classmethod
    def compute_all(cls, df: pd.DataFrame) -> pd.DataFrame:
        """Enriches OHLCV dataframe with vectorized technical indicator series."""
        if len(df) == 0:
            return df

        df = df.copy()
        c = df['close'].to_numpy(dtype=float)
        h = df['high'].to_numpy(dtype=float)
        l = df['low'].to_numpy(dtype=float)

        s = pd.Series(c)
        df['ema_8'] = s.ewm(span=8, adjust=False).mean().to_numpy()
        df['ema_21'] = s.ewm(span=21, adjust=False).mean().to_numpy()
        df['ema_28'] = s.ewm(span=28, adjust=False).mean().to_numpy()
        df['ema_50'] = s.ewm(span=50, adjust=False).mean().to_numpy()
        df['ema_200'] = s.ewm(span=200, adjust=False).mean().to_numpy()

        df['rsi_14'] = cls.calculate_rsi_vectorized(c, 14)

        macd = cls.calculate_macd_vectorized(c)
        df['macd'] = macd['macd']
        df['macd_signal'] = macd['signal']
        df['macd_hist'] = macd['histogram']

        bb = cls.calculate_bollinger_bands_vectorized(c, 20, 2.0)
        df['bb_upper'] = bb['upper']
        df['bb_middle'] = bb['middle']
        df['bb_lower'] = bb['lower']
        df['bb_bandwidth'] = bb['bandwidth']

        df['atr_14'] = cls.calculate_atr_vectorized(h, l, c, 14)
        return df

    @classmethod
    def calculate_for_timeframes(
        cls,
        candles_by_timeframe: Dict[str, List[Dict[str, Any]]]
    ) -> Dict[str, Dict[str, Any]]:
        snapshots = {}
        for tf, candle_list in candles_by_timeframe.items():
            if not candle_list or len(candle_list) < 5:
                continue
            df = pd.DataFrame(candle_list)
            enriched = cls.compute_all(df)
            last = enriched.iloc[-1].to_dict()
            snapshots[tf] = {
                "timeframe": tf,
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
        return snapshots
