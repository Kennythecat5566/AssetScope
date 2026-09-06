import csv
import hashlib
import re
from dataclasses import dataclass
from datetime import datetime
from email import policy
from email.parser import BytesParser
from io import BytesIO
from pathlib import Path

from pypdf import PdfReader

from app.connectors.sinopac_card import _category
from app.models import Currency, Expense


@dataclass
class SinopacCardPdfImportResult:
    output_path: Path
    expenses: list[Expense]


def import_sinopac_card_eml(
    input_path: Path,
    import_dir: Path,
    password: str = "",
) -> SinopacCardPdfImportResult:
    text = extract_pdf_text_from_eml(input_path, password)
    expenses = parse_sinopac_card_pdf_text(text, input_path.name)
    if not expenses:
        raise ValueError("No credit-card expense could be parsed from the PDF.")

    import_dir.mkdir(parents=True, exist_ok=True)
    output_path = import_dir / _output_name(input_path)
    _write_expense_csv(output_path, expenses)
    return SinopacCardPdfImportResult(output_path=output_path, expenses=expenses)


def extract_pdf_text_from_eml(input_path: Path, password: str = "") -> str:
    message = BytesParser(policy=policy.default).parsebytes(input_path.read_bytes())
    pdf_payload = None
    for part in message.walk():
        payload = part.get_payload(decode=True) or b""
        filename = part.get_filename() or ""
        if payload.startswith(b"%PDF") or filename.lower().endswith(".pdf"):
            pdf_payload = payload
            break

    if pdf_payload is None:
        raise ValueError(f"{input_path.name} does not contain a PDF attachment.")
    return extract_pdf_text(pdf_payload, password)


def extract_pdf_text(pdf_payload: bytes, password: str = "") -> str:
    reader = PdfReader(BytesIO(pdf_payload))
    if reader.is_encrypted:
        if not password:
            raise ValueError("The PDF is encrypted. Please provide its password.")
        if reader.decrypt(password) == 0:
            raise ValueError("The PDF password is incorrect.")
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def parse_sinopac_card_pdf_text(text: str, source: str) -> list[Expense]:
    normalized = _normalize_text(text)
    transaction_date = _find_date(normalized)
    merchant = _find_merchant(normalized)
    amount, currency = _find_amount(normalized)
    card_last_four = _find_card_last_four(normalized)

    if not transaction_date or not merchant or amount <= 0:
        return []

    stable_key = "|".join(
        [source, transaction_date, merchant, str(amount), currency, card_last_four]
    )
    return [
        Expense(
            id=hashlib.sha256(stable_key.encode()).hexdigest()[:24],
            transaction_date=transaction_date,
            posted_date=None,
            merchant=merchant,
            category=_category("", merchant),
            amount=amount,
            currency=currency,
            card_last_four=card_last_four,
            note=f"Imported from encrypted PDF attachment: {source}",
        )
    ]


def _normalize_text(text: str) -> str:
    return re.sub(r"[ \t]+", " ", text.replace("\u3000", " "))


def _find_date(text: str) -> str:
    label_pattern = r"(?:交易日期|消費日期|授權日期|交易時間|消費時間|日期|Date)[:：\s]*"
    for label in re.finditer(label_pattern, text, re.IGNORECASE):
        parsed = _parse_date_candidate(text[label.end() : label.end() + 64])
        if parsed:
            return parsed

    for pattern in (
        r"(?<!\d)(?P<year>\d{4})[/-](?P<month>\d{1,2})[/-](?P<day>\d{1,2})(?!\d)",
        r"(?<!\d)(?P<year>\d{3})[/-](?P<month>\d{1,2})[/-](?P<day>\d{1,2})(?!\d)",
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
    return ""


def _parse_date_candidate(text: str) -> str:
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
    return ""


def _date_from_parts(year: int, month: int, day: int) -> str:
    if year < 1911:
        year += 1911
    try:
        return datetime(year, month, day).date().isoformat()
    except ValueError:
        return ""


def _find_amount(text: str) -> tuple[float, Currency]:
    patterns = [
        (
            Currency.TWD,
            r"(?:消費金額|交易金額|授權金額|金額|Amount)[:：\s]*(?:NT\$|NTD|TWD)?\s*([\d,]+(?:\.\d+)?)",
        ),
        (Currency.TWD, r"(?:NT\$|NTD|TWD)\s*([\d,]+(?:\.\d+)?)"),
        (Currency.USD, r"(?:US\$|USD|\$)\s*([\d,]+(?:\.\d+)?)"),
    ]
    for currency, pattern in patterns:
        match = re.search(pattern, text, re.IGNORECASE)
        if match:
            return float(match.group(1).replace(",", "")), currency
    return 0, Currency.TWD


def _find_merchant(text: str) -> str:
    labels = (
        "消費店家",
        "消費商店",
        "特約商店",
        "商店名稱",
        "商店",
        "Merchant",
    )
    for label in labels:
        match = re.search(
            rf"{label}[:：\s]*([^\n\r]+)",
            text,
            re.IGNORECASE,
        )
        if match:
            return _clean_merchant(match.group(1))

    lines = [_clean_merchant(line) for line in text.splitlines()]
    candidates = [
        line
        for line in lines
        if line
        and not re.search(r"(永豐|信用卡|通知|日期|金額|卡號|交易|消費)", line)
        and not re.fullmatch(r"[\d,./:$NTDUS -]+", line, re.IGNORECASE)
    ]
    return candidates[0] if candidates else ""


def _clean_merchant(value: str) -> str:
    cleaned = re.split(
        r"(?:交易日期|消費日期|授權日期|消費金額|交易金額|授權金額|卡號|末四碼|Amount|Date)",
        value,
        maxsplit=1,
        flags=re.IGNORECASE,
    )[0]
    return " ".join(cleaned.strip(" :：，,。").split())[:120]


def _find_card_last_four(text: str) -> str:
    match = re.search(
        r"(?:末四碼|卡號|信用卡|card|ending).{0,24}?(\d{4})",
        text,
        re.IGNORECASE,
    )
    return match.group(1) if match else ""


def _write_expense_csv(path: Path, expenses: list[Expense]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=[
                "transaction_date",
                "posted_date",
                "merchant",
                "amount",
                "currency",
                "card_last_four",
                "category",
                "note",
            ],
        )
        writer.writeheader()
        for expense in expenses:
            writer.writerow(
                {
                    "transaction_date": expense.transaction_date,
                    "posted_date": expense.posted_date or "",
                    "merchant": expense.merchant,
                    "amount": f"{expense.amount:g}",
                    "currency": expense.currency,
                    "card_last_four": expense.card_last_four,
                    "category": expense.category,
                    "note": expense.note,
                }
            )


def _output_name(input_path: Path) -> str:
    suffix = hashlib.sha256(input_path.read_bytes()).hexdigest()[:8]
    return f"sinopac-card-eml-{input_path.stem}-{suffix}.csv"
