import Charts
import SwiftUI

struct OverviewView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        ScrollView {
            if let portfolio = store.portfolio {
                let metrics = PortfolioMetrics(
                    holdings: portfolio.holdings,
                    usdToTwd: portfolio.exchangeRates.usdToTwd
                )
                LazyVStack(spacing: 18) {
                    balanceCard(portfolio: portfolio, metrics: metrics)
                    performanceCard(portfolio: portfolio)
                    if let history = store.history, !history.points.isEmpty {
                        trendCard(history: history, usdToTwd: portfolio.exchangeRates.usdToTwd)
                    }
                    allocationCard(metrics: metrics)
                    sourceCard(portfolio: portfolio)
                }
                .padding()
            } else {
                ContentUnavailableView(
                    store.text("尚無資產資料", "No portfolio data"),
                    systemImage: "chart.pie",
                    description: Text(
                        store.text(
                            "請先到同步頁設定電腦伺服器。",
                            "Configure your computer server on the Sync tab."
                        )
                    )
                )
                .padding(.top, 80)
            }
        }
        .background(AppTheme.background)
        .navigationTitle(store.text("總覽", "Overview"))
        .toolbar { ScreenToolbar() }
    }

    private func balanceCard(
        portfolio: PortfolioResponse,
        metrics: PortfolioMetrics
    ) -> some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(store.text("總資產淨值", "Total balance"))
                .font(.subheadline)
                .foregroundStyle(AppTheme.muted)
            Text(metrics.totalValue(in: store.displayCurrency).money(
                currency: store.displayCurrency
            ))
            .font(.system(size: 42, weight: .regular, design: .rounded))
            .minimumScaleFactor(0.65)

            HStack {
                MetricView(
                    title: store.text("未實現損益", "Unrealized"),
                    value: metrics.totalProfit(in: store.displayCurrency).money(
                        currency: store.displayCurrency
                    ),
                    positive: metrics.totalProfit(in: store.displayCurrency) >= 0
                )
                Spacer()
                MetricView(
                    title: store.text("報酬率", "Return"),
                    value: metrics.returnRate.percent,
                    positive: metrics.returnRate >= 0
                )
                Spacer()
                MetricView(
                    title: "USD/TWD",
                    value: portfolio.exchangeRates.usdToTwd.formatted(
                        .number.precision(.fractionLength(3))
                    )
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
        .background(AppTheme.primary.opacity(0.09))
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
    }

    private func performanceCard(portfolio: PortfolioResponse) -> some View {
        let performance = portfolio.performance
        return VStack(alignment: .leading, spacing: 16) {
            SectionTitle(
                eyebrow: store.text("投資績效", "Performance"),
                title: performance.returnRate.percent
            )
            LazyVGrid(columns: [.init(.flexible()), .init(.flexible())], spacing: 12) {
                PerformanceCell(
                    title: store.text("已實現損益", "Realized"),
                    value: converted(performance.realizedProfit, portfolio).money(
                        currency: store.displayCurrency
                    )
                )
                PerformanceCell(
                    title: store.text("未實現損益", "Unrealized"),
                    value: converted(performance.unrealizedProfit, portfolio).money(
                        currency: store.displayCurrency
                    )
                )
                PerformanceCell(
                    title: store.text("股息收入", "Dividends"),
                    value: converted(performance.dividendIncome, portfolio).money(
                        currency: store.displayCurrency
                    )
                )
                PerformanceCell(
                    title: store.text("總投資報酬", "Total return"),
                    value: converted(performance.totalReturn, portfolio).money(
                        currency: store.displayCurrency
                    )
                )
            }
        }
        .cardStyle()
    }

    private func trendCard(
        history: PortfolioHistoryResponse,
        usdToTwd: Double
    ) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(store.text("資產趨勢", "Portfolio trend"))
                .font(.title2.weight(.semibold))
            Chart(history.points) { point in
                LineMark(
                    x: .value("Date", point.timestamp),
                    y: .value(
                        "Value",
                        store.displayCurrency == .twd
                            ? point.valueTwd
                            : point.valueTwd / usdToTwd
                    )
                )
                .foregroundStyle(AppTheme.primary)
                .interpolationMethod(.catmullRom)
                AreaMark(
                    x: .value("Date", point.timestamp),
                    y: .value(
                        "Value",
                        store.displayCurrency == .twd
                            ? point.valueTwd
                            : point.valueTwd / usdToTwd
                    )
                )
                .foregroundStyle(
                    LinearGradient(
                        colors: [AppTheme.primary.opacity(0.25), .clear],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
            }
            .chartYAxis {
                AxisMarks(position: .leading)
            }
            .frame(height: 210)
        }
        .cardStyle()
    }

    private func allocationCard(metrics: PortfolioMetrics) -> some View {
        let allocations = metrics.institutionAllocations(in: store.displayCurrency)
        return VStack(alignment: .leading, spacing: 16) {
            Text(store.text("資產分布", "Allocation"))
                .font(.title2.weight(.semibold))
            Chart(allocations) { item in
                SectorMark(
                    angle: .value("Value", item.value),
                    innerRadius: .ratio(0.58),
                    angularInset: 2
                )
                .foregroundStyle(AppTheme.palette[item.colorIndex % AppTheme.palette.count])
                .cornerRadius(4)
            }
            .frame(height: 220)

            ForEach(allocations) { item in
                HStack {
                    Circle()
                        .fill(AppTheme.palette[item.colorIndex % AppTheme.palette.count])
                        .frame(width: 10, height: 10)
                    Text(item.name)
                    Spacer()
                    Text(item.value.money(currency: store.displayCurrency))
                        .foregroundStyle(AppTheme.muted)
                }
                .font(.subheadline)
            }
        }
        .cardStyle()
    }

    private func sourceCard(portfolio: PortfolioResponse) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(store.text("資料來源", "Data sources"))
                .font(.headline)
            Text(portfolio.sources.joined(separator: " · "))
                .font(.footnote)
                .foregroundStyle(AppTheme.muted)
            Text(portfolio.generatedAt.formatted(date: .abbreviated, time: .shortened))
                .font(.caption)
                .foregroundStyle(AppTheme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }

    private func converted(_ valueTwd: Double, _ portfolio: PortfolioResponse) -> Double {
        store.displayCurrency == .twd
            ? valueTwd
            : valueTwd / portfolio.exchangeRates.usdToTwd
    }
}

private struct MetricView: View {
    let title: String
    let value: String
    var positive: Bool?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.muted)
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(color)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }

    private var color: Color {
        guard let positive else { return .primary }
        return positive ? AppTheme.positive : AppTheme.negative
    }
}

private struct PerformanceCell: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.muted)
            Text(value)
                .font(.headline)
                .minimumScaleFactor(0.7)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(AppTheme.background)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct SectionTitle: View {
    let eyebrow: String
    let title: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(eyebrow.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.secondary)
            Spacer()
            Text(title)
                .font(.title3.weight(.semibold))
        }
    }
}
