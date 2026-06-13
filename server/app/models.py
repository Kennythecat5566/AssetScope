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
    updated_at: datetime | None = None
    source: str = "configured"


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


class ExpenseCategory(StrEnum):
    DINING = "DINING"
    TRANSPORT = "TRANSPORT"
    SHOPPING = "SHOPPING"
    GROCERIES = "GROCERIES"
    ENTERTAINMENT = "ENTERTAINMENT"
    SUBSCRIPTION = "SUBSCRIPTION"
    TRAVEL = "TRAVEL"
    HEALTH = "HEALTH"
    UTILITIES = "UTILITIES"
    OTHER = "OTHER"


class Expense(BaseModel):
    id: str
    institution: Institution = Institution.SINOPAC_BANK
    transaction_date: str
    posted_date: str | None = None
    merchant: str
    category: ExpenseCategory
    amount: float
    currency: Currency
    card_last_four: str = ""
    note: str = ""


class PerformanceSummary(BaseModel):
    realized_profit: float = 0
    unrealized_profit: float = 0
    dividend_income: float = 0
    total_return: float = 0
    return_rate: float = 0
    total_buy_cost: float = 0
    valuation_note: str = ""


class PortfolioResponse(BaseModel):
    schema_version: int = 3
    generated_at: datetime
    exchange_rates: ExchangeRates
    holdings: list[Holding]
    transactions: list[Transaction] = []
    expenses: list[Expense] = []
    performance: PerformanceSummary = PerformanceSummary()
    sources: list[str]


class PriceCandle(BaseModel):
    date: str
    open: float = Field(gt=0)
    high: float = Field(gt=0)
    low: float = Field(gt=0)
    close: float = Field(gt=0)
    volume: float = Field(ge=0)


class PriceHistoryResponse(BaseModel):
    symbol: str
    currency: Currency
    source: str
    candles: list[PriceCandle]


class MarketSummaryRequestItem(BaseModel):
    institution: Institution
    symbol: str


class MarketSummariesRequest(BaseModel):
    items: list[MarketSummaryRequestItem] = Field(max_length=50)


class MarketSummary(BaseModel):
    institution: Institution
    symbol: str
    currency: Currency
    latest_price: float = Field(gt=0)
    change: float
    change_rate: float
    closes: list[float]
    source: str


class MarketSummariesResponse(BaseModel):
    summaries: list[MarketSummary]


class PortfolioHistoryPoint(BaseModel):
    timestamp: datetime
    value_twd: float = Field(ge=0)


class PortfolioHistoryResponse(BaseModel):
    currency: Currency = Currency.TWD
    points: list[PortfolioHistoryPoint]


class PaperBotTrade(BaseModel):
    id: str
    timestamp: datetime
    bot_id: str
    symbol: str
    name: str
    side: str
    quantity: float = Field(gt=0)
    price_twd: float = Field(gt=0)
    amount_twd: float = Field(gt=0)
    reason: str


class PaperBotPosition(BaseModel):
    symbol: str
    name: str
    quantity: float = Field(gt=0)
    average_cost_twd: float = Field(gt=0)
    market_price_twd: float = Field(gt=0)
    market_value_twd: float = Field(gt=0)
    unrealized_profit_twd: float


class PaperBotEquityPoint(BaseModel):
    timestamp: datetime
    net_value_twd: float = Field(ge=0)


class PaperBotPerformancePoint(BaseModel):
    timestamp: datetime
    bot_value_twd: float = Field(ge=0)
    taiwan_index_value: float | None = Field(default=None, ge=0)
    us_index_value: float | None = Field(default=None, ge=0)


class PaperBotSummary(BaseModel):
    id: str
    name: str
    strategy: str
    market_scope: str
    paper_only: bool = True
    initial_cash_twd: float
    cash_twd: float
    net_value_twd: float
    total_return_twd: float
    return_rate: float
    trade_count: int
    last_run_at: datetime | None = None
    positions: list[PaperBotPosition] = []
    recent_trades: list[PaperBotTrade] = []
    equity_history: list[PaperBotEquityPoint] = []
    performance_history: list[PaperBotPerformancePoint] = []


class PaperTradingResponse(BaseModel):
    generated_at: datetime
    paper_only: bool = True
    bots: list[PaperBotSummary]
