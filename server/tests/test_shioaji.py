from types import SimpleNamespace

from app.config import Settings
from app.connectors.shioaji import load_shioaji_assets
from app.models import AssetType, Institution


class FakeShioaji:
    def __init__(self) -> None:
        self.stock_account = SimpleNamespace(signed=True)
        self.logged_out = False
        self.requested_unit = None

    def login(
        self,
        *,
        api_key: str,
        secret_key: str,
        fetch_contract: bool,
        subscribe_trade: bool,
    ) -> None:
        assert api_key == "test-api-key"
        assert secret_key == "test-secret-key"
        assert not fetch_contract
        assert not subscribe_trade

    def list_positions(self, *, account: object, unit: object) -> list[object]:
        assert account is self.stock_account
        self.requested_unit = unit
        return [
            SimpleNamespace(
                code="2330",
                quantity=1250,
                price=900,
                last_price=950,
            )
        ]

    def account_balance(self, *, account: object) -> object:
        assert account is self.stock_account
        return SimpleNamespace(acc_balance=123456, errmsg="")

    def logout(self) -> None:
        self.logged_out = True


def test_loads_stock_positions_and_settlement_balance() -> None:
    api = FakeShioaji()
    settings = Settings(
        api_token="a-long-enough-test-token",
        shioaji_enabled=True,
        shioaji_api_key="test-api-key",
        shioaji_secret_key="test-secret-key",
    )

    holdings = load_shioaji_assets(settings, api_factory=lambda: api)

    assert len(holdings) == 2
    assert holdings[0].institution == Institution.SINOPAC_SECURITIES
    assert holdings[0].quantity == 1250
    assert holdings[1].institution == Institution.SINOPAC_BANK
    assert holdings[1].asset_type == AssetType.DEPOSIT
    assert holdings[1].market_price == 123456
    assert api.requested_unit is not None
    assert api.logged_out
