import base64
import hashlib
import re
from dataclasses import dataclass
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from pathlib import Path
from typing import Any, Protocol

from app.config import Settings
from app.connectors.sinopac_card import _category
from app.connectors.sinopac_card_pdf import extract_pdf_text, parse_sinopac_card_pdf_text
from app.models import Currency, Expense, ExpenseCategory, Institution

GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly"


@dataclass
class ParsedCardNotification:
    message_id: str
    transaction_date: str
    merchant: str
    amount: float
    currency: Currency
    card_last_four: str = ""
    note: str = ""


class GmailServiceProtocol(Protocol):
    def users(self) -> Any: ...


def load_gmail_card_expenses(
    settings: Settings,
    service: GmailServiceProtocol | None = None,
) -> tuple[list[Expense], list[str]]:
    if not settings.gmail_expenses_enabled:
        return [], []

    if service is None:
        service = _build_gmail_service(settings)

    messages = _list_messages(service, settings.gmail_query, settings.gmail_max_messages)
    expenses: list[Expense] = []
    for message_ref in messages:
        message_id = message_ref.get("id")
        if not message_id:
            continue
        message = _get_message(service, message_id)
        expenses.extend(_expenses_from_message(settings, service, message))
    return (
        sorted(expenses, key=lambda item: item.transaction_date, reverse=True),
        ["gmail-card-notifications"] if expenses else [],
    )


def parse_card_notification(message: dict[str, Any]) -> ParsedCardNotification | None:
    message_id = str(message.get("id") or "")
    text = _message_text(message)
    if not _looks_like_card_notification(text):
        return None

    amount, currency = _extract_amount(text)
    if amount <= 0:
        return None

    merchant = _extract_merchant(text) or "Credit card notification"
    transaction_date = _extract_date(text) or _message_date(message)
    card_last_four = _extract_card_last_four(text)
    note = _header(message, "From")

    return ParsedCardNotification(
        message_id=message_id,
        transaction_date=transaction_date,
        merchant=merchant[:120],
        amount=amount,
        currency=currency,
        card_last_four=card_last_four,
        note=note[:160],
    )


def _expenses_from_message(
    settings: Settings,
    service: GmailServiceProtocol,
    message: dict[str, Any],
) -> list[Expense]:
    expenses: list[Expense] = []
    parsed = parse_card_notification(message)
    if parsed is not None:
        expenses.append(_to_expense(parsed))

    if settings.gmail_pdf_attachments_enabled:
        expenses.extend(_pdf_attachment_expenses(settings, service, message))
    return expenses


def _build_gmail_service(settings: Settings) -> GmailServiceProtocol:
    token_file = _resolve_server_path(settings.gmail_token_file)
    if not token_file.exists():
        raise RuntimeError("Gmail token is missing. Run authorize-gmail.cmd first.")

    try:
        from google.auth.transport.requests import Request
        from google.oauth2.credentials import Credentials
        from googleapiclient.discovery import build
    except ImportError as error:
        raise RuntimeError(
            "Install Gmail dependencies with: pip install -e .[gmail]"
        ) from error

    credentials = Credentials.from_authorized_user_file(
        str(token_file),
        [GMAIL_READONLY_SCOPE],
    )
    if credentials.expired and credentials.refresh_token:
        credentials.refresh(Request())
        token_file.write_text(credentials.to_json(), encoding="utf-8")
    if not credentials.valid:
        raise RuntimeError("Gmail token is invalid. Run authorize-gmail.cmd again.")
    return build("gmail", "v1", credentials=credentials, cache_discovery=False)


def _list_messages(
    service: GmailServiceProtocol,
    query: str,
    max_messages: int,
) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    request = (
        service.users()
        .messages()
        .list(userId="me", q=query, maxResults=min(max_messages, 100))
    )
    while request is not None and len(result) < max_messages:
        response = request.execute()
        result.extend(response.get("messages", []))
        if len(result) >= max_messages:
            break
        request = service.users().messages().list_next(request, response)
    return result[:max_messages]


def _get_message(service: GmailServiceProtocol, message_id: str) -> dict[str, Any]:
    return (
        service.users()
        .messages()
        .get(userId="me", id=message_id, format="full")
        .execute()
    )


def _get_attachment(
    service: GmailServiceProtocol,
    message_id: str,
    attachment_id: str,
) -> bytes:
    response = (
        service.users()
        .messages()
        .attachments()
        .get(userId="me", messageId=message_id, id=attachment_id)
        .execute()
    )
    data = response.get("data")
    if not isinstance(data, str):
        return b""
    return _decode_base64url_bytes(data)


