import csv
import hashlib
import re
from datetime import datetime
from pathlib import Path

from app.models import Currency, Expense, ExpenseCategory

HEADER_ALIASES = {
    "transaction_date": ("transaction_date", "消費日", "交易日期", "消費日期"),
    "posted_date": ("posted_date", "入帳日", "入帳日期"),
    "merchant": ("merchant", "消費明細", "商店", "特店名稱", "交易說明"),
    "amount": ("amount", "新臺幣金額", "新台幣金額", "消費金額", "金額"),
    "currency": ("currency", "幣別", "交易幣別"),
    "card_last_four": ("card_last_four", "卡號末四碼", "卡號"),
    "category": ("category", "分類"),
    "note": ("note", "備註"),
}

CATEGORY_ALIASES = {
    "餐飲": ExpenseCategory.DINING,
    "交通": ExpenseCategory.TRANSPORT,
    "購物": ExpenseCategory.SHOPPING,
    "日用品": ExpenseCategory.GROCERIES,
    "娛樂": ExpenseCategory.ENTERTAINMENT,
    "訂閱": ExpenseCategory.SUBSCRIPTION,
    "旅遊": ExpenseCategory.TRAVEL,
    "醫療": ExpenseCategory.HEALTH,
    "水電瓦斯": ExpenseCategory.UTILITIES,
}

CATEGORY_KEYWORDS = {
    ExpenseCategory.DINING: (
        "餐", "咖啡", "coffee", "starbucks", "麥當勞", "foodpanda", "ubereats",
    ),
    ExpenseCategory.TRANSPORT: (
        "uber", "計程車", "台鐵", "高鐵", "捷運", "加油", "停車",
    ),
    ExpenseCategory.GROCERIES: (
        "全聯", "家樂福", "costco", "便利商店", "7-eleven", "全家",
    ),
    ExpenseCategory.SUBSCRIPTION: (
        "netflix", "spotify", "youtube", "google", "apple.com/bill", "訂閱",
    ),
    ExpenseCategory.TRAVEL: (
        "飯店", "hotel", "booking", "agoda", "航空", "airlines",
    ),
    ExpenseCategory.HEALTH: (
        "醫院", "診所", "藥局", "牙醫",
    ),
    ExpenseCategory.UTILITIES: (
        "電費", "水費", "瓦斯", "電信", "中華電信", "台灣大哥大", "遠傳",
    ),
    ExpenseCategory.ENTERTAINMENT: (
        "電影", "影城", "遊戲", "steam", "playstation", "nintendo",
    ),
    ExpenseCategory.SHOPPING: (
        "蝦皮", "momo", "pchome", "百貨", "uniqlo", "amazon",
    ),
}


def load_sinopac_card_expenses(import_dir: Path) -> tuple[list[Expense], list[str]]:
    expenses: list[Expense] = []
    sources: list[str] = []
    for path in sorted(import_dir.glob("sinopac-card*.csv")):
        expenses.extend(load_sinopac_card_csv(path))
        sources.append(path.name)
    return (
        sorted(expenses, key=lambda item: item.transaction_date, reverse=True),
        sources,
    )


def load_sinopac_card_csv(path: Path) -> list[Expense]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        headers = _header_map(reader.fieldnames or [])
        required = {"transaction_date", "merchant", "amount"}
        missing = required - headers.keys()
        if missing:
            raise ValueError(
                f"{path.name} missing card columns: {', '.join(sorted(missing))}"
            )
        return [
            _to_expense(path.name, row_number, row, headers)
            for row_number, row in enumerate(reader, start=2)
            if any((value or "").strip() for value in row.values())
        ]


def _header_map(fieldnames: list[str]) -> dict[str, str]:
    normalized = {header.strip().lower(): header for header in fieldnames}
    result: dict[str, str] = {}
    for canonical, aliases in HEADER_ALIASES.items():
        for alias in aliases:
            if alias.lower() in normalized:
                result[canonical] = normalized[alias.lower()]
                break
    return result


def _to_expense(
    source: str,
    row_number: int,
    row: dict[str, str | None],
    headers: dict[str, str],
) -> Expense:
    def value(name: str) -> str:
        header = headers.get(name)
        return (row.get(header) or "").strip() if header else ""

    transaction_date = _normalize_date(value("transaction_date"))
    posted_date = _normalize_date(value("posted_date")) if value("posted_date") else None
    merchant = value("merchant")
    amount = _parse_amount(value("amount"))
    currency_text = value("currency").upper() or "TWD"
    currency = Currency.USD if currency_text in {"USD", "美金", "美元"} else Currency.TWD
    card_last_four = re.sub(r"\D", "", value("card_last_four"))[-4:]
    category = _category(value("category"), merchant)
    stable_key = "|".join(
        [source, str(row_number), transaction_date, merchant, str(amount), card_last_four]
    )
    return Expense(
        id=hashlib.sha256(stable_key.encode()).hexdigest()[:24],
        transaction_date=transaction_date,
        posted_date=posted_date,
        merchant=merchant,
        category=category,
        amount=amount,
        currency=currency,
        card_last_four=card_last_four,
        note=value("note"),
    )


def _normalize_date(value: str) -> str:
    cleaned = value.strip()
    for pattern in ("%Y-%m-%d", "%Y/%m/%d", "%m/%d/%Y", "%Y%m%d"):
        try:
            return datetime.strptime(cleaned, pattern).date().isoformat()
        except ValueError:
            pass
    match = re.fullmatch(r"(\d{2,3})[/-](\d{1,2})[/-](\d{1,2})", cleaned)
    if match:
        year, month, day = map(int, match.groups())
        return datetime(year + 1911, month, day).date().isoformat()
    raise ValueError(f"unsupported card transaction date: {value}")


def _parse_amount(value: str) -> float:
    cleaned = value.replace(",", "").replace("NT$", "").replace("$", "").strip()
    if cleaned.startswith("(") and cleaned.endswith(")"):
        cleaned = f"-{cleaned[1:-1]}"
    return float(cleaned)


def _category(explicit: str, merchant: str) -> ExpenseCategory:
    if explicit:
        upper = explicit.upper()
        if upper in ExpenseCategory.__members__:
            return ExpenseCategory[upper]
        if explicit in CATEGORY_ALIASES:
            return CATEGORY_ALIASES[explicit]
    merchant_lower = merchant.lower()
    for category, keywords in CATEGORY_KEYWORDS.items():
        if any(keyword.lower() in merchant_lower for keyword in keywords):
            return category
    return ExpenseCategory.OTHER
