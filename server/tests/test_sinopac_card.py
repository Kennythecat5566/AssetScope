from pathlib import Path

from app.connectors.sinopac_card import load_sinopac_card_csv
from app.models import ExpenseCategory


def test_loads_and_categorizes_sinopac_card_csv(tmp_path: Path) -> None:
    path = tmp_path / "sinopac-card-2026-06.csv"
    path.write_text(
        "消費日,入帳日,消費明細,新台幣金額,卡號末四碼\n"
        "2026/06/10,2026/06/11,STARBUCKS,180,1234\n"
        "115/06/09,115/06/10,台灣高鐵,\"1,490\",1234\n"
        "2026/06/08,2026/06/09,退款項目,(300),1234\n",
        encoding="utf-8-sig",
    )

    expenses = load_sinopac_card_csv(path)

    assert len(expenses) == 3
    assert expenses[0].category == ExpenseCategory.DINING
    assert expenses[1].category == ExpenseCategory.TRANSPORT
    assert expenses[1].transaction_date == "2026-06-09"
    assert expenses[2].amount == -300
