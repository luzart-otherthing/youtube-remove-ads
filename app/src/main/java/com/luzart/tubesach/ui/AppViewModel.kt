package com.luzart.tubesach.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luzart.tubesach.App
import com.luzart.tubesach.BuildConfig
import com.luzart.tubesach.data.ChannelPage
import com.luzart.tubesach.data.Chip
import com.luzart.tubesach.data.CommentItem
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.ShortItem
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.update.UpdateChecker
import com.luzart.tubesach.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

enum class Tab(val label: String) { HOME("Trang chủ"), SHORTS("Shorts"), SUBSCRIPTIONS("Kênh đăng ký"), YOU("Bạn") }

enum class ListKind(val title: String) {
    HISTORY("Video đã xem"),
    LIKED("Video đã thích"),
    WATCH_LATER("Xem sau"),
    TRENDING("Thịnh hành tại Việt Nam"),
    TOP_MUSIC("Nhạc hàng đầu Việt Nam"),
}

sealed interface Screen {
    data object SearchInput : Screen
    data class Results(val query: String) : Screen
    data class Channel(val id: String) : Screen
    data object Settings : Screen
    data class VideoList(val kind: ListKind) : Screen
}

data class FeedState(
    val entries: List<FeedEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val continuation: String? = null,
    val chips: List<Chip> = emptyList(),
    val selectedChip: Int = 0,
    val loaded: Boolean = false,
)

data class ChannelState(
    val page: ChannelPage? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val tab: Int = 0,
    val videos: FeedState = FeedState(loading = true),
)

