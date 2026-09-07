import hashlib
import importlib
import json
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Protocol

from app.config import Settings
from app.models import (
    AssetType,
    Currency,
    Holding,
    Institution,
    Transaction,
    TransactionType,
)


@dataclass
class FirstradeApiData:
    holdings: list[Holding]
    transactions: list[Transaction]


class AccountDataProtocol(Protocol):
    account_numbers: list[str]

    def get_positions(self, account: str) -> dict[str, Any]: ...

    def get_account_balances(self, account: str) -> dict[str, Any]: ...

    def get_account_history(
        self,
        account: str,
        date_range: str = "ytd",
        custom_range: list[str] | None = None,
    ) -> dict[str, Any]: ...


def load_firstrade_api_data(
    settings: Settings,
    account_factory: Any | None = None,
) -> FirstradeApiData:
    if not settings.firstrade_api_enabled:
        return FirstradeApiData([], [])

    token_file = _resolve_server_path(settings.firstrade_api_token_file)
    if not token_file.exists():
        raise RuntimeError(
            "Firstrade API session is missing. Run authorize-firstrade-api.cmd first."
        )

    if account_factory is None:
        account_factory = _build_account_factory(settings, token_file)

    try:
        accounts: AccountDataProtocol = account_factory()
        account_numbers = [
            settings.firstrade_api_account.strip()
        ] if settings.firstrade_api_account.strip() else accounts.account_numbers

        holdings: list[Holding] = []
        transactions: list[Transaction] = []
        for account in account_numbers:
            positions = accounts.get_positions(account)
            holdings.extend(_positions_to_holdings(account, positions))
            holdings.extend(
                _balance_to_cash_holding(account, accounts.get_account_balances(account))
            )
            transactions.extend(
                _history_to_transactions(
                    account,
                    accounts.get_account_history(
                        account,
                        date_range=settings.firstrade_api_history_range,
                    ),
                )
            )
    except Exception as error:
        raise RuntimeError(
            "Firstrade API session is unavailable. "
            "Run authorize-firstrade-api.cmd again."
        ) from error

    return FirstradeApiData(holdings=holdings, transactions=transactions)


def _build_account_factory(settings: Settings, token_file: Path) -> Any:
    _add_firstrade_path(settings.firstrade_api_path)
    account_module = importlib.import_module("firstrade.account")
    tokens = json.loads(token_file.read_text(encoding="utf-8"))

    def factory() -> AccountDataProtocol:
        session = account_module.FTSession(username="", password="")
        session.build_session_from_tokens(tokens)
        return account_module.FTAccountData(session)

    return factory


def _add_firstrade_path(path: Path) -> None:
    package_root = _resolve_server_path(path)
    if not package_root.exists():
        raise RuntimeError(f"Firstrade API wrapper was not found: {package_root}")
    root = str(package_root)
    if root not in sys.path:
        sys.path.insert(0, root)


def _positions_to_holdings(account: str, payload: dict[str, Any]) -> list[Holding]:
    items = _items(payload)
    holdings: list[Holding] = []
    for index, item in enumerate(items, start=1):
        symbol = str(_first(item, "symbol", "ticker", "security", default="")).strip().upper()
        if not symbol:
            continue
        quantity = _decimal(_first(item, "quantity", "qty", "shares", default=0))
        if quantity <= 0:
            continue
        price = _decimal(
            _first(
                item,
                "market_price",
                "last_price",
                "last",
                "price",
                "current_price",
                default=0,
            )
        )
        average_cost = _average_cost(item, quantity)
        name = str(
            _first(
                item,
                "description",
                "company_name",
                "security_name",
                "name",
                default=symbol,
            )
        ).strip()
        holdings.append(
            Holding(
                id=_id("firstrade-api", account, symbol),
                institution=Institution.FIRSTRADE,
                account_name=f"Firstrade {account[-4:]}",
                symbol=symbol,
                name=name or symbol,
                asset_type=_asset_type(name),
                currency=Currency.USD,
                quantity=float(quantity),
                average_cost=float(average_cost),
                market_price=float(price),
            )
        )
    return holdings


