package tw.kensuke.assetscope.domain

import tw.kensuke.assetscope.domain.model.Allocation
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution
import tw.kensuke.assetscope.domain.model.PortfolioSummary

object PortfolioCalculator {
    fun calculate(
        holdings: List<Holding>,
        rates: ExchangeRates,
    ): PortfolioSummary {
        fun Double.toTwd(currency: Currency): Double = when (currency) {
            Currency.TWD -> this
            Currency.USD -> this * rates.usdToTwd
        }

        val totalValue = holdings.sumOf { it.marketValue.toTwd(it.currency) }
        val totalCost = holdings.sumOf { it.cost.toTwd(it.currency) }
        val grouped = holdings
            .groupBy(Holding::institution)
            .map { (institution, items) ->
                val value = items.sumOf { it.marketValue.toTwd(it.currency) }
                Allocation(
                    label = institution.displayName,
                    valueTwd = value,
                    ratio = if (totalValue == 0.0) 0.0 else value / totalValue,
                )
            }
            .sortedByDescending(Allocation::valueTwd)

        val overseas = holdings
            .filter { it.institution == Institution.FIRSTRade }
            .sumOf { it.marketValue.toTwd(it.currency) }

        return PortfolioSummary(
            totalValueTwd = totalValue,
            totalCostTwd = totalCost,
            unrealizedProfitTwd = totalValue - totalCost,
            returnRate = if (totalCost == 0.0) 0.0 else (totalValue - totalCost) / totalCost,
            overseasValueTwd = overseas,
            domesticValueTwd = totalValue - overseas,
            allocations = grouped,
        )
    }
}

