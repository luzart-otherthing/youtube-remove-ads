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

    private let network = NetworkMonitor()

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
        player.onNearEnd = { [weak self] in
            DispatchQueue.main.async { self?.prepareNext() }
        }
        player.onAutoplayFailed = { [weak self] video in
            DispatchQueue.main.async { self?.autoplayFailed(video) }
        }
        // Phiên đăng nhập Google (thử nghiệm) đã lưu.
        if let cookies = AccountStore.loadCookies() {
            let session = AccountStore.makeSession(cookies)
            if session.isValid {
                youtube.innerTube.account = session
                account = AccountStore.loadInfo() ?? AccountInfo(name: "Tài khoản Google")
            }
        }
        network.onReconnect = { [weak self] in self?.onReconnect() }
        network.start()
    }

    /// Có mạng lại: tải lại những gì đang lỗi (không làm mất chỗ đang xem).
    func onReconnect() {
        player.networkReconnected()
        if home.error != nil && !home.loading {
            if home.selectedChip == 0 { refreshHomeInBackground() } else { selectChip(home.selectedChip) }
        }
        if watchError != nil { retryWatch() }
        if subscriptionFeed.loaded && subscriptionFeed.entries.isEmpty && !store.subscriptions.isEmpty {
            loadSubscriptions(force: true)
        }
        if shorts.isEmpty && tab == .shorts { ensureShorts() }
        for (kind, st) in lists where st.error != nil { reloadList(kind) }
    }

    // MARK: Thông báo nhanh

    @Published var toast: String?
    private var toastTask: Task<Void, Never>?

    func showToast(_ text: String) {
        toast = text
        toastTask?.cancel()
        toastTask = Task {
            try? await Task.sleep(nanoseconds: 3_000_000_000)
            if !Task.isCancelled { toast = nil }
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

    /// Mở trang xem và phát video. `automatic` = tự phát tiếp khi hết video trước.
    func play(_ video: VideoItem, resume: Bool = true, expand: Bool = true, automatic: Bool = false) {
        if tab == .shorts { tab = .home }
        if !automatic {
            autoplayFailures = 0
            failedIds = []
        }
        queuedNext = nil
        var start = resume ? store.position(of: video.id) : 0
        if video.durationSec > 0, start > Double(video.durationSec) - 10 { start = 0 }
        store.addToHistory(video, position: start)
        player.play(video, start: start, automatic: automatic)
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

    /// Danh sách để chọn video tự phát tiếp (video liên quan của video gần nhất tải được).
    private var autoplaySource: [VideoItem] = []
    /// Video tiếp theo đã chọn và tải sẵn link (~30 giây trước khi hết video).
    private var queuedNext: VideoItem?
    private var autoplayFailures = 0
    private var failedIds: Set<String> = []

    private func nextCandidate() -> VideoItem? {
        guard let current = player.current else { return nil }
        let recent = Set(store.history.prefix(30).map(\.video.id))
        let pool = autoplaySource.filter { $0.id != current.id && !failedIds.contains($0.id) && !store.hidden.contains($0.id) }
        return pool.first { !$0.isLive && !recent.contains($0.id) } ?? pool.first
    }

    /// Còn ~30 giây: chọn video tiếp theo và tải sẵn link để chuyển ngay (cả khi tắt màn hình).
    private func prepareNext() {
        guard store.settings.autoplay, !player.loop, let next = nextCandidate() else { return }
        queuedNext = next
        player.prefetch(next)
    }

    /// Phát video tiếp theo trong danh sách liên quan.
    func playNext(automatic: Bool) {
        if automatic && !store.settings.autoplay { return }
        let next = (queuedNext.flatMap { q in failedIds.contains(q.id) ? nil : q }) ?? nextCandidate()
        guard let next else { return }
        play(next, resume: false, expand: watchExpanded, automatic: automatic)
    }

    /// Tự phát tiếp gặp video không xem được (riêng tư, bị chặn…): bỏ qua, tối đa 3 video liền.
    private func autoplayFailed(_ video: VideoItem) {
        failedIds.insert(video.id)
        autoplayFailures += 1
        guard autoplayFailures < 3 else { return }
        playNext(automatic: true)
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
            // Trang chủ vừa được làm mới / đổi chip trong lúc tải → bỏ kết quả cũ.
            // Lỗi mạng → giữ token để cuộn xuống lần nữa là thử lại.
            guard let page, home.continuation == token else {
                home.loadingMore = false
                return
            }
            var keys = Set(home.entries.map(\.key))
            home.entries += page.entries.filter { keys.insert($0.key).inserted }
            home.continuation = page.continuation
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
    @Published private(set) var relatedLoadingMore = false
    private var watchTask: Task<Void, Never>?

    /// Cuộn tới cuối danh sách video liên quan: tải thêm (trước đây dừng ở ~20 video).
    func loadMoreRelated() {
        guard let w = watch, let token = w.relatedContinuation, !relatedLoadingMore else { return }
        relatedLoadingMore = true
        let id = w.videoId
        Task {
            let page = try? await youtube.relatedMore(token)
            relatedLoadingMore = false
            guard let page, var cur = watch, cur.videoId == id, cur.relatedContinuation == token else { return }
            var seen = Set(cur.related.map(\.id))
            var more: [VideoItem] = []
            for e in page.entries {
                switch e {
                case .video(let v): if seen.insert(v.id).inserted { more.append(v) }
                case .shelf(_, let videos): more += videos.filter { seen.insert($0.id).inserted }
                default: break
                }
            }
            cur.related += more
            cur.relatedContinuation = page.continuation
            watch = cur
            if player.current?.id == id { autoplaySource = cur.related }
        }
    }

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
                if !info.related.isEmpty { autoplaySource = info.related }
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
            comments.loadingMore = false
            guard let page else { return }
            comments.items += page.items
            comments.continuation = page.continuation
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
        // Đã đăng nhập: lấy tab "Kênh đăng ký" của chính tài khoản; lỗi thì dùng RSS như khi chưa đăng nhập.
        if signedIn {
            subscriptionFeed.loading = subscriptionFeed.entries.isEmpty
            if let page = try? await youtube.accountSubscriptionFeed(), !page.entries.isEmpty {
                subscriptionFeed = FeedState(entries: page.entries, continuation: page.continuation, loaded: true)
                return
            }
        }
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
            cur.videos.loadingMore = false
            // Lỗi mạng hoặc đã đổi tab → giữ token để thử lại.
            if let page, cur.videos.continuation == token {
                var keys = Set(cur.videos.entries.map(\.key))
                cur.videos.entries += page.entries.filter { keys.insert($0.key).inserted }
                cur.videos.continuation = page.continuation
            }
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

    // MARK: Tài khoản Google (thử nghiệm)

    @Published private(set) var account: AccountInfo?
    @Published private(set) var commentPosting = false

    var signedIn: Bool { account != nil && youtube.signedIn }

    /// Trang đăng nhập báo đã có cookie YouTube.
    func loginFinished(cookies: [String: String]) {
        AccountStore.saveCookies(cookies)
        youtube.innerTube.account = AccountStore.makeSession(cookies)
        account = AccountInfo(name: "Tài khoản Google")
        home = FeedState()
        refreshHomeInBackground()
        Task {
            if let info = try? await youtube.accountInfo() {
                account = info
                AccountStore.saveInfo(info)
            }
            // Đưa các kênh đã đăng ký trong tài khoản vào danh sách trên máy.
            if let channels = try? await youtube.accountChannels() { store.addSubscriptions(channels) }
            subscriptionFeed = FeedState()
            if tab == .subscriptions { loadSubscriptions(force: true) }
        }
    }

    func logout() {
        AccountStore.clear()
        youtube.innerTube.account = nil
        account = nil
        home = FeedState()
        subscriptionFeed = FeedState()
        refreshHomeInBackground()
        showToast("Đã đăng xuất. Video đã xem, đã thích và kênh đăng ký trên máy vẫn được giữ.")
    }

    /// Gửi thay đổi lên tài khoản (nếu đã đăng nhập). Lỗi thì báo nhẹ, thay đổi trên máy vẫn giữ.
    private func syncToAccount(_ what: String, _ block: @escaping () async throws -> Void) {
        guard signedIn else { return }
        Task {
            do { try await block() } catch { showToast("Không đồng bộ được \(what) lên tài khoản Google") }
        }
    }

    func toggleSubscription(_ channel: ChannelRef) {
        store.toggleSubscription(channel)
        let subscribed = store.isSubscribed(channel.id)
        let yt = youtube
        syncToAccount("kênh đăng ký") { try await yt.setSubscribed(channel.id, subscribed) }
    }

    func toggleLike(_ video: VideoItem) {
        store.toggleLike(video)
        let liked = store.isLiked(video.id)
        let yt = youtube
        syncToAccount("lượt thích") { try await yt.rate(video.id, liked ? "like" : "removelike") }
    }

    func toggleDislike(_ video: VideoItem) {
        store.toggleDislike(video)
        let disliked = store.isDisliked(video.id)
        let yt = youtube
        syncToAccount("lượt không thích") { try await yt.rate(video.id, disliked ? "dislike" : "removelike") }
    }

    /// Đăng bình luận cho video (đang xem hoặc Short đang mở). Trả về true nếu thành công.
    func postComment(_ text: String, shorts: Bool) async -> Bool {
        let videoId = shorts ? shortComments.videoId : (comments.videoId.isEmpty ? (player.current?.id ?? "") : comments.videoId)
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !videoId.isEmpty, !body.isEmpty, !commentPosting else { return false }
        commentPosting = true
        defer { commentPosting = false }
        do {
            try await youtube.postComment(videoId: videoId, text: body)
        } catch {
            showToast(friendlyError(error))
            return false
        }
        let me = account
        let mine = CommentItem(
            id: "mine-\(Date().timeIntervalSince1970)",
            author: me?.handle ?? me?.name ?? "",
            avatar: me?.photo,
            text: body,
            time: "Vừa xong",
            likes: nil,
            replies: 0,
            pinned: false
        )
        if shorts {
            if shortComments.videoId == videoId { shortComments.items.insert(mine, at: 0) }
        } else if comments.videoId == videoId {
            comments.items.insert(mine, at: 0)
        }
        return true
    }

    // MARK: Bình luận của Shorts

    @Published private(set) var shortComments = CommentsState()

    func loadShortComments(_ videoId: String) {
        if shortComments.videoId == videoId && (shortComments.loading || !shortComments.items.isEmpty) { return }
        shortComments = CommentsState(videoId: videoId, loading: true)
        Task {
            do {
                let info = try await youtube.watch(videoId)
                guard let token = info.commentsToken else {
                    if shortComments.videoId == videoId {
                        shortComments = CommentsState(videoId: videoId, error: "Video này đã tắt bình luận")
                    }
                    return
                }
                let page = try await youtube.comments(token: token)
                guard shortComments.videoId == videoId else { return }
                shortComments = CommentsState(videoId: videoId, token: token, items: page.items, continuation: page.continuation)
            } catch {
                guard shortComments.videoId == videoId else { return }
                shortComments = CommentsState(videoId: videoId, error: "Không tải được bình luận")
            }
        }
    }

    func loadMoreShortComments() {
        guard let token = shortComments.continuation, !shortComments.loadingMore else { return }
        let id = shortComments.videoId
        shortComments.loadingMore = true
        Task {
            let page = try? await youtube.comments(token: token)
            guard shortComments.videoId == id else { return }
            shortComments.loadingMore = false
            guard let page else { return }
            shortComments.items += page.items
            shortComments.continuation = page.continuation
        }
    }

    // MARK: Mở sẵn một màn hình khi khởi động

    /// Đọc tham số khởi động (ví dụ `-openVideo dQw4w9WgXcQ`), dùng để chụp màn hình tự động trên máy ảo.
    func handleLaunchArguments() {
        let d = UserDefaults.standard
        switch d.string(forKey: "openTab") {
        case "shorts": selectTab(.shorts)
        case "subscriptions": selectTab(.subscriptions)
        case "library": selectTab(.library)
        default: break
        }
        if let q = d.string(forKey: "openSearch"), !q.isEmpty { openSearch(q) }
        if let c = d.string(forKey: "openChannel"), !c.isEmpty { openChannel(c) }
        if let v = d.string(forKey: "openVideo"), v.count == 11 { play(videoId: v) }
        if d.bool(forKey: "openSettings") {
            selectTab(.library)
            push(.settings)
        }
    }

    // MARK: Quyền riêng tư

    func resetRecommendations() {
        // Đã đăng nhập thì đề xuất theo tài khoản; vẫn xoá mã khách ẩn danh như khi chưa đăng nhập.
        youtube.innerTube.resetVisitor()
        home = FeedState()
        homeTrending = []
        refreshHomeInBackground()
    }
}
