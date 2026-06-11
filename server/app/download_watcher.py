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
        path.resolve(): (path.stat().st_mtime_ns, path.stat().st_size)
        for path in downloads_dir.iterdir()
        if path.is_file() and path.suffix.lower() in CSV_SUFFIXES
    }
    print(f"正在監控新的 CSV：{downloads_dir}")
    print("請在已登入的 Firstrade 頁面依序操作：")
    print("  1. 我的帳戶")
    print("  2. 稅務中心（Tax Center）")
    print("  3. Download Account Information")
    print("  4. Excel CSV Files")
    print("  5. 選擇帳戶與日期範圍，按 Download")
    print("下載完成後程式會自動複製檔案，現在保持此視窗開啟即可。")

    while time.time() - started_at < timeout_seconds:
        candidates = []
        for path in downloads_dir.iterdir():
            if (
                not path.is_file()
                or path.suffix.lower() not in CSV_SUFFIXES
                or path.name.endswith((".crdownload", ".part", ".tmp"))
            ):
                continue
            current = (path.stat().st_mtime_ns, path.stat().st_size)
            if existing.get(path.resolve()) != current:
                candidates.append(path)
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
