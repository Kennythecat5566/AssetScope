from types import SimpleNamespace

from app.config import Settings
from app.connectors.shioaji import load_shioaji_data
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

    def list_position_detail(self, *, account: object) -> list[object]:
        assert account is self.stock_account
        return [
            SimpleNamespace(
                code="2330",
                quantity=1,
                price=900000,
                date="2025-01-02",
                dseq="BUY001",
            ),
            SimpleNamespace(
                code="2330",
                quantity=0.25,
                price=225000,
                date="2025-02-03",
                dseq="BUY002",
            ),
        ]

    def list_profit_loss(
        self,
        *,
        account: object,
        begin_date: str,
        end_date: str,
        unit: object,
    ) -> list[object]:
        assert account is self.stock_account
        assert begin_date < end_date
        assert unit is not None
        return [
            SimpleNamespace(
                code="0050",
                quantity=1000,
                price=200,
                cost=180000,
                pnl=20000,
                date="2025-03-04",
                dseq="SELL001",
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

    data = load_shioaji_data(settings, api_factory=lambda: api)
    holdings = data.holdings

    assert len(holdings) == 2
    assert holdings[0].institution == Institution.SINOPAC_SECURITIES
    assert holdings[0].quantity == 1250
    assert holdings[1].institution == Institution.SINOPAC_BANK
    assert holdings[1].asset_type == AssetType.DEPOSIT
    assert holdings[1].market_price == 123456
    assert api.requested_unit is not None
    assert api.logged_out
    assert len(data.transactions) == 3
    assert data.transactions[0].transaction_type.value == "SELL"
    buys_by_date = {
        item.trade_date: item
        for item in data.transactions
        if item.transaction_type.value == "BUY"
    }
    assert buys_by_date["2025-01-02"].quantity == 1000
    assert buys_by_date["2025-02-03"].quantity == 250
