import logging
import time
from datetime import UTC, datetime
from threading import Lock

from app.config import Settings
from app.connectors.csv_folder import load_csv_folder
from app.connectors.exchange_rates import load_exchange_rates
from app.connectors.firstrade_api import load_firstrade_api_data
from app.connectors.firstrade_history import load_firstrade_activity
from app.connectors.gmail_card import load_gmail_card_expenses
from app.connectors.shioaji import load_shioaji_data
from app.connectors.sinopac_card import load_sinopac_card_expenses
from app.models import Institution, PerformanceSummary, PortfolioResponse
from app.portfolio_history import record_portfolio_snapshot

logger = logging.getLogger(__name__)
_portfolio_cache_lock = Lock()
_portfolio_cache: tuple[str, float, PortfolioResponse] | None = None


def build_portfolio(settings: Settings) -> PortfolioResponse:
    global _portfolio_cache

    cache_key = _settings_cache_key(settings)
    if settings.portfolio_cache_seconds > 0:
        with _portfolio_cache_lock:
            cached = _portfolio_cache
            if cached is not None:
                cached_key, cached_at, cached_response = cached
                if (
                    cached_key == cache_key
                    and time.monotonic() - cached_at < settings.portfolio_cache_seconds
                ):
                    return cached_response

    response = _build_portfolio_uncached(settings)
    if settings.portfolio_cache_seconds > 0:
        with _portfolio_cache_lock:
            _portfolio_cache = (cache_key, time.monotonic(), response)
    return response


def clear_portfolio_cache() -> None:
    global _portfolio_cache

    with _portfolio_cache_lock:
        _portfolio_cache = None


def _settings_cache_key(settings: Settings) -> str:
    return settings.model_dump_json()


def _build_portfolio_uncached(settings: Settings) -> PortfolioResponse:
    csv_holdings, sources = load_csv_folder(settings.import_dir)
    shioaji_data = load_shioaji_data(settings)
    if shioaji_data.holdings:
        sources.append("shioaji")
    firstrade_api_data = load_firstrade_api_data(settings)
    if firstrade_api_data.holdings:
        sources.append("firstrade-api")
        csv_holdings = [
            holding
            for holding in csv_holdings
            if holding.institution != Institution.FIRSTRADE
        ]

    holdings_by_id = {
        holding.id: holding
        for holding in [
            *csv_holdings,
            *firstrade_api_data.holdings,
            *shioaji_data.holdings,
        ]
    }
    firstrade_transactions, performance = load_firstrade_activity(
        settings.import_dir / "firstrade.activity.json"
    )
    if firstrade_api_data.transactions:
        firstrade_transactions = firstrade_api_data.transactions
        performance = PerformanceSummary()
    expenses, expense_sources = load_sinopac_card_expenses(settings.import_dir)
    try:
        gmail_expenses, gmail_sources = load_gmail_card_expenses(settings)
    except Exception:
        logger.exception("Gmail expense import failed; continuing without Gmail data")
        gmail_expenses, gmail_sources = [], []
    expenses = sorted(
        [*expenses, *gmail_expenses],
        key=lambda item: item.transaction_date,
        reverse=True,
    )
    sources.extend(expense_sources)
    sources.extend(gmail_sources)
    transactions = sorted(
        [*firstrade_transactions, *shioaji_data.transactions],
        key=lambda item: item.trade_date,
        reverse=True,
    )
    generated_at = datetime.now(UTC)
    exchange_rates = load_exchange_rates(settings)
    holdings = list(holdings_by_id.values())
    record_portfolio_snapshot(
        settings.import_dir.parent / "cache" / "portfolio-history.json",
        holdings,
        exchange_rates,
        generated_at,
    )
    return PortfolioResponse(
        generated_at=generated_at,
        exchange_rates=exchange_rates,
        holdings=holdings,
        transactions=transactions,
        expenses=expenses,
        performance=performance,
        sources=sources,
    )
