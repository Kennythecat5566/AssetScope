from app.browser_export import _safe_filename


def test_safe_filename_removes_path_and_special_characters() -> None:
    assert _safe_filename("../../Account Export (1).csv") == "Account_Export_1_.csv"


def test_safe_filename_uses_default_for_empty_name() -> None:
    assert _safe_filename("...") == "firstrade-export.csv"

