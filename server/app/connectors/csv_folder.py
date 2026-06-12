import csv
import hashlib
from pathlib import Path

from app.models import AssetType, Currency, Holding, Institution

REQUIRED_COLUMNS = {
    "institution",
    "account",
    "symbol",
    "name",
    "type",
    "currency",
    "quantity",
    "average_cost",
    "market_price",
}

INSTITUTION_ALIASES = {
    "firstrade": Institution.FIRSTRADE,
    "sinopac_securities": Institution.SINOPAC_SECURITIES,
    "永豐證券": Institution.SINOPAC_SECURITIES,
    "sinopac_bank": Institution.SINOPAC_BANK,
    "永豐銀行": Institution.SINOPAC_BANK,
}


def load_csv_folder(import_dir: Path) -> tuple[list[Holding], list[str]]:
    import_dir.mkdir(parents=True, exist_ok=True)
    holdings: list[Holding] = []
    sources: list[str] = []

    for path in sorted(import_dir.glob("*.csv")):
        if path.name.lower().startswith("sinopac-card"):
            continue
        holdings.extend(load_csv(path))
        sources.append(path.name)

    return holdings, sources


def load_csv(path: Path) -> list[Holding]:
    with path.open("r", encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        headers = {header.strip().lower() for header in reader.fieldnames or []}
        missing = REQUIRED_COLUMNS - headers
        if missing:
            raise ValueError(f"{path.name} missing columns: {', '.join(sorted(missing))}")

        return [
            _to_holding(path.name, index, _normalize_row(row))
            for index, row in enumerate(reader, start=2)
            if any(value and value.strip() for value in row.values())
        ]


def _normalize_row(row: dict[str, str | None]) -> dict[str, str]:
    return {
        key.strip().lower(): (value or "").strip()
        for key, value in row.items()
        if key is not None
    }


def _to_holding(source: str, row_number: int, row: dict[str, str]) -> Holding:
    institution_key = row["institution"].lower()
    if institution_key not in INSTITUTION_ALIASES:
        raise ValueError(f"{source}:{row_number} unsupported institution")

    symbol = row["symbol"].upper()
    stable_key = "|".join(
        [source, row["institution"], row["account"], symbol, row["currency"]]
    )
    holding_id = hashlib.sha256(stable_key.encode()).hexdigest()[:24]
    return Holding(
        id=holding_id,
        institution=INSTITUTION_ALIASES[institution_key],
        account_name=row["account"],
        symbol=symbol,
        name=row["name"],
        asset_type=AssetType(row["type"].upper()),
        currency=Currency(row["currency"].upper()),
        quantity=float(row["quantity"]),
        average_cost=float(row["average_cost"]),
        market_price=float(row["market_price"]),
    )
