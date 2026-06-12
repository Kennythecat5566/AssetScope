from types import SimpleNamespace

from app.connectors.price_history import _aggregate_shioaji_daily


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
