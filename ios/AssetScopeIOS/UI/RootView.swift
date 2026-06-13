import SwiftUI

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var selectedTab = Tab.overview
    @State private var showingSettings = false

    enum Tab: Hashable {
        case overview
        case holdings
        case activity
        case bots
        case sync
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack { OverviewView() }
                .tabItem { Label(store.text("總覽", "Overview"), systemImage: "chart.pie") }
                .tag(Tab.overview)

            NavigationStack { HoldingsView() }
                .tabItem { Label(store.text("持股", "Holdings"), systemImage: "briefcase") }
                .tag(Tab.holdings)

            NavigationStack { ActivityView() }
                .tabItem { Label(store.text("明細", "Activity"), systemImage: "list.bullet.rectangle") }
                .tag(Tab.activity)

            NavigationStack { PaperTradingView() }
                .tabItem { Label(store.text("機器人", "Bots"), systemImage: "cpu") }
                .tag(Tab.bots)

            NavigationStack { SyncView() }
                .tabItem { Label(store.text("同步", "Sync"), systemImage: "arrow.triangle.2.circlepath") }
                .tag(Tab.sync)
        }
        .tint(AppTheme.primary)
        .background(AppTheme.background)
        .toolbarBackground(AppTheme.background, for: .tabBar)
        .overlay(alignment: .top) {
            if store.isLoading {
                ProgressView()
                    .padding(10)
                    .background(.ultraThinMaterial)
                    .clipShape(Capsule())
                    .padding(.top, 4)
            }
        }
        .alert(
            store.text("同步失敗", "Sync failed"),
            isPresented: Binding(
                get: { store.errorMessage != nil },
                set: { if !$0 { store.errorMessage = nil } }
            ),
            actions: {
                Button(store.text("完成", "OK")) { store.errorMessage = nil }
            },
            message: {
                Text(store.errorMessage ?? "")
            }
        )
        .sheet(isPresented: $showingSettings) {
            SettingsView()
        }
        .environment(\.openSettingsAction) {
            showingSettings = true
        }
    }
}

private struct OpenSettingsActionKey: EnvironmentKey {
    static let defaultValue: () -> Void = {}
}

extension EnvironmentValues {
    var openSettingsAction: () -> Void {
        get { self[OpenSettingsActionKey.self] }
        set { self[OpenSettingsActionKey.self] = newValue }
    }
}

struct ScreenToolbar: ToolbarContent {
    @EnvironmentObject private var store: AppStore
    @Environment(\.openSettingsAction) private var openSettings

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button {
                Task { await store.refresh() }
            } label: {
                Image(systemName: "arrow.clockwise")
            }
            .disabled(!store.isConfigured || store.isLoading)

            Button(action: openSettings) {
                Image(systemName: "gearshape")
            }
        }
    }
}
