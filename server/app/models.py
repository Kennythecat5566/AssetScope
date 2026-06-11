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


class TransactionType(StrEnum):
    BUY = "BUY"
    SELL = "SELL"
    DIVIDEND = "DIVIDEND"


class Transaction(BaseModel):
    id: str
    institution: Institution
    account_name: str
    symbol: str
    name: str
    transaction_type: TransactionType
    currency: Currency
    quantity: float = Field(ge=0)
    price: float = Field(ge=0)
    amount: float
    realized_profit: float = 0
    trade_date: str
    settled_date: str | None = None


class PerformanceSummary(BaseModel):
    realized_profit: float = 0
    unrealized_profit: float = 0
    dividend_income: float = 0
    total_return: float = 0
    return_rate: float = 0
    total_buy_cost: float = 0
    valuation_note: str = ""


class PortfolioResponse(BaseModel):
    schema_version: int = 2
    generated_at: datetime
    exchange_rates: ExchangeRates
    holdings: list[Holding]
    transactions: list[Transaction] = []
    performance: PerformanceSummary = PerformanceSummary()
    sources: list[str]
