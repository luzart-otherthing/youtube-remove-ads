package com.luzart.tubesach.tv

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luzart.tubesach.data.ChannelPage
import com.luzart.tubesach.data.Chip
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.ShortItem
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.tv.update.TvUpdater
import com.luzart.tubesach.tv.update.UpdateState
import com.luzart.tubesach.update.UpdateChecker
import com.luzart.tubesach.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** Các mục trên thanh bên trái. */
enum class Section(val label: String) {
    SEARCH("Tìm kiếm"),
    HOME("Trang chủ"),
    SUBSCRIPTIONS("Kênh đăng ký"),
    LIBRARY("Thư viện"),
    SETTINGS("Cài đặt"),
}

enum class LibraryKind(val title: String) {
    HISTORY("Video đã xem"),
    WATCH_LATER("Xem sau"),
    LIKED("Video đã thích"),
}

/** Bảng chọn mở ra từ trình phát. */
enum class PlayerPanel(val title: String) {
    QUALITY("Chất lượng"),
    CAPTIONS("Phụ đề"),
    SPEED("Tốc độ phát"),
    SLEEP("Hẹn giờ tắt"),
    INFO("Thông tin video"),
}

data class FeedState(
    val entries: List<FeedEntry> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
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

/** Kết quả khi bấm nút Quay lại. */
enum class BackResult {
    /** Đã xử lý (đóng bảng chọn, ẩn thanh điều khiển, đóng video, quay lại trang trước…). */
    HANDLED,

    /** Chưa xử lý: giao diện tự quyết định (đưa ô sáng về thanh bên trái / về Trang chủ / thoát). */
    NOT_HANDLED,
}

class TvViewModel(app: Application) : AndroidViewModel(app) {

    val store = (app as TvApp).store
    private val http = (app as TvApp).http

    // ---------------------------------------------------------------- Điều hướng

    var section by mutableStateOf(Section.HOME)
        private set

    /** Các trang kênh mở chồng lên mục hiện tại. */
    val channelStack = mutableStateListOf<String>()

    var playerOpen by mutableStateOf(false)
        private set

    /** Thanh điều khiển của trình phát đang hiện. */
    var controlsVisible by mutableStateOf(false)
    var panel by mutableStateOf<PlayerPanel?>(null)

    fun selectSection(s: Section) {
        channelStack.clear()
        section = s
        when (s) {
            // Chỉ làm mới khi đã lâu (≥ 10 phút) để danh sách không bị thay đột ngột lúc đang chọn.
            Section.HOME -> if (!home.loaded || (homeNeedsRefresh && System.currentTimeMillis() - homeLoadedAt > 10 * 60_000L)) {
                loadHome(force = true)
            }
            Section.SUBSCRIPTIONS -> loadSubscriptions()
            else -> Unit
        }
    }

    fun openVideo(video: VideoItem, resume: Boolean = true) {
        PlaybackController.play(video, resume)
        playerOpen = true
        controlsVisible = false
        panel = null
        homeNeedsRefresh = true
    }

    /** Shorts trên TV phát như video thường (màn hình ngang, có điều khiển đầy đủ). */
    fun openShort(short: ShortItem) = openVideo(
        VideoItem.ofId(short.id, title = short.title, thumbnail = short.thumbnail).copy(isShort = true),
        resume = false,
    )

    fun closePlayer() {
        playerOpen = false
        controlsVisible = false
        panel = null
        PlaybackController.close()
    }

    fun openChannel(id: String) {
        if (playerOpen) closePlayer()
        if (channelStack.lastOrNull() != id) channelStack.add(id)
        loadChannel(id)
    }

    /**
     * Nút Quay lại, theo thứ tự: đóng bảng chọn → ẩn thanh điều khiển → đóng video → đóng trang kênh.
     * Phần còn lại (về thanh bên trái, về Trang chủ, thoát app) do giao diện xử lý.
     */
    fun onBack(): BackResult {
        when {
            playerOpen && panel != null -> panel = null
            playerOpen && controlsVisible -> controlsVisible = false
            playerOpen -> closePlayer()
            channelStack.isNotEmpty() -> channelStack.removeAt(channelStack.lastIndex)
            else -> return BackResult.NOT_HANDLED
        }
        return BackResult.HANDLED
    }

    // ---------------------------------------------------------------- Trang chủ

    var home by mutableStateOf(FeedState())
        private set
    var homeTrending by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    private var homeNeedsRefresh = false
    private var homeLoadedAt = 0L
    private var homeJob: Job? = null

    fun loadHome(force: Boolean = false) {
        if ((home.loaded || home.loading) && !force) return
        homeJob?.cancel()
        homeMoreJob?.cancel()
        home = home.copy(loading = home.entries.isEmpty(), error = null)
        homeJob = viewModelScope.launch {
            try {
                val page = YouTubeRepository.home()
                home = FeedState(entries = page.entries, continuation = page.continuation, chips = page.chips, loaded = true)
                homeNeedsRefresh = false
                homeLoadedAt = System.currentTimeMillis()
                if (page.entries.isEmpty() && homeTrending.isEmpty()) {
                    homeTrending = runCatching { YouTubeRepository.trendingVietnam() }.getOrNull()
                        ?.entries?.filterIsInstance<FeedEntry.Video>()?.map { it.video }.orEmpty()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                home = home.copy(loading = false, error = errorText(e), loaded = true)
            }
        }
    }

    fun selectChip(index: Int) {
        val chip = home.chips.getOrNull(index) ?: return
        val token = chip.token
        if (index == 0 || token == null) {
            loadHome(force = true)
            return
        }
        homeJob?.cancel()
        homeMoreJob?.cancel()
        home = home.copy(selectedChip = index, loading = true, loadingMore = false, entries = emptyList(), continuation = null, error = null)
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

    private var homeMoreJob: Job? = null

    fun loadMoreHome() {
        val token = home.continuation ?: return
        if (home.loadingMore || home.loading) return
        home = home.copy(loadingMore = true)
        homeMoreJob = viewModelScope.launch {
            val page = runCatching { YouTubeRepository.feedContinuation(token) }.getOrNull()
            // Trang chủ đã được tải lại / đổi chip trong lúc chờ → bỏ kết quả cũ.
            if (home.continuation != token) return@launch
            home = if (page == null) {
                home.copy(loadingMore = false) // lỗi mạng: giữ token để lần cuộn sau thử lại
            } else {
                home.copy(
                    entries = home.entries + page.entries.filterNot { e -> home.entries.any { it.key == e.key } },
                    continuation = page.continuation,
                    loadingMore = false,
                )
            }
        }
    }

    // ---------------------------------------------------------------- Tìm kiếm

    var searchText by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var resultsQuery by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf(FeedState())
        private set
    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    fun onSearchTextChange(text: String) {
        searchText = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = runCatching { YouTubeRepository.suggestions(text) }.getOrDefault(emptyList())
        }
    }

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        if (section != Section.SEARCH) selectSection(Section.SEARCH)
        channelStack.clear()
        store.addRecentSearch(q)
        searchText = q
        suggestions = emptyList()
        suggestJob?.cancel()
        resultsQuery = q
        results = FeedState(loading = true)
        homeNeedsRefresh = true
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            results = try {
                val page = YouTubeRepository.search(q)
                FeedState(entries = page.entries, continuation = page.continuation, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                FeedState(error = errorText(e), loaded = true)
            }
        }
    }

    fun loadMoreResults() {
        val state = results
        val token = state.continuation ?: return
        if (state.loadingMore || state.loading) return
        val q = resultsQuery
        results = state.copy(loadingMore = true)
        viewModelScope.launch {
            val page = runCatching { YouTubeRepository.searchMore(token) }.getOrNull()
            if (resultsQuery != q || results.continuation != token) return@launch
            val cur = results
            results = if (page == null) {
                cur.copy(loadingMore = false) // lỗi mạng: giữ token để thử lại
            } else {
                cur.copy(
                    entries = cur.entries + page.entries.filterNot { e -> cur.entries.any { it.key == e.key } },
                    continuation = page.continuation,
                    loadingMore = false,
                )
            }
        }
    }

    // ---------------------------------------------------------------- Kênh

    val channels = mutableStateMapOf<String, ChannelState>()

    fun loadChannel(id: String, force: Boolean = false) {
        if (!force && channels[id]?.page != null) return
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
    private var subsLoadedFor: List<String> = emptyList()
    private var subsLoadedAt = 0L

    /** Tải video mới của các kênh đã đăng ký. Kết quả được dùng lại 10 phút (đỡ tải lại trên TV yếu). */
    fun loadSubscriptions(force: Boolean = false) {
        val subs = store.subscriptions.value
        if (subs.isEmpty()) {
            subscriptionFeed = FeedState(loaded = true)
            return
        }
        val ids = subs.map { it.id }
        val fresh = subscriptionFeed.loaded && subscriptionFeed.error == null && ids == subsLoadedFor &&
            System.currentTimeMillis() - subsLoadedAt < 10 * 60_000L
        if ((fresh && !force) || subscriptionFeed.loading) return
        subscriptionFeed = subscriptionFeed.copy(loading = true, error = null)
        viewModelScope.launch {
            val videos = runCatching { YouTubeRepository.subscriptionFeed(subs) }
            subscriptionFeed = videos.fold(
                { FeedState(entries = it.map { v -> FeedEntry.Video(v) }, loaded = true) },
                { FeedState(entries = subscriptionFeed.entries, error = errorText(it), loaded = true) },
            )
            if (videos.isSuccess) {
                subsLoadedFor = ids
                subsLoadedAt = System.currentTimeMillis()
            }
        }
    }

    // ---------------------------------------------------------------- Thư viện

    var libraryKind by mutableStateOf(LibraryKind.HISTORY)

    // ---------------------------------------------------------------- Cập nhật

    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)

    fun checkUpdate(manual: Boolean = false) {
        if (checkingUpdate) return
        checkingUpdate = true
        viewModelScope.launch {
            val result = UpdateChecker.check(http, BuildConfig.UPDATE_REPO, BuildConfig.VERSION_NAME, UpdateChecker.TV_PREFIX)
            store.lastUpdateCheck = System.currentTimeMillis()
            update = result
            checkingUpdate = false
            if (manual) updateMessage = if (result == null) "Bạn đang dùng phiên bản mới nhất." else null
        }
    }

    fun downloadUpdate() {
        val info = update ?: return
        if (updateState is UpdateState.Downloading) return
        viewModelScope.launch {
            TvUpdater.download(getApplication(), http, info).collect { updateState = it }
        }
    }

    fun installUpdate() {
        val ready = updateState as? UpdateState.Ready ?: return
        // Giữ nguyên file đã tải (trạng thái Ready): cấp quyền xong bấm "Cài đặt" lại là cài ngay, không tải lại.
        updateMessage = if (TvUpdater.install(getApplication(), ready.file)) {
            null
        } else {
            "Hãy cho phép Tube Sạch TV cài ứng dụng (Cài đặt → Ứng dụng → Quyền truy cập đặc biệt → Cài ứng dụng không rõ nguồn gốc), rồi bấm Cài đặt lại."
        }
    }

    // ---------------------------------------------------------------- Khác

    fun resetRecommendations() {
        YouTubeRepository.innerTube.resetVisitor()
        home = FeedState()
        homeTrending = emptyList()
        loadHome(force = true)
    }

    private fun errorText(e: Throwable) = when (e) {
        is IOException -> "Không có kết nối mạng"
        else -> "Đã xảy ra lỗi"
    }

    /** Lọc video đã bấm "Không quan tâm" và (tuỳ chọn) ẩn Shorts. */
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

    // ---------------------------------------------------------------- Chỉ dùng cho test

    @androidx.annotation.VisibleForTesting
    fun previewHome(state: FeedState, trending: List<VideoItem> = emptyList()) {
        homeJob?.cancel()
        home = state
        homeTrending = trending
    }

    @androidx.annotation.VisibleForTesting
    fun previewResults(query: String, state: FeedState) {
        section = Section.SEARCH
        searchText = query
        resultsQuery = query
        results = state
    }

    @androidx.annotation.VisibleForTesting
    fun previewSubscriptions(state: FeedState) {
        subscriptionFeed = state
        subsLoadedFor = store.subscriptions.value.map { it.id }
        subsLoadedAt = System.currentTimeMillis()
    }

    @androidx.annotation.VisibleForTesting
    fun previewSection(s: Section) {
        section = s
    }

    @androidx.annotation.VisibleForTesting
    fun previewPlayer(open: Boolean, controls: Boolean = false, panel: PlayerPanel? = null) {
        playerOpen = open
        controlsVisible = controls
        this.panel = panel
    }

    @androidx.annotation.VisibleForTesting
    fun previewUpdate(info: UpdateInfo?, state: UpdateState = UpdateState.Idle) {
        update = info
        updateState = state
    }
}
