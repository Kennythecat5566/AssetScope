import Charts
import SwiftUI

struct HoldingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedHolding: Holding?
    @State private var searchText = ""

    private var holdings: [Holding] {
        guard let source = store.portfolio?.holdings else { return [] }
        let filtered = searchText.isEmpty ? source : source.filter {
            $0.symbol.localizedCaseInsensitiveContains(searchText) ||
            $0.name.localizedCaseInsensitiveContains(searchText)
        }
        return filtered.sorted {
            if $0.assetType.isCash != $1.assetType.isCash {
                return !$0.assetType.isCash
            }
            return $0.marketValue > $1.marketValue
        }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(Array(holdings.enumerated()), id: \.element.id) { index, holding in
                    if holding.assetType.isCash &&
                        (index == 0 || !holdings[index - 1].assetType.isCash) {
                        Divider()
                            .padding(.vertical, 10)
                    }
                    HoldingRow(
                        holding: holding,
                        summary: store.marketSummaries[
                            "\(holding.institution.rawValue):\(holding.symbol)"
                        ],
                        displayCurrency: store.displayCurrency,
                        usdToTwd: store.portfolio?.exchangeRates.usdToTwd ?? 1
                    )
                    .onTapGesture {
                        if !holding.assetType.isCash {
                            selectedHolding = holding
                        }
                    }
                }
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationTitle(store.text("持股", "Holdings"))
        .searchable(
            text: $searchText,
            prompt: store.text("搜尋代號或名稱", "Search symbol or name")
        )
        .toolbar { ScreenToolbar() }
        .sheet(item: $selectedHolding) { holding in
            StockDetailView(holding: holding)
        }
    }
}

private struct HoldingRow: View {
    let holding: Holding
    let summary: MarketSummary?
    let displayCurrency: Currency
    let usdToTwd: Double

    private var convertedValue: Double {
        let metrics = PortfolioMetrics(holdings: [holding], usdToTwd: usdToTwd)
        return metrics.value(of: holding, in: displayCurrency)
    }

    var body: some View {
        HStack(spacing: 14) {
            Text(String(holding.symbol.prefix(3)))
                .font(.subheadline.weight(.medium))
                .frame(width: 58, height: 58)
                .background(AppTheme.primary.opacity(0.10))
                .clipShape(Circle())

            VStack(alignment: .leading, spacing: 3) {
                Text(holding.symbol)
                    .font(.title3.weight(.semibold))
                Text(holding.name)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.muted)
                    .lineLimit(1)
                Text("\(holding.quantity.formatted()) \(holding.assetType.rawValue)")
                    .font(.caption)
                    .foregroundStyle(AppTheme.muted)
            }

            Spacer(minLength: 8)

            if let summary, summary.closes.count > 1 {
                Sparkline(values: summary.closes, positive: summary.change >= 0)
                    .frame(width: 64, height: 35)
            }

            VStack(alignment: .trailing, spacing: 4) {
                Text(convertedValue.money(currency: displayCurrency))
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(holding.returnRate.percent)
                    .font(.caption)
                    .foregroundStyle(
                        holding.unrealizedProfit >= 0
                            ? AppTheme.positive
                            : AppTheme.negative
                    )
            }
        }
        .cardStyle()
        .contentShape(Rectangle())
    }
}

private struct Sparkline: View {
    let values: [Double]
    let positive: Bool

    var body: some View {
        Chart(Array(values.enumerated()), id: \.offset) { index, value in
            LineMark(
                x: .value("Index", index),
                y: .value("Price", value)
            )
            .foregroundStyle(positive ? AppTheme.positive : AppTheme.negative)
            .lineStyle(.init(lineWidth: 2))
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
    }
}

private enum ChartPeriod: Int, CaseIterable, Identifiable {
    case day
    case month
    case quarter
    case year

    var id: Int { rawValue }
    var days: Int {
        switch self {
        case .day: 20
        case .month: 60
        case .quarter: 120
        case .year: 250
        }
    }

    func title(store: AppStore) -> String {
        switch self {
        case .day: store.text("日線", "Daily")
        case .month: store.text("月線", "Monthly")
        case .quarter: store.text("季線", "Quarterly")
        case .year: store.text("年線", "Yearly")
        }
    }
}

private struct StockDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let holding: Holding

