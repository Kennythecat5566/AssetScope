from pathlib import Path

import pytest

from app.download_watcher import _copy_when_complete, capture_next_csv


def test_rejects_missing_download_folder(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="does not exist"):
        capture_next_csv(
            downloads_dir=tmp_path / "missing",
            destination_dir=tmp_path / "raw",
            timeout_seconds=0,
        )


def test_copies_completed_csv(tmp_path: Path) -> None:
    source = tmp_path / "Account Export.csv"
    source.write_text("header\nvalue\n", encoding="utf-8")
    destination_dir = tmp_path / "raw"
    destination_dir.mkdir()

    destination = _copy_when_complete(source, destination_dir)

    assert destination.name.endswith("-Account_Export.csv")
    assert destination.read_text(encoding="utf-8") == "header\nvalue\n"
