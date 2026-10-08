import datetime
from sqlalchemy import (
    Column, Integer, String, Float, DateTime, Boolean, Enum, ForeignKey, Index, UniqueConstraint, Text
)
from sqlalchemy.orm import declarative_base, relationship

Base = declarative_base()

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    email = Column(String(255), unique=True, index=True, nullable=False)
    hashed_password = Column(String(255), nullable=True)  # Nullable for OAuth/Firebase users
    full_name = Column(String(255), nullable=True)
    firebase_uid = Column(String(128), unique=True, index=True, nullable=True)
    is_active = Column(Boolean, default=True, nullable=False)
    is_superuser = Column(Boolean, default=False, nullable=False)
    role = Column(String(50), default="VIEWER", nullable=False)  # SUPER_ADMIN, ADMIN, VIEWER
    tier = Column(String(50), default="PRO_QUANT", nullable=False)  # FREE, PRO, INSTITUTIONAL
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    # Relationships
    api_configs = relationship("ApiKeyConfig", back_populates="user", cascade="all, delete-orphan")
    signals = relationship("Signal", back_populates="created_by_user")

    def __repr__(self):
        return f"<User(id={self.id}, email='{self.email}', tier='{self.tier}')>"


class MarketTick(Base):
    """
    High-frequency raw market ticks for XAUUSD.
    Optimized for rapid writes with composite index on (symbol, timestamp DESC).
    """
    __tablename__ = "market_ticks"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), default="XAUUSD", nullable=False, index=True)
    price = Column(Float, nullable=False)
    bid = Column(Float, nullable=False)
    ask = Column(Float, nullable=False)
    spread = Column(Float, nullable=False)
    volume = Column(Float, default=1.0, nullable=False)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)

    __table_args__ = (
        Index("idx_market_ticks_symbol_timestamp", "symbol", timestamp.desc()),
    )

    def __repr__(self):
        return f"<MarketTick(symbol='{self.symbol}', price={self.price}, ts={self.timestamp})>"


class Candle(Base):
    """
    OHLCV Aggregated bars for XAUUSD across timeframes (1m, 5m, 15m, 1h, 4h, 1d).
    Optimized for fast historical chart fetches and backtesting sweeps.
    """
    __tablename__ = "candles"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), default="XAUUSD", nullable=False, index=True)
    timeframe = Column(String(10), default="15m", nullable=False, index=True)  # 1m, 5m, 15m, 1h, 4h, 1d
    timestamp = Column(DateTime, nullable=False, index=True)
    open = Column(Float, nullable=False)
    high = Column(Float, nullable=False)
    low = Column(Float, nullable=False)
    close = Column(Float, nullable=False)
    volume = Column(Float, default=0.0, nullable=False)
    ticks_count = Column(Integer, default=0, nullable=False)

    __table_args__ = (
        UniqueConstraint("symbol", "timeframe", "timestamp", name="uq_candle_symbol_tf_ts"),
        Index("idx_candles_lookup", "symbol", "timeframe", timestamp.desc()),
    )

    def __repr__(self):
        return f"<Candle(symbol='{self.symbol}', tf='{self.timeframe}', c={self.close}, ts={self.timestamp})>"


class EconomicEvent(Base):
    """
    Macroeconomic calendar events affecting Gold volatility (NFP, CPI, FOMC, Fed Rates, PPI, Retail Sales).
    """
    __tablename__ = "economic_events"

    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String(255), nullable=False, index=True)
    country = Column(String(10), default="USD", nullable=False)  # USD, EUR, GBP, CNY
    impact = Column(String(20), default="HIGH", nullable=False)   # HIGH, MEDIUM, LOW
    timestamp = Column(DateTime, nullable=False, index=True)
    actual = Column(String(50), nullable=True)
    forecast = Column(String(50), nullable=True)
    previous = Column(String(50), nullable=True)
    unit = Column(String(20), nullable=True)
    gold_bias = Column(String(20), default="NEUTRAL", nullable=False)  # BULLISH, BEARISH, NEUTRAL
    notes = Column(Text, nullable=True)

    __table_args__ = (
        Index("idx_events_timestamp_impact", "timestamp", "impact"),
    )

    def __repr__(self):
        return f"<EconomicEvent(title='{self.title}', impact='{self.impact}', ts={self.timestamp})>"


