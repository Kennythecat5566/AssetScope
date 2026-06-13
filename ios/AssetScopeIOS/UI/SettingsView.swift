import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section(store.text("顯示幣別", "Display currency")) {
                    Picker(
                        store.text("幣別", "Currency"),
                        selection: $store.displayCurrency
                    ) {
                        ForEach(Currency.allCases) { currency in
                            Text(currency.rawValue).tag(currency)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section(store.text("介面語言", "Language")) {
                    Picker(
                        store.text("語言", "Language"),
                        selection: $store.language
                    ) {
                        Text("繁體中文").tag(AppLanguage.traditionalChinese)
                        Text("English").tag(AppLanguage.english)
                    }
                    .pickerStyle(.segmented)
                }

                Section(store.text("關於", "About")) {
                    LabeledContent("App", value: "AssetScope iOS")
                    LabeledContent(
                        store.text("最低系統", "Minimum system"),
                        value: "iOS 17"
                    )
                    Link(
                        "GitHub",
                        destination: URL(
                            string: "https://github.com/Kennythecat5566/AssetScope"
                        )!
                    )
                }
            }
            .scrollContentBackground(.hidden)
            .background(AppTheme.background)
            .navigationTitle(store.text("設定", "Settings"))
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(store.text("完成", "Done")) { dismiss() }
                }
            }
        }
    }
}
