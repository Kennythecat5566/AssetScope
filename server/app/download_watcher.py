import argparse
import shutil
import time
from datetime import datetime
from pathlib import Path

from app.browser_export import MAX_DOWNLOAD_BYTES, _safe_filename

CSV_SUFFIXES = {".csv", ".txt"}


def capture_next_csv(
    downloads_dir: Path,
    destination_dir: Path,
    timeout_seconds: int = 30 * 60,
) -> Path:
    downloads_dir = downloads_dir.expanduser().resolve()
    destination_dir.mkdir(parents=True, exist_ok=True)
    if not downloads_dir.is_dir():
        raise ValueError(f"Downloads folder does not exist: {downloads_dir}")

    started_at = time.time()
    existing = {
        path.resolve()
        for path in downloads_dir.iterdir()
        if path.is_file()
    }
    print(f"Watching for a new CSV in: {downloads_dir}")
    print("Use your normal browser to sign in to the official Firstrade website.")
    print("Complete MFA and download the CSV. Do not share your password.")

    while time.time() - started_at < timeout_seconds:
        candidates = [
            path
            for path in downloads_dir.iterdir()
            if path.is_file()
            and path.resolve() not in existing
            and path.suffix.lower() in CSV_SUFFIXES
            and not path.name.endswith((".crdownload", ".part", ".tmp"))
        ]
        if candidates:
            newest = max(candidates, key=lambda path: path.stat().st_mtime)
            return _copy_when_complete(newest, destination_dir)
        time.sleep(1)

    raise TimeoutError("No new CSV was downloaded within 30 minutes")


def _copy_when_complete(source: Path, destination_dir: Path) -> Path:
    previous_size = -1
    stable_checks = 0
    while stable_checks < 3:
        size = source.stat().st_size
        if size == previous_size:
            stable_checks += 1
        else:
            stable_checks = 0
            previous_size = size
        if size > MAX_DOWNLOAD_BYTES:
            raise ValueError("Downloaded file exceeds the 20 MB safety limit")
        time.sleep(1)

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    destination = destination_dir / f"{timestamp}-{_safe_filename(source.name)}"
    shutil.copy2(source, destination)
    print(f"Saved Firstrade export to: {destination}")
    return destination


def main() -> None:
    server_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Capture the next CSV downloaded by your normal browser.",
    )
    parser.add_argument(
        "--downloads",
        type=Path,
        default=Path.home() / "Downloads",
    )
    parser.add_argument(
        "--destination",
        type=Path,
        default=server_root / "data" / "raw" / "firstrade",
    )
    args = parser.parse_args()
    capture_next_csv(args.downloads, args.destination)


if __name__ == "__main__":
    main()