    @State private var period = ChartPeriod.month
    @State private var history: PriceHistoryResponse?
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(holding.symbol) · \(holding.name)")
                            .font(.largeTitle.weight(.medium))
                        Text("\(holding.institution.displayName) · \(holding.currency.rawValue)")
                            .foregroundStyle(AppTheme.muted)
                    }

                    LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 12) {
                        PriceMetric(
                            title: store.text("最新", "Latest"),
                            value: holding.marketPrice.money(currency: holding.currency, digits: 2)
                        )
                        PriceMetric(
                            title: store.text("報酬率", "Return"),
                            value: holding.returnRate.percent
                        )
                        PriceMetric(
                            title: store.text("持有均價", "Average cost"),
                            value: holding.averageCost.money(currency: holding.currency, digits: 2)
                        )
                        PriceMetric(
                            title: store.text("持有數量", "Quantity"),
                            value: holding.quantity.formatted()
                        )
                    }

                    Picker(store.text("週期", "Period"), selection: $period) {
                        ForEach(ChartPeriod.allCases) { item in
                            Text(item.title(store: store)).tag(item)
                        }
                    }
                    .pickerStyle(.segmented)

                    Group {
                        if isLoading {
                            ProgressView()
                                .frame(maxWidth: .infinity, minHeight: 360)
                        } else if let history, !history.candles.isEmpty {
                            CandlestickChart(
                                candles: history.candles,
                                currency: history.currency
                            )
                            .frame(height: 390)
                        } else {
                            ContentUnavailableView(
                                store.text("沒有行情資料", "No price history"),
                                systemImage: "chart.xyaxis.line"
                            )
                            .frame(minHeight: 360)
                        }
                    }
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 40)
                            .onEnded { value in
                                changePeriod(direction: value.translation.width)
                            }
                    )

                    Text(
                        store.text(
                            "X 軸：日期　Y 軸：價格（\(holding.currency.rawValue)）　左右滑切換週期",
                            "X: Date  Y: Price (\(holding.currency.rawValue))  Swipe to change period"
                        )
                    )
                    .font(.caption)
                    .foregroundStyle(AppTheme.muted)
                }
                .padding()
            }
            .background(AppTheme.background)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(store.text("關閉", "Close")) { dismiss() }
                }
            }
            .task(id: period) {
                await loadHistory()
            }
            .alert(
                store.text("載入失敗", "Loading failed"),
                isPresented: Binding(
                    get: { errorMessage != nil },
                    set: { if !$0 { errorMessage = nil } }
                )
            ) {
                Button(store.text("完成", "OK")) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func loadHistory() async {
        isLoading = true
        defer { isLoading = false }
        do {
            history = try await store.loadPriceHistory(
                for: holding,
                days: period.days
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func changePeriod(direction: CGFloat) {
        let target = period.rawValue + (direction < 0 ? 1 : -1)
        guard let next = ChartPeriod(rawValue: target) else { return }
        period = next
    }
}

private struct PriceMetric: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.muted)
            Text(value)
                .font(.title3.weight(.medium))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(AppTheme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

private struct CandlestickChart: View {
    let candles: [PriceCandle]
    let currency: Currency

    var body: some View {
        GeometryReader { geometry in
            let minimum = candles.map(\.low).min() ?? 0
            let maximum = candles.map(\.high).max() ?? 1
            let range = max(maximum - minimum, 0.0001)
            let chartWidth = max(geometry.size.width - 58, 1)
            let candleWidth = max(chartWidth / CGFloat(max(candles.count, 1)), 1.5)

            Canvas { context, size in
                for index in 0..<4 {
                    let y = CGFloat(index) * size.height / 3
                    var path = Path()
                    path.move(to: CGPoint(x: 0, y: y))
                    path.addLine(to: CGPoint(x: chartWidth, y: y))
                    context.stroke(path, with: .color(.black.opacity(0.08)), lineWidth: 1)
                }

                for (index, candle) in candles.enumerated() {
                    let x = (CGFloat(index) + 0.5) * candleWidth
                    func y(_ price: Double) -> CGFloat {
                        size.height - CGFloat((price - minimum) / range) * size.height
                    }
                    let rising = candle.close >= candle.open
                    let color = rising ? AppTheme.positive : AppTheme.negative
                    var wick = Path()
                    wick.move(to: CGPoint(x: x, y: y(candle.high)))
                    wick.addLine(to: CGPoint(x: x, y: y(candle.low)))
                    context.stroke(wick, with: .color(color), lineWidth: 1)

                    let top = min(y(candle.open), y(candle.close))
                    let height = max(abs(y(candle.open) - y(candle.close)), 1)
                    let body = CGRect(
                        x: x - max(candleWidth * 0.3, 0.8),
                        y: top,
                        width: max(candleWidth * 0.6, 1.6),
                        height: height
                    )
                    context.fill(Path(body), with: .color(color))
                }
            }
            .padding(.trailing, 58)
            .overlay(alignment: .topTrailing) {
                Text(maximum.money(currency: currency, digits: 2))
                    .font(.caption2)
                    .foregroundStyle(AppTheme.muted)
            }
            .overlay(alignment: .bottomTrailing) {
                Text(minimum.money(currency: currency, digits: 2))
                    .font(.caption2)
                    .foregroundStyle(AppTheme.muted)
            }
        }
    }
}