def _balance_to_cash_holding(account: str, payload: dict[str, Any]) -> list[Holding]:
    cash = _find_number_by_keywords(
        payload,
        [
            "cash",
            "cash_balance",
            "settled_cash",
            "available_cash",
            "money_market",
        ],
    )
    if cash <= 0:
        return []
    return [
        Holding(
            id=_id("firstrade-api", account, "USD"),
            institution=Institution.FIRSTRADE,
            account_name=f"Firstrade {account[-4:]}",
            symbol="USD",
            name="US Dollar Cash",
            asset_type=AssetType.CASH,
            currency=Currency.USD,
            quantity=1,
            average_cost=float(cash),
            market_price=float(cash),
        )
    ]


def _history_to_transactions(account: str, payload: dict[str, Any]) -> list[Transaction]:
    transactions: list[Transaction] = []
    for index, item in enumerate(_items(payload), start=1):
        symbol = str(_first(item, "symbol", "ticker", default="")).strip().upper()
        action = str(
            _first(item, "action", "type", "transaction_type", "activity", default="")
        ).upper()
        description = str(_first(item, "description", "name", default=symbol)).strip()
        transaction_type = _transaction_type(action, description)
        if transaction_type is None:
            continue
        date = str(
            _first(item, "report_date", "trade_date", "date", "transaction_date", default="")
        ).strip()
        if not date:
            date = datetime.now(UTC).date().isoformat()
        quantity = abs(_decimal(_first(item, "quantity", "qty", "shares", default=0)))
        price = abs(_decimal(_first(item, "price", "trade_price", default=0)))
        amount = _decimal(_first(item, "amount", "net_amount", default=0))
        transactions.append(
            Transaction(
                id=_id("firstrade-api-tx", account, str(index), date, symbol, str(amount)),
                institution=Institution.FIRSTRADE,
                account_name=f"Firstrade {account[-4:]}",
                symbol=symbol or "USD",
                name=description or symbol or "Firstrade transaction",
                transaction_type=transaction_type,
                currency=Currency.USD,
                quantity=float(quantity),
                price=float(price),
                amount=float(amount),
                realized_profit=0,
                trade_date=date[:10],
                settled_date=None,
            )
        )
    return transactions


def _items(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, dict):
        value = payload.get("items")
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    return []


def _first(item: dict[str, Any], *keys: str, default: Any = None) -> Any:
    normalized = {key.lower(): value for key, value in item.items()}
    for key in keys:
        if key.lower() in normalized:
            return normalized[key.lower()]
    return default


def _average_cost(item: dict[str, Any], quantity: Decimal) -> Decimal:
    average = _decimal(
        _first(item, "average_cost", "avg_cost", "cost_per_share", default=0)
    )
    if average > 0:
        return average
    cost_basis = _decimal(_first(item, "cost_basis", "total_cost", "cost", default=0))
    if cost_basis > 0 and quantity > 0:
        return cost_basis / quantity
    return Decimal("0")


def _find_number_by_keywords(payload: Any, keywords: list[str]) -> Decimal:
    best = Decimal("0")

    def walk(node: Any, path: list[str]) -> None:
        nonlocal best
        if isinstance(node, dict):
            for key, value in node.items():
                walk(value, [*path, str(key)])
            return
        if isinstance(node, list):
            for index, value in enumerate(node):
                walk(value, [*path, str(index)])
            return

        key_path = ".".join(path).lower()
        if any(keyword in key_path for keyword in keywords):
            number = _decimal(node)
            if number > best:
                best = number

    walk(payload, [])
    return best


def _asset_type(name: str) -> AssetType:
    normalized = name.upper()
    return AssetType.ETF if " ETF" in normalized or normalized.endswith("ETF") else AssetType.STOCK


def _transaction_type(action: str, description: str) -> TransactionType | None:
    text = f"{action} {description}".upper()
    if "DIVIDEND" in text or "DIV" in text:
        return TransactionType.DIVIDEND
    if "BUY" in text:
        return TransactionType.BUY
    if "SELL" in text or "SOLD" in text:
        return TransactionType.SELL
    return None


def _decimal(value: Any) -> Decimal:
    if value is None:
        return Decimal("0")
    try:
        return Decimal(str(value).replace(",", "").replace("$", "").strip() or "0")
    except (InvalidOperation, ValueError):
        return Decimal("0")


def _id(*parts: str) -> str:
    return hashlib.sha256("|".join(parts).encode()).hexdigest()[:24]


def _resolve_server_path(path: Path) -> Path:
    if path.is_absolute():
        return path
    return (Path(__file__).resolve().parents[2] / path).resolve()
