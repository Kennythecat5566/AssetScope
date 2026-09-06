from pathlib import Path

from app.config import get_settings
from app.connectors.gmail_card import GMAIL_READONLY_SCOPE, _resolve_server_path


def main() -> int:
    settings = get_settings()
    credentials_file = _resolve_server_path(settings.gmail_credentials_file)
    token_file = _resolve_server_path(settings.gmail_token_file)

    if not credentials_file.exists():
        print(f"Google OAuth client file was not found: {credentials_file}")
        print("Download an OAuth desktop client JSON from Google Cloud Console.")
        return 1

    try:
        from google_auth_oauthlib.flow import InstalledAppFlow
    except ImportError:
        print("Install Gmail dependencies with: pip install -e .[gmail]")
        return 1

    token_file.parent.mkdir(parents=True, exist_ok=True)
    flow = InstalledAppFlow.from_client_secrets_file(
        str(credentials_file),
        [GMAIL_READONLY_SCOPE],
    )
    credentials = flow.run_local_server(port=0)
    token_file.write_text(credentials.to_json(), encoding="utf-8")
    print(f"Gmail OAuth token saved to {token_file}.")
    print("Restart AssetScope Server, then sync the Android app.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
