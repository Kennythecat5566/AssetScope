from __future__ import annotations

from datetime import UTC, datetime
import hashlib
import json
from pathlib import Path
from threading import Lock
from typing import Any

from app.config import Settings
from app.connectors.exchange_rates import load_exchange_rates
from app.connectors.price_history import load_price_history
from app.models import (
    AssetType,
    Currency,
    Holding,
    PaperBotPosition,
    PaperBotEquityPoint,
    PaperBotSummary,
    PaperBotTrade,
    PaperTradingResponse,
)
from app.service import build_portfolio


BOT_CONFIGS = (
    ("aggressive", "激進型", "短線動能、高週轉", 0.35),
    ("conservative", "穩健型", "趨勢確認、低部位", 0.12),
    ("unrestricted", "無限制型", "虛擬帳戶內不設集中度與週轉限制", 0.90),
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
            }

        now = datetime.now(UTC)
        for bot_id, name, strategy, allocation in BOT_CONFIGS:
            bot = state["bots"][bot_id]
            _trade_bot(bot_id, bot, quotes, allocation, now)
            bot["name"] = name
            bot["strategy"] = strategy
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
    bot_id: str,
    bot: dict[str, Any],
    quotes: dict[str, dict[str, Any]],
    allocation: float,
    now: datetime,
) -> None:
    positions = bot["positions"]
    scored = sorted(quotes.items(), key=lambda item: item[1]["return_5"], reverse=True)
    eligible_symbols = {
        symbol
        for symbol, quote in quotes.items()
        if bot["last_cycles"].get(symbol) != f"{symbol}:{quote['date']}"
    }
    for symbol, quote in quotes.items():
        if symbol not in eligible_symbols:
            continue
        position = positions.get(symbol)
        signal = _signal(bot_id, quote, position is not None)
        cycle_key = f"{symbol}:{quote['date']}"
        bot["last_cycles"][symbol] = cycle_key
        if signal == "SELL" and position:
            quantity = position["quantity"]
            amount = quantity * quote["price_twd"]
            bot["cash_twd"] += amount
            del positions[symbol]
            _record_trade(bot_id, bot, symbol, quote, "SELL", quantity, amount, now, signal)

    candidates = scored if bot_id != "unrestricted" else scored[:1]
    for symbol, quote in candidates:
        if symbol not in eligible_symbols:
            continue
        if _signal(bot_id, quote, symbol in positions) != "BUY":
            continue
        budget = bot["cash_twd"] * allocation
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
        if bot_id != "aggressive":
            break


def _signal(bot_id: str, quote: dict[str, Any], holding: bool) -> str:
    if bot_id == "aggressive":
        if quote["return_5"] > 0.006:
            return "BUY"
        if holding and quote["return_5"] < -0.004:
            return "SELL"
    elif bot_id == "conservative":
        if quote["return_20"] > 0.025 and quote["above_ma20"]:
            return "BUY"
        if holding and not quote["above_ma20"]:
            return "SELL"
    else:
        if quote["return_5"] >= 0:
            return "BUY"
        if holding:
            return "SELL"
    return "HOLD"


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
    for bot_id, name, strategy, _ in BOT_CONFIGS:
        bot = state["bots"][bot_id]
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
                id=bot_id,
                name=name,
                strategy=strategy,
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
                    for item in reversed(bot["trades"][-20:])
                ],
                equity_history=[
                    PaperBotEquityPoint.model_validate(item)
                    for item in bot["equity_history"][-365:]
                ],
            )
        )
    return PaperTradingResponse(generated_at=now, bots=summaries)


def _load_state(path: Path, initial_cash: float) -> dict[str, Any]:
    if path.exists():
        state = json.loads(path.read_text(encoding="utf-8"))
    else:
        state = {
            "paper_only": True,
            "initial_cash_twd": initial_cash,
            "bots": {
                bot_id: {
                    "cash_twd": initial_cash,
                    "positions": {},
                    "trades": [],
                    "last_cycles": {},
                    "equity_history": [],
                }
                for bot_id, *_ in BOT_CONFIGS
            },
        }
    for bot_id, *_ in BOT_CONFIGS:
        bot = state["bots"][bot_id]
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
