package com.luzart.tubesach.car

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luzart.tubesach.App
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** Các mục bên trái màn hình ô tô. */
enum class CarSection(val label: String) {
    HOME("Trang chủ"),
    SEARCH("Tìm kiếm"),
    SUBSCRIPTIONS("Kênh đăng ký"),
    HISTORY("Đã xem"),
    LIBRARY("Thư viện"),
}

/** Danh sách con trong mục "Thư viện". */
enum class CarShelf(val label: String) {
    WATCH_LATER("Xem sau"),
    LIKED("Đã thích"),
    TRENDING("Thịnh hành"),
    TOP_MUSIC("Nhạc hàng đầu"),
}

data class CarFeed(
    val videos: List<VideoItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val continuation: String? = null,
    val loaded: Boolean = false,
    /** Ghi chú phía trên lưới, ví dụ "Thịnh hành tại Việt Nam" khi trang chủ còn trống. */
    val note: String? = null,
)

/**
 * Trạng thái của giao diện trên màn hình ô tô. Dùng chung dữ liệu với app trên điện thoại
 * (lịch sử, kênh đăng ký, xem sau…) và chung một trình phát.
 */
class CarViewModel(app: Application) : AndroidViewModel(app) {

    val store = (app as App).store

    var section by mutableStateOf(CarSection.HOME)
        private set
    var shelf by mutableStateOf(CarShelf.WATCH_LATER)
        private set

    /** Đang mở màn hình xem video to. */
    var playerOpen by mutableStateOf(false)

    val feeds = mutableStateMapOf<String, CarFeed>()

    fun select(s: CarSection) {
        // Bấm lại "Trang chủ" khi đang ở Trang chủ → tải đề xuất mới (giống YouTube).
        val again = s == section && !playerOpen
        section = s
        playerOpen = false
        // Kênh đăng ký và Thư viện tự tải khi màn hình hiện ra (xem CarScreens).
        if (s == CarSection.HOME) loadHome(force = again)
    }

    fun selectShelf(s: CarShelf) {
        shelf = s
        when (s) {
            CarShelf.TRENDING -> load(KEY_TRENDING) { YouTubeRepository.trendingVietnam() }
            CarShelf.TOP_MUSIC -> load(KEY_TOP_MUSIC) { YouTubeRepository.topMusicVietnam() }
            CarShelf.WATCH_LATER, CarShelf.LIKED -> Unit
        }
    }

    fun openVideo(video: VideoItem) {
        PlaybackController.play(video)
        playerOpen = true
    }

    /** Nút Quay lại của xe. Trả về false nếu không còn gì để quay lại. */
    fun back(): Boolean {
        when {
            playerOpen -> playerOpen = false
            section == CarSection.SEARCH && query.isNotEmpty() -> clearSearch()
            section != CarSection.HOME -> select(CarSection.HOME)
            else -> return false
        }
        return true
    }

    // ---------------------------------------------------------------- Danh sách

    fun loadHome(force: Boolean = false) {
        val cur = feeds[KEY_HOME]
        if (!force && cur != null && (cur.loading || (cur.loaded && cur.error == null))) return
        launchLoad(KEY_HOME) {
            val page = YouTubeRepository.home()
            val videos = videosOf(page)
            if (videos.isNotEmpty()) {
                CarFeed(videos = videos, continuation = page.continuation, loaded = true)
            } else {
                // Lần đầu dùng app (chưa xem gì) trang chủ YouTube trống → hiện Thịnh hành giống trên điện thoại.
                CarFeed(videos = videosOf(YouTubeRepository.trendingVietnam()), loaded = true, note = "Thịnh hành tại Việt Nam")
            }
        }
    }

    fun loadSubscriptions() {
        val channels = store.subscriptions.value
        if (channels.isEmpty()) {
            feeds[KEY_SUBS] = CarFeed(loaded = true)
            return
        }
        launchLoad(KEY_SUBS) {
            CarFeed(videos = visible(YouTubeRepository.subscriptionFeed(channels)).take(120), loaded = true)
        }
    }

    private fun load(key: String, force: Boolean = false, fetch: suspend () -> FeedPage) {
        val cur = feeds[key]
        if (!force && cur != null && (cur.loading || (cur.loaded && cur.error == null))) return
        launchLoad(key) {
            val page = fetch()
            CarFeed(videos = videosOf(page), continuation = page.continuation, loaded = true)
        }
    }

