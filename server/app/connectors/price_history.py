from __future__ import annotations

import json
from collections import defaultdict
from datetime import UTC, date, datetime, timedelta
from pathlib import Path
from typing import Any
from urllib.parse import quote
from urllib.request import Request, urlopen

from app.config import Settings
from app.models import (
    Currency,
    Institution,
    MarketSummary,
    MarketSummaryRequestItem,
    PriceCandle,
    PriceHistoryResponse,
)


CACHE_TTL = timedelta(minutes=30)


def load_price_history(
    settings: Settings,
    institution: Institution,
    symbol: str,
    days: int,
) -> PriceHistoryResponse:
    normalized_symbol = symbol.strip().upper()
    if not normalized_symbol or not normalized_symbol.replace(".", "").isalnum():
        raise ValueError("Invalid stock symbol")

    cache_path = settings.import_dir.parent / "cache" / (
        f"history-{institution.value.lower()}-{normalized_symbol.lower()}-{days}.json"
    )
    cached = _read_cache(cache_path)
    if cached is not None:
        return cached

    if institution == Institution.SINOPAC_SECURITIES:
        response = _load_shioaji_history(settings, normalized_symbol, days)
    elif institution == Institution.FIRSTRADE:
        response = _load_us_history(normalized_symbol, days)
    else:
        raise ValueError("Price history is available for stock holdings only")

    _write_cache(cache_path, response)
    return response


def load_market_summaries(
    settings: Settings,
    items: list[MarketSummaryRequestItem],
) -> list[MarketSummary]:
    summaries: list[MarketSummary] = []
    seen: set[tuple[Institution, str]] = set()
    for item in items:
        symbol = item.symbol.strip().upper()
        key = (item.institution, symbol)
        if key in seen:
            continue
        seen.add(key)
        try:
            history = load_price_history(settings, item.institution, symbol, 30)
        except (OSError, RuntimeError, ValueError, KeyError):
            continue
        closes = [candle.close for candle in history.candles]
        if len(closes) < 2:
            continue
        latest = closes[-1]
        previous = closes[-2]
        change = latest - previous
        summaries.append(
            MarketSummary(
                institution=item.institution,
                symbol=symbol,
                currency=history.currency,
                latest_price=latest,
                change=change,
                change_rate=change / previous if previous else 0,
                closes=closes,
                source=history.source,
            )
        )
    return summaries


def _load_shioaji_history(
    settings: Settings,
    symbol: str,
    days: int,
) -> PriceHistoryResponse:
    if not settings.shioaji_enabled:
        raise RuntimeError("Shioaji is not enabled")

    import shioaji as sj

    api = sj.Shioaji()
    logged_in = False
    try:
        api.login(
            api_key=settings.shioaji_api_key,
            secret_key=settings.shioaji_secret_key,
            fetch_contract=True,
            contracts_timeout=30_000,
            subscribe_trade=False,
        )
        logged_in = True
        contract = api.Contracts.Stocks[symbol]
        if contract is None:
            raise ValueError(f"Taiwan stock contract not found: {symbol}")

        end = date.today()
        start = end - timedelta(days=max(days * 2, 120))
        kbars = api.kbars(
            contract=contract,
            start=start.isoformat(),
            end=end.isoformat(),
        )
        candles = _aggregate_shioaji_daily(kbars)[-days:]
        if not candles:
            raise RuntimeError(f"No Taiwan market history returned for {symbol}")
        return PriceHistoryResponse(
            symbol=symbol,
            currency=Currency.TWD,
            source="Shioaji",
            candles=candles,
        )
    finally:
        if logged_in:
            api.logout()


def _aggregate_shioaji_daily(kbars: Any) -> list[PriceCandle]:
    grouped: dict[str, list[dict[str, float]]] = defaultdict(list)
    for index, timestamp in enumerate(kbars.ts):
        day = datetime.fromtimestamp(timestamp / 1_000_000_000, UTC).astimezone().date()
        grouped[day.isoformat()].append(
            {
                "open": float(kbars.Open[index]),
                "high": float(kbars.High[index]),
                "low": float(kbars.Low[index]),
                "close": float(kbars.Close[index]),
                "volume": float(kbars.Volume[index]),
            }
        )

    return [
        PriceCandle(
            date=day,
            open=bars[0]["open"],
            high=max(bar["high"] for bar in bars),
            low=min(bar["low"] for bar in bars),
            close=bars[-1]["close"],
            volume=sum(bar["volume"] for bar in bars),
        )
        for day, bars in sorted(grouped.items())
    ]


def _load_us_history(symbol: str, days: int) -> PriceHistoryResponse:
    range_name = "6mo" if days <= 120 else "1y"
    url = (
        "https://query1.finance.yahoo.com/v8/finance/chart/"
        f"{quote(symbol)}?range={range_name}&interval=1d&events=history"
    )
    request = Request(url, headers={"User-Agent": "AssetScope/0.2"})
    with urlopen(request, timeout=15) as response:
        root = json.load(response)

    result = root["chart"]["result"][0]
    timestamps = result["timestamp"]
    quote_data = result["indicators"]["quote"][0]
    candles = []
    for index, timestamp in enumerate(timestamps):
        values = {
            key: quote_data[key][index]
            for key in ("open", "high", "low", "close", "volume")
        }
        if any(value is None for value in values.values()):
            continue
        candles.append(
            PriceCandle(
                date=datetime.fromtimestamp(timestamp, UTC).date().isoformat(),
                open=float(values["open"]),
                high=float(values["high"]),
                low=float(values["low"]),
                close=float(values["close"]),
                volume=float(values["volume"]),
            )
        )

    candles = candles[-days:]
    if not candles:
        raise RuntimeError(f"No US market history returned for {symbol}")
    return PriceHistoryResponse(
        symbol=symbol,
        currency=Currency.USD,
        source="Yahoo Finance",
        candles=candles,
    )


def _read_cache(path: Path) -> PriceHistoryResponse | None:
    if not path.exists():
        return None
    modified_at = datetime.fromtimestamp(path.stat().st_mtime, UTC)
    if datetime.now(UTC) - modified_at > CACHE_TTL:
        return None
    return PriceHistoryResponse.model_validate_json(path.read_text(encoding="utf-8"))


def _write_cache(path: Path, response: PriceHistoryResponse) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(response.model_dump_json(), encoding="utf-8")
