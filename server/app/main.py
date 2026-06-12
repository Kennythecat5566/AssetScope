import secrets
from contextlib import asynccontextmanager
from threading import Event, Thread

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.config import Settings, get_settings
from app.connectors.price_history import load_market_summaries, load_price_history
from app.models import (
    Institution,
    MarketSummariesRequest,
    MarketSummariesResponse,
    PortfolioHistoryResponse,
    PortfolioResponse,
    PriceHistoryResponse,
    PaperTradingResponse,
)
from app.paper_trading import load_paper_trading, run_paper_trading_cycle
from app.portfolio_history import load_portfolio_history
from app.service import build_portfolio

_paper_stop = Event()


def _paper_worker() -> None:
    while not _paper_stop.is_set():
        try:
            settings = get_settings()
            if settings.paper_trading_enabled:
                run_paper_trading_cycle(settings)
            interval = settings.paper_trading_interval_minutes * 60
        except Exception:
            interval = 15 * 60
        _paper_stop.wait(interval)


@asynccontextmanager
async def lifespan(_: FastAPI):
    _paper_stop.clear()
    worker = Thread(target=_paper_worker, name="paper-trading", daemon=True)
    worker.start()
    yield
    _paper_stop.set()
    worker.join(timeout=2)


app = FastAPI(
    title="AssetScope Server",
    description="Read-only broker aggregation with isolated paper trading.",
    version="0.1.0",
    lifespan=lifespan,
)


def authorize(
    authorization: str | None = Header(default=None),
    settings: Settings = Depends(get_settings),
) -> None:
    scheme, _, token = (authorization or "").partition(" ")
    valid = scheme.lower() == "bearer" and secrets.compare_digest(
        token,
        settings.api_token,
    )
    if not valid:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid API token",
        )


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get(
    "/api/v1/portfolio",
    response_model=PortfolioResponse,
    dependencies=[Depends(authorize)],
)
def portfolio(settings: Settings = Depends(get_settings)) -> PortfolioResponse:
    try:
        return build_portfolio(settings)
    except (OSError, RuntimeError, ValueError) as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from error


@app.get(
    "/api/v1/history/{institution}/{symbol}",
    response_model=PriceHistoryResponse,
    dependencies=[Depends(authorize)],
)
def price_history(
    institution: Institution,
    symbol: str,
    days: int = 90,
    settings: Settings = Depends(get_settings),
) -> PriceHistoryResponse:
    if days < 20 or days > 250:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="days must be between 20 and 250",
        )
    try:
        return load_price_history(settings, institution, symbol, days)
    except ValueError as error:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(error),
        ) from error
    except (OSError, RuntimeError) as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from error


@app.post(
    "/api/v1/market/summaries",
    response_model=MarketSummariesResponse,
    dependencies=[Depends(authorize)],
)
def market_summaries(
    request: MarketSummariesRequest,
    settings: Settings = Depends(get_settings),
) -> MarketSummariesResponse:
    return MarketSummariesResponse(
        summaries=load_market_summaries(settings, request.items),
    )


@app.get(
    "/api/v1/portfolio/history",
    response_model=PortfolioHistoryResponse,
    dependencies=[Depends(authorize)],
)
def portfolio_history(
    days: int = 365,
    settings: Settings = Depends(get_settings),
) -> PortfolioHistoryResponse:
    if days < 7 or days > 730:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="days must be between 7 and 730",
        )
    return load_portfolio_history(
        settings.import_dir.parent / "cache" / "portfolio-history.json",
        days,
    )


@app.get(
    "/api/v1/paper-trading",
    response_model=PaperTradingResponse,
    dependencies=[Depends(authorize)],
)
def paper_trading(
    settings: Settings = Depends(get_settings),
) -> PaperTradingResponse:
    try:
        return load_paper_trading(settings)
    except (OSError, RuntimeError, ValueError) as error:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=str(error),
        ) from error
