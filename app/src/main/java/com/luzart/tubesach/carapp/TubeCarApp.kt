package com.luzart.tubesach.carapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luzart.tubesach.App
import com.luzart.tubesach.R
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * Tube Sạch trên Android Auto cho điện thoại Android cũ (Android 14 trở xuống).
 *
 * Android Auto chỉ cho ứng dụng dẫn đường (bản đồ) vẽ lên màn hình xe, nên phần này khai báo là
 * ứng dụng dẫn đường rồi vẽ video lên chỗ của bản đồ (giống CarStream / Fermata Auto).
 * Danh sách video, tìm kiếm dùng các mẫu giao diện chuẩn của Android Auto (bấm được bằng màn hình cảm ứng
 * hoặc núm xoay). Hình chỉ hiện khi xe đứng yên ([ParkingGate]); xe chạy thì chỉ phát tiếng.
 */
class TubeCarAppService : CarAppService() {

    /**
     * App cài ngoài CH Play nên không kiểm tra chữ ký của Android Auto (giống Fermata Auto).
     * Máy chủ nào kết nối được cũng chỉ điều khiển được việc xem video, không đọc được dữ liệu khác.
     */
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = TubeCarSession()
}

class TubeCarSession : Session() {

    lateinit var renderer: VideoSurfaceRenderer
        private set
    lateinit var gate: ParkingGate
        private set

    override fun onCreateScreen(intent: Intent): Screen {
        renderer = VideoSurfaceRenderer(carContext)
        gate = ParkingGate(carContext)
        runCatching { carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer) }
        gate.start()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                gate.stop()
                renderer.release()
            }
        })
        val home = VideoGridScreen(carContext, this, "Tube Sạch", isHome = true) { homeVideos() }
        // Đang nghe dở (ví dụ từ điện thoại) → mở luôn màn hình xem.
        if (PlaybackController.nowPlaying.value?.isShort == false) {
            carContext.getCarService(ScreenManager::class.java).push(home)
            return PlayerScreen(carContext, this)
        }
        return home
    }

    /** Bấm vào một video ở bất cứ đâu: phát và mở màn hình xem (không chồng thêm nhiều màn hình xem). */
    fun play(screenManager: ScreenManager, video: VideoItem) {
        PlaybackController.play(video)
        openPlayer(screenManager)
    }

    fun openPlayer(screenManager: ScreenManager) {
        if (screenManager.screenStack.any { it.marker == PlayerScreen.MARKER }) {
            screenManager.popTo(PlayerScreen.MARKER)
        } else {
            screenManager.push(PlayerScreen(carContext, this))
        }
    }
}

// ---------------------------------------------------------------- Dữ liệu

private suspend fun homeVideos(): List<VideoItem> {
    val home = videosOf(YouTubeRepository.home())
    return home.ifEmpty { videosOf(YouTubeRepository.trendingVietnam()) }
}

private fun videosOf(page: FeedPage): List<VideoItem> = page.entries.flatMap { e ->
    when (e) {
        is FeedEntry.Video -> listOf(e.video)
        is FeedEntry.Shelf -> e.videos
        else -> emptyList()
    }
}.filter { !it.isShort }.distinctBy { it.id }

private fun CarContext.icon(res: Int): CarIcon = CarIcon.Builder(IconCompat.createWithResource(this, res)).build()

private fun CarContext.gridLimit(): Int =
    runCatching { getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_GRID) }
        .getOrDefault(6).coerceIn(1, 100)

private fun CarContext.listLimit(): Int =
    runCatching { getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST) }
        .getOrDefault(6).coerceIn(1, 100)

private fun errorText(e: Throwable) = if (e is IOException) "Không có kết nối mạng" else "Đã xảy ra lỗi. Bấm Tải lại."

/** Ảnh thu nhỏ cho danh sách trên xe (thu nhỏ còn ~320 điểm ảnh cho nhẹ). */
object CarThumbs {
    private val cache = LruCache<String, Bitmap>(120)
    private val limit = Semaphore(6)

    fun cached(url: String?): Bitmap? = url?.let { cache.get(it) }

