from app.config import Settings
from app.models import AssetType, Currency, Holding, Institution


def load_shioaji_positions(settings: Settings) -> list[Holding]:
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

    api = sj.Shioaji()
    api.login(
        api_key=settings.shioaji_api_key,
        secret_key=settings.shioaji_secret_key,
    )
    stock_account = api.stock_account
    if stock_account is None:
        raise RuntimeError("No Shioaji stock account was returned")

    positions = api.list_positions(stock_account)
    return [
        Holding(
            id=f"shioaji-{position.code}",
            institution=Institution.SINOPAC_SECURITIES,
            account_name="永豐證券",
            symbol=position.code,
            name=position.code,
            asset_type=AssetType.STOCK,
            currency=Currency.TWD,
            quantity=float(position.quantity) * 1000,
            average_cost=float(position.price),
            market_price=float(position.last_price),
        )
        for position in positions
    ]
