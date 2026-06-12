from datetime import UTC, datetime, timedelta
from pathlib import Path

from app.models import AssetType, Currency, ExchangeRates, Holding, Institution
from app.portfolio_history import load_portfolio_history, record_portfolio_snapshot


def test_records_one_snapshot_per_day(tmp_path: Path) -> None:
    path = tmp_path / "portfolio-history.json"
    holding = Holding(
        id="usd-cash",
        institution=Institution.FIRSTRADE,
        account_name="Main",
        symbol="USD",
        name="Cash",
        asset_type=AssetType.CASH,
        currency=Currency.USD,
        quantity=100,
        average_cost=1,
        market_price=1,
    )
    now = datetime.now(UTC)

    record_portfolio_snapshot(path, [holding], ExchangeRates(usd_to_twd=32), now)
    record_portfolio_snapshot(
        path,
        [holding],
        ExchangeRates(usd_to_twd=33),
        now + timedelta(minutes=5),
    )

    history = load_portfolio_history(path, 30)
    assert len(history.points) == 1
    assert history.points[0].value_twd == 3300