    suspend fun load(context: android.content.Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        cache.get(url)?.let { return it }
        return limit.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val full = if (url.startsWith("//")) "https:$url" else url
                    App.of(context).http.newCall(Request.Builder().url(full).build()).execute().use { r ->
                        val bytes = r.body?.bytes() ?: return@use null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / (sample * 2) >= 320) sample *= 2
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()?.also { cache.put(url, it) }
            }
        }
    }

    /** Tải ảnh còn thiếu rồi gọi [onLoaded] một lần (đỡ làm mới màn hình xe nhiều lần). */
    suspend fun preload(context: android.content.Context, urls: List<String?>, onLoaded: () -> Unit) {
        val missing = urls.filterNotNull().distinct().filter { cache.get(it) == null }
        if (missing.isEmpty()) return
        coroutineScope { missing.map { async { load(context, it) } }.awaitAll() }
        onLoaded()
    }
}

private fun CarContext.videoIcon(video: VideoItem): CarIcon =
    CarThumbs.cached(video.thumbnail)?.let { CarIcon.Builder(IconCompat.createWithBitmap(it)).build() }
        ?: icon(R.drawable.ic_pip_play)

private fun subtitle(v: VideoItem): String = listOfNotNull(
    v.uploader.ifBlank { null },
    if (v.isLive) "Trực tiếp" else v.viewText,
).joinToString(" • ")

// ---------------------------------------------------------------- Lưới video

