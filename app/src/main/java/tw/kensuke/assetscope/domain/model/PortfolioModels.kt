package tw.kensuke.assetscope.domain.model

enum class Institution(val displayName: String) {
    FIRSTRade("Firstrade"),
    SINOPAC_SECURITIES("永豐證券"),
    SINOPAC_BANK("永豐銀行"),
}

enum class AssetType(val displayName: String) {
    STOCK("股票"),
    ETF("ETF"),
    CASH("現金"),
    DEPOSIT("存款"),
}

enum class Currency {
    TWD,
    USD,
}

data class Holding(
    val id: String,
    val institution: Institution,
    val accountName: String,
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val currency: Currency,
    val quantity: Double,
    val averageCost: Double,
    val marketPrice: Double,
) {
    val marketValue: Double
        get() = quantity * marketPrice

    val cost: Double
        get() = quantity * averageCost

    val unrealizedProfit: Double
        get() = marketValue - cost
}

data class ExchangeRates(
    val usdToTwd: Double = 32.4,
)

data class Allocation(
    val label: String,
    val valueTwd: Double,
    val ratio: Double,
)

data class PortfolioSummary(
    val totalValueTwd: Double,
    val totalCostTwd: Double,
    val unrealizedProfitTwd: Double,
    val returnRate: Double,
    val overseasValueTwd: Double,
    val domesticValueTwd: Double,
    val allocations: List<Allocation>,
)

