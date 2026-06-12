from datetime import UTC, datetime

from app.config import Settings
from app.connectors.csv_folder import load_csv_folder
from app.connectors.exchange_rates import load_exchange_rates
from app.connectors.firstrade_history import load_firstrade_activity
from app.connectors.shioaji import load_shioaji_data
from app.connectors.sinopac_card import load_sinopac_card_expenses
from app.models import PortfolioResponse
from app.portfolio_history import record_portfolio_snapshot


def build_portfolio(settings: Settings) -> PortfolioResponse:
    csv_holdings, sources = load_csv_folder(settings.import_dir)
    shioaji_data = load_shioaji_data(settings)
    if shioaji_data.holdings:
        sources.append("shioaji")

    holdings_by_id = {
        holding.id: holding
        for holding in [*csv_holdings, *shioaji_data.holdings]
    }
    firstrade_transactions, performance = load_firstrade_activity(
        settings.import_dir / "firstrade.activity.json"
    )
    expenses, expense_sources = load_sinopac_card_expenses(settings.import_dir)
    sources.extend(expense_sources)
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