def _pdf_attachment_expenses(
    settings: Settings,
    service: GmailServiceProtocol,
    message: dict[str, Any],
) -> list[Expense]:
    message_id = str(message.get("id") or "")
    result: list[Expense] = []
    for filename, payload in _pdf_attachment_payloads(service, message):
        try:
            text = extract_pdf_text(payload, settings.sinopac_card_pdf_password)
            expenses = parse_sinopac_card_pdf_text(text, f"gmail:{message_id}:{filename}")
        except ValueError:
            continue
        for expense in expenses:
            result.append(
                expense.model_copy(
                    update={
                        "id": _gmail_pdf_expense_id(message_id, filename, expense),
                        "note": f"{_header(message, 'From')[:120]} | PDF: {filename}",
                    }
                )
            )
    return result


def _pdf_attachment_payloads(
    service: GmailServiceProtocol,
    message: dict[str, Any],
) -> list[tuple[str, bytes]]:
    message_id = str(message.get("id") or "")
    payload = message.get("payload")
    if not isinstance(payload, dict):
        return []
    return list(_walk_pdf_attachment_payloads(service, message_id, payload))


def _walk_pdf_attachment_payloads(
    service: GmailServiceProtocol,
    message_id: str,
    payload: dict[str, Any],
) -> list[tuple[str, bytes]]:
    result: list[tuple[str, bytes]] = []
    filename = str(payload.get("filename") or "")
    mime_type = str(payload.get("mimeType") or "")
    body = payload.get("body") if isinstance(payload.get("body"), dict) else {}
    is_pdf = filename.lower().endswith(".pdf") or mime_type in {
        "application/pdf",
        "application/octet-stream",
    }
    if filename and is_pdf:
        data = body.get("data")
        attachment_id = body.get("attachmentId")
        if isinstance(data, str):
            decoded = _decode_base64url_bytes(data)
        elif isinstance(attachment_id, str):
            decoded = _get_attachment(service, message_id, attachment_id)
        else:
            decoded = b""
        if decoded:
            result.append((filename, decoded))

    for part in payload.get("parts", []) or []:
        if isinstance(part, dict):
            result.extend(_walk_pdf_attachment_payloads(service, message_id, part))
    return result


def _gmail_pdf_expense_id(message_id: str, filename: str, expense: Expense) -> str:
    stable_key = "|".join(
        [
            "gmail-card-pdf",
            message_id,
            filename,
            expense.transaction_date,
            expense.merchant,
            str(expense.amount),
            expense.card_last_four,
        ]
    )
    return hashlib.sha256(stable_key.encode()).hexdigest()[:24]


def _to_expense(parsed: ParsedCardNotification) -> Expense:
    stable_key = "|".join(
        [
            "gmail-card",
            parsed.message_id,
            parsed.transaction_date,
            parsed.merchant,
            str(parsed.amount),
        ]
    )
    return Expense(
        id=hashlib.sha256(stable_key.encode()).hexdigest()[:24],
        institution=Institution.SINOPAC_BANK,
        transaction_date=parsed.transaction_date,
        posted_date=None,
        merchant=parsed.merchant,
        category=_category("", parsed.merchant),
        amount=parsed.amount,
        currency=parsed.currency,
        card_last_four=parsed.card_last_four,
        note=parsed.note,
    )


def _message_text(message: dict[str, Any]) -> str:
    payload = message.get("payload")
    parts = []
    if isinstance(payload, dict):
        parts.extend(_payload_text(payload))
    snippet = message.get("snippet")
    if isinstance(snippet, str):
        parts.append(snippet)
    return "\n".join(part for part in parts if part)


def _payload_text(payload: dict[str, Any]) -> list[str]:
    result: list[str] = []
    mime_type = str(payload.get("mimeType") or "")
    body = payload.get("body")
    if mime_type.startswith("text/") and isinstance(body, dict):
        data = body.get("data")
        if isinstance(data, str):
            result.append(_decode_base64url(data))

    for part in payload.get("parts", []) or []:
        if isinstance(part, dict):
            result.extend(_payload_text(part))
    return result


def _decode_base64url(value: str) -> str:
    return _decode_base64url_bytes(value).decode(
        "utf-8",
        errors="ignore",
    )


def _decode_base64url_bytes(value: str) -> bytes:
    padded = value + "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(padded.encode())


