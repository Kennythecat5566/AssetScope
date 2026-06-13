import Foundation

enum Institution: String, Codable {
    case firstrade = "FIRSTRade"
    case sinopacSecurities = "SINOPAC_SECURITIES"
    case sinopacBank = "SINOPAC_BANK"

    var displayName: String {
        switch self {
        case .firstrade: "Firstrade"
        case .sinopacSecurities: "永豐證券"
        case .sinopacBank: "永豐銀行"
        }
    }
}

enum AssetType: String, Codable {
    case stock = "STOCK"
    case etf = "ETF"
    case cash = "CASH"
    case deposit = "DEPOSIT"

    var isCash: Bool { self == .cash || self == .deposit }
}

enum Currency: String, Codable, CaseIterable, Identifiable {
    case twd = "TWD"
    case usd = "USD"

    var id: String { rawValue }
}

enum AppLanguage: String, CaseIterable, Identifiable {
    case traditionalChinese
    case english

    var id: String { rawValue }
}

enum TransactionType: String, Codable {
    case buy = "BUY"
    case sell = "SELL"
    case dividend = "DIVIDEND"
}

enum ExpenseCategory: String, Codable {
    case dining = "DINING"
    case transport = "TRANSPORT"
    case shopping = "SHOPPING"
    case groceries = "GROCERIES"
    case entertainment = "ENTERTAINMENT"
    case subscription = "SUBSCRIPTION"
    case travel = "TRAVEL"
    case health = "HEALTH"
    case utilities = "UTILITIES"
    case other = "OTHER"
}

struct ExchangeRates: Codable {
    let usdToTwd: Double
    let updatedAt: Date?
    let source: String
}

struct Holding: Codable, Identifiable, Hashable {
    let id: String
    let institution: Institution
    let accountName: String
    let symbol: String
    let name: String
    let assetType: AssetType
    let currency: Currency
    let quantity: Double
    let averageCost: Double
    let marketPrice: Double

    var marketValue: Double { quantity * marketPrice }
    var cost: Double { quantity * averageCost }
    var unrealizedProfit: Double { marketValue - cost }
    var returnRate: Double { cost == 0 ? 0 : unrealizedProfit / cost }
}

struct Transaction: Codable, Identifiable {
    let id: String
    let institution: Institution
    let accountName: String
    let symbol: String
    let name: String
    let transactionType: TransactionType
    let currency: Currency
    let quantity: Double
    let price: Double
    let amount: Double
    let realizedProfit: Double
    let tradeDate: String
    let settledDate: String?
}

struct Expense: Codable, Identifiable {
    let id: String
    let institution: Institution
    let transactionDate: String
    let postedDate: String?
    let merchant: String
    let category: ExpenseCategory
    let amount: Double
    let currency: Currency
    let cardLastFour: String
    let note: String
}

struct PerformanceSummary: Codable {
    let realizedProfit: Double
    let unrealizedProfit: Double
    let dividendIncome: Double
    let totalReturn: Double
    let returnRate: Double
    let totalBuyCost: Double
    let valuationNote: String
}

struct PortfolioResponse: Codable {
    let schemaVersion: Int
    let generatedAt: Date
    let exchangeRates: ExchangeRates
    let holdings: [Holding]
    let transactions: [Transaction]
    let expenses: [Expense]
    let performance: PerformanceSummary
    let sources: [String]
}

struct PriceCandle: Codable, Identifiable {
    let date: String
    let open: Double
    let high: Double
    let low: Double
    let close: Double
    let volume: Double

    var id: String { date }
}

struct PriceHistoryResponse: Codable {
    let symbol: String
    let currency: Currency
    let source: String
    let candles: [PriceCandle]
}

struct MarketSummaryRequest: Encodable {
    let items: [MarketSummaryRequestItem]
}

struct MarketSummaryRequestItem: Encodable {
    let institution: Institution
    let symbol: String
}

struct MarketSummariesResponse: Decodable {
    let summaries: [MarketSummary]
}

struct MarketSummary: Codable, Identifiable {
    let institution: Institution
    let symbol: String
    let currency: Currency
    let latestPrice: Double
    let change: Double
    let changeRate: Double
    let closes: [Double]
    let source: String

    var id: String { "\(institution.rawValue):\(symbol)" }
}

struct PortfolioHistoryResponse: Codable {
    let currency: Currency
    let points: [PortfolioHistoryPoint]
}

struct PortfolioHistoryPoint: Codable, Identifiable {
    let timestamp: Date
    let valueTwd: Double

    var id: Date { timestamp }
}

struct PaperTradingResponse: Codable {
    let generatedAt: Date
    let paperOnly: Bool
    let bots: [PaperBot]
}

struct PaperBot: Codable, Identifiable {
    let id: String
    let name: String
    let strategy: String
    let marketScope: String
    let paperOnly: Bool
    let initialCashTwd: Double
    let cashTwd: Double
    let netValueTwd: Double
    let totalReturnTwd: Double
    let returnRate: Double
    let tradeCount: Int
    let lastRunAt: Date?
    let positions: [PaperBotPosition]
    let recentTrades: [PaperBotTrade]
    let equityHistory: [PaperBotEquityPoint]
    let performanceHistory: [PaperBotPerformancePoint]
}

struct PaperBotPosition: Codable, Identifiable {
    let symbol: String
    let name: String
    let quantity: Double
    let averageCostTwd: Double
    let marketPriceTwd: Double
    let marketValueTwd: Double
    let unrealizedProfitTwd: Double

    var id: String { symbol }
}

struct PaperBotTrade: Codable, Identifiable {
    let id: String
    let timestamp: Date
    let botId: String
    let symbol: String
    let name: String
    let side: String
    let quantity: Double
    let priceTwd: Double
    let amountTwd: Double
    let reason: String
}

struct PaperBotEquityPoint: Codable, Identifiable {
    let timestamp: Date
    let netValueTwd: Double

    var id: Date { timestamp }
}

struct PaperBotPerformancePoint: Codable, Identifiable {
    let timestamp: Date
    let botValueTwd: Double
    let taiwanIndexValue: Double?
    let usIndexValue: Double?

    var id: Date { timestamp }
}
