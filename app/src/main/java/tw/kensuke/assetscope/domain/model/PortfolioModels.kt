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

enum class TransactionType(val displayName: String) {
    BUY("買入"),
    SELL("賣出"),
    DIVIDEND("股息"),
}

data class Transaction(
    val id: String,
    val institution: Institution,
    val accountName: String,
    val symbol: String,
    val name: String,
    val transactionType: TransactionType,
    val currency: Currency,
    val quantity: Double,
    val price: Double,
    val amount: Double,
    val realizedProfit: Double,
    val tradeDate: String,
    val settledDate: String?,
)

enum class ExpenseCategory(val displayName: String) {
    DINING("餐飲"),
    TRANSPORT("交通"),
    SHOPPING("購物"),
    GROCERIES("日用品"),
    ENTERTAINMENT("娛樂"),
    SUBSCRIPTION("訂閱"),
    TRAVEL("旅遊"),
    HEALTH("醫療"),
    UTILITIES("水電電信"),
    OTHER("其他"),
}

data class Expense(
    val id: String,
    val institution: Institution,
    val transactionDate: String,
    val postedDate: String?,
    val merchant: String,
    val category: ExpenseCategory,
    val amount: Double,
    val currency: Currency,
    val cardLastFour: String,
    val note: String,
)

data class PerformanceSummary(
    val realizedProfit: Double = 0.0,
    val unrealizedProfit: Double = 0.0,
    val dividendIncome: Double = 0.0,
    val totalReturn: Double = 0.0,
    val returnRate: Double = 0.0,
    val totalBuyCost: Double = 0.0,
    val valuationNote: String = "",
)

data class PortfolioInsights(
    val transactions: List<Transaction> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val performance: PerformanceSummary = PerformanceSummary(),
)

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

data class PriceCandle(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
)

data class PriceHistory(
    val symbol: String,
    val currency: Currency,
    val source: String,
    val candles: List<PriceCandle>,
)

data class MarketSummary(
    val institution: Institution,
    val symbol: String,
    val currency: Currency,
    val latestPrice: Double,
    val change: Double,
    val changeRate: Double,
    val closes: List<Double>,
    val source: String,
) {
    val key: String
        get() = "${institution.name}:$symbol"
}

data class ExchangeRates(
    val usdToTwd: Double = 32.4,
    val updatedAt: String? = null,
    val source: String = "configured",
)

data class PortfolioHistoryPoint(
    val timestamp: String,
    val valueTwd: Double,
)

data class PortfolioHistory(
    val points: List<PortfolioHistoryPoint>,
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
    val institutionAllocations: List<Allocation>,
    val assetAllocations: List<Allocation>,
)
