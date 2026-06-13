import Foundation

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var portfolio: PortfolioResponse?
    @Published private(set) var history: PortfolioHistoryResponse?
    @Published private(set) var paperTrading: PaperTradingResponse?
    @Published private(set) var marketSummaries: [String: MarketSummary] = [:]
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    @Published var displayCurrency: Currency {
        didSet { defaults.set(displayCurrency.rawValue, forKey: Keys.currency) }
    }
    @Published var language: AppLanguage {
        didSet { defaults.set(language.rawValue, forKey: Keys.language) }
    }
    @Published var serverAddress: String {
        didSet { defaults.set(serverAddress, forKey: Keys.serverAddress) }
    }
    @Published var token: String

    private let defaults = UserDefaults.standard

    private enum Keys {
        static let currency = "displayCurrency"
        static let language = "language"
        static let serverAddress = "serverAddress"
        static let token = "apiToken"
    }

    init() {
        displayCurrency = Currency(
            rawValue: defaults.string(forKey: Keys.currency) ?? ""
        ) ?? .twd
        language = AppLanguage(
            rawValue: defaults.string(forKey: Keys.language) ?? ""
        ) ?? .traditionalChinese
        serverAddress = defaults.string(forKey: Keys.serverAddress) ?? ""
        token = KeychainStore.read(account: Keys.token)
        portfolio = PortfolioCache.load()
    }

    var isConfigured: Bool {
        !serverAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func text(_ chinese: String, _ english: String) -> String {
        language == .traditionalChinese ? chinese : english
    }

    func bootstrap() async {
        guard isConfigured else { return }
        await refresh()
    }

    func saveConnection() async {
        do {
            try KeychainStore.save(
                token.trimmingCharacters(in: .whitespacesAndNewlines),
                account: Keys.token
            )
            await refresh()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func refresh() async {
        guard !isLoading, isConfigured else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let client = try APIClient(serverAddress: serverAddress, token: token)
            async let portfolioRequest = client.portfolio()
            async let historyRequest = client.portfolioHistory()
            async let botsRequest = client.paperTrading()
            let loadedPortfolio = try await portfolioRequest
            portfolio = loadedPortfolio
            PortfolioCache.save(loadedPortfolio)
            history = try? await historyRequest
            paperTrading = try? await botsRequest

            if let summaries = try? await client.marketSummaries(
                for: loadedPortfolio.holdings
            ) {
                marketSummaries = Dictionary(
                    uniqueKeysWithValues: summaries.map { ($0.id, $0) }
                )
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func loadPriceHistory(for holding: Holding, days: Int) async throws -> PriceHistoryResponse {
        let client = try APIClient(serverAddress: serverAddress, token: token)
        return try await client.priceHistory(for: holding, days: days)
    }
}
