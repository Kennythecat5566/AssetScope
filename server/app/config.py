from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="ASSETSCOPE_",
        extra="ignore",
    )

    api_token: str = Field(min_length=16)
    import_dir: Path = Path("data/imports")
    usd_to_twd: float = Field(default=32.4, gt=0)
    exchange_rate_auto_update: bool = True
    exchange_rate_cache_hours: int = Field(default=6, ge=1, le=168)
    shioaji_enabled: bool = False
    shioaji_api_key: str = ""
    shioaji_secret_key: str = ""
    shioaji_history_days: int = Field(default=365, ge=30, le=730)
    firstrade_api_enabled: bool = False
    firstrade_api_path: Path = Path("../firstrade-api-main")
    firstrade_api_token_file: Path = Path("data/raw/firstrade-api/session.json")
    firstrade_api_account: str = ""
    firstrade_api_history_range: str = "ytd"
    gmail_expenses_enabled: bool = False
    gmail_credentials_file: Path = Path("google-oauth-client.json")
    gmail_token_file: Path = Path("data/raw/gmail/token.json")
    gmail_query: str = (
        'newer_than:180d (信用卡 OR 刷卡 OR 消費 OR "card transaction" OR "credit card")'
    )
    gmail_max_messages: int = Field(default=25, ge=1, le=500)
    gmail_pdf_attachments_enabled: bool = True
    sinopac_card_pdf_password: str = ""
    paper_trading_enabled: bool = True
    paper_trading_interval_minutes: int = Field(default=60, ge=15, le=1440)
    paper_trading_initial_cash_twd: float = Field(default=1_000_000, gt=0)


def get_settings() -> Settings:
    return Settings()