class Signal(Base):
    """
    Algorithmic and AI Quantitative trading signals for XAUUSD.
    Tracks entry, stop-loss, dual take-profits, confidence, and trade lifecycle.
    """
    __tablename__ = "signals"

    id = Column(Integer, primary_key=True, autoincrement=True)
    symbol = Column(String(20), default="XAUUSD", nullable=False, index=True)
    strategy_name = Column(String(100), nullable=False)  # e.g., 'GoldBreakoutQuant', 'MacroConfluenceAI'
    signal_type = Column(String(30), nullable=False)     # STRONG BUY, BUY, WEAK BUY, WAIT, WEAK SELL, SELL, STRONG SELL, NO SIGNAL
    timeframe = Column(String(10), default="15m", nullable=False)
    entry_price = Column(Float, nullable=False)
    entry_zone_low = Column(Float, nullable=True)
    entry_zone_high = Column(Float, nullable=True)
    stop_loss = Column(Float, nullable=False)
    take_profit_1 = Column(Float, nullable=False)
    take_profit_2 = Column(Float, nullable=False)
    confidence_score = Column(Float, nullable=False)      # 0.0 to 100.0%
    risk_reward_ratio = Column(Float, nullable=False)     # e.g. 2.5
    status = Column(String(20), default="ACTIVE", nullable=False, index=True) # ACTIVE, HIT_TP1, HIT_TP2, HIT_SL, EXPIRED
    reasoning = Column(Text, nullable=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)
    closed_at = Column(DateTime, nullable=True)
    pnl_pips = Column(Float, default=0.0, nullable=True)

    created_by_user = relationship("User", back_populates="signals")

    __table_args__ = (
        Index("idx_signals_status_ts", "status", timestamp.desc()),
    )

    def __repr__(self):
        return f"<Signal(id={self.id}, type='{self.signal_type}', entry={self.entry_price}, status='{self.status}')>"


class ApiKeyConfig(Base):
    """
    Dynamic API configuration storage.
    Enables users to provide keys (Market Data, OpenAI, Gemini, News, TradingView Webhook)
    via Frontend UI without changing backend code, with fallback to environment variables.
    """
    __tablename__ = "api_configs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    key_name = Column(String(100), nullable=False, index=True)  # e.g. 'MARKET_DATA_API_KEY', 'OPENAI_API_KEY'
    key_value = Column(Text, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    description = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)

    user = relationship("User", back_populates="api_configs")

    __table_args__ = (
        UniqueConstraint("user_id", "key_name", name="uq_user_key_name"),
    )

    def __repr__(self):
        return f"<ApiKeyConfig(key_name='{self.key_name}', active={self.is_active})>"


class TradingViewAlert(Base):
    """
    Stores verified incoming TradingView webhook alerts for custom proprietary indicators
    (e.g., 'Scalping with Dr Hafiz V2', '3ESRA').
    Strictly protected with TRADINGVIEW_WEBHOOK_SECRET.
    Never fabricates values; only persists verified real alerts.
    """
    __tablename__ = "tradingview_alerts"

    id = Column(Integer, primary_key=True, autoincrement=True)
    indicator = Column(String(100), nullable=False, index=True)
    symbol = Column(String(20), default="XAUUSD", nullable=False)
    action = Column(String(20), nullable=False)  # BUY, SELL, EXIT, ALERT
    price = Column(Float, nullable=False)
    timeframe = Column(String(10), default="15m", nullable=False)
    metrics_json = Column(Text, nullable=True)
    message = Column(Text, nullable=True)
    timestamp = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)
    received_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)
    verified = Column(Boolean, default=True, nullable=False)

    __table_args__ = (
        Index("idx_tv_indicator_ts", "indicator", timestamp.desc()),
    )

    def __repr__(self):
        return f"<TradingViewAlert(indicator='{self.indicator}', action='{self.action}', price={self.price})>"


