package tw.kensuke.assetscope.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution

class HoldingNormalizerTest {
    @Test
    fun `normalizes cash and deposit holdings to one balance unit`() {
        val holding = holding(
            assetType = AssetType.CASH,
            quantity = 42_000.0,
            averageCost = 42_000.0,
            marketPrice = 42_000.0,
        )

        val result = HoldingNormalizer.normalize(holding)

        assertEquals(1.0, result.quantity, 0.001)
        assertEquals(42_000.0, result.averageCost, 0.001)
        assertEquals(42_000.0, result.marketPrice, 0.001)
    }

    @Test
    fun `normalizes total cost basis when stored as average cost`() {
        val holding = holding(
            quantity = 100.0,
            averageCost = 12_000.0,
            marketPrice = 130.0,
        )

        val result = HoldingNormalizer.normalize(holding)

        assertEquals(120.0, result.averageCost, 0.001)
    }

    @Test
    fun `keeps ordinary unit average cost unchanged`() {
        val holding = holding(
            quantity = 100.0,
            averageCost = 120.0,
            marketPrice = 130.0,
        )

        val result = HoldingNormalizer.normalize(holding)

        assertEquals(120.0, result.averageCost, 0.001)
    }

    private fun holding(
        assetType: AssetType = AssetType.STOCK,
        quantity: Double,
        averageCost: Double,
        marketPrice: Double,
    ) = Holding(
        id = "test",
        institution = Institution.FIRSTRade,
        accountName = "test",
        symbol = "TEST",
        name = "Test",
        assetType = assetType,
        currency = Currency.USD,
        quantity = quantity,
        averageCost = averageCost,
        marketPrice = marketPrice,
    )
}
