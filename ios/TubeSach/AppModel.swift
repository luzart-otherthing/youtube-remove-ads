import Foundation
import SwiftUI
import TubeSachCore

enum Tab: Hashable {
    case home, shorts, subscriptions, library
}

enum ListKind: String, Hashable, CaseIterable, Identifiable {
    case history, liked, watchLater, trending, topMusic

    var id: String { rawValue }

    var title: String {
        switch self {
        case .history: return "Video đã xem"
        case .liked: return "Video đã thích"
        case .watchLater: return "Xem sau"
        case .trending: return "Thịnh hành tại Việt Nam"
        case .topMusic: return "Nhạc hàng đầu Việt Nam"
        }
    }

    var icon: String {
        switch self {
        case .history: return "clock.arrow.circlepath"
        case .liked: return "hand.thumbsup"
        case .watchLater: return "clock"
        case .trending: return "flame"
        case .topMusic: return "music.note"
        }
    }
}

enum Route: Hashable {
    case search(String)
    case channel(String)
    case list(ListKind)
    case settings
}

struct FeedState {
    var entries: [FeedEntry] = []
    var loading = false
    var loadingMore = false
    var error: String?
    var continuation: String?
    var chips: [Chip] = []
    var selectedChip = 0
    var loaded = false
}

struct ChannelState {
    var page: ChannelPage?
    var loading = true
    var error: String?
    var tab = 0
    var videos = FeedState(loading: true)
}

struct CommentsState {
    var videoId = ""
    var token: String?
    var items: [CommentItem] = []
    var loading = false
    var loadingMore = false
    var error: String?
    var continuation: String?
}

/// Trạng thái chung của app: điều hướng, trang chủ, trang xem, kênh, Shorts…
@MainActor
final class AppModel: ObservableObject {
    let store: LocalStore
    let player: PlayerController
    private let youtube = Services.youtube

    init(store: LocalStore) {
        self.store = store
        self.player = PlayerController(store: store)
        player.onEnded = { [weak self] in
            DispatchQueue.main.async { self?.playNext(automatic: true) }
        }
        player.onNext = { [weak self] in
            DispatchQueue.main.async { self?.playNext(automatic: false) }
        }
        player.onRestoreUI = { [weak self] in
            DispatchQueue.main.async { self?.watchExpanded = true }
        }
    }

    // MARK: Điều hướng

    @Published var tab: Tab = .home
    @Published var homePath: [Route] = []
    @Published var subscriptionsPath: [Route] = []
    @Published var libraryPath: [Route] = []
    /// Trang xem video đang mở to (false = thu nhỏ thành trình phát mini).
    @Published var watchExpanded = false

    func selectTab(_ t: Tab) {
        if t == tab {
            // Bấm lại tab đang mở: về đầu.
            switch t {
            case .home: if homePath.isEmpty { refreshHomeInBackground() } else { homePath = [] }
            case .subscriptions: subscriptionsPath = []
            case .library: libraryPath = []
            case .shorts: break
            }
            return
        }
        tab = t
        if t == .shorts {
            player.pause()
            ensureShorts()
        }
        if t == .subscriptions { loadSubscriptions() }
        if t == .home && homeNeedsRefresh { refreshHomeInBackground() }
    }

    func push(_ route: Route) {
        watchExpanded = false
        switch tab {
        case .home, .shorts:
            tab = .home
            homePath.append(route)
        case .subscriptions:
            subscriptionsPath.append(route)
        case .library:
            libraryPath.append(route)
        }
    }

    func openChannel(_ id: String) {
        push(.channel(id))
    }

    func openSearch(_ text: String = "") {
        push(.search(text))
    }

    // MARK: Phát video

    /// Mở trang xem và phát video.
    func play(_ video: VideoItem, resume: Bool = true, expand: Bool = true) {
        if tab == .shorts { tab = .home }
        var start = resume ? store.position(of: video.id) : 0
        if video.durationSec > 0, start > Double(video.durationSec) - 10 { start = 0 }
        store.addToHistory(video, position: start)
        player.play(video, start: start)
        if expand { watchExpanded = true }
        homeNeedsRefresh = true
        loadWatch(video)
    }

    /// Mở video từ mã (link chia sẻ).
    func play(videoId: String) {
        play(VideoItem(id: videoId, title: ""))
    }

    func progress(of video: VideoItem) -> Double {
        guard let entry = store.history.first(where: { $0.video.id == video.id }) else { return 0 }
        let d = Double(entry.video.durationSec > 0 ? entry.video.durationSec : video.durationSec)
        guard d > 0 else { return 0 }
        return min(1, entry.position / d)
    }

