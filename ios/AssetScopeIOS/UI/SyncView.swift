import SwiftUI

struct SyncView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                VStack(alignment: .leading, spacing: 10) {
                    Label(
                        store.text("電腦資產伺服器", "Computer asset server"),
                        systemImage: "desktopcomputer"
                    )
                    .font(.title2.weight(.semibold))
                    Text(
                        store.text(
                            "同一 Wi-Fi 可使用 192.168.x.x；外出時可使用電腦的 Tailscale 100.x.x.x 位址。",
                            "Use a 192.168.x.x address on the same Wi-Fi, or the computer's Tailscale 100.x.x.x address remotely."
                        )
                    )
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.muted)
                }

                VStack(alignment: .leading, spacing: 16) {
                    TextField(
                        store.text("伺服器網址", "Server URL"),
                        text: $store.serverAddress,
                        prompt: Text("http://100.x.x.x:8787")
                    )
                    .textInputAutocapitalization(.never)
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                    .padding()
                    .background(AppTheme.background)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    SecureField(
                        "API Token",
                        text: $store.token
                    )
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .padding()
                    .background(AppTheme.background)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    Button {
                        Task { await store.saveConnection() }
                    } label: {
                        Label(
                            store.text("連接並同步", "Connect and sync"),
                            systemImage: "arrow.triangle.2.circlepath"
                        )
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.primary)
                    .disabled(store.serverAddress.isEmpty || store.token.isEmpty || store.isLoading)
                }
                .cardStyle()

                if let portfolio = store.portfolio {
                    VStack(alignment: .leading, spacing: 8) {
                        Label(
                            store.text("最近同步成功", "Last sync succeeded"),
                            systemImage: "checkmark.circle.fill"
                        )
                        .foregroundStyle(AppTheme.positive)
                        Text(portfolio.generatedAt.formatted(date: .long, time: .standard))
                            .font(.subheadline)
                        Text(
                            store.text(
                                "\(portfolio.holdings.count) 項資產 · \(portfolio.transactions.count) 筆交易",
                                "\(portfolio.holdings.count) assets · \(portfolio.transactions.count) trades"
                            )
                        )
                        .font(.caption)
                        .foregroundStyle(AppTheme.muted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .cardStyle()
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(store.text("安全說明", "Security"))
                        .font(.headline)
                    Text(
                        store.text(
                            "API Token 儲存在 iOS Keychain。HTTP 僅允許私人區網與 Tailscale 位址，公開伺服器必須使用 HTTPS。",
                            "The API token is stored in iOS Keychain. HTTP is limited to private and Tailscale addresses; public servers must use HTTPS."
                        )
                    )
                    .font(.footnote)
                    .foregroundStyle(AppTheme.muted)
                }
                .cardStyle()
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationTitle(store.text("同步", "Sync"))
        .toolbar { ScreenToolbar() }
    }
}
