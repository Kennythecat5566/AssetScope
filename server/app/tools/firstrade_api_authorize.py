import json
import os
import sys
from pathlib import Path

from app.config import get_settings
from app.connectors.firstrade_api import _add_firstrade_path, _resolve_server_path


def main() -> int:
    settings = get_settings()
    username = os.environ.get("ASSETSCOPE_FIRSTRADE_API_USERNAME", "").strip()
    password = os.environ.get("ASSETSCOPE_FIRSTRADE_API_PASSWORD", "")
    pin = os.environ.get("ASSETSCOPE_FIRSTRADE_API_PIN", "")
    email = os.environ.get("ASSETSCOPE_FIRSTRADE_API_EMAIL", "")
    phone = os.environ.get("ASSETSCOPE_FIRSTRADE_API_PHONE", "")
    mfa_secret = os.environ.get("ASSETSCOPE_FIRSTRADE_API_MFA_SECRET", "")

    if not username or not password:
        print("Firstrade username and password are required for authorization.")
        return 1

    _add_firstrade_path(settings.firstrade_api_path)
    from firstrade import account  # type: ignore[import-not-found]

    token_file = _resolve_server_path(settings.firstrade_api_token_file)
    token_file.parent.mkdir(parents=True, exist_ok=True)

    session = account.FTSession(
        username=username,
        password=password,
        pin=pin,
        email=email,
        phone=phone,
        mfa_secret=mfa_secret,
        save_session=False,
    )
    need_code = session.login()
    if need_code:
        code = input("Enter the Firstrade MFA code: ").strip()
        if not code:
            print("MFA code is required.")
            return 1
        session.login_two(code)

    token_file.write_text(
        json.dumps(session.get_tokens(), indent=2),
        encoding="utf-8",
    )
    accounts = account.FTAccountData(session)
    masked_accounts = [f"...{item[-4:]}" for item in accounts.account_numbers]
    print(f"Firstrade API session saved to {token_file}.")
    print(f"Detected accounts: {', '.join(masked_accounts)}")
    print("Restart AssetScope Server, then sync the Android app.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
