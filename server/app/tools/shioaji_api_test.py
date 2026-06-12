from __future__ import annotations

import sys

import shioaji as sj

from app.config import Settings


TEST_SYMBOL = "2890"


def main() -> int:
    settings = Settings()
    if not settings.shioaji_api_key or not settings.shioaji_secret_key:
        print("Shioaji credentials are missing. Run configure-shioaji.cmd first.")
        return 1

    print("SinoPac Python API test")
    print(f"Shioaji version: {sj.__version__}")
    print("Environment: SIMULATION (no real order will be placed)")

    api = sj.Shioaji(simulation=True)
    try:
        accounts = api.login(
            api_key=settings.shioaji_api_key,
            secret_key=settings.shioaji_secret_key,
            fetch_contract=True,
            contracts_timeout=30_000,
            subscribe_trade=False,
        )
        if not accounts or api.stock_account is None:
            raise RuntimeError("Simulation login returned no stock account")

        print("Login test: passed")
        contract = api.Contracts.Stocks.TSE.TSE2890
        if contract is None or contract.code != TEST_SYMBOL:
            raise RuntimeError("The official stock test contract could not be loaded")
        test_price = float(contract.reference)
        if not float(contract.limit_down) <= test_price <= float(contract.limit_up):
            raise RuntimeError("The current test price is outside the daily price limits")
        print(
            f"Simulation test price: {test_price:g} "
            f"(limits {float(contract.limit_down):g}-{float(contract.limit_up):g})"
        )

        order = sj.StockOrder(
            action=sj.Action.Buy,
            price=test_price,
            quantity=1,
            price_type=sj.StockPriceType.LMT,
            order_type=sj.OrderType.ROD,
            order_lot=sj.StockOrderLot.Common,
            order_cond=sj.StockOrderCond.Cash,
            account=api.stock_account,
        )
        trade = api.place_order(contract, order)
        status = trade.status
        status_name = getattr(status.status, "value", str(status.status))
        status_code = status.status_code
        message = status.msg or ""

        print(f"Simulation order status: {status_name}")
        print(f"Simulation status code: {status_code}")
        if message:
            print(f"Simulation message: {message}")

        if status_name in {"PendingSubmit", "Submitted"}:
            print("Stock API order test: submitted successfully")
            print("Wait at least 5 minutes for SinoPac review.")
            return 0

        print("Stock API order test was not accepted.")
        return 1
    except Exception as error:
        message = str(error)
        print(f"API test failed: {message}")
        if "doesn't have permission" in message:
            print(
                "Enable the Trading permission for this API Key in SinoPac "
                "API Key management, then run this test again."
            )
        if "Please sign" in message:
            print(
                "The stock account has not completed SinoPac's stock API "
                "agreement. Complete the securities API agreement in the "
                "Sign Center before running this test again."
            )
        return 1
    finally:
        try:
            api.logout()
        except Exception:
            pass


if __name__ == "__main__":
    sys.exit(main())
