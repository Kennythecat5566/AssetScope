package tw.kensuke.assetscope.domain

import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.Holding
import kotlin.math.abs

object HoldingNormalizer {
    fun normalize(holdings: List<Holding>): List<Holding> = holdings.map(::normalize)

    fun normalize(holding: Holding): Holding {
        if (holding.assetType == AssetType.CASH || holding.assetType == AssetType.DEPOSIT) {
            val balance = maxOf(holding.averageCost, holding.marketPrice)
            return holding.copy(
                quantity = 1.0,
                averageCost = balance,
                marketPrice = balance,
            )
        }

        if (holding.quantity <= 1.0 || holding.averageCost <= 0.0 || holding.marketPrice <= 0.0) {
            return holding
        }

        val costAsUnitPrice = holding.averageCost
        val costAsTotalCost = holding.averageCost / holding.quantity
        val totalCostLooksLikeUnitPrice = costAsUnitPrice.isNear(holding.marketPrice)
        val totalCostLooksLikeTotalCost = costAsTotalCost.isNear(holding.marketPrice)
        return if (!totalCostLooksLikeUnitPrice && totalCostLooksLikeTotalCost) {
            holding.copy(averageCost = costAsTotalCost)
        } else {
            holding
        }
    }

    private fun Double.isNear(reference: Double): Boolean {
        if (reference <= 0.0) return false
        val ratio = this / reference
        return ratio in 0.05..5.0 || abs(this - reference) <= 1.0
    }
}
