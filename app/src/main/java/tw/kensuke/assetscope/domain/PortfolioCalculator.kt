package tw.kensuke.assetscope.domain

import tw.kensuke.assetscope.domain.model.Allocation
import tw.kensuke.assetscope.domain.model.AssetType
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
        val investmentHoldings = holdings.filter {
            it.assetType == AssetType.STOCK || it.assetType == AssetType.ETF
        }
        val totalCost = investmentHoldings.sumOf { it.cost.toTwd(it.currency) }
        val unrealizedProfit = investmentHoldings.sumOf {
            it.unrealizedProfit.toTwd(it.currency)
        }
        val institutionAllocations = holdings
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
        val assetAllocations = holdings
            .groupBy { it.symbol to it.name }
            .map { (asset, items) ->
                val value = items.sumOf { it.marketValue.toTwd(it.currency) }
                Allocation(
                    label = asset.first.ifBlank { asset.second },
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
            unrealizedProfitTwd = unrealizedProfit,
            returnRate = if (totalCost == 0.0) 0.0 else unrealizedProfit / totalCost,
            overseasValueTwd = overseas,
            domesticValueTwd = totalValue - overseas,
            institutionAllocations = institutionAllocations,
            assetAllocations = assetAllocations,
        )
    }
}
