from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from backend.api.routes import router as api_router
from backend.core.config import settings

try:
    from fastapi.responses import ORJSONResponse
    DefaultResponse = ORJSONResponse
except ImportError:
    try:
        from fastapi.responses import UJSONResponse
        DefaultResponse = UJSONResponse
    except ImportError:
        from fastapi.responses import JSONResponse
        DefaultResponse = JSONResponse

from contextlib import asynccontextmanager
from backend.data.subsystem import market_data_subsystem

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Start real-time background market data poller and worker
    market_data_subsystem.start_background_worker()
    # Trigger initial poll to seed ring buffers immediately
    try:
        await market_data_subsystem.poll_market_data()
    except Exception:
        pass
    yield
    # Gracefully stop worker on shutdown
    market_data_subsystem.stop_background_worker()

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description="Production-ready XAUUSD Institutional Quantitative & AI Financial Analyst Backend",
    default_response_class=DefaultResponse,
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api")
app.include_router(api_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    return {
        "status": "online",
        "service": "Gold AI Analyst Backend",
        "instrument": "XAUUSD"
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("backend.main:app", host="0.0.0.0", port=8000, reload=True)
