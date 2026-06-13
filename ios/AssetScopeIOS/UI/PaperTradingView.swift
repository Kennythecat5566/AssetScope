import Charts
import SwiftUI

struct PaperTradingView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedBot: PaperBot?

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 14) {
                if let dashboard = store.paperTrading {
                    HStack {
                        Image(systemName: "shield.lefthalf.filled")
                        Text(
                            store.text(
                                "僅限虛擬交易，不會送出真實訂單",
                                "Paper trading only. No real orders are submitted."
                            )
                        )
                        .font(.footnote)
                    }
                    .foregroundStyle(AppTheme.primary)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.primary.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                    ForEach(dashboard.bots) { bot in
                        BotCard(bot: bot)
                            .onTapGesture { selectedBot = bot }
                    }
                } else {
                    ContentUnavailableView(
                        store.text("尚無機器人資料", "No bot data"),
                        systemImage: "cpu",
                        description: Text(
                            store.text(
                                "連接伺服器並同步以載入模擬交易。",
                                "Connect and sync to load paper trading."
                            )
                        )
                    )
                    .padding(.top, 70)
                }
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationTitle(store.text("模擬機器人", "Paper bots"))
        .toolbar { ScreenToolbar() }
        .sheet(item: $selectedBot) { bot in
            BotDetailView(bot: bot)
        }
    }
}

private struct BotCard: View {
    @EnvironmentObject private var store: AppStore
    let bot: PaperBot

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(bot.name)
                        .font(.title2.weight(.semibold))
                    Text(bot.marketScope)
                        .font(.caption)
                        .foregroundStyle(AppTheme.muted)
                }
                Spacer()
                Text(bot.returnRate.percent)
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(
                        bot.returnRate >= 0 ? AppTheme.positive : AppTheme.negative
                    )
            }

            if bot.equityHistory.count > 1 {
                Chart(bot.equityHistory) { point in
                    LineMark(
                        x: .value("Date", point.timestamp),
                        y: .value("Value", point.netValueTwd)
                    )
                    .foregroundStyle(AppTheme.primary)
                    .interpolationMethod(.catmullRom)
                }
                .chartXAxis(.hidden)
                .chartYAxis(.hidden)
                .frame(height: 75)
            }

            HStack {
                BotMetric(
                    title: store.text("淨值", "Equity"),
                    value: bot.netValueTwd.money(currency: .twd)
                )
                Spacer()
                BotMetric(
                    title: store.text("交易", "Trades"),
                    value: bot.tradeCount.formatted()
                )
                Spacer()
                BotMetric(
                    title: store.text("持倉", "Positions"),
                    value: bot.positions.count.formatted()
                )
            }
        }
        .cardStyle()
        .contentShape(Rectangle())
    }
}

private struct BotMetric: View {
    let title: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.muted)
            Text(value)
                .font(.subheadline.weight(.medium))
        }
    }
}

private struct BotDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let bot: PaperBot

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(bot.strategy)
                            .foregroundStyle(AppTheme.muted)
                        Text(bot.netValueTwd.money(currency: .twd))
                            .font(.largeTitle.weight(.medium))
                        Text(bot.returnRate.percent)
                            .foregroundStyle(
                                bot.returnRate >= 0
                                    ? AppTheme.positive
                                    : AppTheme.negative
                            )
                    }

                    if !bot.performanceHistory.isEmpty {
                        VStack(alignment: .leading, spacing: 12) {
                            Text(store.text("績效比較", "Performance"))
                                .font(.title2.weight(.semibold))
                            Chart(bot.performanceHistory) { point in
                                LineMark(
                                    x: .value("Date", point.timestamp),
                                    y: .value("Bot", point.botValueTwd),
                                    series: .value("Series", store.text("機器人", "Bot"))
                                )
                                .foregroundStyle(AppTheme.primary)
                                if let value = point.taiwanIndexValue {
                                    LineMark(
                                        x: .value("Date", point.timestamp),
                                        y: .value("TW", value),
                                        series: .value("Series", store.text("台股指數", "Taiwan index"))
                                    )
                                    .foregroundStyle(AppTheme.secondary)
                                }
                                if let value = point.usIndexValue {
                                    LineMark(
                                        x: .value("Date", point.timestamp),
                                        y: .value("US", value),
                                        series: .value("Series", store.text("美股指數", "US index"))
                                    )
                                    .foregroundStyle(Color.blue.opacity(0.65))
                                }
                            }
                            .chartLegend(position: .bottom)
                            .frame(height: 280)
                        }
                        .cardStyle()
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        Text(store.text("交易紀錄", "Trade history"))
                            .font(.title2.weight(.semibold))
                        if bot.recentTrades.isEmpty {
                            Text(store.text("尚無交易", "No trades yet"))
                                .foregroundStyle(AppTheme.muted)
                        }
                        ForEach(bot.recentTrades) { trade in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("\(trade.side) · \(trade.symbol)")
                                        .font(.headline)
                                    Text(trade.timestamp.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.muted)
                                    Text(trade.reason)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.muted)
                                }
                                Spacer()
                                Text(trade.amountTwd.money(currency: .twd))
                                    .font(.subheadline.weight(.medium))
                            }
                            if trade.id != bot.recentTrades.last?.id {
                                Divider()
                            }
                        }
                    }
                    .cardStyle()
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(bot.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(store.text("關閉", "Close")) { dismiss() }
                }
            }
        }
    }
}
