from pathlib import Path

from app.connectors.csv_folder import load_csv
from app.models import Institution


def test_loads_standardized_csv(tmp_path: Path) -> None:
    csv_path = tmp_path / "holdings.csv"
    csv_path.write_text(
        "institution,account,symbol,name,type,currency,quantity,"
        "average_cost,market_price\n"
        "firstrade,Main,VTI,Vanguard Total Market,ETF,USD,2,250.5,290\n"
        "永豐銀行,DAWHO,TWD,新台幣活存,DEPOSIT,TWD,1,100000,100000\n",
        encoding="utf-8",
    )

    holdings = load_csv(csv_path)

    assert len(holdings) == 2
    assert holdings[0].symbol == "VTI"
    assert holdings[1].institution == Institution.SINOPAC_BANK

