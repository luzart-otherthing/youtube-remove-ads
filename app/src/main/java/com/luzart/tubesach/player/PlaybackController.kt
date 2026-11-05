package com.luzart.tubesach.player

import android.os.SystemClock
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.WatchInfo
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.CaptionTrack
import com.luzart.tubesach.innertube.InnerTubeException
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.innertube.WatchTracker
import com.luzart.tubesach.net.NetworkMonitor
import com.luzart.tubesach.sponsor.Dislikes
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.sponsor.SegmentCategory
import com.luzart.tubesach.sponsor.SponsorBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.io.IOException

/** Video đang mở trong trình phát. */
data class NowPlaying(
    val video: VideoItem,
    val watch: WatchInfo? = null,
    val related: List<VideoItem> = emptyList(),
    val votes: Dislikes.Votes? = null,
    val captions: List<CaptionTrack> = emptyList(),
    val availableHeights: List<Int> = emptyList(),
    val isShort: Boolean = false,
    /** Token để tải thêm video liên quan khi cuộn xuống. */
    val relatedContinuation: String? = null,
    /** Không tải được thông tin trang xem (tiêu đề, kênh, video liên quan) → cho phép thử lại. */
    val watchFailed: Boolean = false,
)

/** Phụ đề đang chọn: [translateTo] = "vi" nghĩa là YouTube tự động dịch sang tiếng Việt. */
data class CaptionChoice(val track: CaptionTrack, val translateTo: String? = null) {
    val label: String get() = if (translateTo != null) "${track.name} >> Tiếng Việt" else track.name
}

/** Hẹn giờ ngủ: [endAt] = thời điểm dừng; null nghĩa là "khi video kết thúc". */
data class SleepTimer(val endAt: Long?)

/**
 * Điều khiển việc phát video cho toàn ứng dụng (một trình phát duy nhất).
 * Dịch vụ [PlaybackService] bọc trình phát này để phát nền / màn hình khoá.
 */
@SuppressLint("StaticFieldLeak") // chỉ giữ applicationContext
@OptIn(UnstableApi::class)
object PlaybackController {

    private lateinit var appContext: Context
    private lateinit var store: LocalStore
    private lateinit var sources: StreamSourceFactory
    private lateinit var tracker: WatchTracker
    private lateinit var sponsorBlock: SponsorBlock
    private lateinit var dislikes: Dislikes
    private val scope = MainScope()

    private var _player: ExoPlayer? = null
    val player: ExoPlayer
        get() = _player ?: createPlayer().also { _player = it }

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
    val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _loop = MutableStateFlow(false)
    val loop: StateFlow<Boolean> = _loop.asStateFlow()

    private val _segments = MutableStateFlow<List<Segment>>(emptyList())
    val segments: StateFlow<List<Segment>> = _segments.asStateFlow()

    private val _caption = MutableStateFlow<CaptionChoice?>(null)
    val caption: StateFlow<CaptionChoice?> = _caption.asStateFlow()

    private val _relatedLoading = MutableStateFlow(false)
    /** Đang tải thêm video liên quan. */
    val relatedLoading: StateFlow<Boolean> = _relatedLoading.asStateFlow()

    private val _skipped = MutableSharedFlow<Segment>(extraBufferCapacity = 4)
    /** Phát ra mỗi khi tự bỏ qua một đoạn tài trợ (để hiện thông báo "Đã bỏ qua…"). */
    val skipped: SharedFlow<Segment> = _skipped.asSharedFlow()

    private var loadJob: Job? = null
    private var watchJob: Job? = null
    private var retryJob: Job? = null
    private var sleepJob: Job? = null
    private var retryCount = 0
    private var currentData: PlayerData? = null
    private var usingFallback = false
    private val playedIds = LinkedHashSet<String>()
    private val backStack = ArrayDeque<VideoItem>()
    private val skippedUuids = HashSet<String>()

    /**
     * Video kế tiếp đã được xếp sẵn vào danh sách phát của ExoPlayer (phát liền mạch khi hết video,
     * kể cả lúc tắt màn hình – không phải chờ tải lại, không bị hệ thống chặn mạng khi chạy nền).
     */
    private class Queued(val video: VideoItem, val data: PlayerData, val caption: CaptionChoice?)
    private var queued: Queued? = null
    private var prefetchingId: String? = null

    /** Video đang phát do "tự phát" mở (không phải người dùng bấm). */
    private var startedByAutoplay = false
    /** Số video liên tiếp tự phát bỏ qua vì không phát được. */
    private var autoSkips = 0
    /** Video liên quan của video trước (dùng khi video tự phát hiện tại chưa có danh sách liên quan). */
    private var fallbackRelated: List<VideoItem> = emptyList()
    /** Video YouTube không cho phát (riêng tư, bị chặn, đã xoá…) – tự phát sẽ bỏ qua. */
    private val unplayableIds = HashSet<String>()
    /** Dữ liệu /player lấy trước cho video kế tiếp (dùng lại khi bấm "Tiếp theo"). */
    private var prefetched: PlayerData? = null

