from pathlib import Path

import pytest

from app.download_watcher import capture_next_csv


def test_rejects_missing_download_folder(tmp_path: Path) -> None:
    with pytest.raises(ValueError, match="does not exist"):
        capture_next_csv(
            downloads_dir=tmp_path / "missing",
            destination_dir=tmp_path / "raw",
            timeout_seconds=0,
        )

