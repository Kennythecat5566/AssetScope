import Foundation

struct Allocation: Identifiable {
    let name: String
    let value: Double
    let colorIndex: Int

    var id: String { name }
}

struct PortfolioMetrics {
    let holdings: [Holding]
    let usdToTwd: Double

    func converted(_ amount: Double, from source: Currency, to target: Currency) -> Double {
        guard source != target else { return amount }
        return target == .twd ? amount * usdToTwd : amount / usdToTwd
    }

    func value(of holding: Holding, in currency: Currency) -> Double {
        converted(holding.marketValue, from: holding.currency, to: currency)
    }

    func profit(of holding: Holding, in currency: Currency) -> Double {
        converted(holding.unrealizedProfit, from: holding.currency, to: currency)
    }

    func totalValue(in currency: Currency) -> Double {
        holdings.reduce(0) { $0 + value(of: $1, in: currency) }
    }

    func totalCost(in currency: Currency) -> Double {
        holdings.reduce(0) {
            $0 + converted($1.cost, from: $1.currency, to: currency)
        }
    }

    func totalProfit(in currency: Currency) -> Double {
        totalValue(in: currency) - totalCost(in: currency)
    }

    var returnRate: Double {
        let cost = totalCost(in: .twd)
        return cost == 0 ? 0 : totalProfit(in: .twd) / cost
    }

    func institutionAllocations(in currency: Currency) -> [Allocation] {
        let grouped = Dictionary(grouping: holdings, by: \.institution)
        return grouped.enumerated().map { offset, pair in
            Allocation(
                name: pair.key.displayName,
                value: pair.value.reduce(0) { $0 + value(of: $1, in: currency) },
                colorIndex: offset
            )
        }
        .sorted { $0.value > $1.value }
    }
}