    private val jobs = HashMap<String, Job>()

    private fun launchLoad(key: String, block: suspend () -> CarFeed) {
        jobs[key]?.cancel()
        feeds[key] = (feeds[key] ?: CarFeed()).copy(loading = true, error = null)
        jobs[key] = viewModelScope.launch {
            feeds[key] = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                CarFeed(error = errorText(e), loaded = true)
            }
        }
    }

    /** Cuộn gần hết → tải thêm (trang chủ, kết quả tìm kiếm). */
    fun loadMore(key: String) {
        val cur = feeds[key] ?: return
        val token = cur.continuation ?: return
        if (cur.loading || cur.loadingMore) return
        feeds[key] = cur.copy(loadingMore = true)
        viewModelScope.launch {
            val page = runCatching {
                if (key.startsWith(KEY_SEARCH_PREFIX)) YouTubeRepository.searchMore(token)
                else YouTubeRepository.feedContinuation(token)
            }.getOrNull()
            val now = feeds[key] ?: return@launch
            val more = page?.let { videosOf(it) }.orEmpty().filterNot { v -> now.videos.any { it.id == v.id } }
            feeds[key] = now.copy(videos = now.videos + more, continuation = page?.continuation, loadingMore = false)
        }
    }

    fun refresh() {
        when (section) {
            CarSection.HOME -> loadHome(force = true)
            CarSection.SUBSCRIPTIONS -> loadSubscriptions()
            CarSection.SEARCH -> if (submitted.isNotEmpty()) runSearch(submitted, force = true)
            CarSection.LIBRARY -> when (shelf) {
                CarShelf.TRENDING -> load(KEY_TRENDING, force = true) { YouTubeRepository.trendingVietnam() }
                CarShelf.TOP_MUSIC -> load(KEY_TOP_MUSIC, force = true) { YouTubeRepository.topMusicVietnam() }
                else -> Unit
            }
            CarSection.HISTORY -> Unit
        }
    }

    // ---------------------------------------------------------------- Tìm kiếm

    /** Chữ đang gõ trong ô tìm kiếm. */
    var query by mutableStateOf("")
        private set
    /** Câu đã bấm tìm (kết quả đang hiện). */
    var submitted by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestJob: Job? = null

    fun onQueryChange(text: String) {
        query = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = YouTubeRepository.suggestions(text).take(6)
        }
    }

    fun search(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        section = CarSection.SEARCH
        playerOpen = false
        query = q
        suggestions = emptyList()
        store.addRecentSearch(q)
        runSearch(q)
    }

    private fun runSearch(q: String, force: Boolean = false) {
        submitted = q
        load(searchKey(q), force = force) { YouTubeRepository.search(q, YouTubeRepository.SearchFilter.VIDEOS) }
    }

    fun clearSearch() {
        query = ""
        submitted = ""
        suggestions = emptyList()
    }

    // ---------------------------------------------------------------- Tiện ích

    private fun videosOf(page: FeedPage): List<VideoItem> = visible(
        page.entries.flatMap { e ->
            when (e) {
                is FeedEntry.Video -> listOf(e.video)
                is FeedEntry.Shelf -> e.videos
                else -> emptyList()
            }
        },
    ).distinctBy { it.id }

    /** Màn hình xe không có Shorts (video dọc); bỏ video đã bấm "Không quan tâm". */
    private fun visible(list: List<VideoItem>): List<VideoItem> {
        val hidden = store.hidden.value
        return list.filter { !it.isShort && it.id !in hidden }
    }

    private fun errorText(e: Throwable) = when (e) {
        is IOException -> "Không có kết nối mạng"
        else -> "Đã xảy ra lỗi"
    }

    companion object {
        const val KEY_HOME = "home"
        const val KEY_SUBS = "subs"
        const val KEY_TRENDING = "trending"
        const val KEY_TOP_MUSIC = "top_music"
        const val KEY_SEARCH_PREFIX = "search:"

        fun searchKey(q: String) = KEY_SEARCH_PREFIX + q

        /** Chủ đề gợi ý để bấm nhanh khi ngồi trên xe (đỡ phải gõ chữ). */
        val TOPICS = listOf(
            "Nhạc trữ tình", "Bolero", "Tin tức hôm nay", "Thời sự VTV", "Cải lương",
            "Hài", "Nhạc trẻ", "Karaoke", "Phim truyện", "Ca nhạc thiếu nhi",
        )
    }
}
