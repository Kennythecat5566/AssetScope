import secrets

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
)
from app.portfolio_history import load_portfolio_history
from app.service import build_portfolio

app = FastAPI(
    title="AssetScope Server",
    description="Read-only personal asset aggregation API.",
    version="0.1.0",
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