    func closePlayer() {
        player.close()
        watchExpanded = false
        watch = nil
    }

    /// Phát video tiếp theo trong danh sách liên quan.
    func playNext(automatic: Bool) {
        if automatic && !store.settings.autoplay { return }
        guard let current = player.current, let info = watch, info.videoId == current.id else { return }
        let recent = Set(store.history.prefix(30).map(\.video.id))
        let next = info.related.first { $0.id != current.id && !$0.isLive && !recent.contains($0.id) }
            ?? info.related.first { $0.id != current.id }
        guard let next else { return }
        play(next, resume: false, expand: watchExpanded)
    }

    /// Xử lý link: tubesach://watch?v=…, tubesach://seek?t=…, link YouTube.
    func handle(_ url: URL) -> Bool {
        let s = url.absoluteString
        if url.scheme == "tubesach" {
            let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
            if url.host == "seek", let t = items.first(where: { $0.name == "t" })?.value, let sec = Double(t) {
                player.seek(to: sec)
                player.avPlayer.play()
                return true
            }
            if let v = items.first(where: { $0.name == "v" })?.value, v.count == 11 {
                play(videoId: v)
                return true
            }
            if let u = items.first(where: { $0.name == "url" })?.value, let id = Format.videoId(fromURL: u) {
                play(videoId: id)
                return true
            }
            return false
        }
        if let id = Format.youtubeVideoId(inText: s) {
            if let t = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "t" })?.value,
               id == player.current?.id, let sec = Double(t.replacingOccurrences(of: "s", with: "")) {
                player.seek(to: sec)
                return true
            }
            play(videoId: id)
            return true
        }
        let lower = s.lowercased()
        if lower.contains("youtube.com/channel/"), let id = url.pathComponents.last, id.hasPrefix("UC") {
            openChannel(id)
            return true
        }
        return false
    }

    // MARK: Trang chủ

    @Published private(set) var home = FeedState()
    @Published private(set) var homeTrending: [VideoItem] = []
    private var homeNeedsRefresh = false
    private var homeTask: Task<Void, Never>?

    func loadHomeIfNeeded() {
        if !home.loaded && !home.loading { refreshHomeInBackground() }
    }

    func refreshHomeInBackground() {
        homeTask?.cancel()
        homeTask = Task { await refreshHome() }
    }

    func refreshHome() async {
        home.loading = home.entries.isEmpty
        home.error = nil
        do {
            let page = try await youtube.home()
            if Task.isCancelled { return }
            home = FeedState(entries: page.entries, continuation: page.continuation, chips: page.chips, loaded: true)
            homeNeedsRefresh = false
            if page.entries.isEmpty && homeTrending.isEmpty {
                homeTrending = (try? await youtube.trendingVietnam())?.videos ?? []
            }
        } catch {
            if Task.isCancelled { return }
            home.loading = false
            home.loaded = true
            home.error = friendlyError(error)
        }
    }

    func selectChip(_ index: Int) {
        guard index < home.chips.count else { return }
        let chip = home.chips[index]
        guard index > 0, let token = chip.token else {
            refreshHomeInBackground()
            return
        }
        homeTask?.cancel()
        home.selectedChip = index
        home.loading = true
        home.entries = []
        home.error = nil
        homeTask = Task {
            do {
                let page = try await youtube.feedContinuation(token)
                if Task.isCancelled { return }
                home.entries = page.entries
                home.continuation = page.continuation
                home.loading = false
            } catch {
                if Task.isCancelled { return }
                home.loading = false
                home.error = friendlyError(error)
            }
        }
    }

    func loadMoreHome() {
        guard let token = home.continuation, !home.loadingMore, !home.loading else { return }
        home.loadingMore = true
        Task {
            let page = try? await youtube.feedContinuation(token)
            let keys = Set(home.entries.map(\.key))
            home.entries += (page?.entries ?? []).filter { !keys.contains($0.key) }
            home.continuation = page?.continuation
            home.loadingMore = false
        }
    }

    /// Lọc các video đã bấm "Không quan tâm" và (tuỳ chọn) ẩn Shorts.
    func visible(_ entries: [FeedEntry]) -> [FeedEntry] {
        let hidden = store.hidden
        let hideShorts = store.settings.hideShorts
        return entries.filter { e in
            switch e {
            case .video(let v): return !hidden.contains(v.id) && !(hideShorts && v.isShort)
            case .shortsShelf: return !hideShorts
            default: return true
            }
        }
    }

    // MARK: Trang xem

    @Published private(set) var watch: WatchInfo?
    @Published private(set) var watchLoading = false
    @Published private(set) var watchError: String?
    @Published private(set) var dislikes: Int64?
    @Published private(set) var comments = CommentsState()
    private var watchTask: Task<Void, Never>?

    func loadWatch(_ video: VideoItem) {
        watchTask?.cancel()
        if watch?.videoId != video.id { watch = nil }
        watchLoading = true
        watchError = nil
        dislikes = nil
        comments = CommentsState(videoId: video.id, loading: true)
        let id = video.id
        let wantVotes = store.settings.returnDislike
        let votesTask = Task<Dislikes.Votes?, Never> {
            guard wantVotes else { return nil }
            return await Dislikes.votes(videoId: id)
        }
        watchTask = Task {
            do {
                let info = try await youtube.watch(id)
                if Task.isCancelled { return }
                watch = info
                watchLoading = false
                // Bổ sung thông tin cho lịch sử (ảnh kênh, tên kênh…).
                if var v = player.current, v.id == id {
                    if v.title.isEmpty { v.title = info.title }
                    if v.channelId == nil { v.channelId = info.channel?.id }
                    if v.channelAvatar == nil { v.channelAvatar = info.channel?.avatar }
                    if v.uploader.isEmpty { v.uploader = info.channel?.name ?? "" }
                    if v.viewText == nil { v.viewText = info.shortViewText ?? info.viewText }
                    store.addToHistory(v, position: store.position(of: id))
                }
                if let token = info.commentsToken {
                    await loadComments(videoId: id, token: token)
                } else {
                    comments = CommentsState(videoId: id, error: "Video này đã tắt bình luận")
                }
            } catch {
                if Task.isCancelled { return }
                watchLoading = false
                watchError = friendlyError(error)
                comments = CommentsState(videoId: id)
            }
            let v = await votesTask.value
            if !Task.isCancelled, player.current?.id == id { dislikes = v?.dislikes }
        }
    }

    func retryWatch() {
        if let v = player.current { loadWatch(v) }
    }

    private func loadComments(videoId: String, token: String) async {
        do {
            let page = try await youtube.comments(token: token)
            guard comments.videoId == videoId else { return }
            comments = CommentsState(videoId: videoId, token: token, items: page.items, continuation: page.continuation)
        } catch {
            guard comments.videoId == videoId else { return }
            comments = CommentsState(videoId: videoId, error: "Không tải được bình luận")
        }
    }

    func loadMoreComments() {
        guard let token = comments.continuation, !comments.loadingMore else { return }
        let id = comments.videoId
        comments.loadingMore = true
        Task {
            let page = try? await youtube.comments(token: token)
            guard comments.videoId == id else { return }
            comments.items += page?.items ?? []
            comments.continuation = page?.continuation
            comments.loadingMore = false
        }
    }

    // MARK: Kênh đăng ký

    @Published private(set) var subscriptionFeed = FeedState()

    func loadSubscriptions(force: Bool = false) {
        if subscriptionFeed.loading { return }
        if subscriptionFeed.loaded && !force { return }
        Task { await refreshSubscriptions() }
    }

    func refreshSubscriptions() async {
        let subs = store.subscriptions
        guard !subs.isEmpty else {
            subscriptionFeed = FeedState(loaded: true)
            return
        }
        subscriptionFeed.loading = subscriptionFeed.entries.isEmpty
        let videos = await youtube.subscriptionFeed(subs)
        subscriptionFeed = FeedState(entries: videos.map { .video($0) }, loaded: true)
    }

    // MARK: Danh sách (thịnh hành…)

    @Published private(set) var lists: [ListKind: FeedState] = [:]

    func loadList(_ kind: ListKind) {
        guard kind == .trending || kind == .topMusic else { return }
        if lists[kind]?.loaded == true || lists[kind]?.loading == true { return }
        lists[kind] = FeedState(loading: true)
        Task {
            do {
                let page: FeedPage
                if kind == .trending {
                    page = try await youtube.trendingVietnam()
                } else {
                    page = try await youtube.topMusicVietnam()
                }
                lists[kind] = FeedState(entries: page.entries, continuation: page.continuation, loaded: true)
            } catch {
                lists[kind] = FeedState(error: friendlyError(error), loaded: true)
            }
        }
    }

    func reloadList(_ kind: ListKind) {
        lists[kind] = nil
        loadList(kind)
    }

    // MARK: Kênh

    @Published private(set) var channels: [String: ChannelState] = [:]

    func loadChannel(_ id: String) {
        if let st = channels[id], st.page != nil || (st.loading && st.error == nil) { return }
        channels[id] = ChannelState()
        Task {
            do {
                let page = try await youtube.channel(id)
                channels[id] = ChannelState(page: page, loading: false)
                loadChannelTab(id, tab: 0)
            } catch {
                channels[id] = ChannelState(loading: false, error: friendlyError(error))
            }
        }
    }

    func reloadChannel(_ id: String) {
        channels[id] = nil
        loadChannel(id)
    }

    func loadChannelTab(_ id: String, tab: Int) {
        guard var st = channels[id], let page = st.page else { return }
        st.tab = tab
        st.videos = FeedState(loading: true)
        channels[id] = st
        Task {
            let params = tab == 1 ? page.liveParams : page.videosParams
            let videos: FeedState
            do {
                let feed = try await youtube.channelVideos(id, params: params)
                videos = FeedState(entries: feed.entries, continuation: feed.continuation, loaded: true)
            } catch {
                videos = FeedState(error: friendlyError(error), loaded: true)
            }
            guard var cur = channels[id], cur.tab == tab else { return }
            cur.videos = videos
            channels[id] = cur
        }
    }

    func loadMoreChannel(_ id: String) {
        guard var st = channels[id], let token = st.videos.continuation, !st.videos.loadingMore else { return }
        st.videos.loadingMore = true
        channels[id] = st
        Task {
            let page = try? await youtube.feedContinuation(token)
            guard var cur = channels[id] else { return }
            cur.videos.entries += page?.entries ?? []
            cur.videos.continuation = page?.continuation
            cur.videos.loadingMore = false
            channels[id] = cur
        }
    }

    // MARK: Shorts

    @Published private(set) var shorts: [ShortItem] = []
    @Published var shortsIndex = 0
    @Published private(set) var shortsLoading = false
    private var shortsContinuation: String?
    private var shortsSeedParams: String?

    /// Mở tab Shorts bắt đầu từ một Short cụ thể (bấm từ kệ Shorts).
    func openShort(_ short: ShortItem, in shelf: [ShortItem]) {
        player.pause()
        watchExpanded = false
        let start = shelf.firstIndex(of: short) ?? 0
        shorts = Array(shelf[start...])
        shortsIndex = 0
        shortsSeedParams = short.sequenceParams ?? shelf.first(where: { $0.sequenceParams != nil })?.sequenceParams
        shortsContinuation = nil
        tab = .shorts
        loadMoreShorts()
    }

    func ensureShorts() {
        guard shorts.isEmpty, !shortsLoading else { return }
        shortsLoading = true
        Task {
            var seed: [ShortItem] = []
            for e in home.entries {
                if case .shortsShelf(_, let s) = e { seed += s }
            }
            if seed.isEmpty { seed = await youtube.shortsSeed() }
            shortsLoading = false
            var seen = Set<String>()
            shorts = seed.filter { seen.insert($0.id).inserted }
            shortsSeedParams = seed.first(where: { $0.sequenceParams != nil })?.sequenceParams
            loadMoreShorts()
        }
    }

    func loadMoreShorts() {
        guard !shortsLoading else { return }
        let params = shortsSeedParams
        let token = shortsContinuation
        guard params != nil || token != nil else { return }
        shortsLoading = true
        Task {
            let r = try? await youtube.shortsSequence(sequenceParams: token == nil ? params : nil, continuation: token)
            shortsLoading = false
            guard let r else { return }
            var seen = Set(shorts.map(\.id))
            for id in r.ids where seen.insert(id).inserted {
                shorts.append(ShortItem(id: id, title: ""))
            }
            shortsContinuation = r.continuation
            if r.continuation == nil { shortsSeedParams = nil }
        }
    }

    // MARK: Cập nhật

    @Published private(set) var updateVersion: String?
    @Published private(set) var updatePage: String?
    @Published var updateMessage: String?

    func checkUpdate(manual: Bool = false) {
        Task {
            let r = await YouTube.latestIOSRelease(repo: Services.updateRepo)
            if let r, Format.isNewer(r.version, than: Services.appVersion) {
                updateVersion = r.version
                updatePage = r.page
                if manual { updateMessage = "Đã có bản mới \(r.version)." }
            } else if manual {
                updateMessage = r == nil
                    ? "Không kiểm tra được bản mới. Hãy xem trang Releases trên GitHub."
                    : "Bạn đang dùng phiên bản mới nhất."
            }
        }
    }

    // MARK: Quyền riêng tư

    func resetRecommendations() {
        youtube.innerTube.resetVisitor()
        home = FeedState()
        homeTrending = []
        refreshHomeInBackground()
    }
}
