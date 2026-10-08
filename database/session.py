import os
import logging
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker

logger = logging.getLogger("GoldAI.Database")

# Default automatically to a local SQLite database if DATABASE_URL is not set
RAW_DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./gold_ai.db").strip()
if not RAW_DATABASE_URL:
    RAW_DATABASE_URL = "sqlite:///./gold_ai.db"

# Convert standard connection strings to async dialect
if RAW_DATABASE_URL.startswith("postgresql://"):
    ASYNC_DATABASE_URL = RAW_DATABASE_URL.replace("postgresql://", "postgresql+asyncpg://", 1)
elif RAW_DATABASE_URL.startswith("sqlite:///"):
    ASYNC_DATABASE_URL = RAW_DATABASE_URL.replace("sqlite:///", "sqlite+aiosqlite:///", 1)
else:
    ASYNC_DATABASE_URL = RAW_DATABASE_URL

from sqlalchemy import event

# Configure engine arguments appropriately for SQLite vs Postgres
if "sqlite" in ASYNC_DATABASE_URL:
    engine = create_async_engine(
        ASYNC_DATABASE_URL,
        echo=False,
        connect_args={"check_same_thread": False}
    )

    # SPEED & DATABASE OPTIMIZATION: Enable WAL mode and synchronous normal
    @event.listens_for(engine.sync_engine, "connect")
    def set_sqlite_pragma(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA synchronous=NORMAL")
        cursor.execute("PRAGMA cache_size=-64000")  # 64MB page cache
        cursor.execute("PRAGMA busy_timeout=5000")  # 5s busy timeout
        cursor.close()
else:
    engine = create_async_engine(
        ASYNC_DATABASE_URL,
        echo=False,
        pool_size=20,
        max_overflow=10,
        pool_pre_ping=True
    )

AsyncSessionLocal = sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False
)

async def get_db():
    """FastAPI dependency for yielding async db sessions."""
    async with AsyncSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()
