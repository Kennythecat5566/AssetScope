import Foundation

enum PortfolioCache {
    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()
    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    private static var url: URL? {
        FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first?.appending(path: "portfolio-cache.json")
    }

    static func save(_ portfolio: PortfolioResponse) {
        guard let url, let data = try? encoder.encode(portfolio) else { return }
        try? FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true,
            attributes: nil
        )
        try? data.write(to: url, options: .atomic)
    }

    static func load() -> PortfolioResponse? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return try? decoder.decode(PortfolioResponse.self, from: data)
    }
}
