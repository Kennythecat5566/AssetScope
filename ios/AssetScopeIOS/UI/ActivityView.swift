import SwiftUI

struct ActivityView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selection = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $selection) {
                Text(store.text("交易", "Trades")).tag(0)
                Text(store.text("消費", "Expenses")).tag(1)
            }
            .pickerStyle(.segmented)
            .padding()

            if selection == 0 {
                transactions
            } else {
                expenses
            }
        }
        .background(AppTheme.background)
        .navigationTitle(store.text("明細", "Activity"))
        .toolbar { ScreenToolbar() }
    }

    private var transactions: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if let items = store.portfolio?.transactions, !items.isEmpty {
                    ForEach(items) { transaction in
                        TransactionRow(transaction: transaction)
                    }
                } else {
                    ContentUnavailableView(
                        store.text("尚無交易資料", "No transactions"),
                        systemImage: "list.bullet.rectangle"
                    )
                    .padding(.top, 60)
                }
            }
            .padding()
        }
    }

    private var expenses: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if let items = store.portfolio?.expenses, !items.isEmpty {
                    ExpenseSummary(expenses: items)
                    ForEach(items) { expense in
                        ExpenseRow(expense: expense)
                    }
                } else {
                    ContentUnavailableView(
                        store.text("尚無消費資料", "No expenses"),
                        systemImage: "creditcard"
                    )
                    .padding(.top, 60)
                }
            }
            .padding()
        }
    }
}

private struct TransactionRow: View {
    @EnvironmentObject private var store: AppStore
    let transaction: Transaction

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(color)
                .frame(width: 44, height: 44)
                .background(color.opacity(0.10))
                .clipShape(Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text("\(transaction.symbol) · \(transaction.name)")
                    .font(.headline)
                    .lineLimit(1)
                Text("\(transaction.institution.displayName) · \(transaction.tradeDate)")
                    .font(.caption)
                    .foregroundStyle(AppTheme.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 4) {
                Text(label)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(color)
                Text(transaction.amount.money(currency: transaction.currency, digits: 2))
                    .font(.subheadline.weight(.medium))
            }
        }
        .cardStyle()
    }

    private var label: String {
        switch transaction.transactionType {
        case .buy: store.text("買入", "Buy")
        case .sell: store.text("賣出", "Sell")
        case .dividend: store.text("股息", "Dividend")
        }
    }

    private var icon: String {
        switch transaction.transactionType {
        case .buy: "arrow.down.left"
        case .sell: "arrow.up.right"
        case .dividend: "dollarsign"
        }
    }

    private var color: Color {
        transaction.transactionType == .buy ? AppTheme.secondary : AppTheme.positive
    }
}

private struct ExpenseSummary: View {
    @EnvironmentObject private var store: AppStore
    let expenses: [Expense]

    var body: some View {
        let totalTwd = expenses.reduce(0) {
            $0 + ($1.currency == .twd
                ? $1.amount
                : $1.amount * (store.portfolio?.exchangeRates.usdToTwd ?? 1))
        }
        VStack(alignment: .leading, spacing: 8) {
            Text(store.text("累計消費", "Total spending"))
                .foregroundStyle(AppTheme.muted)
            Text(totalTwd.money(currency: .twd))
                .font(.largeTitle.weight(.medium))
            Text(store.text("\(expenses.count) 筆紀錄", "\(expenses.count) records"))
                .font(.caption)
                .foregroundStyle(AppTheme.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(22)
        .background(AppTheme.secondary.opacity(0.10))
        .clipShape(RoundedRectangle(cornerRadius: 26))
    }
}

private struct ExpenseRow: View {
    @EnvironmentObject private var store: AppStore
    let expense: Expense

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(expense.merchant)
                    .font(.headline)
                Text("\(categoryName) · \(expense.transactionDate)")
                    .font(.caption)
                    .foregroundStyle(AppTheme.muted)
            }
            Spacer()
            Text(expense.amount.money(currency: expense.currency, digits: 2))
                .font(.headline)
        }
        .cardStyle()
    }

    private var categoryName: String {
        switch expense.category {
        case .dining: store.text("餐飲", "Dining")
        case .transport: store.text("交通", "Transport")
        case .shopping: store.text("購物", "Shopping")
        case .groceries: store.text("日用品", "Groceries")
        case .entertainment: store.text("娛樂", "Entertainment")
        case .subscription: store.text("訂閱", "Subscriptions")
        case .travel: store.text("旅遊", "Travel")
        case .health: store.text("健康", "Health")
        case .utilities: store.text("水電通訊", "Utilities")
        case .other: store.text("其他", "Other")
        }
    }
}
