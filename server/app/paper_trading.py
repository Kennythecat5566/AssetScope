from __future__ import annotations

from datetime import UTC, datetime
import hashlib
import json
from pathlib import Path
from threading import Lock
from typing import Any

from app.config import Settings
from app.connectors.exchange_rates import load_exchange_rates
from app.connectors.price_history import load_benchmark_history, load_price_history
from app.models import (
    AssetType,
    Currency,
    Institution,
    PaperBotEquityPoint,
    PaperBotPerformancePoint,
    PaperBotPosition,
    PaperBotSummary,
    PaperBotTrade,
    PaperTradingResponse,
)
from app.service import build_portfolio


BOT_CONFIGS = (
    {
        "id": "aggressive",
        "name": "美股自由機器人",
        "strategy": "不限制策略風格，只交易美股個股",
        "market_scope": "US_STOCKS",
        "allocation": 0.90,
    },
    {
        "id": "conservative",
        "name": "台股自由機器人",
        "strategy": "不限制策略風格，只交易台股個股",
        "market_scope": "TW_STOCKS",
        "allocation": 0.90,
    },
    {
        "id": "unrestricted",
        "name": "全球自由機器人",
        "strategy": "不限制集中度與周轉率，可交易全部美股與台股個股",
        "market_scope": "ALL_STOCKS",
        "allocation": 0.90,
    },
)
_lock = Lock()


def run_paper_trading_cycle(settings: Settings) -> PaperTradingResponse:
    with _lock:
        portfolio = build_portfolio(settings)
        rates = load_exchange_rates(settings)
        market_holdings = [
            item
            for item in portfolio.holdings
            if item.asset_type in {AssetType.STOCK, AssetType.ETF}
        ]
        state_path = _state_path(settings)
        state = _load_state(state_path, settings.paper_trading_initial_cash_twd)
        quotes: dict[str, dict[str, Any]] = {}
        for holding in market_holdings:
            try:
                history = load_price_history(
                    settings,
                    holding.institution,
                    holding.symbol,
                    30,
                )
            except (OSError, RuntimeError, ValueError, KeyError):
                continue
            closes = [item.close for item in history.candles]
            if len(closes) < 6:
                continue
            multiplier = rates.usd_to_twd if history.currency == Currency.USD else 1.0
            quotes[holding.symbol] = {
                "name": holding.name,
                "price_twd": closes[-1] * multiplier,
                "date": history.candles[-1].date,
                "return_5": closes[-1] / closes[-6] - 1,
                "return_20": closes[-1] / closes[max(0, len(closes) - 20)] - 1,
                "above_ma20": closes[-1] >= sum(closes[-20:]) / min(20, len(closes)),
                "institution": holding.institution.value,
                "asset_type": holding.asset_type.value,
            }

        state["benchmarks"] = _load_benchmarks(settings, state.get("benchmarks", {}))
        now = datetime.now(UTC)
        for config in BOT_CONFIGS:
            bot = state["bots"][config["id"]]
            _trade_bot(config, bot, quotes, now)
            bot["name"] = config["name"]
            bot["strategy"] = config["strategy"]
            bot["market_scope"] = config["market_scope"]
            bot["last_run_at"] = now.isoformat()
            _record_equity(bot, quotes, now)
        _save_state(state_path, state)
        return _to_response(state, quotes, now)


def load_paper_trading(settings: Settings, run_cycle: bool = True) -> PaperTradingResponse:
    if run_cycle and settings.paper_trading_enabled:
        return run_paper_trading_cycle(settings)
    with _lock:
        state = _load_state(
            _state_path(settings),
            settings.paper_trading_initial_cash_twd,
        )
        return _to_response(state, {}, datetime.now(UTC))


