import argparse
import queue
import re
import shutil
import time
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import BrowserContext, Download, Page, TimeoutError, sync_playwright

from app.browser_export import MAX_DOWNLOAD_BYTES, _safe_filename

SINOPAC_MMA = "https://mma.sinopac.com/"
ALLOWED_HOST_SUFFIXES = (".sinopac.com", ".sinopac.com.tw")
DOWNLOAD_SUFFIXES = (".csv", ".txt")
READ_ONLY_NAVIGATION_LABELS = (
    "信用卡",
    "近期帳單",
)
DOWNLOAD_LABELS = (
    "CSV",
    "下載CSV",
    "匯出CSV",
    "下載",
    "匯出",
)


def capture_sinopac_card_download(server_root: Path) -> Path:
    profile_dir = server_root / "browser-profiles" / "sinopac"
    raw_dir = server_root / "data" / "raw" / "sinopac-card"
    import_dir = server_root / "data" / "imports"
    profile_dir.mkdir(parents=True, exist_ok=True)
    raw_dir.mkdir(parents=True, exist_ok=True)
    import_dir.mkdir(parents=True, exist_ok=True)

    print("Opening the official SinoPac MMA website in a dedicated Edge profile.")
    print("Log in yourself and complete MFA in the browser window.")
    print("AssetScope never asks for or stores your banking password or MFA code.")
    print("After login, the tool only tries read-only Credit Card > Recent Bills links.")
    print("It will never click payment, transfer, cash advance, or trading controls.")

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
            page.goto(SINOPAC_MMA, wait_until="domcontentloaded", timeout=60_000)
            _assert_official_page(page)
            _assist_read_only_navigation(context)
            print("Waiting up to 30 minutes for a CSV download.")
            print("If no CSV button is visible, select a bill period and click CSV/Export.")
            download = _wait_for_download(context, timeout_ms=30 * 60 * 1000)
            return _save_card_download(download, raw_dir, import_dir)
        finally:
            context.close()


def _assist_read_only_navigation(context: BrowserContext) -> None:
    print("Waiting for you to finish login...")
    for _ in range(180):
        page = _active_official_page(context)
        if page is not None and _looks_authenticated(page):
            break
        page.wait_for_timeout(1_000) if page is not None else None
    else:
        print("Login was not detected automatically. Continue in the browser window.")
        return

    for label in READ_ONLY_NAVIGATION_LABELS:
        page = _active_official_page(context)
        if page is None:
            return
        locator = page.get_by_text(label, exact=True)
        try:
            if locator.count() > 0 and locator.first.is_visible():
                locator.first.click(timeout=5_000)
                page.wait_for_timeout(1_500)
                _assert_official_page(page)
                print(f"Opened read-only section: {label}")
        except TimeoutError:
            print(f"Could not open {label} automatically; please click it yourself.")

    page = _active_official_page(context)
    if page is None:
        return
    for label in DOWNLOAD_LABELS:
        locator = page.get_by_text(label, exact=True)
        try:
            if locator.count() > 0 and locator.first.is_visible():
                print(f"Found a possible export control: {label}")
                return
        except TimeoutError:
            continue


def _wait_for_download(context: BrowserContext, timeout_ms: int) -> Download:
    downloads: queue.Queue[Download] = queue.Queue()

    def watch(page: Page) -> None:
        page.on("download", downloads.put)

    for page in context.pages:
        watch(page)
    context.on("page", watch)

    deadline = time.monotonic() + timeout_ms / 1000
    while time.monotonic() < deadline:
        try:
            return downloads.get_nowait()
        except queue.Empty:
            pages = context.pages
            if pages:
                pages[-1].wait_for_timeout(250)
            else:
                time.sleep(0.25)
    raise TimeoutError("No CSV was downloaded within 30 minutes")


def _save_card_download(
    download: Download,
    raw_dir: Path,
    import_dir: Path,
) -> Path:
    suggested_name = _safe_filename(download.suggested_filename)
    if not suggested_name.lower().endswith(DOWNLOAD_SUFFIXES):
        download.cancel()
        raise ValueError(
            f"Downloaded {suggested_name}, but this version only supports CSV/TXT. "
            "Choose the CSV export option."
        )
    temporary_path = download.path()
    if temporary_path is None:
        raise RuntimeError(download.failure() or "The download did not produce a file")
    if temporary_path.stat().st_size > MAX_DOWNLOAD_BYTES:
        raise ValueError("Downloaded file exceeds the 20 MB safety limit")

    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    raw_path = raw_dir / f"{timestamp}-{suggested_name}"
    shutil.copy2(temporary_path, raw_path)
    import_path = import_dir / f"sinopac-card-{timestamp}.csv"
    shutil.copy2(raw_path, import_path)
    print(f"Saved original SinoPac export to: {raw_path}")
    print(f"Prepared AssetScope import: {import_path}")
    print("Open AssetScope and tap Sync now. The Expense page will update.")
    return import_path


def _active_official_page(context: BrowserContext) -> Page | None:
    for page in reversed(context.pages):
        if _is_official_url(page.url):
            return page
    return context.pages[-1] if context.pages else None


def _assert_official_page(page: Page) -> None:
    if not _is_official_url(page.url):
        raise RuntimeError(f"Stopped after navigation outside SinoPac: {page.url}")


def _is_official_url(url: str) -> bool:
    host = (urlparse(url).hostname or "").lower()
    return host == "sinopac.com" or any(
        host.endswith(suffix) for suffix in ALLOWED_HOST_SUFFIXES
    )


def _looks_authenticated(page: Page) -> bool:
    url = page.url.lower()
    if re.search(r"login|signin", url):
        return False
    try:
        body = page.locator("body").inner_text(timeout=2_000)
    except TimeoutError:
        return False
    return "登出" in body or "信用卡" in body or "帳戶總覽" in body


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Capture an official SinoPac card CSV after user-authenticated login.",
    )
    parser.add_argument(
        "--server-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    args = parser.parse_args()
    capture_sinopac_card_download(args.server_root.resolve())


if __name__ == "__main__":
    main()
