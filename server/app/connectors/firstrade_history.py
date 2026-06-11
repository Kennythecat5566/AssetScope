import csv
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

from app.models import AssetType

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
    cash = ZERO

    for row in rows:
        cash += _decimal(row.get("Amount"))
        if (row.get("RecordType") or "").strip() != "Trade":
            continue

        action = (row.get("Action") or "").strip().upper()
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
            new_quantity = position.quantity + quantity
            total_cost = position.quantity * position.average_cost + quantity * price
            position.quantity = new_quantity
            position.average_cost = total_cost / new_quantity
        else:
            if quantity > position.quantity + Decimal("0.000001"):
                raise ValueError(
                    f"{source.name}: SELL quantity exceeds known holdings for {symbol}"
                )
            position.quantity -= quantity
            if abs(position.quantity) < Decimal("0.000001"):
                position.quantity = ZERO
                position.average_cost = ZERO
        position.latest_price = price
        position.name = description

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
    return len(normalized_rows)


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

