import argparse
import os
from pathlib import Path

from app.config import Settings
from app.connectors.sinopac_card_pdf import import_sinopac_card_eml


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Import a SinoPac credit-card encrypted PDF from an .eml file.",
    )
    parser.add_argument("--input", required=True, help="Path to the .eml file.")
    parser.add_argument(
        "--password-env",
        default="ASSETSCOPE_SINOPAC_CARD_PDF_PASSWORD",
        help="Environment variable that contains the PDF password.",
    )
    args = parser.parse_args()

    settings = Settings()
    password = os.environ.get(args.password_env, "")
    try:
        result = import_sinopac_card_eml(
            Path(args.input),
            settings.import_dir,
            password=password,
        )
    except ValueError as error:
        print(f"Import failed: {error}")
        return 1
    print(f"Imported {len(result.expenses)} expense(s).")
    print(f"Saved CSV to {result.output_path}.")
    print("Restart AssetScope Server, then sync the Android app.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
