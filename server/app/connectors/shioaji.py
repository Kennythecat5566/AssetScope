from collections.abc import Callable
from typing import Any

from app.config import Settings
from app.models import AssetType, Currency, Holding, Institution


def load_shioaji_assets(
    settings: Settings,
    api_factory: Callable[[], Any] | None = None,
) -> list[Holding]:
    if not settings.shioaji_enabled:
        return []
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

        positions = api.list_positions(
            account=stock_account,
            unit=sj.Unit.Share,
        )
        holdings = [_position_to_holding(position) for position in positions]

        account_balance = api.account_balance(account=stock_account)
        if account_balance.errmsg:
            raise RuntimeError(
                f"Shioaji account balance failed: {account_balance.errmsg}"
            )
        holdings.append(_balance_to_holding(account_balance.acc_balance))
        return holdings
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
