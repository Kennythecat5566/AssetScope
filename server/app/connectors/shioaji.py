from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, timedelta
import hashlib
from typing import Any

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
class ShioajiData:
    holdings: list[Holding]
    transactions: list[Transaction]


def load_shioaji_assets(
    settings: Settings,
    api_factory: Callable[[], Any] | None = None,
) -> list[Holding]:
    return load_shioaji_data(settings, api_factory).holdings


def load_shioaji_data(
    settings: Settings,
    api_factory: Callable[[], Any] | None = None,
) -> ShioajiData:
    if not settings.shioaji_enabled:
        return ShioajiData([], [])
    if not settings.shioaji_api_key or not settings.shioaji_secret_key:
        raise ValueError("Shioaji is enabled but API credentials are missing")

    try:
        import shioaji as sj
    except ImportError as error:
        raise RuntimeError(
            "Install the optional dependency with: pip install -e .[shioaji]"
        ) from error

    api = api_factory() if api_factory else sj.Shioaji()
    logged_in = False
    try:
        try:
            api.login(
                api_key=settings.shioaji_api_key,
                secret_key=settings.shioaji_secret_key,
                fetch_contract=False,
                subscribe_trade=False,
            )
            logged_in = True
            stock_account = api.stock_account
            if stock_account is None:
                raise RuntimeError("No Shioaji stock account was returned")
            if not getattr(stock_account, "signed", False):
                raise RuntimeError(
                    "Shioaji stock account is not API-signed. Complete the "
                    "SinoPac API agreement and stock API test, then wait for "
                    "the account signed status to become true."
                )

            positions = api.list_positions(
                account=stock_account,
                unit=sj.Unit.Share,
            )
            holdings = [_position_to_holding(position) for position in positions]
            position_details = (
                api.list_position_detail(account=stock_account)
                if positions
                else []
            )
            transactions = _position_details_to_transactions(
                positions,
                position_details,
            )
            begin_date = date.today() - timedelta(days=settings.shioaji_history_days)
            profit_losses = api.list_profit_loss(
                account=stock_account,
                begin_date=begin_date.isoformat(),
                end_date=date.today().isoformat(),
                unit=sj.Unit.Share,
            )
            transactions.extend(_profit_losses_to_transactions(profit_losses))

            account_balance = api.account_balance(account=stock_account)
            if account_balance.errmsg:
                raise RuntimeError(
                    f"Shioaji account balance failed: {account_balance.errmsg}"
                )
            holdings.append(_balance_to_holding(account_balance.acc_balance))
            return ShioajiData(
                holdings=holdings,
                transactions=sorted(
                    transactions,
                    key=lambda item: item.trade_date,
                    reverse=True,
                ),
            )
        except RuntimeError:
            raise
        except Exception as error:
            raise RuntimeError(f"Shioaji query failed: {error}") from error
    finally:
        if logged_in:
            api.logout()


def _position_to_holding(position: Any) -> Holding:
    return Holding(
        id=f"shioaji-stock-{position.code}",
        institution=Institution.SINOPAC_SECURITIES,
        account_name="永豐大戶投",
        symbol=position.code,
        name=position.code,
        asset_type=AssetType.STOCK,
        currency=Currency.TWD,
        quantity=float(position.quantity),
        average_cost=float(position.price),
        market_price=float(position.last_price),
    )


def _balance_to_holding(balance: float) -> Holding:
    return Holding(
        id="shioaji-settlement-balance",
        institution=Institution.SINOPAC_BANK,
        account_name="永豐證券交割帳戶",
        symbol="TWD",
        name="新台幣交割帳戶餘額",
        asset_type=AssetType.DEPOSIT,
        currency=Currency.TWD,
        quantity=1,
        average_cost=float(balance),
        market_price=float(balance),
    )


def _position_details_to_transactions(
    positions: list[Any],
    details: list[Any],
) -> list[Transaction]:
    shares_by_code = {
        str(position.code): float(position.quantity)
        for position in positions
    }
    detail_quantity_by_code: dict[str, float] = {}
    for detail in details:
        code = str(detail.code)
        detail_quantity_by_code[code] = (
            detail_quantity_by_code.get(code, 0.0) + float(detail.quantity)
        )

    transactions: list[Transaction] = []
    for detail in details:
        code = str(detail.code)
        raw_quantity = float(detail.quantity)
        total_raw = detail_quantity_by_code.get(code, raw_quantity)
        share_ratio = shares_by_code.get(code, raw_quantity) / total_raw
        quantity = raw_quantity * share_ratio
        total_cost = float(detail.price)
        unit_price = total_cost / quantity if quantity else 0.0
        transactions.append(
            Transaction(
                id=_stable_id("position", code, detail.date, detail.dseq),
                institution=Institution.SINOPAC_SECURITIES,
                account_name="SinoPac Securities",
                symbol=code,
                name=code,
                transaction_type=TransactionType.BUY,
                currency=Currency.TWD,
                quantity=quantity,
                price=unit_price,
                amount=-total_cost,
                realized_profit=0,
                trade_date=str(detail.date),
                settled_date=None,
            )
        )
    return transactions


def _profit_losses_to_transactions(items: list[Any]) -> list[Transaction]:
    transactions: list[Transaction] = []
    for item in items:
        quantity = float(getattr(item, "quantity", 0))
        price = float(getattr(item, "price", 0))
        cost = float(getattr(item, "cost", 0))
        pnl = float(getattr(item, "pnl", 0))
        trade_date = str(getattr(item, "date", ""))
        code = str(getattr(item, "code", ""))
        transactions.append(
            Transaction(
                id=_stable_id(
                    "profit-loss",
                    code,
                    trade_date,
                    getattr(item, "dseq", ""),
                ),
                institution=Institution.SINOPAC_SECURITIES,
                account_name="SinoPac Securities",
                symbol=code,
                name=code,
                transaction_type=TransactionType.SELL,
                currency=Currency.TWD,
                quantity=quantity,
                price=price,
                amount=cost + pnl,
                realized_profit=pnl,
                trade_date=trade_date,
                settled_date=None,
            )
        )
    return transactions


def _stable_id(*parts: object) -> str:
    return hashlib.sha256("|".join(map(str, parts)).encode()).hexdigest()[:24]