class NewsArticle(Base):
    """
    Financial macroeconomic news strictly filtered for:
    Gold (XAUUSD), USD, Federal Reserve, inflation (CPI/PCE), and Treasury yields.
    """
    __tablename__ = "news_articles"

    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String(500), nullable=False, index=True)
    source = Column(String(100), nullable=False)
    url = Column(String(1000), nullable=True)
    summary = Column(Text, nullable=False)
    category = Column(String(50), nullable=False)  # GOLD, FED_POLICY, INFLATION, TREASURY_YIELDS, USD_DXY
    sentiment = Column(String(20), default="NEUTRAL", nullable=False)  # BULLISH, BEARISH, NEUTRAL
    relevance_score = Column(Float, default=1.0, nullable=False)
    published_at = Column(DateTime, nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False)

    __table_args__ = (
        Index("idx_news_category_pub", "category", published_at.desc()),
    )

    def __repr__(self):
        return f"<NewsArticle(title='{self.title[:30]}...', cat='{self.category}')>"


class FundamentalAnalysis(Base):
    """
    AI Reasoning Engine synthesis integrating:
    - Filtered news feed
    - Economic calendar releases
    - Multi-timeframe technical scores
    Classifies market risk (High/Medium/Low) and fundamental bias (Bullish/Bearish/Neutral).
    """
    __tablename__ = "fundamental_analyses"

    id = Column(Integer, primary_key=True, autoincrement=True)
    market_risk = Column(String(20), nullable=False)  # High, Medium, Low
    fundamental_bias = Column(String(20), nullable=False)  # Bullish, Bearish, Neutral
    confidence_score = Column(Float, default=80.0, nullable=False)
    key_drivers = Column(Text, nullable=False)  # JSON string of drivers
    catalyst_impact = Column(Text, nullable=False)
    recommended_action = Column(Text, nullable=True)
    detailed_synthesis = Column(Text, nullable=False)
    llm_provider = Column(String(50), default="OpenAI", nullable=False)
    model_name = Column(String(50), default="gpt-4o", nullable=False)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)

    def __repr__(self):
        return f"<FundamentalAnalysis(risk='{self.market_risk}', bias='{self.fundamental_bias}')>"


class BacktestOutcome(Base):
    """
    Stores event-driven backtesting execution outcomes and historical signal calibration data.
    Tracks win rate, profit factor, max drawdown, net PnL, and individual trade executions.
    """
    __tablename__ = "backtest_outcomes"

    id = Column(Integer, primary_key=True, autoincrement=True)
    strategy_name = Column(String(100), nullable=False, index=True)
    timeframe = Column(String(10), default="15m", nullable=False)
    total_trades = Column(Integer, nullable=False)
    winning_trades = Column(Integer, nullable=False)
    losing_trades = Column(Integer, nullable=False)
    win_rate_pct = Column(Float, nullable=False)
    profit_factor = Column(Float, nullable=False)
    max_drawdown_pct = Column(Float, nullable=False)
    net_profit = Column(Float, nullable=False)
    sharpe_ratio = Column(Float, nullable=False)
    initial_capital = Column(Float, default=10000.0, nullable=False)
    final_equity = Column(Float, nullable=False)
    trades_json = Column(Text, nullable=True)
    calibration_notes = Column(Text, nullable=True)
    created_at = Column(DateTime, default=datetime.datetime.utcnow, nullable=False, index=True)

    def __repr__(self):
        return f"<BacktestOutcome(strategy='{self.strategy_name}', winRate={self.win_rate_pct}%, pf={self.profit_factor})>"