def _trade_bot(
    config: dict[str, Any],
    bot: dict[str, Any],
    quotes: dict[str, dict[str, Any]],
    now: datetime,
) -> None:
    bot_id = config["id"]
    positions = bot["positions"]
    eligible_symbols = {
        symbol
        for symbol, quote in quotes.items()
        if bot["last_cycles"].get(symbol) != f"{symbol}:{quote['date']}"
    }

    for symbol, position in list(positions.items()):
        quote = quotes.get(symbol)
        if quote is None or _is_allowed(config["market_scope"], quote):
            continue
        quantity = position["quantity"]
        amount = quantity * quote["price_twd"]
        bot["cash_twd"] += amount
        del positions[symbol]
        _record_trade(
            bot_id,
            bot,
            symbol,
            quote,
            "SELL",
            quantity,
            amount,
            now,
            "market_scope_changed",
        )

    allowed_quotes = {
        symbol: quote
        for symbol, quote in quotes.items()
        if _is_allowed(config["market_scope"], quote)
    }
    ranked = sorted(
        allowed_quotes.items(),
        key=lambda item: _ranking_score(item[1]),
        reverse=True,
    )
    target_symbols = {symbol for symbol, _ in ranked[:1]}
    for symbol, quote in allowed_quotes.items():
        if symbol not in eligible_symbols:
            continue
        position = positions.get(symbol)
        bot["last_cycles"][symbol] = f"{symbol}:{quote['date']}"
        if position and symbol not in target_symbols:
            quantity = position["quantity"]
            amount = quantity * quote["price_twd"]
            bot["cash_twd"] += amount
            del positions[symbol]
            _record_trade(
                bot_id,
                bot,
                symbol,
                quote,
                "SELL",
                quantity,
                amount,
                now,
                "portfolio_rotation",
            )

    for symbol, quote in ranked[:1]:
        if symbol not in eligible_symbols:
            continue
        if symbol in positions:
            continue
        budget = bot["cash_twd"] * config["allocation"]
        quantity = int(budget / quote["price_twd"])
        if quantity <= 0:
            continue
        amount = quantity * quote["price_twd"]
        existing = positions.get(symbol)
        if existing:
            total_quantity = existing["quantity"] + quantity
            existing["average_cost_twd"] = (
                existing["average_cost_twd"] * existing["quantity"] + amount
            ) / total_quantity
            existing["quantity"] = total_quantity
        else:
            positions[symbol] = {
                "name": quote["name"],
                "quantity": quantity,
                "average_cost_twd": quote["price_twd"],
            }
        bot["cash_twd"] -= amount
        _record_trade(bot_id, bot, symbol, quote, "BUY", quantity, amount, now, "signal")
        break


def _is_allowed(market_scope: str, quote: dict[str, Any]) -> bool:
    if quote["asset_type"] != AssetType.STOCK.value:
        return False
    institution = quote["institution"]
    if market_scope == "US_STOCKS":
        return institution == Institution.FIRSTRADE.value
    if market_scope == "TW_STOCKS":
        return institution == Institution.SINOPAC_SECURITIES.value
    return institution in {
        Institution.FIRSTRADE.value,
        Institution.SINOPAC_SECURITIES.value,
    }


def _ranking_score(quote: dict[str, Any]) -> float:
    return quote["return_5"] + quote["return_20"] * 0.35


def _record_trade(
    bot_id: str,
    bot: dict[str, Any],
    symbol: str,
    quote: dict[str, Any],
    side: str,
    quantity: float,
    amount: float,
    now: datetime,
    reason: str,
) -> None:
    trade_id = hashlib.sha256(
        f"{bot_id}|{symbol}|{side}|{now.isoformat()}".encode()
    ).hexdigest()[:24]
    bot["trades"].append(
        {
            "id": trade_id,
            "timestamp": now.isoformat(),
            "bot_id": bot_id,
            "symbol": symbol,
            "name": quote["name"],
            "side": side,
            "quantity": quantity,
            "price_twd": quote["price_twd"],
            "amount_twd": amount,
            "reason": reason,
        }
    )
    bot["trades"] = bot["trades"][-500:]