/** Lưới video có ảnh (Trang chủ, Kênh đăng ký, Đã xem, Thịnh hành…). */
class VideoGridScreen(
    carContext: CarContext,
    private val session: TubeCarSession,
    private val title: String,
    private val isHome: Boolean = false,
    private val loader: suspend () -> List<VideoItem>,
) : Screen(carContext) {

    private var videos: List<VideoItem>? = null
    private var error: String? = null
    private var job: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = load()
        })
        if (isHome) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    PlaybackController.nowPlaying.collect { invalidate() }
                }
            }
        }
    }

    private fun load() {
        job?.cancel()
        error = null
        job = lifecycleScope.launch {
            try {
                val list = loader()
                videos = list
                invalidate()
                CarThumbs.preload(carContext, list.take(carContext.gridLimit()).map { it.thumbnail }) { invalidate() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                videos = emptyList()
                error = errorText(e)
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val b = GridTemplate.Builder().setTitle(title)
        b.setHeaderAction(if (isHome) Action.APP_ICON else Action.BACK)
        val strip = ActionStrip.Builder()
        if (isHome) {
            strip.addAction(
                Action.Builder().setIcon(carContext.icon(R.drawable.ic_car_search))
                    .setOnClickListener { screenManager.push(SearchScreen(carContext, session)) }.build(),
            )
            strip.addAction(
                Action.Builder().setIcon(carContext.icon(R.drawable.ic_car_menu))
                    .setOnClickListener { screenManager.push(MenuScreen(carContext, session)) }.build(),
            )
        } else {
            strip.addAction(Action.Builder().setTitle("Tải lại").setOnClickListener { videos = null; invalidate(); load() }.build())
        }
        b.setActionStrip(strip.build())

        val list = videos ?: return b.setLoading(true).build()
        val items = ItemList.Builder()
        val now = PlaybackController.nowPlaying.value
        var limit = carContext.gridLimit()
        if (isHome && now != null && !now.isShort) {
            items.addItem(
                GridItem.Builder()
                    .setTitle("Đang phát")
                    .setText(now.video.title.ifBlank { " " })
                    .setImage(carContext.icon(R.drawable.ic_pip_play))
                    .setOnClickListener { session.openPlayer(screenManager) }
                    .build(),
            )
            limit--
        }
        list.take(limit.coerceAtLeast(0)).forEach { v ->
            items.addItem(
                GridItem.Builder()
                    .setTitle(v.title.ifBlank { "Video" })
                    .setText(subtitle(v).ifBlank { " " })
                    .setImage(carContext.videoIcon(v), GridItem.IMAGE_TYPE_LARGE)
                    .setOnClickListener { session.play(screenManager, v) }
                    .build(),
            )
        }
        items.setNoItemsMessage(error ?: "Chưa có video")
        return b.setSingleList(items.build()).build()
    }
}

// ---------------------------------------------------------------- Các mục khác

class MenuScreen(carContext: CarContext, private val session: TubeCarSession) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val store = App.of(carContext).store
        val entries: List<Pair<String, suspend () -> List<VideoItem>>> = listOf(
            "Kênh đăng ký" to { YouTubeRepository.subscriptionFeed(store.subscriptions.value).filter { !it.isShort } },
            "Đã xem" to { store.history.value.map { it.video }.filter { !it.isShort } },
            "Xem sau" to { store.favorites.value },
            "Video đã thích" to { store.liked.value },
            "Thịnh hành tại Việt Nam" to { videosOf(YouTubeRepository.trendingVietnam()) },
            "Nhạc hàng đầu Việt Nam" to { videosOf(YouTubeRepository.topMusicVietnam()) },
        )
        val items = ItemList.Builder()
        entries.take(carContext.listLimit()).forEach { (label, loader) ->
            items.addItem(
                Row.Builder()
                    .setTitle(label)
                    .setBrowsable(true)
                    .setOnClickListener { screenManager.push(VideoGridScreen(carContext, session, label, loader = loader)) }
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setTitle("Mục khác")
            .setHeaderAction(Action.BACK)
            .setSingleList(items.build())
            .build()
    }
}

// ---------------------------------------------------------------- Tìm kiếm

class SearchScreen(carContext: CarContext, private val session: TubeCarSession) : Screen(carContext) {

    private val store = App.of(carContext).store
    private var text = ""
    private var suggestions: List<String> = emptyList()
    private var results: List<VideoItem>? = null
    private var loading = false
    private var error: String? = null
    private var job: Job? = null

    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            text = searchText
            results = null
            job?.cancel()
            job = lifecycleScope.launch {
                delay(300)
                suggestions = if (searchText.isBlank()) emptyList() else YouTubeRepository.suggestions(searchText)
                invalidate()
            }
        }

        override fun onSearchSubmitted(searchText: String) = search(searchText)
    }

    private fun search(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        text = query
        store.addRecentSearch(query)
        job?.cancel()
        loading = true
        error = null
        invalidate()
        job = lifecycleScope.launch {
            try {
                val list = videosOf(YouTubeRepository.search(query, YouTubeRepository.SearchFilter.VIDEOS))
                results = list
                loading = false
                invalidate()
                CarThumbs.preload(carContext, list.take(carContext.listLimit()).map { it.thumbnail }) { invalidate() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                results = emptyList()
                loading = false
                error = errorText(e)
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val b = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Tìm kiếm trên YouTube")
            .setInitialSearchText(text)
            .setShowKeyboardByDefault(results == null && text.isEmpty())
        if (loading) return b.setLoading(true).build()
        val items = ItemList.Builder()
        val limit = carContext.listLimit()
        val r = results
        if (r != null) {
            r.take(limit).forEach { v ->
                items.addItem(
                    Row.Builder()
                        .setTitle(v.title.ifBlank { "Video" })
                        .addText(subtitle(v).ifBlank { " " })
                        .setImage(carContext.videoIcon(v), Row.IMAGE_TYPE_LARGE)
                        .setOnClickListener { session.play(screenManager, v) }
                        .build(),
                )
            }
            items.setNoItemsMessage(error ?: "Không tìm thấy video")
        } else {
            val words = if (text.isBlank()) store.recentSearches.value else suggestions
            words.take(limit).forEach { w ->
                items.addItem(Row.Builder().setTitle(w).setOnClickListener { search(w) }.build())
            }
        }
        return b.setItemList(items.build()).build()
    }
}

// ---------------------------------------------------------------- Xem video

/**
 * Màn hình xem: video chiếm chỗ của bản đồ, các nút điều khiển là nút chuẩn của Android Auto.
 * Xe chạy → chỉ phát tiếng (hiện dòng chữ nhắc).
 */
class PlayerScreen(carContext: CarContext, private val session: TubeCarSession) : Screen(carContext) {

    init {
        marker = MARKER
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.renderer.onTap = { PlaybackController.togglePlayPause() }
                try {
                    val store = App.of(carContext).store
                    combine(
                        session.gate.state,
                        PlaybackController.isPlaying,
                        PlaybackController.nowPlaying,
                        store.settings,
                    ) { s, playing, now, settings ->
                        listOf(s, playing, now?.video?.id, settings.carVideoWhileDriving)
                    }.distinctUntilChanged().collect {
                        session.renderer.setMode(modeFor(session.gate.state.value, store.settings.value.carVideoWhileDriving))
                        invalidate()
                    }
                } finally {
                    session.renderer.onTap = null
                    session.renderer.setMode(VideoSurfaceRenderer.Mode.Idle)
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val playing = PlaybackController.isPlaying.value
        val strip = ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("Quay lại").setOnClickListener { screenManager.pop() }.build())
            .addAction(
                Action.Builder()
                    .setIcon(carContext.icon(if (playing) R.drawable.ic_pip_pause else R.drawable.ic_pip_play))
                    .setOnClickListener { PlaybackController.togglePlayPause() }
                    .build(),
            )
            .addAction(
                Action.Builder().setIcon(carContext.icon(R.drawable.ic_car_next))
                    .setOnClickListener { PlaybackController.next() }.build(),
            )
        val state = session.gate.state.value
        val passengerMode = App.of(carContext).store.settings.value.carVideoWhileDriving
        val askPermission = !passengerMode && (
            state == DriveState.NO_PERMISSION ||
                (state == DriveState.UNAVAILABLE && session.gate.missingPermission())
            )
        if (askPermission) {
            strip.addAction(Action.Builder().setTitle("Bật hình").setOnClickListener { session.gate.requestPermission { invalidate() } }.build())
        } else {
            strip.addAction(
                Action.Builder().setIcon(carContext.icon(R.drawable.ic_car_list))
                    .setOnClickListener {
                        val related = PlaybackController.nowPlaying.value?.related.orEmpty().filter { !it.isShort }
                        screenManager.push(VideoGridScreen(carContext, session, "Video tiếp theo") { related })
                    }.build(),
            )
        }
        val mapStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder().setIcon(carContext.icon(R.drawable.ic_pip_rewind))
                    .setOnClickListener { PlaybackController.seekBy(-PlaybackController.SEEK_MS) }.build(),
            )
            .addAction(
                Action.Builder().setIcon(carContext.icon(R.drawable.ic_pip_forward))
                    .setOnClickListener { PlaybackController.seekBy(PlaybackController.SEEK_MS) }.build(),
            )
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(strip.build())
            .setMapActionStrip(mapStrip)
            .build()
    }

    companion object {
        const val MARKER = "player"

        /**
         * [whileDriving] = người dùng đã bật "Cho hành khách xem hình khi xe đang chạy" trên điện thoại:
         * luôn hiện hình. Mặc định chỉ hiện hình khi xe đứng yên.
         */
        fun modeFor(state: DriveState, whileDriving: Boolean = false): VideoSurfaceRenderer.Mode = if (whileDriving) {
            VideoSurfaceRenderer.Mode.Video
        } else when (state) {
            DriveState.PARKED -> VideoSurfaceRenderer.Mode.Video
            DriveState.MOVING -> VideoSurfaceRenderer.Mode.AudioOnly("Xe đang chạy nên chỉ phát tiếng.\nDừng xe để xem hình (hoặc bật “Cho hành khách xem”\ntrong Cài đặt của Tube Sạch trên điện thoại).")
            DriveState.UNKNOWN -> VideoSurfaceRenderer.Mode.AudioOnly("Đang kiểm tra xe đang đỗ…")
            DriveState.NO_PERMISSION -> VideoSurfaceRenderer.Mode.AudioOnly("Bấm “Bật hình” rồi cho phép đọc tốc độ xe và vị trí\n(để biết xe đang đỗ) thì mới xem được hình.")
            DriveState.UNAVAILABLE -> VideoSurfaceRenderer.Mode.AudioOnly("Chưa đọc được tốc độ xe nên chưa bật hình.\nVẫn nghe được tiếng.")
        }
    }
}
