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


def get_settings() -> Settings:
    return Settings()