def _to_response(
    state: dict[str, Any],
    quotes: dict[str, dict[str, Any]],
    now: datetime,
) -> PaperTradingResponse:
    summaries = []
    for config in BOT_CONFIGS:
        bot = state["bots"][config["id"]]
        position_models = []
        position_value = 0.0
        for symbol, position in bot["positions"].items():
            price = quotes.get(symbol, {}).get(
                "price_twd",
                position["average_cost_twd"],
            )
            value = position["quantity"] * price
            position_value += value
            position_models.append(
                PaperBotPosition(
                    symbol=symbol,
                    name=position["name"],
                    quantity=position["quantity"],
                    average_cost_twd=position["average_cost_twd"],
                    market_price_twd=price,
                    market_value_twd=value,
                    unrealized_profit_twd=value
                    - position["quantity"] * position["average_cost_twd"],
                )
            )
        net_value = bot["cash_twd"] + position_value
        initial = state["initial_cash_twd"]
        summaries.append(
            PaperBotSummary(
                id=config["id"],
                name=config["name"],
                strategy=config["strategy"],
                market_scope=config["market_scope"],
                initial_cash_twd=initial,
                cash_twd=bot["cash_twd"],
                net_value_twd=net_value,
                total_return_twd=net_value - initial,
                return_rate=net_value / initial - 1,
                trade_count=len(bot["trades"]),
                last_run_at=bot.get("last_run_at"),
                positions=position_models,
                recent_trades=[
                    PaperBotTrade.model_validate(item)
                    for item in reversed(bot["trades"][-500:])
                ],
                equity_history=[
                    PaperBotEquityPoint.model_validate(item)
                    for item in bot["equity_history"][-365:]
                ],
                performance_history=_build_performance_history(state, bot),
            )
        )
    return PaperTradingResponse(generated_at=now, bots=summaries)


def _build_performance_history(
    state: dict[str, Any],
    bot: dict[str, Any],
) -> list[PaperBotPerformancePoint]:
    equity = bot["equity_history"][-365:]
    if not equity:
        return []
    initial_cash = state["initial_cash_twd"]
    benchmarks = state.get("benchmarks", {})
    tw_points = benchmarks.get("taiwan", [])
    us_points = benchmarks.get("us", [])
    first_date = equity[0]["timestamp"][:10]
    tw_base = _benchmark_close(tw_points, first_date)
    us_base = _benchmark_close(us_points, first_date)
    result = []
    for point in equity:
        day = point["timestamp"][:10]
        tw_close = _benchmark_close(tw_points, day)
        us_close = _benchmark_close(us_points, day)
        result.append(
            PaperBotPerformancePoint(
                timestamp=point["timestamp"],
                bot_value_twd=point["net_value_twd"],
                taiwan_index_value=(
                    initial_cash * tw_close / tw_base
                    if tw_close is not None and tw_base
                    else None
                ),
                us_index_value=(
                    initial_cash * us_close / us_base
                    if us_close is not None and us_base
                    else None
                ),
            )
        )
    return result


def _benchmark_close(points: list[dict[str, Any]], day: str) -> float | None:
    eligible = [point["close"] for point in points if point["date"] <= day]
    return eligible[-1] if eligible else None


def _load_benchmarks(
    settings: Settings,
    existing: dict[str, Any],
) -> dict[str, Any]:
    result = dict(existing)
    for key, symbol in (("taiwan", "^TWII"), ("us", "^GSPC")):
        try:
            history = load_benchmark_history(settings, symbol, 365)
        except (OSError, RuntimeError, ValueError, KeyError):
            continue
        result[key] = [
            {"date": candle.date, "close": candle.close}
            for candle in history.candles
        ]
    return result


def _load_state(path: Path, initial_cash: float) -> dict[str, Any]:
    if path.exists():
        state = json.loads(path.read_text(encoding="utf-8"))
    else:
        state = {
            "paper_only": True,
            "initial_cash_twd": initial_cash,
            "benchmarks": {},
            "bots": {
                config["id"]: {
                    "cash_twd": initial_cash,
                    "positions": {},
                    "trades": [],
                    "last_cycles": {},
                    "equity_history": [],
                }
                for config in BOT_CONFIGS
            },
        }
    state.setdefault("benchmarks", {})
    for config in BOT_CONFIGS:
        bot = state["bots"][config["id"]]
        bot.setdefault("equity_history", [])
        bot.setdefault("last_cycles", {})
    return state


def _record_equity(
    bot: dict[str, Any],
    quotes: dict[str, dict[str, Any]],
    now: datetime,
) -> None:
    position_value = sum(
        position["quantity"]
        * quotes.get(symbol, {}).get("price_twd", position["average_cost_twd"])
        for symbol, position in bot["positions"].items()
    )
    history = bot["equity_history"]
    point = {
        "timestamp": now.isoformat(),
        "net_value_twd": bot["cash_twd"] + position_value,
    }
    if history and history[-1]["timestamp"][:10] == point["timestamp"][:10]:
        history[-1] = point
    else:
        history.append(point)
    bot["equity_history"] = history[-730:]


def _save_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(
        json.dumps(state, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(path)


def _state_path(settings: Settings) -> Path:
    return settings.import_dir.parent / "paper-trading.json"
