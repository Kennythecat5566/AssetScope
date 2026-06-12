from datetime import UTC, datetime
from pathlib import Path

from app.config import Settings
from app.connectors.exchange_rates import load_exchange_rates
from app.models import ExchangeRates


def test_uses_cached_exchange_rate(tmp_path: Path) -> None:
    cache_path = tmp_path / "cache" / "exchange-rates.json"
    cache_path.parent.mkdir(parents=True)
    cache_path.write_text(
        ExchangeRates(
            usd_to_twd=31.75,
            updated_at=datetime.now(UTC),
            source="test",
        ).model_dump_json(),
        encoding="utf-8",
    )
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path / "imports",
    )

    rates = load_exchange_rates(settings)

    assert rates.usd_to_twd == 31.75
    assert rates.source == "test"
