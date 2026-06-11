import argparse
import re
import shutil
from datetime import datetime
from pathlib import Path

from playwright.sync_api import Download, sync_playwright
from app.connectors.firstrade_history import convert_firstrade_history

FIRSTRADE_HOME = "https://www.firstrade.com/"
MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024


def capture_firstrade_download(server_root: Path) -> Path:
    profile_dir = server_root / "browser-profiles" / "firstrade"
    raw_dir = server_root / "data" / "raw" / "firstrade"
    profile_dir.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)

    print("Opening a dedicated Microsoft Edge profile.")
    print("Log in yourself and complete MFA in the official browser window.")
    print("Open Accounts > Tax Center > Download Account Information.")
    print("Choose Excel CSV Files and click Download.")
    print("Do not enter your password in this terminal.")

    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            user_data_dir=profile_dir,
            channel="msedge",
            headless=False,
            accept_downloads=True,
            viewport={"width": 1360, "height": 900},
        )
        try:
            page = context.pages[0] if context.pages else context.new_page()
            page.goto(FIRSTRADE_HOME, wait_until="domcontentloaded")
            print("Waiting up to 30 minutes for an official CSV download...")
            with page.expect_download(timeout=30 * 60 * 1000) as download_info:
                pass
            return _save_download(download_info.value, raw_dir)
        finally:
            context.close()


def _save_download(download: Download, raw_dir: Path) -> Path:
    suggested_name = _safe_filename(download.suggested_filename)
    if not suggested_name.lower().endswith((".csv", ".txt")):
        download.cancel()
        raise ValueError(f"Rejected non-CSV download: {suggested_name}")

    temporary_path = download.path()
    if temporary_path is None:
        raise RuntimeError(download.failure() or "The download did not produce a file")
    if temporary_path.stat().st_size > MAX_DOWNLOAD_BYTES:
        raise ValueError("Downloaded file exceeds the 20 MB safety limit")

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    destination = raw_dir / f"{timestamp}-{suggested_name}"
    shutil.copy2(temporary_path, destination)
    print(f"Saved Firstrade export to: {destination}")
    normalized = raw_dir.parents[1] / "imports" / "firstrade.csv"
    count = convert_firstrade_history(destination, normalized)
    print(f"Converted {count} holdings to: {normalized}")
    return destination


def _safe_filename(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", value).strip("._")
    return cleaned or "firstrade-export.csv"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Capture an official Firstrade CSV with a user-authenticated browser.",
    )
    parser.add_argument(
        "--server-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    args = parser.parse_args()
    capture_firstrade_download(args.server_root.resolve())


if __name__ == "__main__":
    main()
