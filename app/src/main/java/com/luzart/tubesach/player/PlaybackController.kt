package com.luzart.tubesach.player

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
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.WatchInfo
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.CaptionTrack
import com.luzart.tubesach.innertube.InnerTubeException
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.innertube.WatchTracker
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

    private val _skipped = MutableSharedFlow<Segment>(extraBufferCapacity = 4)
    /** Phát ra mỗi khi tự bỏ qua một đoạn tài trợ (để hiện thông báo "Đã bỏ qua…"). */
    val skipped: SharedFlow<Segment> = _skipped.asSharedFlow()

    private var loadJob: Job? = null
    private var sleepJob: Job? = null
    private var retryCount = 0
    private var currentData: PlayerData? = null
    private var usingFallback = false
    private val playedIds = LinkedHashSet<String>()
    private val backStack = ArrayDeque<VideoItem>()
    private val skippedUuids = HashSet<String>()

    fun init(context: Context, localStore: LocalStore) {
        appContext = context.applicationContext
        store = localStore
        sources = StreamSourceFactory(appContext)
        tracker = WatchTracker(YouTubeRepository.innerTube)
        val http = (appContext as com.luzart.tubesach.App).http
        sponsorBlock = SponsorBlock(http)
        dislikes = Dislikes(http)
        startTicker()
    }

    private fun createPlayer(): ExoPlayer {
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
        if (videoViewers.isEmpty()) {
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        }
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
                _error.value = null
                _loading.value = false
            }
            if (state == Player.STATE_ENDED) onVideoEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val forbidden = cause is HttpDataSource.InvalidResponseCodeException &&
                (cause.responseCode == 403 || cause.responseCode == 410)
            val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            val current = _nowPlaying.value
            if (current != null && retryCount < 2) {
                // Lần 1: lấy link mới. Lần 2: chuyển sang NewPipeExtractor dự phòng.
                retryCount++
                val pos = player.currentPosition
                load(current.video, pos, forceFallback = retryCount >= 2 || (!forbidden && !network))
            } else {
                _loading.value = false
                _error.value = if (network) "Không có kết nối mạng" else "Đã xảy ra lỗi. Nhấn để thử lại."
            }
        }
    }

    // ---------------------------------------------------------------- Mở video

    /** Mở một video. Nếu đã xem dở, phát tiếp từ chỗ đã dừng. */
    fun play(video: VideoItem, resume: Boolean = true, asShort: Boolean = false) {
        _nowPlaying.value?.video?.takeIf { it.id != video.id && !asShort && _nowPlaying.value?.isShort == false }
            ?.let { backStack.addLast(it) }
        retryCount = 0
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
    }

    private fun metadataOf(v: VideoItem) = MediaMetadata.Builder()
        .setTitle(v.title)
        .setArtist(v.uploader)
        .setDisplayTitle(v.title)
        .apply { v.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
        .build()

    private fun load(video: VideoItem, startPositionMs: Long, forceFallback: Boolean, fresh: Boolean = false) {
        loadJob?.cancel()
        _error.value = null
        _loading.value = true
        if (fresh) {
            player.stop()
            player.clearMediaItems()
            currentData = null
        }
        ensureServiceStarted()
        val isShort = _nowPlaying.value?.isShort == true

        loadJob = scope.launch {
            // Lấy thông tin trang xem song song với link phát.
            val watchJob = if (fresh || _nowPlaying.value?.watch == null) {
                async { runCatching { YouTubeRepository.watch(video.id) }.getOrNull() }
            } else null
            if (fresh && !isShort) {
                launch { loadExtras(video.id) }
            }
            try {
                var item = video
                var directError: Throwable? = null
                val source = if (!forceFallback) {
                    val data = runCatching { YouTubeRepository.player(video.id) }
                        .onFailure { directError = it }
                        .getOrNull()
                    if (data != null && (data.dashManifest() != null || data.hlsUrl != null)) {
                        currentData = data
                        usingFallback = false
                        item = item.copy(
                            title = video.title.ifBlank { data.title },
                            uploader = video.uploader.ifBlank { data.author },
                            channelId = video.channelId ?: data.channelId,
                            durationSec = if (video.durationSec > 0) video.durationSec else data.lengthSec,
                            isLive = data.isLive,
                            thumbnail = video.thumbnail ?: data.thumbnail,
                        )
                        _nowPlaying.value = _nowPlaying.value?.copy(
                            video = item,
                            captions = data.captions,
                            availableHeights = data.availableHeights,
                        )
                        if (fresh) autoSelectCaption(data.captions)
                        tracker.begin(data, startPositionMs)
                        sources.buildDirect(data, metadataOf(item), subtitleConfig())
                    } else null
                } else null

                val finalSource = source ?: run {
                    // Dự phòng: NewPipeExtractor
                    usingFallback = true
                    currentData = null
                    tracker.clear()
                    val info = try {
                        YouTubeRepository.streamInfo(video.url)
                    } catch (e: Exception) {
                        // Ưu tiên thông báo của YouTube (đã là tiếng Việt) nếu có.
                        val d = directError
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
                player.playWhenReady = true
                if (!isShort) store.addToHistory(item, startPositionMs)

                watchJob?.await()?.let { w ->
                    val merged = item.copy(
                        title = w.title.ifBlank { item.title },
                        uploader = w.channel?.name?.ifBlank { null } ?: item.uploader,
                        channelId = w.channel?.id ?: item.channelId,
                        channelAvatar = w.channel?.avatar ?: item.channelAvatar,
                        viewText = w.shortViewText ?: item.viewText,
                        uploadedText = w.relativeDateText ?: item.uploadedText,
                        isLive = item.isLive || w.isLive,
                    )
                    _nowPlaying.value = _nowPlaying.value?.copy(video = merged, watch = w, related = w.related)
                    if (!isShort) store.addToHistory(merged, startPositionMs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _loading.value = false
                _error.value = friendlyMessage(e)
            }
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
        _nowPlaying.value?.related?.firstOrNull { it.id !in playedIds && !it.isLive && !it.isShort }

    fun setSpeed(speed: Float) {
        _speed.value = speed
        _player?.playbackParameters = PlaybackParameters(speed)
    }

    fun setLoop(enabled: Boolean) {
        _loop.value = enabled
        _player?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** Áp dụng cài đặt "Chất lượng" vào bộ chọn luồng của ExoPlayer. */
    fun applyQuality() {
        val p = _player ?: return
        val s = store.settings.value
        val b = p.trackSelectionParameters.buildUpon()
            .clearVideoSizeConstraints()
            .setMinVideoSize(0, 0)
            .setForceHighestSupportedBitrate(false)
        when (s.quality) {
            QualityMode.AUTO -> Unit
            QualityMode.HIGHER -> b.setForceHighestSupportedBitrate(true)
            QualityMode.DATA_SAVER -> b.setMaxVideoSize(Int.MAX_VALUE, 360)
            QualityMode.ADVANCED -> b.setMaxVideoSize(Int.MAX_VALUE, s.fixedHeight).setMinVideoSize(0, s.fixedHeight)
        }
        p.trackSelectionParameters = b.build()
    }

    fun setQuality(mode: QualityMode, height: Int? = null) {
        store.updateSettings { it.copy(quality = mode, fixedHeight = height ?: it.fixedHeight) }
        applyQuality()
    }

    /** Độ phân giải đang phát, ví dụ 720. */
    fun currentHeight(): Int? = _player?.videoFormat?.height?.takeIf { it > 0 }

    // ---------------------------------------------------------------- Phụ đề

    private fun autoSelectCaption(tracks: List<CaptionTrack>) {
        val lang = store.settings.value.captionLanguage
        if (lang.isEmpty() || tracks.isEmpty()) return
        val exact = tracks.firstOrNull { it.languageCode.startsWith(lang) && !it.isAuto }
            ?: tracks.firstOrNull { it.languageCode.startsWith(lang) }
        _caption.value = when {
            exact != null -> CaptionChoice(exact)
            lang == "vi" -> CaptionChoice(tracks.firstOrNull { !it.isAuto } ?: tracks.first(), translateTo = "vi")
            else -> null
        }
    }

    private fun subtitleConfig(): MediaItem.SubtitleConfiguration? {
        val c = _caption.value ?: return null
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
        p.setMediaSource(sources.buildDirect(data, metadataOf(item), subtitleConfig()), pos)
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
        cancelSleepTimer()
        tracker.clear()
        _player?.stop()
        _player?.clearMediaItems()
        _nowPlaying.value = null
        _error.value = null
        _loading.value = false
        _segments.value = emptyList()
        backStack.clear()
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
    }

    // ---------------------------------------------------------------- Màn hình điện thoại / ô tô

    /** Những màn hình đang hiện video (điện thoại, ô tô). Không còn ai xem → chỉ tải tiếng. */
    private val videoViewers = HashSet<String>()

    private val _carScreenActive = MutableStateFlow(false)
    /** true khi video đang được chiếu trên màn hình ô tô (Android Auto) – điện thoại nhường khung hình. */
    val carScreenActive: StateFlow<Boolean> = _carScreenActive.asStateFlow()

    /**
     * Một màn hình bắt đầu / thôi hiện video. Chạy nền hoặc tắt màn hình: chỉ tải tiếng → đỡ tốn 3G/4G và pin.
     * Đếm theo từng màn hình để điện thoại tắt màn hình không làm mất hình trên ô tô (và ngược lại).
     */
    fun setVideoVisible(viewer: String, visible: Boolean) {
        val changed = if (visible) videoViewers.add(viewer) else videoViewers.remove(viewer)
        if (viewer == VIEWER_CAR) _carScreenActive.value = visible
        if (changed) setVideoEnabled(videoViewers.isNotEmpty())
    }

    private fun setVideoEnabled(enabled: Boolean) {
        val p = _player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    @androidx.annotation.VisibleForTesting
    internal fun isVideoTrackEnabled(): Boolean =
        _player?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_VIDEO) != true

    /** [minutes] > 0: dừng sau số phút; 0: dừng khi video kết thúc. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepTimer.value = SleepTimer(endAt = null)
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
        nextCandidate()?.let { play(it, resume = false) }
    }

    private fun saveCurrentPosition() {
        val current = _nowPlaying.value ?: return
        val p = _player ?: return
        if (current.isShort || current.video.isLive || p.playbackState == Player.STATE_IDLE) return
        if (p.playbackState == Player.STATE_ENDED) return
        store.savePosition(current.video.id, p.currentPosition)
    }

    private var prefetchedId: String? = null

    private fun startTicker() {
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(250)
                checkSegments()
                ticks++
                if (ticks % 20 == 0 && _player?.isPlaying == true) { // mỗi 5 giây
                    saveCurrentPosition()
                    prefetchNextIfNearEnd()
                    val pos = _player?.currentPosition ?: 0
                    launch { tracker.onProgress(pos, store.settings.value.personalize) }
                }
            }
        }
    }

    /** Sắp hết video → lấy trước thông tin video kế tiếp để chuyển bài nhanh, kể cả khi tắt màn hình. */
    private fun prefetchNextIfNearEnd() {
        val p = _player ?: return
        val current = _nowPlaying.value ?: return
        if (!store.settings.value.autoplay || current.video.isLive || current.isShort) return
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: return
        if (duration - p.currentPosition > 30_000) return
        val next = nextCandidate() ?: return
        if (prefetchedId == next.id) return
        prefetchedId = next.id
        scope.launch { runCatching { YouTubeRepository.player(next.id) } }
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
    const val VIEWER_PHONE = "phone"
    const val VIEWER_CAR = "car"
}
