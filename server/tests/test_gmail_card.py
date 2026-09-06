import base64
from pathlib import Path

from app.config import Settings
from app.connectors.gmail_card import load_gmail_card_expenses, parse_card_notification
from app.models import Currency, ExpenseCategory


def _encoded(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode()).decode().rstrip("=")


def test_parses_chinese_credit_card_notification() -> None:
    message = {
        "id": "gmail-message-1",
        "internalDate": "1781247902000",
        "payload": {
            "headers": [{"name": "From", "value": "bank@example.com"}],
            "mimeType": "text/plain",
            "body": {
                "data": _encoded(
                    "信用卡交易通知\n"
                    "交易日期：2026/06/10\n"
                    "商店：STARBUCKS\n"
                    "消費金額：NT$180\n"
                    "卡號末四碼：1234"
                )
            },
        },
    }

    parsed = parse_card_notification(message)

    assert parsed is not None
    assert parsed.transaction_date == "2026-06-10"
    assert parsed.merchant == "STARBUCKS"
    assert parsed.amount == 180
    assert parsed.currency == Currency.TWD
    assert parsed.card_last_four == "1234"


def test_ignores_non_card_email() -> None:
    message = {
        "id": "gmail-message-2",
        "snippet": "Your newsletter is ready.",
        "payload": {"headers": [], "body": {}},
    }

    assert parse_card_notification(message) is None


def test_uses_message_date_when_card_date_is_invalid() -> None:
    message = {
        "id": "gmail-message-invalid-date",
        "payload": {
            "headers": [
                {"name": "From", "value": "bank@example.com"},
                {"name": "Date", "value": "Mon, 07 Sep 2026 00:23:00 +0800"},
            ],
            "mimeType": "text/plain",
            "body": {
                "data": _encoded(
                    "Credit card transaction\n"
                    "Date: 2026/23/07 00:23\n"
                    "Merchant: STARBUCKS\n"
                    "Amount: TWD 180"
                )
            },
        },
    }

    parsed = parse_card_notification(message)

    assert parsed is not None
    assert parsed.transaction_date == "2026-09-07"


def test_parses_roc_year_card_date() -> None:
    message = {
        "id": "gmail-message-roc-date",
        "payload": {
            "headers": [{"name": "From", "value": "bank@example.com"}],
            "mimeType": "text/plain",
            "body": {
                "data": _encoded(
                    "信用卡消費通知\n"
                    "交易日期：115/06/09\n"
                    "Merchant: BOOKSTORE\n"
                    "Amount: TWD 300"
                )
            },
        },
    }

    parsed = parse_card_notification(message)

    assert parsed is not None
    assert parsed.transaction_date == "2026-06-09"


def test_parses_english_month_day_year_card_date() -> None:
    message = {
        "id": "gmail-message-us-date",
        "payload": {
            "headers": [{"name": "From", "value": "bank@example.com"}],
            "mimeType": "text/plain",
            "body": {
                "data": _encoded(
                    "Credit card transaction\n"
                    "Date: 09/07/2026\n"
                    "Merchant: CAFE\n"
                    "Amount: USD 12.50"
                )
            },
        },
    }

    parsed = parse_card_notification(message)

    assert parsed is not None
    assert parsed.transaction_date == "2026-09-07"


def test_loads_gmail_expenses_from_service(tmp_path: Path) -> None:
    settings = Settings(
        api_token="a-long-enough-test-token",
        import_dir=tmp_path,
        gmail_expenses_enabled=True,
        gmail_token_file=tmp_path / "token.json",
        gmail_max_messages=10,
    )

    expenses, sources = load_gmail_card_expenses(settings, service=FakeGmailService())

    assert sources == ["gmail-card-notifications"]
    assert len(expenses) == 1
    assert expenses[0].merchant == "STARBUCKS"
    assert expenses[0].category == ExpenseCategory.DINING
    assert expenses[0].amount == 12.5
    assert expenses[0].currency == Currency.USD


class FakeGmailService:
    def users(self) -> "FakeGmailService":
        return self

    def messages(self) -> "FakeGmailService":
        return self

    def list(self, *, userId: str, q: str, maxResults: int) -> "FakeRequest":
        assert userId == "me"
        assert q
        assert maxResults == 10
        return FakeRequest({"messages": [{"id": "gmail-message-3"}]})

    def list_next(
        self,
        request: "FakeRequest",
        response: dict[str, object],
    ) -> None:
        return None

    def get(self, *, userId: str, id: str, format: str) -> "FakeRequest":
        assert userId == "me"
        assert id == "gmail-message-3"
        assert format == "full"
        return FakeRequest(
            {
                "id": "gmail-message-3",
                "internalDate": "1781247902000",
                "payload": {
                    "headers": [{"name": "From", "value": "card@example.com"}],
                    "mimeType": "text/plain",
                    "body": {
                        "data": _encoded(
                            "Credit card transaction\n"
                            "Date: 2026-06-11\n"
                            "Merchant: STARBUCKS\n"
                            "Amount: USD 12.50"
                        )
                    },
                },
            }
        )


class FakeRequest:
    def __init__(self, response: dict[str, object]) -> None:
        self.response = response

    def execute(self) -> dict[str, object]:
        return self.response
