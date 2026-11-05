import SwiftUI
import TubeSachCore

// MARK: - Kênh đăng ký

/// Video mới của các kênh đã đăng ký (lưu trên máy, không cần tài khoản Google).
struct SubscriptionsView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if store.subscriptions.isEmpty {
                    MessageView(
                        icon: "rectangle.stack.badge.plus",
                        title: "Chưa đăng ký kênh nào",
                        message: "Mở một kênh bạn thích rồi bấm “Đăng ký”. Video mới của kênh sẽ hiện ở đây."
                    )
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(alignment: .top, spacing: 16) {
                            ForEach(store.subscriptions) { ch in
                                Button { model.openChannel(ch.id) } label: {
                                    VStack(spacing: 6) {
                                        Avatar(url: ch.avatar, size: 60)
                                        Text(ch.name)
                                            .font(.caption)
                                            .foregroundColor(.primary)
                                            .lineLimit(2)
                                            .multilineTextAlignment(.center)
                                            .frame(width: 76)
                                    }
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding()
                    }
                    let feed = model.subscriptionFeed
                    if feed.loading && feed.entries.isEmpty {
                        LoadingView()
                    } else if feed.entries.isEmpty {
                        MessageView(icon: "tray", title: "Chưa có video mới", actionTitle: "Tải lại") {
                            model.loadSubscriptions(force: true)
                        }
                    } else {
                        FeedList(entries: feed.entries)
                    }
                }
            }
        }
        .refreshable { await model.refreshSubscriptions() }
        .mainToolbar()
        .onAppear { model.loadSubscriptions() }
        .onChange(of: store.subscriptions.map(\.id)) { _ in model.loadSubscriptions(force: true) }
    }
}

// MARK: - Bạn

struct LibraryView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore

    var body: some View {
        List {
            Section {
                HStack(spacing: 14) {
                    Image(systemName: "play.circle.fill")
                        .font(.system(size: 52))
                        .foregroundColor(.accentColor)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Tube Sạch").font(.title2.bold())
                        Text("Không quảng cáo · không cần đăng nhập")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.vertical, 6)
                if let v = model.updateVersion, let page = model.updatePage, let url = URL(string: page) {
                    Link(destination: url) {
                        Label("Có bản mới \(v) – bấm để xem cách cập nhật", systemImage: "arrow.down.circle.fill")
                            .font(.headline)
                    }
                }
            }

            if !store.history.isEmpty {
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        LazyHStack(alignment: .top, spacing: 12) {
                            ForEach(store.history.prefix(20)) { entry in
                                Button { model.play(entry.video) } label: {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Thumbnail(video: entry.video, progress: model.progress(of: entry.video), cornerRadius: 10)
                                        Text(entry.video.title)
                                            .font(.subheadline.weight(.semibold))
                                            .foregroundColor(.primary)
                                            .lineLimit(2)
                                            .multilineTextAlignment(.leading)
                                        Text(entry.video.uploader)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                    .frame(width: 180)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                } header: {
                    HStack {
                        Text("Video đã xem")
                        Spacer()
                        Button("Xem tất cả") { model.push(.list(.history)) }
                            .font(.subheadline)
                    }
                }
            }

            Section {
                ForEach([ListKind.history, .liked, .watchLater]) { kind in
                    NavigationLink(value: Route.list(kind)) {
                        Label {
                            HStack {
                                Text(kind.title)
                                Spacer()
                                Text("\(count(kind))").foregroundColor(.secondary)
                            }
                        } icon: {
                            Image(systemName: kind.icon)
                        }
                    }
                }
            }

            Section("Khám phá") {
                ForEach([ListKind.trending, .topMusic]) { kind in
                    NavigationLink(value: Route.list(kind)) {
                        Label(kind.title, systemImage: kind.icon)
                    }
                }
            }

            Section {
                NavigationLink(value: Route.settings) {
                    Label("Cài đặt", systemImage: "gearshape")
                }
            }
        }
        .listStyle(.insetGrouped)
        .mainToolbar()
    }

    private func count(_ kind: ListKind) -> Int {
        switch kind {
        case .history: return store.history.count
        case .liked: return store.liked.count
        case .watchLater: return store.watchLater.count
        default: return 0
        }
    }
}

// MARK: - Danh sách video

struct VideoListView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    let kind: ListKind
    @State private var confirmClear = false

    var body: some View {
        content
            .navigationTitle(kind.title)
            .navigationBarTitleDisplayMode(.inline)
            .onAppear { model.loadList(kind) }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if kind == .history && !store.history.isEmpty {
                        Button("Xoá hết") { confirmClear = true }
                    }
                }
            }
            .confirmationDialog("Xoá toàn bộ video đã xem?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Xoá hết", role: .destructive) { store.clearHistory() }
                Button("Huỷ", role: .cancel) {}
            }
    }

    @ViewBuilder
    private var content: some View {
        switch kind {
        case .history:
            localList(store.history.map(\.video), empty: "Bạn chưa xem video nào") { v in store.removeFromHistory(v.id) }
        case .liked:
            localList(store.liked, empty: "Chưa có video đã thích") { v in store.toggleLike(v) }
        case .watchLater:
            localList(store.watchLater, empty: "Bấm “Lưu” ở trang xem video để thêm vào đây") { v in store.toggleWatchLater(v) }
        case .trending, .topMusic:
            let st = model.lists[kind] ?? FeedState(loading: true)
            ScrollView {
                if st.loading {
                    LoadingView()
                } else if let e = st.error {
                    ErrorView(message: e) { model.reloadList(kind) }
                } else {
                    FeedList(entries: st.entries)
                }
            }
            .refreshable { model.reloadList(kind) }
        }
    }

    @ViewBuilder
    private func localList(_ videos: [VideoItem], empty: String, remove: @escaping (VideoItem) -> Void) -> some View {
        if videos.isEmpty {
            MessageView(icon: kind.icon, title: empty)
        } else {
            List {
                ForEach(videos) { v in
                    VideoRow(video: v, progress: model.progress(of: v))
                        .swipeActions {
                            Button(role: .destructive) { remove(v) } label: {
                                Label("Xoá", systemImage: "trash")
                            }
                        }
                }
            }
            .listStyle(.plain)
        }
    }
}
