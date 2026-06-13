from datetime import UTC, datetime
from types import SimpleNamespace

from app.config import Settings
from app.models import AssetType, Currency, Institution
from app.paper_trading import load_paper_trading, run_paper_trading_cycle


def test_creates_three_paper_only_bots(monkeypatch, tmp_path) -> None:
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path / "imports",
        exchange_rate_auto_update=False,
        shioaji_enabled=False,
        paper_trading_initial_cash_twd=100_000,
    )
    holdings = [
        SimpleNamespace(
            asset_type=AssetType.STOCK,
            institution=Institution.FIRSTRADE,
            symbol="AAPL",
            name="Apple",
        ),
        SimpleNamespace(
            asset_type=AssetType.STOCK,
            institution=Institution.SINOPAC_SECURITIES,
            symbol="2330",
            name="台積電",
        ),
        SimpleNamespace(
            asset_type=AssetType.ETF,
            institution=Institution.FIRSTRADE,
            symbol="VOO",
            name="Vanguard S&P 500 ETF",
        ),
    ]
    monkeypatch.setattr(
        "app.paper_trading.build_portfolio",
        lambda _: SimpleNamespace(holdings=holdings),
    )
    monkeypatch.setattr(
        "app.paper_trading.load_exchange_rates",
        lambda _: SimpleNamespace(usd_to_twd=32.0),
    )
    # A declining series proves the market-specific bots are no longer
    # blocked by the former momentum or moving-average entry thresholds.
    candles = [
        SimpleNamespace(date=f"2026-05-{day:02d}", close=200 - day)
        for day in range(1, 31)
    ]
    monkeypatch.setattr(
        "app.paper_trading.load_price_history",
        lambda settings_arg, institution, *args: SimpleNamespace(
            currency=(
                Currency.TWD
                if institution == Institution.SINOPAC_SECURITIES
                else Currency.USD
            ),
            candles=candles,
        ),
    )
    monkeypatch.setattr(
        "app.paper_trading.load_benchmark_history",
        lambda settings_arg, symbol, days: SimpleNamespace(
            candles=[
                SimpleNamespace(date=f"2026-05-{day:02d}", close=20_000 + day)
                for day in range(1, 31)
            ],
        ),
    )

    response = run_paper_trading_cycle(settings)

    assert response.paper_only
    assert len(response.bots) == 3
    assert all(bot.paper_only for bot in response.bots)
    assert all(bot.initial_cash_twd == 100_000 for bot in response.bots)
    assert {position.symbol for position in response.bots[0].positions} == {"AAPL"}
    assert {position.symbol for position in response.bots[1].positions} == {"2330"}
    assert response.bots[0].name == "美股自由機器人"
    assert response.bots[1].name == "台股自由機器人"
    assert response.bots[0].cash_twd < 20_000
    assert response.bots[1].cash_twd < 20_000
    assert all(
        position.symbol != "VOO"
        for bot in response.bots
        for position in bot.positions
    )
    assert all(bot.performance_history for bot in response.bots)
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
