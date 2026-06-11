from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field


class Institution(StrEnum):
    FIRSTRADE = "FIRSTRade"
    SINOPAC_SECURITIES = "SINOPAC_SECURITIES"
    SINOPAC_BANK = "SINOPAC_BANK"


class AssetType(StrEnum):
    STOCK = "STOCK"
    ETF = "ETF"
    CASH = "CASH"
    DEPOSIT = "DEPOSIT"


class Currency(StrEnum):
    TWD = "TWD"
    USD = "USD"


class Holding(BaseModel):
    id: str
    institution: Institution
    account_name: str
    symbol: str
    name: str
    asset_type: AssetType
    currency: Currency
    quantity: float = Field(ge=0)
    average_cost: float = Field(ge=0)
    market_price: float = Field(ge=0)


class ExchangeRates(BaseModel):
    usd_to_twd: float = Field(gt=0)


class PortfolioResponse(BaseModel):
    schema_version: int = 1
    generated_at: datetime
    exchange_rates: ExchangeRates
    holdings: list[Holding]
    sources: list[str]