    fun init(context: Context, localStore: LocalStore) {
        appContext = context.applicationContext
        store = localStore
        val http = (appContext as com.luzart.tubesach.App).http
        sources = StreamSourceFactory(appContext, http)
        tracker = WatchTracker(YouTubeRepository.innerTube)
        sponsorBlock = SponsorBlock(http)
        dislikes = Dislikes(http)
        startTicker()
        scope.launch { NetworkMonitor.reconnected.collect { onResumeOrReconnect() } }
    }

    // ---------------------------------------------------------------- Chỗ nối cho test tự động

    /** Nguồn dữ liệu của trình phát. Test thay bằng dữ liệu giả để mô phỏng mất mạng, link hết hạn… */
    internal interface Backend {
        suspend fun player(videoId: String): PlayerData
        suspend fun watch(videoId: String): WatchInfo
        suspend fun relatedMore(token: String): com.luzart.tubesach.data.FeedPage
        suspend fun streamInfo(url: String): org.schabi.newpipe.extractor.stream.StreamInfo
        fun buildDirect(data: PlayerData, metadata: MediaMetadata, subtitle: MediaItem.SubtitleConfiguration?): MediaSource
        fun createPlayer(context: Context): ExoPlayer? = null
    }

    private val realBackend = object : Backend {
        override suspend fun player(videoId: String) = YouTubeRepository.player(videoId)
        override suspend fun watch(videoId: String) = YouTubeRepository.watch(videoId)
        override suspend fun relatedMore(token: String) = YouTubeRepository.relatedMore(token)
        override suspend fun streamInfo(url: String) = YouTubeRepository.streamInfo(url)
        override fun buildDirect(data: PlayerData, metadata: MediaMetadata, subtitle: MediaItem.SubtitleConfiguration?) =
            sources.buildDirect(data, metadata, subtitle)
    }

    @androidx.annotation.VisibleForTesting
    internal var backend: Backend = realBackend

    /** Test: trả lại trạng thái ban đầu (giải phóng trình phát, dùng [newBackend]). */
    @androidx.annotation.VisibleForTesting
    internal fun resetForTest(newBackend: Backend?) {
        close()
        // Không gọi release(): trình phát của test trước có thể dùng đồng hồ giả đã dừng → release() chờ mãi.
        _player?.removeListener(playerListener)
        _player = null
        backend = newBackend ?: realBackend
        playedIds.clear()
        unplayableIds.clear()
        autoSkips = 0
        startedByAutoplay = false
        fallbackRelated = emptyList()
        retryCount = 0
        currentData = null
        prefetchingId = null
        _loop.value = false
        _caption.value = null
        _sleepTimer.value = null
    }

    @androidx.annotation.VisibleForTesting
    internal val queuedVideoId: String? get() = queued?.video?.id

    /** Test: chạy ngay bước "sắp hết video → xếp sẵn video kế tiếp" (bình thường chạy mỗi 5 giây). */
    @androidx.annotation.VisibleForTesting
    internal fun tickForTest() = prepareNextIfNearEnd()