def _looks_like_card_notification(text: str) -> bool:
    normalized = text.lower()
    return any(
        keyword in normalized
        for keyword in (
            "信用卡",
            "刷卡",
            "消費",
            "交易通知",
            "card",
            "credit",
            "transaction",
        )
    )


def _extract_amount(text: str) -> tuple[float, Currency]:
    patterns = [
        (Currency.TWD, r"(?:NT\$|NTD|TWD|新台幣|新臺幣)\s*([\d,]+(?:\.\d+)?)"),
        (Currency.USD, r"(?:US\$|USD|\$)\s*([\d,]+(?:\.\d+)?)"),
        (Currency.TWD, r"([\d,]+(?:\.\d+)?)\s*元"),
        (Currency.TWD, r"(?:金額|消費金額|交易金額)[:：\s]*(?:NT\$|NTD|TWD)?\s*([\d,]+(?:\.\d+)?)"),
    ]
    for currency, pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            return float(match.group(1).replace(",", "")), currency
    return 0, Currency.TWD


def _extract_merchant(text: str) -> str:
    patterns = [
        r"(?:商店|特店|店家|消費地點|交易店家|Merchant)[:：]\s*([^\n\r，,。;；]+)",
        r"(?:於|在)\s*([A-Za-z0-9\u4e00-\u9fff ._\-&]+?)\s*(?:消費|刷卡|交易)",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            return " ".join(match.group(1).split())
    return ""


def _extract_date(text: str) -> str | None:
    label_pattern = (
        r"(?:交易日期|消費日期|授權日期|交易時間|消費時間|日期|"
        r"Date|Transaction Date)[:：\s]*"
    )
    for label in re.finditer(label_pattern, text, re.IGNORECASE):
        parsed = _parse_date_candidate(text[label.end() : label.end() + 48])
        if parsed:
            return parsed

    # Fallback only accepts unambiguous full years or ROC years.
    for pattern in (
        r"(?P<year>\d{4})[/-](?P<month>\d{1,2})[/-](?P<day>\d{1,2})",
        r"(?P<year>\d{3})[/-](?P<month>\d{1,2})[/-](?P<day>\d{1,2})",
    ):
        match = re.search(pattern, text)
        if match:
            parsed = _date_from_parts(
                int(match.group("year")),
                int(match.group("month")),
                int(match.group("day")),
            )
            if parsed:
                return parsed
    return None


def _parse_date_candidate(text: str) -> str | None:
    patterns = [
        r"(?<!\d)(?P<year>\d{4})[/-](?P<month>\d{1,2})[/-](?P<day>\d{1,2})(?!\d)",
        r"(?<!\d)(?P<month>\d{1,2})[/-](?P<day>\d{1,2})[/-](?P<year>\d{4})(?!\d)",
        r"(?<!\d)(?P<year>\d{2,3})[/-](?P<month>\d{1,2})[/-](?P<day>\d{1,2})(?!\d)",
    ]
    for pattern in patterns:
        match = re.search(pattern, text)
        if not match:
            continue
        parsed = _date_from_parts(
            int(match.group("year")),
            int(match.group("month")),
            int(match.group("day")),
        )
        if parsed:
            return parsed
    return None


def _date_from_parts(year: int, month: int, day: int) -> str | None:
    if year < 1911:
        year += 1911
    try:
        return datetime(year, month, day).date().isoformat()
    except ValueError:
        return None


def _message_date(message: dict[str, Any]) -> str:
    internal_date = message.get("internalDate")
    if internal_date:
        try:
            timestamp = int(str(internal_date)) / 1000
            return datetime.fromtimestamp(timestamp, UTC).date().isoformat()
        except ValueError:
            pass
    header_date = _header(message, "Date")
    if header_date:
        try:
            return parsedate_to_datetime(header_date).date().isoformat()
        except (TypeError, ValueError):
            pass
    return datetime.now(UTC).date().isoformat()


def _extract_card_last_four(text: str) -> str:
    match = re.search(
        r"(?:尾號|末四碼|卡號|last\s*four|ending).{0,12}?(\d{4})",
        text,
        re.IGNORECASE,
    )
    return match.group(1) if match else ""


def _header(message: dict[str, Any], name: str) -> str:
    headers = (message.get("payload") or {}).get("headers", [])
    for header in headers:
        if isinstance(header, dict) and header.get("name", "").lower() == name.lower():
            return str(header.get("value") or "")
    return ""


def _resolve_server_path(path: Path) -> Path:
    if path.is_absolute():
        return path
    return (Path(__file__).resolve().parents[2] / path).resolve()
