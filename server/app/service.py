from datetime import UTC, datetime

from app.config import Settings
from app.connectors.csv_folder import load_csv_folder
from app.connectors.firstrade_history import load_firstrade_activity
from app.connectors.shioaji import load_shioaji_assets
from app.models import ExchangeRates, PortfolioResponse


def build_portfolio(settings: Settings) -> PortfolioResponse:
    csv_holdings, sources = load_csv_folder(settings.import_dir)
    shioaji_holdings = load_shioaji_assets(settings)
    if shioaji_holdings:
        sources.append("shioaji")

    holdings_by_id = {
        holding.id: holding
        for holding in [*csv_holdings, *shioaji_holdings]
    }
    transactions, performance = load_firstrade_activity(
        settings.import_dir / "firstrade.activity.json"
    )
    return PortfolioResponse(
        generated_at=datetime.now(UTC),
        exchange_rates=ExchangeRates(usd_to_twd=settings.usd_to_twd),
        holdings=list(holdings_by_id.values()),
        transactions=transactions,
        performance=performance,
        sources=sources,
    )
