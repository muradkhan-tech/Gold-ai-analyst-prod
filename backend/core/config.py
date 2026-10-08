import os
import logging
from typing import Optional, Dict, Any
from pydantic_settings import BaseSettings
from sqlalchemy.future import select
from sqlalchemy.ext.asyncio import AsyncSession
from database.models import ApiKeyConfig

logger = logging.getLogger("GoldAI.ConfigService")

class Settings(BaseSettings):
    PROJECT_NAME: str = "Gold AI Analyst XAUUSD"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Defaults from environment / .env
    MARKET_DATA_API_KEY: str = os.getenv("MARKET_DATA_API_KEY", "")
    NEWS_API_KEY: str = os.getenv("NEWS_API_KEY", "")
    OPENAI_API_KEY: str = os.getenv("OPENAI_API_KEY", "")
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")
    TRADINGVIEW_WEBHOOK_SECRET: str = os.getenv("TRADINGVIEW_WEBHOOK_SECRET", "gold-ai-local-secret")
    
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "sqlite:///./gold_ai.db"
    )
    REDIS_URL: str = os.getenv("REDIS_URL", "memory://internal-cache")

    class Config:
        case_sensitive = True
        env_file = ".env"
        extra = "allow"

settings = Settings()

class ApiConfigService:
    """
    Dynamic API configuration service:
    1. Checks user-specific or global override key stored in PostgreSQL `api_configs` table.
    2. If not found in DB or empty, seamlessly falls back to environment variable (.env).
    3. Caches resolved keys in-memory for microsecond lookup performance.
    """
    def __init__(self):
        self._memory_cache: Dict[str, str] = {}

    async def get_key(
        self,
        key_name: str,
        user_id: Optional[int] = None,
        db: Optional[AsyncSession] = None
    ) -> str:
        cache_key = f"{user_id or 'global'}:{key_name}"
        if cache_key in self._memory_cache:
            return self._memory_cache[cache_key]

        # 1. Attempt DB lookup if session provided
        if db is not None:
            try:
                query = select(ApiKeyConfig).where(
                    ApiKeyConfig.key_name == key_name,
                    ApiKeyConfig.is_active == True
                )
                if user_id:
                    query = query.where(ApiKeyConfig.user_id == user_id)
                else:
                    query = query.where(ApiKeyConfig.user_id.is_(None))

                result = await db.execute(query)
                record = result.scalars().first()
                if record and record.key_value:
                    val = record.key_value.strip()
                    self._memory_cache[cache_key] = val
                    logger.info(f"Loaded '{key_name}' dynamically from database.")
                    return val
            except Exception as e:
                logger.warning(f"Failed to query ApiKeyConfig from DB for '{key_name}': {e}")

        # 2. Fall back to environment variable / settings
        fallback = os.getenv(key_name, getattr(settings, key_name, "")).strip()
        if fallback:
            self._memory_cache[cache_key] = fallback
            logger.info(f"Loaded '{key_name}' from environment fallback.")
            return fallback

        return ""

    async def set_key(
        self,
        key_name: str,
        key_value: str,
        user_id: Optional[int],
        db: AsyncSession,
        description: Optional[str] = None
    ) -> ApiKeyConfig:
        """Insert or update dynamic API key in database and refresh cache."""
        query = select(ApiKeyConfig).where(
            ApiKeyConfig.key_name == key_name
        )
        if user_id:
            query = query.where(ApiKeyConfig.user_id == user_id)
        else:
            query = query.where(ApiKeyConfig.user_id.is_(None))

        result = await db.execute(query)
        record = result.scalars().first()

        if record:
            record.key_value = key_value
            record.is_active = True
            if description:
                record.description = description
        else:
            record = ApiKeyConfig(
                user_id=user_id,
                key_name=key_name,
                key_value=key_value,
                is_active=True,
                description=description
            )
            db.add(record)

        await db.commit()
        await db.refresh(record)

        cache_key = f"{user_id or 'global'}:{key_name}"
        self._memory_cache[cache_key] = key_value
        logger.info(f"Updated dynamic key '{key_name}' in database successfully.")
        return record

    async def get_all_statuses(
        self,
        user_id: Optional[int],
        db: AsyncSession
    ) -> Dict[str, Dict[str, Any]]:
        """Return status and masked preview for all standard keys."""
        standard_keys = [
            "MARKET_DATA_API_KEY",
            "NEWS_API_KEY",
            "OPENAI_API_KEY",
            "GEMINI_API_KEY",
            "TRADINGVIEW_WEBHOOK_SECRET"
        ]
        
        statuses = {}
        for key in standard_keys:
            # Check DB
            query = select(ApiKeyConfig).where(ApiKeyConfig.key_name == key)
            if user_id:
                query = query.where(ApiKeyConfig.user_id == user_id)
            else:
                query = query.where(ApiKeyConfig.user_id.is_(None))
            res = await db.execute(query)
            record = res.scalars().first()

            if record and record.key_value:
                masked = self._mask_key(record.key_value)
                statuses[key] = {
                    "is_configured": True,
                    "source": "database",
                    "preview": masked,
                    "updated_at": record.updated_at.isoformat() if record.updated_at else None
                }
            else:
                env_val = os.getenv(key, getattr(settings, key, ""))
                if env_val:
                    statuses[key] = {
                        "is_configured": True,
                        "source": "env",
                        "preview": self._mask_key(env_val),
                        "updated_at": None
                    }
                else:
                    statuses[key] = {
                        "is_configured": False,
                        "source": "none",
                        "preview": "",
                        "updated_at": None
                    }
        return statuses

    @staticmethod
    def _mask_key(val: str) -> str:
        if not val or len(val) < 8:
            return "••••••••"
        return f"{val[:4]}••••••••{val[-4:]}"

api_config_service = ApiConfigService()
