package com.luzart.tubesach.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/** Video đang mở trong trình phát. */
data class NowPlaying(
    val video: VideoItem,
    val info: StreamInfo? = null,
    val related: List<VideoItem> = emptyList(),
)

/** Hẹn giờ tắt: [endAt] = thời điểm dừng; null nghĩa là "dừng khi hết video này". */
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

    private var loadJob: Job? = null
    private var sleepJob: Job? = null
    private var retryCount = 0
    private val playedIds = LinkedHashSet<String>()

    fun init(context: Context, localStore: LocalStore) {
        appContext = context.applicationContext
        store = localStore
        sources = StreamSourceFactory(appContext)
        startPositionSaver()
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
            }
            if (state == Player.STATE_ENDED) onVideoEnded()
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val forbidden = cause is HttpDataSource.InvalidResponseCodeException &&
                (cause.responseCode == 403 || cause.responseCode == 410)
            val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            val retryable = forbidden || network ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
            val current = _nowPlaying.value
            if (current != null && retryCount < 2 && retryable) {
                // Link phát hết hạn hoặc lỗi mạng tạm thời → lấy link mới và phát tiếp.
                retryCount++
                load(current.video, startPositionMs = player.currentPosition, forceRefresh = true)
            } else {
                _loading.value = false
                _error.value = if (network) {
                    "Mất kết nối mạng.\nKiểm tra Wi-Fi hoặc 4G rồi bấm \"Thử lại\"."
                } else {
                    "Không phát được video này.\nBấm \"Thử lại\" hoặc chọn video khác."
                }
            }
        }
    }

    // ---------------------------------------------------------------- Mở video

    /** Mở một video. Nếu đã xem dở, phát tiếp từ chỗ đã dừng. */
    fun play(video: VideoItem, resume: Boolean = true) {
        retryCount = 0
        val saved = if (resume && !video.isLive) store.positionOf(video.id) else 0L
        val durationMs = video.durationSec * 1000
        val start = if (saved > 10_000 && (durationMs <= 0 || saved < durationMs - 15_000)) saved else 0L
        playedIds.add(video.id)
        load(video, start, forceRefresh = false)
    }

    fun retry() {
        val current = _nowPlaying.value ?: return
        retryCount = 0
        val pos = _player?.currentPosition?.takeIf { it > 0 } ?: store.positionOf(current.video.id)
        load(current.video, pos, forceRefresh = true)
    }

    private fun load(video: VideoItem, startPositionMs: Long, forceRefresh: Boolean) {
        loadJob?.cancel()
        _error.value = null
        _loading.value = true
        val keepInfo = _nowPlaying.value?.takeIf { it.video.id == video.id }
        _nowPlaying.value = keepInfo?.copy(video = video) ?: NowPlaying(video)
        if (keepInfo == null) {
            player.stop()
            player.clearMediaItems()
        }
        ensureServiceStarted()

        loadJob = scope.launch {
            try {
                val info = YouTubeRepository.streamInfo(video.url, forceRefresh)
                val full = VideoItem.from(info).let { v ->
                    if (v.thumbnail == null) v.copy(thumbnail = video.thumbnail) else v
                }
                val metadata = MediaMetadata.Builder()
                    .setTitle(full.title)
                    .setArtist(full.uploader)
                    .setDisplayTitle(full.title)
                    .apply { full.thumbnail?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
                val source = sources.build(info, store.settings.value.maxQuality, metadata)
                _nowPlaying.value = NowPlaying(full, info, YouTubeRepository.relatedOf(info))
                store.addToHistory(full, startPositionMs)

                player.setMediaSource(source, if (full.isLive) C.TIME_UNSET else startPositionMs)
                player.prepare()
                player.playWhenReady = true
                _loading.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _loading.value = false
                _error.value = friendlyMessage(e)
            }
        }
    }

    private fun friendlyMessage(e: Throwable): String = when (e) {
        is AgeRestrictedContentException ->
            "Video này bị giới hạn độ tuổi, YouTube bắt đăng nhập nên không xem được."
        is GeographicRestrictionException -> "Video này không cho xem ở Việt Nam."
        is PrivateContentException -> "Đây là video riêng tư, không xem được."
        is PaidContentException -> "Video này phải trả tiền hoặc chỉ dành cho hội viên."
        is ReCaptchaException, is SignInConfirmNotBotException ->
            "YouTube đang tạm chặn.\nVui lòng đợi vài phút rồi bấm \"Thử lại\"."
        is ContentNotAvailableException -> "Video không còn tồn tại hoặc đã bị gỡ."
        is StreamSourceFactory.NoPlayableStreamException -> "Không tìm thấy luồng phát cho video này."
        is IOException -> "Mất kết nối mạng.\nKiểm tra Wi-Fi hoặc 4G rồi bấm \"Thử lại\"."
        else -> "Không mở được video.\nBấm \"Thử lại\" hoặc chọn video khác."
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

    /** Đóng hẳn video đang phát. */
    fun close() {
        saveCurrentPosition()
        loadJob?.cancel()
        cancelSleepTimer()
        _player?.stop()
        _player?.clearMediaItems()
        _nowPlaying.value = null
        _error.value = null
        _loading.value = false
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
    }

    /**
     * Khi ứng dụng chạy nền / tắt màn hình: tắt luồng hình, chỉ tải tiếng
     * → đỡ tốn 3G/4G và pin. Bật lại khi người dùng quay lại.
     */
    fun setVideoEnabled(enabled: Boolean) {
        val p = _player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    // ---------------------------------------------------------------- Hẹn giờ tắt

    /** [minutes] > 0: dừng sau số phút; 0: dừng khi hết video hiện tại. */
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

    // ---------------------------------------------------------------- Nội bộ

    private fun onVideoEnded() {
        val current = _nowPlaying.value ?: return
        store.savePosition(current.video.id, 0) // xem hết → lần sau xem lại từ đầu
        if (_sleepTimer.value?.endAt == null && _sleepTimer.value != null) {
            _sleepTimer.value = null
            return
        }
        if (!store.settings.value.autoplay) return
        val next = current.related.firstOrNull { it.id !in playedIds && !it.isLive } ?: return
        play(next, resume = false)
    }

    private fun saveCurrentPosition() {
        val current = _nowPlaying.value ?: return
        val p = _player ?: return
        if (current.video.isLive || p.playbackState == Player.STATE_IDLE) return
        if (p.playbackState == Player.STATE_ENDED) return
        store.savePosition(current.video.id, p.currentPosition)
    }

    private var prefetchedId: String? = null

    private fun startPositionSaver() {
        scope.launch {
            while (isActive) {
                delay(5_000)
                if (_player?.isPlaying == true) {
                    saveCurrentPosition()
                    prefetchNextIfNearEnd()
                }
            }
        }
    }

    /** Sắp hết video → lấy trước thông tin video kế tiếp để chuyển bài nhanh, kể cả khi tắt màn hình. */
    private fun prefetchNextIfNearEnd() {
        val p = _player ?: return
        val current = _nowPlaying.value ?: return
        if (!store.settings.value.autoplay || current.video.isLive) return
        val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: return
        if (duration - p.currentPosition > 60_000) return
        val next = current.related.firstOrNull { it.id !in playedIds && !it.isLive } ?: return
        if (prefetchedId == next.id) return
        prefetchedId = next.id
        scope.launch { runCatching { YouTubeRepository.streamInfo(next.url) } }
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

    /** Người dùng vuốt tắt ứng dụng khỏi danh sách gần đây → dừng hẳn. */
    internal fun onTaskRemoved() {
        close()
    }

    const val SEEK_MS = 10_000L
}
