from types import SimpleNamespace

from app.connectors.price_history import _aggregate_shioaji_daily, load_market_summaries
from app.config import Settings
from app.models import (
    Currency,
    Institution,
    MarketSummaryRequestItem,
    PriceCandle,
    PriceHistoryResponse,
)


def test_aggregates_intraday_kbars_into_daily_candles() -> None:
    kbars = SimpleNamespace(
        ts=[
            1_767_225_600_000_000_000,
            1_767_229_200_000_000_000,
            1_767_312_000_000_000_000,
        ],
        Open=[100, 102, 110],
        High=[105, 108, 115],
        Low=[98, 101, 109],
        Close=[102, 107, 114],
        Volume=[10, 20, 30],
    )

    candles = _aggregate_shioaji_daily(kbars)

    assert len(candles) == 2
    assert candles[0].open == 100
    assert candles[0].high == 108
    assert candles[0].low == 98
    assert candles[0].close == 107
    assert candles[0].volume == 30


def test_builds_market_summary_and_skips_failed_symbol(monkeypatch, tmp_path) -> None:
    def fake_history(settings, institution, symbol, days):
        if symbol == "FAIL":
            raise RuntimeError("unavailable")
        return PriceHistoryResponse(
            symbol=symbol,
            currency=Currency.USD,
            source="test",
            candles=[
                PriceCandle(
                    date="2026-06-11",
                    open=100,
                    high=101,
                    low=99,
                    close=100,
                    volume=10,
                ),
                PriceCandle(
                    date="2026-06-12",
                    open=100,
                    high=106,
                    low=100,
                    close=105,
                    volume=20,
                ),
            ],
        )

    monkeypatch.setattr(
        "app.connectors.price_history.load_price_history",
        fake_history,
    )
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        exchange_rate_auto_update=False,
    )
    summaries = load_market_summaries(
        settings,
        [
            MarketSummaryRequestItem(
                institution=Institution.FIRSTRADE,
                symbol="VTI",
            ),
            MarketSummaryRequestItem(
                institution=Institution.FIRSTRADE,
                symbol="FAIL",
            ),
        ],
    )

    assert len(summaries) == 1
    assert summaries[0].latest_price == 105
    assert summaries[0].change == 5
    assert summaries[0].change_rate == 0.05
