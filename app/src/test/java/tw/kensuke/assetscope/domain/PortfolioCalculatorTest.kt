package tw.kensuke.assetscope.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution

class PortfolioCalculatorTest {
    @Test
    fun `converts USD holdings and calculates portfolio return`() {
        val holdings = listOf(
            holding(
                institution = Institution.FIRSTRade,
                currency = Currency.USD,
                quantity = 10.0,
                cost = 100.0,
                price = 120.0,
            ),
            holding(
                institution = Institution.SINOPAC_BANK,
                currency = Currency.TWD,
                quantity = 1.0,
                cost = 1_000.0,
                price = 1_000.0,
            ),
        )

        val result = PortfolioCalculator.calculate(
            holdings = holdings,
            rates = ExchangeRates(usdToTwd = 30.0),
        )

        assertEquals(37_000.0, result.totalValueTwd, 0.001)
        assertEquals(31_000.0, result.totalCostTwd, 0.001)
        assertEquals(6_000.0, result.unrealizedProfitTwd, 0.001)
        assertEquals(36_000.0, result.overseasValueTwd, 0.001)
        assertEquals(1, result.assetAllocations.size)
        assertEquals("TEST", result.assetAllocations.first().label)
        assertEquals(37_000.0, result.assetAllocations.first().valueTwd, 0.001)
        assertEquals(2, result.institutionAllocations.size)
    }

    private fun holding(
        institution: Institution,
        currency: Currency,
        quantity: Double,
        cost: Double,
        price: Double,
    ) = Holding(
        id = "$institution-$currency",
        institution = institution,
        accountName = "test",
        symbol = "TEST",
        name = "Test holding",
        assetType = AssetType.STOCK,
        currency = currency,
        quantity = quantity,
        averageCost = cost,
        marketPrice = price,
    )
}
