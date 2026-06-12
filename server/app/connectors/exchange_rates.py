import json
from datetime import UTC, datetime, timedelta
from pathlib import Path
from urllib.request import Request, urlopen

from app.config import Settings
from app.models import ExchangeRates

YAHOO_CHART_URL = (
    "https://query1.finance.yahoo.com/v8/finance/chart/TWD=X"
    "?range=5d&interval=1d"
)


def load_exchange_rates(settings: Settings) -> ExchangeRates:
    cache_path = settings.import_dir.parent / "cache" / "exchange-rates.json"
    cached = _read_cache(cache_path)
    if not settings.exchange_rate_auto_update:
        return cached or ExchangeRates(usd_to_twd=settings.usd_to_twd)

    if cached and cached.updated_at:
        age = datetime.now(UTC) - cached.updated_at
        if age < timedelta(hours=settings.exchange_rate_cache_hours):
            return cached

    try:
        rate = _fetch_usd_to_twd()
        result = ExchangeRates(
            usd_to_twd=rate,
            updated_at=datetime.now(UTC),
            source="Yahoo Finance",
        )
        _write_cache(cache_path, result)
        return result
    except (OSError, RuntimeError, ValueError, KeyError, TypeError):
        return cached or ExchangeRates(usd_to_twd=settings.usd_to_twd)


def _fetch_usd_to_twd() -> float:
    request = Request(
        YAHOO_CHART_URL,
        headers={"User-Agent": "AssetScope/0.3"},
    )
    with urlopen(request, timeout=8) as response:
        payload = json.load(response)
    result = payload["chart"]["result"][0]
    meta = result["meta"]
    rate = meta.get("regularMarketPrice") or meta.get("previousClose")
    if not rate or float(rate) <= 0:
        raise RuntimeError("USD/TWD exchange rate is unavailable")
    return float(rate)


def _read_cache(path: Path) -> ExchangeRates | None:
    if not path.exists():
        return None
    try:
        return ExchangeRates.model_validate_json(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


def _write_cache(path: Path, rates: ExchangeRates) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(rates.model_dump_json(), encoding="utf-8")