    private fun createPlayer(): ExoPlayer {
        backend.createPlayer(appContext)?.let { p ->
            p.addListener(playerListener)
            return p
        }
        val p = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // rút tai nghe → tự dừng
            .setWakeMode(C.WAKE_MODE_NETWORK) // tắt màn hình vẫn phát
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build()
        p.addListener(playerListener)
        return p
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (!isPlaying) saveCurrentPosition()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                retryCount = 0
                autoSkips = 0
                _error.value = null
                _loading.value = false
            }
            if (state == Player.STATE_ENDED) onVideoEnded()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // AUTO: hết video. SEEK: nút "Tiếp" trên thông báo, màn hình khoá, tai nghe Bluetooth.
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) return
            val q = queued ?: return
            if (mediaItem?.mediaId != q.video.id) return
            queued = null
            onAutoAdvanced(q)
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val forbidden = cause is HttpDataSource.InvalidResponseCodeException &&
                (cause.responseCode == 403 || cause.responseCode == 410)
            val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            val current = _nowPlaying.value
            val q = queued
            val p = player
            if (q != null && current != null && p.currentMediaItem?.mediaId != q.video.id &&
                p.duration != C.TIME_UNSET && p.duration - p.currentPosition < 3_000
            ) {
                // Video hiện tại đã gần hết, lỗi là do video kế tiếp (đã xếp sẵn) không tải được
                // → mở video kế tiếp theo cách thường (lấy link mới), không phát lại đoạn cuối video cũ.
                queued = null
                store.savePosition(current.video.id, 0)
                play(q.video, resume = false, auto = true)
                return
            }
            if (current != null && retryCount < MAX_RETRIES) {
                // Link hết hạn (403) → lấy link mới. Mất mạng (vừa bật màn hình, đổi Wi-Fi/4G) → chờ chút rồi thử lại.
                // Lỗi khác → chuyển sang NewPipeExtractor dự phòng.
                retryCount++
                val attempt = retryCount
                val pos = player.currentPosition
                val fallback = !network && (attempt >= 2 || !forbidden)
                _loading.value = true
                retryJob?.cancel()
                retryJob = scope.launch {
                    if (network) delay(1_500L * attempt)
                    if (_nowPlaying.value?.video?.id == current.video.id) load(current.video, pos, forceFallback = fallback)
                }
            } else {
                giveUp(if (network) "Không có kết nối mạng. Nhấn để thử lại." else "Đã xảy ra lỗi. Nhấn để thử lại.", network)
            }
        }
    }

    /**
     * Không phát được video hiện tại. Nếu video do "tự phát" mở (ví dụ đang nghe lúc tắt màn hình) thì
     * bỏ qua sang video tiếp theo – tối đa [MAX_AUTO_SKIPS] lần liên tiếp – thay vì dừng im lặng.
     * Mất mạng thì không bỏ qua (video nào cũng sẽ lỗi): báo lỗi, có mạng lại sẽ tự thử lại.
     */
    private fun giveUp(message: String, network: Boolean) {
        _loading.value = false
        val current = _nowPlaying.value
        if (current != null && !network) {
            unplayableIds.add(current.video.id)
            if (startedByAutoplay && autoSkips < MAX_AUTO_SKIPS && canAutoAdvance(current)) {
                val next = nextCandidate()
                if (next != null) {
                    autoSkips++
                    play(next, resume = false, auto = true)
                    return
                }
            }
        }
        _error.value = message
    }

    // ---------------------------------------------------------------- Mở video

    /** Mở một video. Nếu đã xem dở, phát tiếp từ chỗ đã dừng. */
    fun play(video: VideoItem, resume: Boolean = true, asShort: Boolean = false) = play(video, resume, asShort, auto = false)

    /** [auto] = true: mở do tự phát (hết video), không phải người dùng bấm. */
    private fun play(video: VideoItem, resume: Boolean, asShort: Boolean = false, auto: Boolean) {
        fallbackRelated = if (auto) _nowPlaying.value?.related.orEmpty().ifEmpty { fallbackRelated } else emptyList()
        startedByAutoplay = auto
        if (!auto) autoSkips = 0
        _nowPlaying.value?.video?.takeIf { it.id != video.id && !asShort && _nowPlaying.value?.isShort == false }
            ?.let { backStack.addLast(it) }
        saveCurrentPosition()
        retryCount = 0
        watchJob?.cancel()
        _relatedLoading.value = false
        val saved = if (resume && !video.isLive && !asShort) store.positionOf(video.id) else 0L
        val durationMs = video.durationSec * 1000
        val start = if (saved > 10_000 && (durationMs <= 0 || saved < durationMs - 15_000)) saved else 0L
        playedIds.add(video.id)
        skippedUuids.clear()
        _segments.value = emptyList()
        _caption.value = null
        if (_loop.value != asShort) setLoop(asShort)
        _nowPlaying.value = NowPlaying(video, isShort = asShort)
        load(video, start, forceFallback = false, fresh = true)
    }

    fun playShort(id: String) = play(VideoItem.ofId(id).copy(isShort = true), resume = false, asShort = true)

    fun retry() {
        val current = _nowPlaying.value ?: return
        retryCount = 0
        val pos = _player?.currentPosition?.takeIf { it > 0 } ?: store.positionOf(current.video.id)
        load(current.video, pos, forceFallback = false)
        if (current.watch == null && !current.isShort) reloadWatch()
    }

    /** Link phát của YouTube hết hạn sau ~6 giờ (tạm dừng lâu, tắt màn hình qua đêm…). */
    private fun linkExpired(): Boolean {
        val d = currentData ?: return false
        return SystemClock.elapsedRealtime() > d.expiresAtMs - 5 * 60_000L
    }

    /**
     * Gọi khi mở lại ứng dụng / bật màn hình / có mạng trở lại:
     * - đang lỗi → tự thử lại;
     * - đang tạm dừng mà link đã hết hạn → lấy link mới ngay (khỏi phải đợi lỗi 403 rồi mới tải lại);
     * - chưa tải được video liên quan → tải lại.
     */
    fun onResumeOrReconnect() {
        val current = _nowPlaying.value ?: return
        val p = _player
        when {
            _error.value != null -> retry()
            p != null && !p.isPlaying && linkExpired() -> {
                retryCount = 0
                load(current.video, p.currentPosition, forceFallback = false, autoPlay = p.playWhenReady)
            }
            current.watch == null && !current.isShort && watchJob?.isActive != true -> reloadWatch()
        }
    }

    private fun metadataOf(v: VideoItem) = MediaMetadata.Builder()
        .setTitle(v.title)
        .setArtist(v.uploader)
        .setDisplayTitle(v.title)
        .apply { v.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
        .build()

    private fun load(
        video: VideoItem,
        startPositionMs: Long,
        forceFallback: Boolean,
        fresh: Boolean = false,
        autoPlay: Boolean = true,
    ) {
        loadJob?.cancel()
        retryJob?.cancel()
        _error.value = null
        _loading.value = true
        queued = null // setMediaSource bên dưới thay cả danh sách phát
        if (fresh) {
            player.stop()
            player.clearMediaItems()
            currentData = null
        }
        ensureServiceStarted()
        val isShort = _nowPlaying.value?.isShort == true

        // Lấy thông tin trang xem (tiêu đề, kênh, video liên quan) song song với link phát.
        if (fresh || (_nowPlaying.value?.watch == null && watchJob?.isActive != true)) {
            startWatchJob(video.id, startPositionMs)
        }
        if (fresh && !isShort) {
            scope.launch { loadExtras(video.id) }
        }

        loadJob = scope.launch {
            try {
                var item = video
                var directError: Throwable? = null
                val source = if (!forceFallback) {
                    val cached = prefetched?.takeIf { it.videoId == video.id && SystemClock.elapsedRealtime() < it.expiresAtMs - 5 * 60_000L }
                    prefetched = null
                    val data = cached ?: runCatching { backend.player(video.id) }
                        .onFailure { directError = it }
                        .getOrNull()
                    if (data != null && (data.dashManifest() != null || data.hlsUrl != null)) {
                        currentData = data
                        usingFallback = false
                        item = merge(item, data)
                        _nowPlaying.value = _nowPlaying.value?.copy(
                            video = item,
                            captions = data.captions,
                            availableHeights = data.availableHeights,
                        )
                        if (fresh) autoSelectCaption(data.captions)
                        tracker.begin(data, startPositionMs)
                        trackForAccount(data.videoId)
                        backend.buildDirect(data, metadataOf(item), subtitleConfig())
                    } else null
                } else null

                val finalSource = source ?: run {
                    // Mất mạng (không phải YouTube đổi cách hoạt động) → NewPipe cũng không tải được,
                    // thử thêm chỉ làm người dùng chờ lâu hơn. Báo lỗi mạng ngay; có mạng lại sẽ tự tải lại.
                    val d = directError
                    if (d != null && isNetworkError(d)) throw d
                    // Dự phòng: NewPipeExtractor
                    usingFallback = true
                    currentData = null
                    tracker.clear()
                    val info = try {
                        backend.streamInfo(video.url)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Ưu tiên thông báo của YouTube (đã là tiếng Việt) nếu có.
                        throw if (d is InnerTubeException && d.code == 0) d else e
                    }
                    val full = VideoItem.from(info)
                    item = item.copy(
                        title = item.title.ifBlank { full.title },
                        uploader = item.uploader.ifBlank { full.uploader },
                        durationSec = if (item.durationSec > 0) item.durationSec else full.durationSec,
                        isLive = full.isLive,
                        thumbnail = item.thumbnail ?: full.thumbnail,
                    )
                    _nowPlaying.value = _nowPlaying.value?.copy(video = item)
                    sources.build(info, 1080, metadataOf(item))
                }

                applyQuality()
                player.setMediaSource(finalSource, if (item.isLive) C.TIME_UNSET else startPositionMs)
                player.prepare()
                player.playWhenReady = autoPlay
                if (!isShort) store.addToHistory(item, startPositionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                giveUp(friendlyMessage(e), isNetworkError(e))
            }
        }
    }

    /** Lỗi do mạng (không kết nối được, hết thời gian chờ…), khác với lỗi do YouTube trả về. */
    private fun isNetworkError(e: Throwable): Boolean = e is IOException && e !is InnerTubeException

    /** Ghép thông tin từ /player vào video đang mở (khi mở từ link chia sẻ chỉ có mã video). */
    private fun merge(video: VideoItem, data: PlayerData) = video.copy(
        title = video.title.ifBlank { data.title },
        uploader = video.uploader.ifBlank { data.author },
        channelId = video.channelId ?: data.channelId,
        durationSec = if (video.durationSec > 0) video.durationSec else data.lengthSec,
        isLive = data.isLive,
        thumbnail = video.thumbnail ?: data.thumbnail,
    )

    private fun startWatchJob(videoId: String, positionMs: Long) {
        watchJob?.cancel()
        watchJob = scope.launch { fetchWatch(videoId, positionMs) }
    }

    /**
     * Tải thông tin trang xem. Trả về false nếu lỗi (mất mạng…) – khi đó giao diện hiện nút "Thử lại"
     * thay vì vòng xoay mãi không dừng, và app tự tải lại khi có mạng trở lại.
     */
    private suspend fun fetchWatch(videoId: String, positionMs: Long): Boolean {
        val w = try {
            backend.watch(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        val cur = _nowPlaying.value ?: return false
        if (cur.video.id != videoId) return false
        if (w == null) {
            _nowPlaying.value = cur.copy(watchFailed = true)
            return false
        }
        val item = cur.video
        val merged = item.copy(
            title = w.title.ifBlank { item.title },
            uploader = w.channel?.name?.ifBlank { null } ?: item.uploader,
            channelId = w.channel?.id ?: item.channelId,
            channelAvatar = w.channel?.avatar ?: item.channelAvatar,
            viewText = w.shortViewText ?: item.viewText,
            uploadedText = w.relativeDateText ?: item.uploadedText,
            isLive = item.isLive || w.isLive,
        )
        _nowPlaying.value = cur.copy(
            video = merged,
            watch = w,
            related = w.related,
            relatedContinuation = w.relatedContinuation,
            watchFailed = false,
        )
        if (!cur.isShort) store.addToHistory(merged, _player?.currentPosition?.takeIf { it > 0 } ?: positionMs)
        return true
    }

    /** Thử tải lại thông tin trang xem + video liên quan (nút "Thử lại"). */
    fun reloadWatch() {
        val cur = _nowPlaying.value ?: return
        _nowPlaying.value = cur.copy(watchFailed = false)
        startWatchJob(cur.video.id, _player?.currentPosition ?: 0)
    }

    /** Cuộn gần cuối danh sách video liên quan → tải thêm (YouTube trả 20 video mỗi lần). */
    fun loadMoreRelated() {
        val cur = _nowPlaying.value ?: return
        val token = cur.relatedContinuation ?: return
        if (_relatedLoading.value) return
        _relatedLoading.value = true
        scope.launch {
            val page = try {
                backend.relatedMore(token)
            } catch (e: CancellationException) {
                _relatedLoading.value = false
                throw e
            } catch (e: Throwable) {
                null
            }
            _relatedLoading.value = false
            val now = _nowPlaying.value ?: return@launch
            if (now.video.id != cur.video.id || page == null) return@launch
            val known = now.related.mapTo(HashSet()) { it.id }
            val more = page.entries.flatMap {
                when (it) {
                    is FeedEntry.Video -> listOf(it.video)
                    is FeedEntry.Shelf -> it.videos
                    else -> emptyList()
                }
            }.filter { known.add(it.id) }
            _nowPlaying.value = now.copy(related = now.related + more, relatedContinuation = page.continuation)
        }
    }

    /** Đã đăng nhập: ghi video vào lịch sử xem của tài khoản Google (nếu bật "Đề xuất theo video đã xem"). */
    private fun trackForAccount(videoId: String) {
        if (!YouTubeRepository.signedIn || !store.settings.value.personalize) return
        scope.launch {
            val (playback, watchtime) = runCatching { YouTubeRepository.accountTracking(videoId) }.getOrNull() ?: return@launch
            tracker.useAccountUrls(videoId, playback, watchtime)
        }
    }

    private suspend fun loadExtras(videoId: String) {
        val s = store.settings.value
        if (s.sponsorBlock) {
            val segs = sponsorBlock.segments(videoId, s.sponsorCategories)
            if (_nowPlaying.value?.video?.id == videoId) _segments.value = segs
        }
        if (s.returnDislike) {
            val v = dislikes.votes(videoId)
            if (v != null && _nowPlaying.value?.video?.id == videoId) {
                _nowPlaying.value = _nowPlaying.value?.copy(votes = v)
            }
        }
    }

    private fun friendlyMessage(e: Throwable): String = when (e) {
        is AgeRestrictedContentException -> "Video này bị giới hạn độ tuổi. Cần đăng nhập để xem."
        is GeographicRestrictionException -> "Video này không có ở quốc gia của bạn."
        is PrivateContentException -> "Video riêng tư."
        is PaidContentException -> "Video chỉ dành cho hội viên."
        is ReCaptchaException, is SignInConfirmNotBotException -> "YouTube đang tạm chặn. Vui lòng thử lại sau ít phút."
        is ContentNotAvailableException -> "Video không có sẵn."
        is InnerTubeException -> e.message ?: "Video không có sẵn."
        is StreamSourceFactory.NoPlayableStreamException -> "Không phát được video này."
        is IOException -> "Không có kết nối mạng"
        else -> "Đã xảy ra lỗi. Nhấn để thử lại."
    }

    // ---------------------------------------------------------------- Điều khiển

    fun togglePlayPause() {
        val p = _player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            p.seekTo(0)
            p.play()
        } else if (p.isPlaying) {
            p.pause()
        } else if (linkExpired() || (p.playbackState == Player.STATE_IDLE && _nowPlaying.value != null && p.mediaItemCount == 0)) {
            // Tạm dừng quá lâu → link cũ đã hết hạn: lấy link mới rồi phát tiếp từ chỗ cũ.
            retry()
        } else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }

    fun pause() {
        _player?.pause()
    }

    fun seekBy(deltaMs: Long) {
        val p = _player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, dur))
    }

    fun seekTo(positionMs: Long) {
        _player?.seekTo(positionMs)
    }

    /** Nút "Tiếp theo" trên trình phát. */
    fun next() {
        val next = nextCandidate() ?: return
        play(next, resume = false)
    }

    fun hasNext() = nextCandidate() != null

    fun hasPrevious() = backStack.isNotEmpty()

    /** Nút "Trước": quay lại video trước (giống YouTube: nếu đã xem > 3 giây thì tua về đầu). */
    fun previous() {
        val p = _player ?: return
        if (p.currentPosition > 3_000 || backStack.isEmpty()) {
            p.seekTo(0)
            return
        }
        val prev = backStack.removeLast()
        val current = _nowPlaying.value?.video
        play(prev, resume = true)
        if (current != null && backStack.lastOrNull()?.id == current.id) backStack.removeLast()
    }

    private fun nextCandidate(): VideoItem? =
        _nowPlaying.value?.related.orEmpty().ifEmpty { fallbackRelated }
            .firstOrNull { it.id !in playedIds && it.id !in unplayableIds && !it.isLive && !it.isShort }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        _player?.playbackParameters = PlaybackParameters(speed)
    }

    fun setLoop(enabled: Boolean) {
        if (enabled) dropQueued()
        _loop.value = enabled
        _player?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Áp dụng cài đặt "Chất lượng" vào bộ chọn luồng của ExoPlayer. */
    fun applyQuality() {
        val p = _player ?: return
        val s = store.settings.value
        // "360p", "720p"… là cạnh NGẮN của khung hình. Video dọc (Shorts) 720p = 720×1280,
        // nên giới hạn phải đặt lên chiều rộng – nếu đặt lên chiều cao thì Shorts bị chọn 144p, rất mờ.
        val portrait = currentData?.isPortrait ?: (_nowPlaying.value?.isShort == true)
        val b = p.trackSelectionParameters.buildUpon()
            .clearVideoSizeConstraints()
            .setMinVideoSize(0, 0)
            .setForceHighestSupportedBitrate(false)
        fun max(q: Int) = if (portrait) b.setMaxVideoSize(q, Int.MAX_VALUE) else b.setMaxVideoSize(Int.MAX_VALUE, q)
        fun min(q: Int) = if (portrait) b.setMinVideoSize(q, 0) else b.setMinVideoSize(0, q)
        when (s.quality) {
            QualityMode.AUTO -> Unit
            QualityMode.HIGHER -> b.setForceHighestSupportedBitrate(true)
            QualityMode.DATA_SAVER -> max(360)
            QualityMode.ADVANCED -> { max(s.fixedHeight); min(s.fixedHeight) }
        }
        p.trackSelectionParameters = b.build()
    }

    fun setQuality(mode: QualityMode, height: Int? = null) {
        store.updateSettings { it.copy(quality = mode, fixedHeight = height ?: it.fixedHeight) }
        applyQuality()
    }

    /** Độ phân giải đang phát, ví dụ 720. */
    fun currentHeight(): Int? = _player?.videoFormat?.let { f -> if (f.width > 0 && f.height > 0) minOf(f.width, f.height) else f.height }?.takeIf { it > 0 }

    // ---------------------------------------------------------------- Phụ đề

    private fun autoSelectCaption(tracks: List<CaptionTrack>) {
        _caption.value = captionFor(tracks)
    }

    private fun captionFor(tracks: List<CaptionTrack>): CaptionChoice? {
        val lang = store.settings.value.captionLanguage
        if (lang.isEmpty() || tracks.isEmpty()) return null
        val exact = tracks.firstOrNull { it.languageCode.startsWith(lang) && !it.isAuto }
            ?: tracks.firstOrNull { it.languageCode.startsWith(lang) }
        return when {
            exact != null -> CaptionChoice(exact)
            lang == "vi" -> CaptionChoice(tracks.firstOrNull { !it.isAuto } ?: tracks.first(), translateTo = "vi")
            else -> null
        }
    }

    private fun subtitleConfig(c: CaptionChoice? = _caption.value): MediaItem.SubtitleConfiguration? {
        c ?: return null
        return MediaItem.SubtitleConfiguration.Builder(Uri.parse(c.track.vttUrl(c.translateTo)))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(c.translateTo ?: c.track.languageCode)
            .setLabel(c.label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    /** Chọn phụ đề (null = tắt). Ghi nhớ ngôn ngữ cho lần sau. */
    fun setCaption(choice: CaptionChoice?) {
        _caption.value = choice
        store.updateSettings { it.copy(captionLanguage = choice?.let { c -> c.translateTo ?: c.track.languageCode.take(2) } ?: "") }
        val p = _player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, choice == null)
            .setPreferredTextLanguage(choice?.let { it.translateTo ?: it.track.languageCode })
            .build()
        val data = currentData ?: return
        val item = _nowPlaying.value?.video ?: return
        val pos = p.currentPosition
        val playing = p.playWhenReady
        queued = null // setMediaSource thay cả danh sách phát → xếp lại video kế tiếp sau
        p.setMediaSource(backend.buildDirect(data, metadataOf(item), subtitleConfig()), pos)
        p.prepare()
        p.playWhenReady = playing
    }

    // ---------------------------------------------------------------- SponsorBlock

    /** Bật/tắt SponsorBlock bất cứ lúc nào. */
    fun setSponsorBlock(enabled: Boolean) {
        store.updateSettings { it.copy(sponsorBlock = enabled) }
        if (!enabled) {
            _segments.value = emptyList()
        } else {
            _nowPlaying.value?.video?.id?.let { id -> scope.launch { loadExtras(id) } }
        }
    }

    /** "Hoàn tác" sau khi tự bỏ qua: tua lại đầu đoạn và không bỏ qua đoạn đó nữa. */
    fun undoSkip(segment: Segment) {
        skippedUuids.add(segment.uuid)
        _player?.seekTo(segment.startMs)
    }

    private fun checkSegments() {
        val p = _player ?: return
        if (!p.isPlaying || !store.settings.value.sponsorBlock) return
        val pos = p.currentPosition
        val enabled = store.settings.value.sponsorCategories
        val seg = _segments.value.firstOrNull {
            pos >= it.startMs && pos < it.endMs - 500 && it.uuid !in skippedUuids && it.category in enabled
        } ?: return
        skippedUuids.add(seg.uuid)
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo(seg.endMs.coerceAtMost(dur))
        _skipped.tryEmit(seg)
    }

    fun categoryLabel(seg: Segment) = SegmentCategory.of(seg.category)?.label ?: "đoạn"

    // ---------------------------------------------------------------- Khác

    /** Đóng hẳn video đang phát. */
    fun close() {
        saveCurrentPosition()
        loadJob?.cancel()
        watchJob?.cancel()
        retryJob?.cancel()
        queued = null
        prefetched = null
        cancelSleepTimer()
        tracker.clear()
        _player?.stop()
        _player?.clearMediaItems()
        _nowPlaying.value = null
        _error.value = null
        _loading.value = false
        _segments.value = emptyList()
        _relatedLoading.value = false
        backStack.clear()
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
    }

    /** Khi chạy nền / tắt màn hình: chỉ tải tiếng → đỡ tốn 3G/4G và pin. */
    fun setVideoEnabled(enabled: Boolean) {
        val p = _player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    /** [minutes] > 0: dừng sau số phút; 0: dừng khi video kết thúc. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepTimer.value = SleepTimer(endAt = null)
            dropQueued() // dừng khi hết video này → không xếp sẵn video kế tiếp
            return
        }
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimer(endAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            _player?.pause()
            _sleepTimer.value = null
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = null
    }

    private fun onVideoEnded() {
        val current = _nowPlaying.value ?: return
        if (current.isShort) return
        store.savePosition(current.video.id, 0) // xem hết → lần sau xem lại từ đầu
        if (_sleepTimer.value != null && _sleepTimer.value?.endAt == null) {
            _sleepTimer.value = null
            return
        }
        if (!store.settings.value.autoplay || _loop.value) return
        val next = nextCandidate()
        if (next != null) {
            play(next, resume = false, auto = true)
        } else if (current.watch == null) {
            // Chưa có danh sách video liên quan (lỗi mạng lúc mở video) → tải lại rồi phát tiếp.
            scope.launch {
                if (fetchWatch(current.video.id, 0) && _player?.playbackState == Player.STATE_ENDED) {
                    nextCandidate()?.let { play(it, resume = false, auto = true) }
                }
            }
        }
    }

    private fun saveCurrentPosition() {
        val current = _nowPlaying.value ?: return
        val p = _player ?: return
        if (current.isShort || current.video.isLive || p.playbackState == Player.STATE_IDLE) return
        if (p.playbackState == Player.STATE_ENDED) return
        if (p.currentMediaItem?.mediaId.let { it != null && it != current.video.id }) return
        store.savePosition(current.video.id, p.currentPosition)
    }

    private fun startTicker() {
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(250)
                checkSegments()
                ticks++
                if (ticks % 20 == 0 && _player?.isPlaying == true) { // mỗi 5 giây
                    saveCurrentPosition()
                    prepareNextIfNearEnd()
                    val pos = _player?.currentPosition ?: 0
                    launch { tracker.onProgress(pos, store.settings.value.personalize) }
                }
            }
        }
    }

    /** Có được tự chuyển sang video kế tiếp khi hết video này không. */
    private fun canAutoAdvance(current: NowPlaying): Boolean =
        store.settings.value.autoplay && !_loop.value && !current.isShort && !current.video.isLive &&
            !(_sleepTimer.value != null && _sleepTimer.value?.endAt == null)

    /**
     * Còn ≤ 30 giây → lấy trước link phát của video kế tiếp và xếp nó vào sau video hiện tại trong
     * ExoPlayer. Khi hết video, ExoPlayer chuyển ngay sang video mới (không quay vòng chờ tải),
     * kể cả lúc tắt màn hình: trình phát không dừng nên Android không chặn mạng / cho máy ngủ.
     */
    private fun prepareNextIfNearEnd() {
        val p = _player ?: return
        val current = _nowPlaying.value ?: return
        if (!canAutoAdvance(current)) {
            dropQueued()
            return
        }
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: return
        if (duration - p.currentPosition > 30_000) return
        val next = nextCandidate() ?: return
        if (queued?.video?.id == next.id || prefetchingId == next.id) return
        dropQueued()
        prefetchingId = next.id
        scope.launch {
            val data = try {
                prefetched?.takeIf { it.videoId == next.id && SystemClock.elapsedRealtime() < it.expiresAtMs - 5 * 60_000L }
                    ?: backend.player(next.id)
            } catch (e: CancellationException) {
                prefetchingId = null
                throw e
            } catch (e: Throwable) {
                if (!isNetworkError(e)) unplayableIds.add(next.id) // video không phát được → tự phát bỏ qua
                null
            } finally {
                if (prefetchingId == next.id) prefetchingId = null
            }
            if (data == null || data.isLive || data.dashManifest() == null) return@launch
            prefetched = data
            // Trong lúc tải, người dùng có thể đã đổi video / tắt tự phát / bật lặp lại.
            val now = _nowPlaying.value ?: return@launch
            if (now.video.id != current.video.id || !canAutoAdvance(now) || nextCandidate()?.id != next.id) return@launch
            if (usingFallback || p.currentMediaItem?.mediaId != current.video.id || queued != null) return@launch
            val item = merge(next, data)
            val caption = captionFor(data.captions)
            val source = runCatching { backend.buildDirect(data, metadataOf(item), subtitleConfig(caption)) }.getOrNull()
                ?: return@launch
            p.addMediaSource(p.currentMediaItemIndex + 1, source)
            queued = Queued(item, data, caption)
        }
    }

    /** Bỏ video kế tiếp đã xếp sẵn (tắt tự phát, bật lặp lại, hẹn giờ "hết video này"…). */
    private fun dropQueued() {
        val q = queued ?: return
        queued = null
        val p = _player ?: return
        for (i in p.mediaItemCount - 1 downTo 0) {
            if (i != p.currentMediaItemIndex && p.getMediaItemAt(i).mediaId == q.video.id) p.removeMediaItem(i)
        }
    }

    /** ExoPlayer vừa tự chuyển sang video đã xếp sẵn → cập nhật giao diện và thông tin video mới. */
    private fun onAutoAdvanced(q: Queued) {
        val p = player
        val prev = _nowPlaying.value
        fallbackRelated = prev?.related.orEmpty()
        startedByAutoplay = true
        if (prev != null) {
            store.savePosition(prev.video.id, 0)
            if (!prev.isShort && prev.video.id != q.video.id) backStack.addLast(prev.video)
        }
        // Bỏ video cũ khỏi danh sách phát.
        if (p.currentMediaItemIndex > 0) p.removeMediaItems(0, p.currentMediaItemIndex)
        loadJob?.cancel()
        watchJob?.cancel()
        retryJob?.cancel()
        retryCount = 0
        prefetched = null
        playedIds.add(q.video.id)
        skippedUuids.clear()
        _segments.value = emptyList()
        _relatedLoading.value = false
        _error.value = null
        currentData = q.data
        usingFallback = false
        _caption.value = q.caption
        _nowPlaying.value = NowPlaying(
            q.video,
            captions = q.data.captions,
            availableHeights = q.data.availableHeights,
        )
        tracker.begin(q.data, 0)
        trackForAccount(q.data.videoId)
        applyQuality()
        store.addToHistory(q.video, 0)
        startWatchJob(q.video.id, 0)
        scope.launch { loadExtras(q.video.id) }
    }

    private fun ensureServiceStarted() {
        runCatching {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        }
    }

    /** Chỉ dùng cho test giao diện (chụp ảnh màn hình). */
    @androidx.annotation.VisibleForTesting
    internal fun showForPreview(now: NowPlaying?) {
        _nowPlaying.value = now
    }

    @androidx.annotation.VisibleForTesting
    internal fun segmentsForPreview(list: List<Segment>) {
        _segments.value = list
    }

    /** Người dùng vuốt tắt ứng dụng khỏi danh sách gần đây → dừng hẳn. */
    internal fun onTaskRemoved() {
        close()
    }

    const val SEEK_MS = 10_000L
    private const val MAX_RETRIES = 3
    private const val MAX_AUTO_SKIPS = 3
}
