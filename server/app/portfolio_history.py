import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

from app.models import (
    Currency,
    ExchangeRates,
    Holding,
    PortfolioHistoryPoint,
    PortfolioHistoryResponse,
)


def record_portfolio_snapshot(
    path: Path,
    holdings: list[Holding],
    rates: ExchangeRates,
    timestamp: datetime,
) -> None:
    value_twd = sum(
        holding.quantity
        * holding.market_price
        * (rates.usd_to_twd if holding.currency == Currency.USD else 1)
        for holding in holdings
    )
    points = _read_points(path)
    point = PortfolioHistoryPoint(timestamp=timestamp, value_twd=value_twd)

    if points and points[-1].timestamp.date() == timestamp.date():
        points[-1] = point
    else:
        points.append(point)

    cutoff = datetime.now(UTC) - timedelta(days=730)
    points = [item for item in points if item.timestamp >= cutoff]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            [item.model_dump(mode="json") for item in points],
            ensure_ascii=True,
            separators=(",", ":"),
        ),
        encoding="utf-8",
    )


def load_portfolio_history(path: Path, days: int) -> PortfolioHistoryResponse:
    cutoff = datetime.now(UTC) - timedelta(days=days)
    points = [item for item in _read_points(path) if item.timestamp >= cutoff]
    return PortfolioHistoryResponse(points=points)


def _read_points(path: Path) -> list[PortfolioHistoryPoint]:
    if not path.exists():
        return []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        return [PortfolioHistoryPoint.model_validate(item) for item in payload]
    except (OSError, ValueError, TypeError):
        return []
