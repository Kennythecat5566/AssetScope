import secrets

from fastapi import Depends, FastAPI, Header, HTTPException, status

from app.config import Settings, get_settings
from app.models import PortfolioResponse
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

