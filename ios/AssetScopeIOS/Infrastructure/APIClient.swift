import Foundation

enum APIError: LocalizedError {
    case invalidServerURL
    case insecurePublicServer
    case invalidResponse
    case server(Int, String)

    var errorDescription: String? {
        switch self {
        case .invalidServerURL:
            "Server URL is invalid."
        case .insecurePublicServer:
            "HTTP is only allowed for private or Tailscale addresses. Use HTTPS for public servers."
        case .invalidResponse:
            "The server returned an invalid response."
        case let .server(code, message):
            "Server error \(code): \(message)"
        }
    }
}

struct APIClient {
    let serverURL: URL
    let token: String

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let value = try container.decode(String.self)
            if let date = ISO8601DateFormatter.withFractionalSeconds.date(from: value)
                ?? ISO8601DateFormatter.standard.date(from: value) {
                return date
            }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid ISO-8601 date: \(value)"
            )
        }
        return decoder
    }()

    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return encoder
    }()

    init(serverAddress: String, token: String) throws {
        let trimmed = serverAddress.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed),
              let scheme = url.scheme?.lowercased(),
              let host = url.host,
              scheme == "https" || scheme == "http" else {
            throw APIError.invalidServerURL
        }
        if scheme == "http" && !Self.isPrivateHost(host) {
            throw APIError.insecurePublicServer
        }
        self.serverURL = url
        self.token = token.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func portfolio() async throws -> PortfolioResponse {
        try await get("api/v1/portfolio")
    }

    func portfolioHistory(days: Int = 365) async throws -> PortfolioHistoryResponse {
        try await get("api/v1/portfolio/history?days=\(days)")
    }

    func paperTrading() async throws -> PaperTradingResponse {
        try await get("api/v1/paper-trading")
    }

    func priceHistory(for holding: Holding, days: Int) async throws -> PriceHistoryResponse {
        let institution = holding.institution.rawValue
        let symbol = holding.symbol.addingPercentEncoding(
            withAllowedCharacters: .urlPathAllowed
        ) ?? holding.symbol
        return try await get("api/v1/history/\(institution)/\(symbol)?days=\(days)")
    }

    func marketSummaries(for holdings: [Holding]) async throws -> [MarketSummary] {
        let request = MarketSummaryRequest(
            items: holdings
                .filter { !$0.assetType.isCash }
                .prefix(50)
                .map {
                    MarketSummaryRequestItem(
                        institution: $0.institution,
                        symbol: $0.symbol
                    )
                }
        )
        let body = try Self.encoder.encode(request)
        let response: MarketSummariesResponse = try await send(
            path: "api/v1/market/summaries",
            method: "POST",
            body: body
        )
        return response.summaries
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        try await send(path: path, method: "GET", body: nil)
    }

    private func send<T: Decodable>(
        path: String,
        method: String,
        body: Data?
    ) async throws -> T {
        guard let url = URL(string: path, relativeTo: serverURL)?.absoluteURL else {
            throw APIError.invalidServerURL
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 15
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        guard 200..<300 ~= http.statusCode else {
            let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            let detail = payload?["detail"] as? String
            throw APIError.server(http.statusCode, detail ?? "Unknown error")
        }
        do {
            return try Self.decoder.decode(T.self, from: data)
        } catch {
            throw APIError.server(http.statusCode, "Unable to decode server data: \(error)")
        }
    }

    private static func isPrivateHost(_ host: String) -> Bool {
        let value = host.lowercased()
        if value == "localhost" || value.hasSuffix(".local") {
            return true
        }
        let parts = value.split(separator: ".").compactMap { Int($0) }
        guard parts.count == 4 else { return false }
        switch (parts[0], parts[1]) {
        case (10, _), (127, _), (192, 168):
            return true
        case (172, 16...31), (100, 64...127):
            return true
        default:
            return false
        }
    }
}

private extension ISO8601DateFormatter {
    static let standard = ISO8601DateFormatter()

    static let withFractionalSeconds: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
