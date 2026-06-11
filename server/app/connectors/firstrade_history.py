import csv
import hashlib
import json
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

from app.models import (
    AssetType,
    Currency,
    Institution,
    PerformanceSummary,
    Transaction,
    TransactionType,
)

ZERO = Decimal("0")


@dataclass
class Position:
    symbol: str
    name: str
    asset_type: AssetType
    quantity: Decimal = ZERO
    average_cost: Decimal = ZERO
    latest_price: Decimal = ZERO


def convert_firstrade_history(source: Path, destination: Path) -> int:
    with source.open("r", encoding="utf-8-sig", newline="") as stream:
        rows = list(csv.DictReader(stream))

    _validate_headers(rows, source)
    positions: dict[str, Position] = {}
    transactions: list[Transaction] = []
    cash = ZERO
    realized_profit = ZERO
    dividend_income = ZERO
    total_buy_cost = ZERO

    for row_number, row in enumerate(rows, start=2):
        cash += _decimal(row.get("Amount"))
        action = (row.get("Action") or "").strip()
        if action == "Dividend":
            amount = _decimal(row.get("Amount"))
            dividend_income += amount
            transactions.append(
                _transaction(
                    source=source,
                    row_number=row_number,
                    row=row,
                    transaction_type=TransactionType.DIVIDEND,
                    realized_profit=ZERO,
                )
            )
        if (row.get("RecordType") or "").strip() != "Trade":
            continue

        action = action.upper()
        if action not in {"BUY", "SELL"}:
            continue
        symbol = (row.get("Symbol") or "").strip().upper()
        if not symbol:
            continue
        quantity = abs(_decimal(row.get("Quantity")))
        price = _decimal(row.get("Price"))
        if quantity <= ZERO or price < ZERO:
            continue

        description = " ".join((row.get("Description") or symbol).split())
        position = positions.setdefault(
            symbol,
            Position(
                symbol=symbol,
                name=description,
                asset_type=_infer_asset_type(description),
            ),
        )
        if action == "BUY":
            total_buy_cost += quantity * price
            new_quantity = position.quantity + quantity
            total_cost = position.quantity * position.average_cost + quantity * price
            position.quantity = new_quantity
            position.average_cost = total_cost / new_quantity
        else:
            if quantity > position.quantity + Decimal("0.000001"):
                raise ValueError(
                    f"{source.name}: SELL quantity exceeds known holdings for {symbol}"
                )
            sale_profit = quantity * (price - position.average_cost)
            realized_profit += sale_profit
            position.quantity -= quantity
            if abs(position.quantity) < Decimal("0.000001"):
                position.quantity = ZERO
                position.average_cost = ZERO
        position.latest_price = price
        position.name = description
        transactions.append(
            _transaction(
                source=source,
                row_number=row_number,
                row=row,
                transaction_type=TransactionType(action),
                realized_profit=sale_profit if action == "SELL" else ZERO,
            )
        )

    normalized_rows = [
        {
            "institution": "firstrade",
            "account": "Firstrade",
            "symbol": position.symbol,
            "name": position.name,
            "type": position.asset_type.value,
            "currency": "USD",
            "quantity": _format_decimal(position.quantity),
            "average_cost": _format_decimal(position.average_cost),
            "market_price": _format_decimal(position.latest_price),
        }
        for position in sorted(positions.values(), key=lambda item: item.symbol)
        if position.quantity > ZERO
    ]
    if cash > ZERO:
        normalized_rows.append(
            {
                "institution": "firstrade",
                "account": "Firstrade",
                "symbol": "USD",
                "name": "US Dollar Cash (derived from transaction history)",
                "type": "CASH",
                "currency": "USD",
                "quantity": "1",
                "average_cost": _format_decimal(cash),
                "market_price": _format_decimal(cash),
            }
        )

    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=[
                "institution",
                "account",
                "symbol",
                "name",
                "type",
                "currency",
                "quantity",
                "average_cost",
                "market_price",
            ],
        )
        writer.writeheader()
        writer.writerows(normalized_rows)
    temporary.replace(destination)
    unrealized_profit = sum(
        position.quantity * (position.latest_price - position.average_cost)
        for position in positions.values()
        if position.quantity > ZERO
    )
    total_return = realized_profit + unrealized_profit + dividend_income
    performance = PerformanceSummary(
        realized_profit=float(realized_profit),
        unrealized_profit=float(unrealized_profit),
        dividend_income=float(dividend_income),
        total_return=float(total_return),
        return_rate=float(total_return / total_buy_cost) if total_buy_cost > ZERO else 0,
        total_buy_cost=float(total_buy_cost),
        valuation_note="Firstrade CSV 無即時行情；未實現損益暫以各標的最近成交價估算。",
    )
    _write_activity(
        destination.with_suffix(".activity.json"),
        transactions=sorted(transactions, key=lambda item: item.trade_date, reverse=True),
        performance=performance,
    )
    return len(normalized_rows)


def load_firstrade_activity(
    path: Path,
) -> tuple[list[Transaction], PerformanceSummary]:
    if not path.exists():
        return [], PerformanceSummary()
    payload = json.loads(path.read_text(encoding="utf-8"))
    return (
        [Transaction.model_validate(item) for item in payload.get("transactions", [])],
        PerformanceSummary.model_validate(payload.get("performance", {})),
    )


def _validate_headers(rows: list[dict[str, str]], source: Path) -> None:
    if not rows:
        raise ValueError(f"{source.name} is empty")
    required = {
        "Symbol",
        "Quantity",
        "Price",
        "Action",
        "Description",
        "Amount",
        "RecordType",
    }
    missing = required - set(rows[0])
    if missing:
        raise ValueError(f"{source.name} missing columns: {', '.join(sorted(missing))}")


def _decimal(value: str | None) -> Decimal:
    cleaned = (value or "0").strip().replace(",", "").replace("$", "")
    return Decimal(cleaned or "0")


def _infer_asset_type(description: str) -> AssetType:
    normalized = description.upper()
    return AssetType.ETF if " ETF" in normalized or normalized.endswith("ETF") else AssetType.STOCK


def _format_decimal(value: Decimal) -> str:
    return format(value.quantize(Decimal("0.000001")), "f").rstrip("0").rstrip(".") or "0"


def _transaction(
    source: Path,
    row_number: int,
    row: dict[str, str],
    transaction_type: TransactionType,
    realized_profit: Decimal,
) -> Transaction:
    symbol = (row.get("Symbol") or "").strip().upper()
    description = " ".join((row.get("Description") or symbol or "Dividend").split())
    stable_key = f"{source.name}|{row_number}|{row.get('TradeDate')}|{symbol}|{transaction_type}"
    return Transaction(
        id=hashlib.sha256(stable_key.encode()).hexdigest()[:24],
        institution=Institution.FIRSTRADE,
        account_name="Firstrade",
        symbol=symbol or "CASH",
        name=description,
        transaction_type=transaction_type,
        currency=Currency.USD,
        quantity=float(abs(_decimal(row.get("Quantity")))),
        price=float(_decimal(row.get("Price"))),
        amount=float(_decimal(row.get("Amount"))),
        realized_profit=float(realized_profit),
        trade_date=(row.get("TradeDate") or "").strip(),
        settled_date=(row.get("SettledDate") or "").strip() or None,
    )


def _write_activity(
    destination: Path,
    transactions: list[Transaction],
    performance: PerformanceSummary,
) -> None:
    payload = {
        "transactions": [item.model_dump(mode="json") for item in transactions],
        "performance": performance.model_dump(mode="json"),
    }
    temporary = destination.with_suffix(".tmp")
    temporary.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    temporary.replace(destination)
