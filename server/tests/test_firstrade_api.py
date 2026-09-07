from pathlib import Path

from app.config import Settings
from app.connectors.firstrade_api import load_firstrade_api_data
from app.models import AssetType, Institution, TransactionType


class FakeFirstradeAccounts:
    account_numbers = ["12345678"]

    def get_positions(self, account: str) -> dict[str, object]:
        assert account == "12345678"
        return {
            "items": [
                {
                    "symbol": "VOO",
                    "description": "Vanguard S&P 500 ETF",
                    "quantity": "2.5",
                    "average_cost": "450.00",
                    "last_price": "500.00",
                }
            ]
        }

    def get_account_balances(self, account: str) -> dict[str, object]:
        assert account == "12345678"
        return {"balances": {"available_cash": "1234.56"}}

    def get_account_history(
        self,
        account: str,
        date_range: str = "ytd",
        custom_range: list[str] | None = None,
    ) -> dict[str, object]:
        assert account == "12345678"
        assert date_range == "ytd"
        assert custom_range is None
        return {
            "items": [
                {
                    "symbol": "VOO",
                    "description": "Vanguard S&P 500 ETF",
                    "action": "BUY",
                    "quantity": "2.5",
                    "price": "450",
                    "amount": "-1125",
                    "report_date": "2026-01-02",
                },
                {
                    "symbol": "VOO",
                    "description": "Vanguard S&P 500 ETF Dividend",
                    "action": "Dividend",
                    "quantity": "0",
                    "price": "0",
                    "amount": "8.25",
                    "report_date": "2026-02-03",
                },
            ]
        }


def test_loads_firstrade_api_assets_and_transactions(tmp_path: Path) -> None:
    token_file = tmp_path / "session.json"
    token_file.write_text("{}", encoding="utf-8")
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        firstrade_api_enabled=True,
        firstrade_api_token_file=token_file,
    )

    data = load_firstrade_api_data(
        settings,
        account_factory=FakeFirstradeAccounts,
    )

    assert len(data.holdings) == 2
    stock = next(item for item in data.holdings if item.symbol == "VOO")
    cash = next(item for item in data.holdings if item.symbol == "USD")
    assert stock.institution == Institution.FIRSTRADE
    assert stock.account_name == "Firstrade 5678"
    assert stock.asset_type == AssetType.ETF
    assert stock.quantity == 2.5
    assert stock.average_cost == 450
    assert stock.market_price == 500
    assert cash.market_price == 1234.56
    assert [item.transaction_type for item in data.transactions] == [
        TransactionType.BUY,
        TransactionType.DIVIDEND,
    ]


def test_treats_firstrade_cost_as_total_cost_basis(tmp_path: Path) -> None:
    class CostBasisAccounts(FakeFirstradeAccounts):
        def get_positions(self, account: str) -> dict[str, object]:
            return {
                "items": [
                    {
                        "symbol": "VOO",
                        "description": "Vanguard S&P 500 ETF",
                        "quantity": "2.5",
                        "cost": "1125.00",
                        "last_price": "500.00",
                    }
                ]
            }

    token_file = tmp_path / "session.json"
    token_file.write_text("{}", encoding="utf-8")
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        firstrade_api_enabled=True,
        firstrade_api_token_file=token_file,
    )

    data = load_firstrade_api_data(
        settings,
        account_factory=CostBasisAccounts,
    )

    stock = next(item for item in data.holdings if item.symbol == "VOO")
    assert stock.average_cost == 450


def test_firstrade_api_is_disabled_by_default() -> None:
    settings = Settings(
        api_token="a-long-enough-test-token",
        firstrade_api_enabled=False,
    )

    data = load_firstrade_api_data(settings)

    assert data.holdings == []
    assert data.transactions == []
