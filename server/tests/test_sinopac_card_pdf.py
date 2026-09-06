from pathlib import Path

from app.connectors.sinopac_card import load_sinopac_card_csv
from app.connectors.sinopac_card_pdf import (
    import_sinopac_card_eml,
    parse_sinopac_card_pdf_text,
)
from app.models import Currency


def test_parses_sinopac_card_pdf_text() -> None:
    expenses = parse_sinopac_card_pdf_text(
        """
        永豐銀行信用卡消費通知
        交易日期：2026/08/29
        商店名稱：STARBUCKS
        消費金額：NT$180
        信用卡末四碼：1234
        """,
        "sample.eml",
    )

    assert len(expenses) == 1
    assert expenses[0].transaction_date == "2026-08-29"
    assert expenses[0].merchant == "STARBUCKS"
    assert expenses[0].amount == 180
    assert expenses[0].currency == Currency.TWD
    assert expenses[0].card_last_four == "1234"


def test_parses_sinopac_pdf_authorization_row() -> None:
    expenses = parse_sinopac_card_pdf_text(
        """
        期間：115/08/29
        親愛的客戶您好：
        卡別 卡號末四碼 消費日期 消費時間 消費地區 消費金額
        正卡 3609 08 / 29 20：40 TW NT$ 304
        注意事項：
        2. 因部分商店之實際交易授權時間與您的刷卡消費時間不盡相同
        """,
        "sinopac.eml",
    )

    assert len(expenses) == 1
    assert expenses[0].transaction_date == "2026-08-29"
    assert expenses[0].merchant == "永豐信用卡授權消費"
    assert expenses[0].amount == 304
    assert expenses[0].currency == Currency.TWD
    assert expenses[0].card_last_four == "3609"


def test_imported_pdf_csv_can_be_loaded_by_expense_importer(
    tmp_path: Path,
    monkeypatch,
) -> None:
    def fake_extract_pdf_text_from_eml(input_path: Path, password: str) -> str:
        assert password == "secret"
        return (
            "永豐銀行信用卡消費通知\n"
            "消費日期：115/08/29\n"
            "特約商店：BOOKSTORE\n"
            "交易金額：TWD 300\n"
            "卡號末四碼：5678"
        )

    monkeypatch.setattr(
        "app.connectors.sinopac_card_pdf.extract_pdf_text_from_eml",
        fake_extract_pdf_text_from_eml,
    )
    eml_path = tmp_path / "notice.eml"
    eml_path.write_bytes(b"fake email")

    result = import_sinopac_card_eml(eml_path, tmp_path, password="secret")
    loaded = load_sinopac_card_csv(result.output_path)

    assert result.output_path.name.startswith("sinopac-card-eml-notice-")
    assert len(loaded) == 1
    assert loaded[0].transaction_date == "2026-08-29"
    assert loaded[0].merchant == "BOOKSTORE"
