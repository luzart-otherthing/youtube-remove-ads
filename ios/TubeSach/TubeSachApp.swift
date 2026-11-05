import SwiftUI
import TubeSachCore

@main
struct TubeSachApp: App {
    @StateObject private var store: LocalStore
    @StateObject private var model: AppModel

    init() {
        Services.configureCaches()
        let store = LocalStore()
        _store = StateObject(wrappedValue: store)
        _model = StateObject(wrappedValue: AppModel(store: store))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(store)
                .environmentObject(model.player)
        }
    }
}

/// Khung chính: 4 tab giống app YouTube + trình phát mini + trang xem video.
struct RootView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    @EnvironmentObject var player: PlayerController

    var body: some View {
        TabView(selection: Binding(get: { model.tab }, set: { model.selectTab($0) })) {
            NavigationStack(path: $model.homePath) {
                HomeView().withRoutes()
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayerBar() }
            .tabItem { Label("Trang chủ", systemImage: "house.fill") }
            .tag(Tab.home)

            ShortsView()
                .tabItem { Label("Shorts", systemImage: "play.square.stack.fill") }
                .tag(Tab.shorts)

            NavigationStack(path: $model.subscriptionsPath) {
                SubscriptionsView().withRoutes()
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayerBar() }
            .tabItem { Label("Kênh đăng ký", systemImage: "rectangle.stack.badge.play.fill") }
            .tag(Tab.subscriptions)

            NavigationStack(path: $model.libraryPath) {
                LibraryView().withRoutes()
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayerBar() }
            .tabItem { Label("Bạn", systemImage: "person.crop.circle.fill") }
            .tag(Tab.library)
        }
        .fullScreenCover(isPresented: $model.watchExpanded) {
            WatchView()
                .environmentObject(model)
                .environmentObject(store)
                .environmentObject(player)
                .dynamicTypeSize(store.settings.textSize.dynamicType)
                .preferredColorScheme(store.settings.theme.colorScheme)
        }
        .dynamicTypeSize(store.settings.textSize.dynamicType)
        .preferredColorScheme(store.settings.theme.colorScheme)
        .onOpenURL { url in _ = model.handle(url) }
        .onChange(of: store.settings) { _ in player.settingsChanged() }
        .task {
            model.handleLaunchArguments()
            model.checkUpdate()
        }
    }
}

extension View {
    /// Các trang có thể mở trong mỗi tab.
    func withRoutes() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .search(let text): SearchView(initialText: text)
            case .channel(let id): ChannelView(channelId: id)
            case .list(let kind): VideoListView(kind: kind)
            case .settings: SettingsView()
            }
        }
    }

    /// Thanh trên cùng: logo bên trái, nút tìm kiếm bên phải.
    func mainToolbar() -> some View {
        modifier(MainToolbar())
    }
}

private struct MainToolbar: ViewModifier {
    @EnvironmentObject var model: AppModel

    func body(content: Content) -> some View {
        content
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) { AppLogo() }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { model.openSearch() } label: {
                        Image(systemName: "magnifyingglass")
                            .font(.title3)
                    }
                    .accessibilityLabel("Tìm kiếm")
                }
            }
    }
}

/// Trình phát mini nằm trên thanh dưới.
struct MiniPlayerBar: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var player: PlayerController

    var body: some View {
        if let v = player.current, !model.watchExpanded {
            VStack(spacing: 0) {
                GeometryReader { geo in
                    Rectangle()
                        .fill(Color.red)
                        .frame(width: player.duration > 0 ? geo.size.width * min(1, player.position / player.duration) : 0)
                }
                .frame(height: 2)
                HStack(spacing: 12) {
                    Button { model.watchExpanded = true } label: {
                        HStack(spacing: 12) {
                            RemoteImage(url: v.thumbnail)
                                .frame(width: 96, height: 54)
                                .clipShape(RoundedRectangle(cornerRadius: 6))
                            VStack(alignment: .leading, spacing: 2) {
                                Text(v.title.isEmpty ? "Đang tải…" : v.title)
                                    .font(.subheadline.weight(.semibold))
                                    .lineLimit(1)
                                Text(v.uploader)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { player.togglePlay() } label: {
                        Image(systemName: player.isPlaying ? "pause.fill" : "play.fill")
                            .font(.title2)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel(player.isPlaying ? "Tạm dừng" : "Phát")
                    Button { model.closePlayer() } label: {
                        Image(systemName: "xmark")
                            .font(.title3)
                            .frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Đóng trình phát")
                }
                .foregroundColor(.primary)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
            }
            .background(.bar)
            .overlay(alignment: .top) { Divider() }
        }
    }
}
