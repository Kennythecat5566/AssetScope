from datetime import UTC, datetime
from types import SimpleNamespace

from app.config import Settings
from app.models import AssetType, Currency
from app.paper_trading import load_paper_trading, run_paper_trading_cycle


def test_creates_three_paper_only_bots(monkeypatch, tmp_path) -> None:
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path / "imports",
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
        paper_trading_initial_cash_twd=100_000,
    )
    holding = SimpleNamespace(
        asset_type=AssetType.ETF,
        institution=SimpleNamespace(value="FIRSTRade"),
        symbol="VOO",
        name="Vanguard S&P 500 ETF",
    )
    monkeypatch.setattr(
        "app.paper_trading.build_portfolio",
        lambda _: SimpleNamespace(holdings=[holding]),
    )
    monkeypatch.setattr(
        "app.paper_trading.load_exchange_rates",
        lambda _: SimpleNamespace(usd_to_twd=32.0),
    )
    candles = [
        SimpleNamespace(date=f"2026-05-{day:02d}", close=100 + day)
        for day in range(1, 31)
    ]
    monkeypatch.setattr(
        "app.paper_trading.load_price_history",
        lambda *_: SimpleNamespace(currency=Currency.USD, candles=candles),
    )

    response = run_paper_trading_cycle(settings)

    assert response.paper_only
    assert len(response.bots) == 3
    assert all(bot.paper_only for bot in response.bots)
    assert all(bot.initial_cash_twd == 100_000 for bot in response.bots)
    assert (tmp_path / "paper-trading.json").exists()


def test_load_without_cycle_never_requires_broker(monkeypatch, tmp_path) -> None:
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path / "imports",
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
    )
    response = load_paper_trading(settings, run_cycle=False)
    assert response.generated_at <= datetime.now(UTC)
    assert {bot.id for bot in response.bots} == {
        "aggressive",
        "conservative",
        "unrestricted",
    }
