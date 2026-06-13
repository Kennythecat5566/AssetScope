import SwiftUI

enum AppTheme {
    static let background = Color(red: 0.976, green: 0.965, blue: 0.949)
    static let surface = Color(red: 0.992, green: 0.984, blue: 0.969)
    static let primary = Color(red: 0.38, green: 0.43, blue: 0.35)
    static let secondary = Color(red: 0.69, green: 0.48, blue: 0.36)
    static let muted = Color(red: 0.48, green: 0.47, blue: 0.43)
    static let positive = Color(red: 0.10, green: 0.58, blue: 0.45)
    static let negative = Color(red: 0.72, green: 0.29, blue: 0.28)
    static let palette: [Color] = [
        primary,
        Color(red: 0.57, green: 0.64, blue: 0.64),
        Color(red: 0.74, green: 0.55, blue: 0.43),
        Color(red: 0.78, green: 0.68, blue: 0.45),
        Color(red: 0.48, green: 0.55, blue: 0.63)
    ]
}

extension View {
    func cardStyle() -> some View {
        padding(18)
            .background(AppTheme.surface)
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .stroke(Color.black.opacity(0.08), lineWidth: 1)
            }
    }
}

extension Double {
    func money(currency: Currency, digits: Int = 0) -> String {
        formatted(
            .currency(code: currency.rawValue)
                .precision(.fractionLength(digits))
        )
    }

    var percent: String {
        formatted(.percent.precision(.fractionLength(1)))
    }
}
