from pathlib import Path

from app.connectors.csv_folder import load_csv
from app.connectors.firstrade_history import (
    convert_firstrade_history,
    load_firstrade_activity,
)


def test_converts_buys_sells_and_cash(tmp_path: Path) -> None:
    source = tmp_path / "history.csv"
    source.write_text(
        "Symbol,Quantity,Price,Action,Description,TradeDate,SettledDate,"
        "Interest,Amount,Commission,Fee,CUSIP,RecordType\n"
        ",0,,Other,Wire,2026-01-01,2026-01-01,0,1000,0,0,,Financial\n"
        "VTI,2,100,BUY,Vanguard ETF,2026-01-02,2026-01-03,0,-200,0,0,,Trade\n"
        "VTI,2,120,BUY,Vanguard ETF,2026-01-04,2026-01-05,0,-240,0,0,,Trade\n"
        "VTI,1,130,SELL,Vanguard ETF,2026-01-06,2026-01-07,0,130,0,0,,Trade\n",
        encoding="utf-8",
    )
    destination = tmp_path / "firstrade.csv"

    count = convert_firstrade_history(source, destination)
    holdings = load_csv(destination)

    assert count == 2
    stock = next(item for item in holdings if item.symbol == "VTI")
    cash = next(item for item in holdings if item.symbol == "USD")
    assert stock.quantity == 3
    assert stock.average_cost == 110
    assert stock.market_price == 130
    assert cash.market_price == 690
    transactions, performance = load_firstrade_activity(
        destination.with_suffix(".activity.json")
    )
    assert len(transactions) == 3
    assert performance.realized_profit == 20
    assert performance.unrealized_profit == 60
    assert performance.total_return == 80
    assert performance.return_rate == 80 / 440