data class CommentsState(
    val videoId: String = "",
    val items: List<CommentItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val session: YouTubeRepository.CommentsSession? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val store = (app as App).store
    private val http = (app as App).http

    // ---------------------------------------------------------------- Điều hướng

    var tab by mutableStateOf(Tab.HOME)
        private set
    val stack = mutableStateListOf<Screen>()
    val top: Screen? get() = stack.lastOrNull()

    /** Trang xem video đang mở to (false = thu nhỏ thành trình phát mini). */
    var watchExpanded by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)

    fun selectTab(t: Tab) {
        if (t == tab && stack.isEmpty()) {
            if (t == Tab.HOME) refreshHome()
            return
        }
        stack.clear()
        tab = t
        watchExpanded = false
        if (t == Tab.SHORTS) {
            ensureShorts()
        } else if (PlaybackController.nowPlaying.value?.isShort == true) {
            PlaybackController.close()
        }
        if (t == Tab.SUBSCRIPTIONS) loadSubscriptions()
        if (t == Tab.HOME && homeNeedsRefresh) refreshHome()
    }

    fun push(screen: Screen) {
        watchExpanded = false
        if (top != screen) stack.add(screen)
    }

    /** Xử lý nút Quay lại. Trả về false nếu không còn gì để quay lại (thoát app). */
    fun back(): Boolean {
        when {
            fullscreen -> fullscreen = false
            watchExpanded -> watchExpanded = false
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            tab != Tab.HOME -> selectTab(Tab.HOME)
            else -> return false
        }
        return true
    }

    fun openVideo(video: VideoItem, resume: Boolean = true) {
        if (tab == Tab.SHORTS) tab = Tab.HOME
        PlaybackController.play(video, resume)
        watchExpanded = true
        homeNeedsRefresh = true
        loadCommentsPreview(video)
    }

    fun expandPlayer() {
        if (PlaybackController.nowPlaying.value != null) watchExpanded = true
    }

    fun openChannel(id: String) {
        push(Screen.Channel(id))
        loadChannel(id)
    }

    // ---------------------------------------------------------------- Trang chủ

    var home by mutableStateOf(FeedState())
        private set
    var homeTrending by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    private var homeNeedsRefresh = false
    private var homeJob: Job? = null

    fun loadHome(force: Boolean = false) {
        if (home.loaded && !force) return
        homeJob?.cancel()
        home = home.copy(loading = !home.loaded || home.entries.isEmpty(), refreshing = home.loaded, error = null)
        homeJob = viewModelScope.launch {
            try {
                val page = YouTubeRepository.home()
                home = FeedState(entries = page.entries, continuation = page.continuation, chips = page.chips, loaded = true)
                homeNeedsRefresh = false
                if (page.entries.isEmpty() && homeTrending.isEmpty()) {
                    homeTrending = runCatching { YouTubeRepository.trendingVietnam() }.getOrNull()
                        ?.entries?.filterIsInstance<FeedEntry.Video>()?.map { it.video }.orEmpty()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                home = home.copy(loading = false, refreshing = false, error = errorText(e), loaded = true)
            }
        }
    }

    fun refreshHome() = loadHome(force = true)

    fun selectChip(index: Int) {
        val chip = home.chips.getOrNull(index) ?: return
        val token = chip.token
        if (index == 0 || token == null) {
            refreshHome()
            return
        }
        homeJob?.cancel()
        home = home.copy(selectedChip = index, loading = true, entries = emptyList(), error = null)
        homeJob = viewModelScope.launch {
            try {
                val page = YouTubeRepository.feedContinuation(token)
                home = home.copy(entries = page.entries, continuation = page.continuation, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                home = home.copy(loading = false, error = errorText(e))
            }
        }
    }

    fun loadMoreHome() {
        val token = home.continuation ?: return
        if (home.loadingMore || home.loading) return
        home = home.copy(loadingMore = true)
        viewModelScope.launch {
            val page = runCatching { YouTubeRepository.feedContinuation(token) }.getOrNull()
            home = home.copy(
                entries = home.entries + page?.entries.orEmpty().filterNot { e -> home.entries.any { it.key == e.key } },
                continuation = page?.continuation,
                loadingMore = false,
            )
        }
    }

    // ---------------------------------------------------------------- Tìm kiếm

    var searchText by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestJob: Job? = null

    fun onSearchTextChange(text: String) {
        searchText = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(200)
            suggestions = YouTubeRepository.suggestions(text)
        }
    }

    fun openSearchInput(prefill: String = "") {
        searchText = prefill
        suggestions = emptyList()
        if (prefill.isNotEmpty()) onSearchTextChange(prefill)
        push(Screen.SearchInput)
    }

    val results = mutableStateMapOf<String, FeedState>()
    val resultFilter = mutableStateMapOf<String, YouTubeRepository.SearchFilter>()

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        store.addRecentSearch(q)
        searchText = q
        stack.removeAll { it is Screen.SearchInput || (it is Screen.Results && it.query == q) }
        push(Screen.Results(q))
        runSearch(q, YouTubeRepository.SearchFilter.ALL)
        homeNeedsRefresh = true
    }

    fun runSearch(q: String, filter: YouTubeRepository.SearchFilter) {
        resultFilter[q] = filter
        results[q] = FeedState(loading = true)
        viewModelScope.launch {
            results[q] = try {
                val page = YouTubeRepository.search(q, filter)
                FeedState(entries = page.entries, continuation = page.continuation, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FeedState(error = errorText(e), loaded = true)
            }
        }
    }

    fun loadMoreResults(q: String) {
        val state = results[q] ?: return
        val token = state.continuation ?: return
        if (state.loadingMore || state.loading) return
        results[q] = state.copy(loadingMore = true)
        viewModelScope.launch {
            val page = runCatching { YouTubeRepository.searchMore(token) }.getOrNull()
            val cur = results[q] ?: return@launch
            results[q] = cur.copy(
                entries = cur.entries + page?.entries.orEmpty().filterNot { e -> cur.entries.any { it.key == e.key } },
                continuation = page?.continuation,
                loadingMore = false,
            )
        }
    }

    // ---------------------------------------------------------------- Kênh

    val channels = mutableStateMapOf<String, ChannelState>()

    fun loadChannel(id: String) {
        if (channels[id]?.page != null) return
        channels[id] = ChannelState()
        viewModelScope.launch {
            try {
                val page = YouTubeRepository.channel(id)
                channels[id] = ChannelState(page = page, loading = false)
                loadChannelTab(id, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                channels[id] = ChannelState(loading = false, error = errorText(e))
            }
        }
    }

    fun loadChannelTab(id: String, tab: Int) {
        val st = channels[id] ?: return
        val page = st.page ?: return
        channels[id] = st.copy(tab = tab, videos = FeedState(loading = true))
        viewModelScope.launch {
            val params = if (tab == 1) page.liveParams else page.videosParams
            val feed = runCatching { YouTubeRepository.channelVideos(id, params) }
            val cur = channels[id] ?: return@launch
            channels[id] = cur.copy(
                videos = feed.fold(
                    { FeedState(entries = it.entries, continuation = it.continuation, loaded = true) },
                    { FeedState(error = errorText(it), loaded = true) },
                ),
            )
        }
    }

    fun loadMoreChannel(id: String) {
        val st = channels[id] ?: return
        val token = st.videos.continuation ?: return
        if (st.videos.loadingMore) return
        channels[id] = st.copy(videos = st.videos.copy(loadingMore = true))
        viewModelScope.launch {
            val page = runCatching { YouTubeRepository.feedContinuation(token) }.getOrNull()
            val cur = channels[id] ?: return@launch
            channels[id] = cur.copy(
                videos = cur.videos.copy(
                    entries = cur.videos.entries + page?.entries.orEmpty(),
                    continuation = page?.continuation,
                    loadingMore = false,
                ),
            )
        }
    }

    // ---------------------------------------------------------------- Kênh đăng ký

    var subscriptionFeed by mutableStateOf(FeedState())
        private set

    fun loadSubscriptions(force: Boolean = false) {
        val subs = store.subscriptions.value
        if (subs.isEmpty()) {
            subscriptionFeed = FeedState(loaded = true)
            return
        }
        if (subscriptionFeed.loaded && !force && !subscriptionFeed.loading) {
            subscriptionFeed = subscriptionFeed.copy(refreshing = true)
        } else {
            subscriptionFeed = subscriptionFeed.copy(loading = true)
        }
        viewModelScope.launch {
            val videos = YouTubeRepository.subscriptionFeed(subs)
            subscriptionFeed = FeedState(entries = videos.map { FeedEntry.Video(it) }, loaded = true)
        }
    }

    // ---------------------------------------------------------------- Danh sách (thịnh hành…)

    val lists = mutableStateMapOf<ListKind, FeedState>()

    fun loadList(kind: ListKind) {
        if (kind != ListKind.TRENDING && kind != ListKind.TOP_MUSIC) return
        if (lists[kind]?.loaded == true) return
        lists[kind] = FeedState(loading = true)
        viewModelScope.launch {
            lists[kind] = try {
                val page = if (kind == ListKind.TRENDING) YouTubeRepository.trendingVietnam() else YouTubeRepository.topMusicVietnam()
                FeedState(entries = page.entries, continuation = page.continuation, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FeedState(error = errorText(e), loaded = true)
            }
        }
    }

    // ---------------------------------------------------------------- Shorts

    val shorts = mutableStateListOf<String>()
    private var shortsContinuation: String? = null
    private var shortsSeedParams: String? = null
    var shortsLoading by mutableStateOf(false)
        private set
    var shortsStart by mutableStateOf(0)

    /** Mở tab Shorts bắt đầu từ một Short cụ thể (bấm từ kệ Shorts). */
    fun openShort(short: ShortItem) {
        shorts.clear()
        shorts.add(short.id)
        shortsSeedParams = short.sequenceParams
        shortsContinuation = null
        shortsStart = 0
        stack.clear()
        tab = Tab.SHORTS
        watchExpanded = false
        loadMoreShorts()
    }

    private fun ensureShorts() {
        if (shorts.isNotEmpty() || shortsLoading) return
        shortsLoading = true
        viewModelScope.launch {
            val fromHome = home.entries.filterIsInstance<FeedEntry.ShortsShelf>().flatMap { it.shorts }
            val seed = fromHome.ifEmpty { YouTubeRepository.shortsSeed() }
            shortsLoading = false
            if (seed.isEmpty()) return@launch
            shorts.addAll(seed.map { it.id }.distinct())
            shortsSeedParams = seed.firstOrNull { it.sequenceParams != null }?.sequenceParams
            loadMoreShorts()
        }
    }

    fun loadMoreShorts() {
        if (shortsLoading) return
        val params = shortsSeedParams
        val token = shortsContinuation
        if (params == null && token == null) return
        shortsLoading = true
        viewModelScope.launch {
            val r = runCatching { YouTubeRepository.shortsSequence(if (token == null) params else null, token) }.getOrNull()
            shortsLoading = false
            if (r != null) {
                r.first.filterNot { it in shorts }.forEach { shorts.add(it) }
                shortsContinuation = r.second
                if (r.second == null) shortsSeedParams = null
            }
        }
    }

    // ---------------------------------------------------------------- Bình luận

    var comments by mutableStateOf(CommentsState())
        private set

    fun loadCommentsPreview(video: VideoItem) {
        if (comments.videoId == video.id && (comments.items.isNotEmpty() || comments.loading)) return
        comments = CommentsState(videoId = video.id, loading = true)
        viewModelScope.launch {
            comments = try {
                val (items, session) = YouTubeRepository.comments(video.url)
                if (comments.videoId != video.id) return@launch
                CommentsState(video.id, items, session = session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CommentsState(video.id, error = "Không tải được bình luận")
            }
        }
    }

    fun loadMoreComments() {
        val st = comments
        val session = st.session ?: return
        if (st.loadingMore || !session.hasMore) return
        comments = st.copy(loadingMore = true)
        viewModelScope.launch {
            val more = runCatching { YouTubeRepository.moreComments(session) }.getOrDefault(emptyList())
            if (comments.videoId == st.videoId) comments = comments.copy(items = comments.items + more, loadingMore = false)
        }
    }

    // ---------------------------------------------------------------- Cập nhật

    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)

    fun checkUpdate(manual: Boolean = false) {
        if (checkingUpdate) return
        checkingUpdate = true
        viewModelScope.launch {
            val result = UpdateChecker.check(http, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME, UpdateChecker.PHONE_PREFIX)
            store.lastUpdateCheck = System.currentTimeMillis()
            update = result
            checkingUpdate = false
            if (manual) updateMessage = if (result == null) "Bạn đang dùng phiên bản mới nhất." else null
        }
    }

    fun resetRecommendations() {
        YouTubeRepository.innerTube.resetVisitor()
        home = FeedState()
        loadHome(force = true)
    }

    private fun errorText(e: Throwable) = when (e) {
        is IOException -> "Không có kết nối mạng"
        else -> "Đã xảy ra lỗi"
    }

    /** Lọc các video người dùng đã bấm "Không quan tâm" và (tuỳ chọn) ẩn Shorts. */
    fun visible(entries: List<FeedEntry>): List<FeedEntry> {
        val hidden = store.hidden.value
        val hideShorts = store.settings.value.hideShorts
        return entries.filter {
            when (it) {
                is FeedEntry.Video -> it.video.id !in hidden && !(hideShorts && it.video.isShort)
                is FeedEntry.ShortsShelf -> !hideShorts
                else -> true
            }
        }
    }

    fun asPage(state: FeedState) = FeedPage(state.entries, state.continuation, state.chips)

    // ---------------------------------------------------------------- Chỉ dùng cho test giao diện

    @androidx.annotation.VisibleForTesting
    internal fun previewHome(state: FeedState, trending: List<VideoItem> = emptyList()) {
        home = state
        homeTrending = trending
    }

    @androidx.annotation.VisibleForTesting
    internal fun previewComments(state: CommentsState) {
        comments = state
    }

    @androidx.annotation.VisibleForTesting
    internal fun previewTab(t: Tab) {
        tab = t
    }
}
