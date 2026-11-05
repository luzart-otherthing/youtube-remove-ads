package com.luzart.tubesach

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.FakeClock
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.upstream.Allocator
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.WatchInfo
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.innertube.StreamFormat
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration

/**
 * Mô phỏng các tình huống thật với trình phát ExoPlayer thật (bộ giải mã giả) và dữ liệu YouTube giả:
 * tự chuyển video khi hết, tắt/bật màn hình, mất mạng rồi có mạng lại, link hết hạn khi tạm dừng lâu,
 * tải thêm video đề xuất, Shorts dọc.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackScenarioTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    /** YouTube giả: điều khiển được lúc nào "mất mạng", link hết hạn, video nào liên quan. */
    private class FakeYouTube : PlaybackController.Backend {
        var networkDown = false
        var watchDown = false
        /** Số lần tiếp theo trình phát gặp lỗi mạng khi đang tải video. */
        var streamFailures = 0
        var expiresInMs = 6 * 3600_000L
        var portrait = false
        /** Video YouTube không cho phát (riêng tư, bị chặn…). */
        val unplayable = mutableSetOf<String>()
        var allUnplayableExcept: String? = null
        /** Video lấy được link nhưng tải luồng luôn bị 403 (như khi YouTube chặn). */
        val forbidden = mutableSetOf<String>()
        val playerCalls = mutableListOf<String>()
        val watchCalls = mutableListOf<String>()
        var related: (String) -> List<VideoItem> = { id -> (1..20).map { video(rel(id, it)) } }

        /** Đồng hồ của trình phát: chỉ chạy khi test cho thời gian trôi (cùng nhịp với đồng hồ hệ thống). */
        val clock = FakeClock(/* isAutoAdvancing = */ false)
        var moreCalls = 0

        override suspend fun player(videoId: String): PlayerData {
            playerCalls += videoId
            if (networkDown) throw IOException("mất mạng")
            if (videoId in unplayable || (allUnplayableExcept != null && videoId != allUnplayableExcept)) {
                throw com.luzart.tubesach.innertube.InnerTubeException("Video không có sẵn.")
            }
            return playerData(videoId, expiresInMs, portrait)
        }

        override suspend fun watch(videoId: String): WatchInfo {
            watchCalls += videoId
            if (networkDown || watchDown) throw IOException("mất mạng")
            return WatchInfo(
                videoId, "Video $videoId", null, null, null, null, null, "", null, null, null, false,
                related(videoId), "token-$videoId",
            )
        }

        override suspend fun relatedMore(token: String): FeedPage {
            moreCalls++
            if (networkDown) throw IOException("mất mạng")
            val id = token.removePrefix("token-")
            // Trang sau có lặp lại 1 video cũ (YouTube hay làm vậy) → phải lọc trùng.
            val items = listOf(video(rel(id, 20))) + (21..40).map { video(rel(id, it)) }
            return FeedPage(items.map { FeedEntry.Video(it) }, "token2-$id")
        }

        var fallbackCalls = 0

        override suspend fun streamInfo(url: String): org.schabi.newpipe.extractor.stream.StreamInfo {
            fallbackCalls++
            throw org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException("không dùng NewPipe trong test")
        }

        override fun buildDirect(data: PlayerData, metadata: MediaMetadata, subtitle: MediaItem.SubtitleConfiguration?): MediaSource {
            val item = MediaItem.Builder().setMediaId(data.videoId).build()
            val timeline = FakeTimeline(
                FakeTimeline.TimelineWindowDefinition.Builder()
                    .setUid(data.videoId)
                    .setDurationUs(data.lengthSec * 1_000_000)
                    .setMediaItem(item)
                    .build(),
            )
            val spec = androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://x/${data.videoId}"))
            if (data.videoId in forbidden) {
                val error = HttpDataSource.InvalidResponseCodeException(403, "Forbidden", null, emptyMap(), spec, ByteArray(0))
                return object : FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT) {
                    override fun createMediaPeriod(
                        id: MediaSource.MediaPeriodId,
                        trackGroupArray: androidx.media3.exoplayer.source.TrackGroupArray,
                        allocator: Allocator,
                        mediaSourceEventDispatcher: androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher,
                        drmSessionManager: androidx.media3.exoplayer.drm.DrmSessionManager,
                        drmEventDispatcher: androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher,
                        transferListener: androidx.media3.datasource.TransferListener?,
                    ): MediaPeriod = NeverPreparedPeriod(error)

                    override fun releaseMediaPeriod(mediaPeriod: MediaPeriod) {
                        if (mediaPeriod !is NeverPreparedPeriod) super.releaseMediaPeriod(mediaPeriod)
                    }
                }
            }
            if (streamFailures > 0) {
                streamFailures--
                // Như khi mạng chưa sẵn sàng: ExoPlayer không tải được luồng → lỗi IO_NETWORK_CONNECTION_FAILED.
                return object : FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT) {
                    override fun createMediaPeriod(
                        id: MediaSource.MediaPeriodId,
                        trackGroupArray: androidx.media3.exoplayer.source.TrackGroupArray,
                        allocator: Allocator,
                        mediaSourceEventDispatcher: androidx.media3.exoplayer.source.MediaSourceEventListener.EventDispatcher,
                        drmSessionManager: androidx.media3.exoplayer.drm.DrmSessionManager,
                        drmEventDispatcher: androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher,
                        transferListener: androidx.media3.datasource.TransferListener?,
                    ): MediaPeriod = NeverPreparedPeriod(
                        HttpDataSource.HttpDataSourceException(
                            "mất mạng", spec, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, HttpDataSource.HttpDataSourceException.TYPE_OPEN,
                        ),
                    )

                    override fun releaseMediaPeriod(mediaPeriod: MediaPeriod) {
                        if (mediaPeriod !is NeverPreparedPeriod) super.releaseMediaPeriod(mediaPeriod)
                    }
                }
            }
            return FakeMediaSource(timeline, ExoPlayerTestRunner.VIDEO_FORMAT, ExoPlayerTestRunner.AUDIO_FORMAT)
        }

        override fun createPlayer(context: Context): ExoPlayer = TestExoPlayerBuilder(context).setClock(clock).build()
    }

    /** Phần video không bao giờ tải xong và báo lỗi [error] (mất mạng, 403…). */
    private class NeverPreparedPeriod(private val error: IOException) : MediaPeriod {
        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) = Unit
        override fun maybeThrowPrepareError() = throw error
        override fun getTrackGroups() = androidx.media3.exoplayer.source.TrackGroupArray.EMPTY
        override fun selectTracks(
            selections: Array<out androidx.media3.exoplayer.trackselection.ExoTrackSelection?>,
            mayRetainStreamFlags: BooleanArray,
            streams: Array<androidx.media3.exoplayer.source.SampleStream?>,
            streamResetFlags: BooleanArray,
            positionUs: Long,
        ) = positionUs
        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) = Unit
        override fun readDiscontinuity() = C.TIME_UNSET
        override fun seekToUs(positionUs: Long) = positionUs
        override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: androidx.media3.exoplayer.SeekParameters) = positionUs
        override fun getBufferedPositionUs() = 0L
        override fun getNextLoadPositionUs() = C.TIME_END_OF_SOURCE
        override fun continueLoading(loadingInfo: androidx.media3.exoplayer.LoadingInfo) = false
        override fun isLoading() = false
        override fun reevaluateBuffer(positionUs: Long) = Unit
    }

    private lateinit var yt: FakeYouTube
    private val player get() = PlaybackController.player
    private val now get() = PlaybackController.nowPlaying.value

    @Before
    fun setUp() {
        yt = FakeYouTube()
        app.store.updateSettings {
            it.copy(autoplay = true, sponsorBlock = false, returnDislike = false, personalize = false, quality = QualityMode.AUTO, captionLanguage = "")
        }
        PlaybackController.resetForTest(yt)
    }

    @After
    fun tearDown() {
        PlaybackController.resetForTest(null)
    }

    /**
     * Cho thời gian trôi (từng bước 50 ms: đồng hồ trình phát + đồng hồ hệ thống/luồng chính) cho tới khi
     * [condition] đúng. [timeoutMs] là thời gian giả lập.
     */
    private fun runUntil(what: String, timeoutMs: Long = 30_000, condition: () -> Boolean) {
        var waited = 0L
        while (!condition()) {
            if (waited > timeoutMs) {
                throw AssertionError(
                    "Không đạt sau ${timeoutMs / 1000}s: $what (nowPlaying=${now?.video?.id}, state=${player.playbackState}, " +
                        "pos=${player.currentPosition}, error=${PlaybackController.error.value}, calls=${yt.playerCalls})",
                )
            }
            advance(50)
            waited += 50
        }
    }

    /** Để thời gian trôi [ms] (giả lập). */
    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(50L, left)
            yt.clock.advanceTime(step)
            Thread.sleep(1) // cho luồng phát (chạy riêng) xử lý
            shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
            left -= step
        }
    }

    private fun openAndWaitReady(id: String = A) {
        PlaybackController.play(video(id), resume = false)
        runUntil("video $id sẵn sàng phát") { player.playbackState == Player.STATE_READY && now?.watch != null }
    }

    // ------------------------------------------------------------------ Tự chuyển video

    @Test
    fun autoplayQueuesNextVideoAndSwitchesWithoutStopping() {
        openAndWaitReady(A)
        val states = mutableListOf<Int>()
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { states += state }
        })
        // Tua tới còn 40 giây; bộ đếm 5 giây/lần của app sẽ tự xếp sẵn video kế tiếp khi còn ≤ 30 giây.
        player.seekTo(player.duration - 40_000)
        runUntil("đã xếp sẵn video kế tiếp") { PlaybackController.queuedVideoId == rel(A, 1) }
        assertTrue("Xếp sẵn khi còn ≤ 30 giây", player.duration - player.currentPosition <= 31_000)
        assertEquals(2, player.mediaItemCount)

        // Hết video A → ExoPlayer tự chuyển sang A-r1, không dừng (không qua trạng thái ENDED/IDLE).
        runUntil("chuyển sang video kế tiếp") { now?.video?.id == rel(A, 1) && now?.watch != null }
        assertFalse("Không được dừng hẳn giữa hai video", states.contains(Player.STATE_ENDED) || states.contains(Player.STATE_IDLE))
        assertTrue(player.playWhenReady)
        assertEquals(1, player.mediaItemCount) // video cũ đã bị bỏ khỏi danh sách
        assertEquals(rel(A, 1), player.currentMediaItem?.mediaId)
        assertTrue("Nút Trước phải quay lại được video A", PlaybackController.hasPrevious())
        assertEquals("Lấy link phát video kế tiếp đúng 1 lần (lúc xếp sẵn)", 1, yt.playerCalls.count { it == rel(A, 1) })
        assertEquals(rel(rel(A, 1), 1), now?.related?.first()?.id) // đề xuất của video mới
        assertEquals(0L, app.store.positionOf(A)) // xem hết → lần sau xem lại từ đầu
    }

    @Test
    fun notificationNextButtonSwitchesToQueuedVideo() {
        openAndWaitReady(A)
        player.seekTo(player.duration - 40_000)
        runUntil("đã xếp sẵn") { PlaybackController.queuedVideoId == rel(A, 1) }
        player.seekToNext() // nút "Tiếp" trên thanh thông báo / màn hình khoá / tai nghe
        runUntil("giao diện chuyển theo") { now?.video?.id == rel(A, 1) && now?.watch != null && player.isPlaying }
        assertEquals(1, player.mediaItemCount)
        assertEquals(rel(A, 1), player.currentMediaItem?.mediaId)
    }

    @Test
    fun queuedNextFailsToLoadThenItIsOpenedNormally() {
        openAndWaitReady(A)
        yt.streamFailures = 1 // video kế tiếp xếp sẵn sẽ không tải được
        player.seekTo(player.duration - 40_000)
        runUntil("đã xếp sẵn") { PlaybackController.queuedVideoId == rel(A, 1) }
        runUntil("mở video kế tiếp bằng link mới", 60_000) {
            now?.video?.id == rel(A, 1) && player.playbackState == Player.STATE_READY && player.isPlaying
        }
        assertNull(PlaybackController.error.value)
        assertEquals(2, yt.playerCalls.count { it == rel(A, 1) })
    }

    @Test
    fun androidNetworkCallbackTriggersRecovery() {
        yt.networkDown = true
        PlaybackController.play(video(A), resume = false)
        runUntil("báo lỗi mạng") { PlaybackController.error.value != null }
        yt.networkDown = false
        // Android báo có mạng (Wi-Fi mới / 4G) → app tự thử lại, người dùng không cần bấm gì.
        val cm = app.getSystemService(android.net.ConnectivityManager::class.java)
        val callbacks = shadowOf(cm).networkCallbacks
        assertTrue("App phải đăng ký theo dõi mạng", callbacks.isNotEmpty())
        callbacks.forEach { it.onAvailable(org.robolectric.shadows.ShadowNetwork.newInstance(4242)) }
        runUntil("tự phát lại") { player.playbackState == Player.STATE_READY && now?.watch != null }
        assertNull(PlaybackController.error.value)
    }

    @Test
    fun autoplayAlsoWorksWhenNextWasNotQueued() {
        openAndWaitReady(A)
        // Nhảy thẳng tới cuối (không kịp xếp sẵn) → vẫn tự mở video kế tiếp khi hết.
        player.seekTo(player.duration - 100)
        runUntil("phát video kế tiếp") { now?.video?.id == rel(A, 1) && player.playbackState == Player.STATE_READY }
    }

    @Test
    fun noAutoplayWhenTurnedOffOrLooping() {
        app.store.updateSettings { it.copy(autoplay = false) }
        openAndWaitReady(A)
        player.seekTo(player.duration - 20_000)
        PlaybackController.tickForTest()
        advance(1_000)
        assertNull(PlaybackController.queuedVideoId)
        runUntil("hết video") { player.playbackState == Player.STATE_ENDED }
        advance(2_000)
        assertEquals(A, now?.video?.id)

        // Bật tự phát, xếp sẵn rồi bật "Lặp lại" → bỏ video đã xếp.
        app.store.updateSettings { it.copy(autoplay = true) }
        openAndWaitReady(B)
        player.seekTo(player.duration - 20_000)
        PlaybackController.tickForTest()
        runUntil("đã xếp sẵn") { PlaybackController.queuedVideoId != null }
        PlaybackController.setLoop(true)
        assertNull(PlaybackController.queuedVideoId)
        assertEquals(1, player.mediaItemCount)
    }

    @Test
    fun sleepTimerEndOfVideoStopsInsteadOfPlayingNext() {
        openAndWaitReady(A)
        player.seekTo(player.duration - 20_000)
        PlaybackController.tickForTest()
        runUntil("đã xếp sẵn") { PlaybackController.queuedVideoId != null }
        PlaybackController.setSleepTimer(0) // "Khi video kết thúc"
        assertNull(PlaybackController.queuedVideoId)
        runUntil("hết video") { player.playbackState == Player.STATE_ENDED }
        advance(2_000)
        assertEquals(A, now?.video?.id)
    }

    // ------------------------------------------------------------------ Video không phát được

    @Test
    fun autoplaySkipsVideoThatCannotPlay() {
        yt.unplayable += rel(A, 1)
        openAndWaitReady(A)
        player.seekTo(player.duration - 40_000)
        runUntil("bỏ qua video không phát được, phát video tiếp theo", 90_000) {
            now?.video?.id == rel(A, 2) && player.isPlaying
        }
        assertNull(PlaybackController.error.value)
    }

    @Test
    fun autoplaySkipsVideoWhoseStreamsAreBlocked() {
        yt.forbidden += rel(A, 1) // lấy được link nhưng tải luồng bị 403
        openAndWaitReady(A)
        player.seekTo(player.duration - 40_000)
        runUntil("đã xếp sẵn") { PlaybackController.queuedVideoId == rel(A, 1) }
        // Video bị chặn được thử lại (link mới, NewPipe) rồi bỏ qua sang video liên quan tiếp theo.
        runUntil("bỏ qua video bị chặn, phát video tiếp theo", 120_000) {
            now?.video?.id.let { it != A && it != rel(A, 1) } && player.isPlaying
        }
        assertNull(PlaybackController.error.value)
        assertTrue("Video bị chặn không được phát lại nữa", PlaybackController.hasPrevious())
    }

    @Test
    fun videoOpenedByUserThatCannotPlayShowsError() {
        yt.unplayable += B
        PlaybackController.play(video(B), resume = false)
        runUntil("báo lỗi") { PlaybackController.error.value != null }
        assertEquals("Video không có sẵn.", PlaybackController.error.value)
        advance(5_000)
        assertEquals("Người dùng tự bấm thì không tự nhảy sang video khác", B, now?.video?.id)
    }

    @Test
    fun autoplayStopsSkippingAfterThreeFailures() {
        yt.allUnplayableExcept = A
        openAndWaitReady(A)
        player.seekTo(player.duration - 40_000)
        runUntil("dừng lại và báo lỗi", 120_000) { PlaybackController.error.value != null }
        assertEquals("Video không có sẵn.", PlaybackController.error.value)
        val tried = yt.playerCalls.filter { it != A }.toSet().size
        // Tối đa 6 lần thử chuẩn bị (mỗi 5 giây trong 30 giây cuối) + video tự phát đầu tiên + 3 lần bỏ qua.
        assertTrue("Đã thử $tried video", tried in 4..10)
        advance(60_000)
        assertEquals("Không thử mãi", tried, yt.playerCalls.filter { it != A }.toSet().size)
    }

    // ------------------------------------------------------------------ Mất mạng / có mạng lại

    @Test
    fun noNetworkWhenOpeningThenRecoversOnReconnect() {
        yt.networkDown = true
        PlaybackController.play(video(A), resume = false)
        runUntil("báo lỗi mạng") { PlaybackController.error.value != null && now?.watchFailed == true }
        assertEquals("Không có kết nối mạng", PlaybackController.error.value)
        assertFalse(PlaybackController.loading.value)
        assertEquals("Mất mạng thì không thử NewPipe (chỉ tốn thêm thời gian chờ)", 0, yt.fallbackCalls)

        // Có mạng lại (NetworkMonitor / bật màn hình gọi hàm này) → tự tải lại video và đề xuất.
        yt.networkDown = false
        PlaybackController.onResumeOrReconnect()
        runUntil("phát lại được") { player.playbackState == Player.STATE_READY && now?.watch != null }
        assertNull(PlaybackController.error.value)
        assertFalse(now!!.watchFailed)
        assertEquals(20, now!!.related.size)
    }

    @Test
    fun networkDropWhileStreamingRetriesWithBackoff() {
        yt.streamFailures = 2 // 2 lần đầu ExoPlayer không tải được luồng (vừa bật màn hình, mạng chưa sẵn sàng)
        PlaybackController.play(video(A), resume = false)
        runUntil("phát được sau khi thử lại", 60_000) { player.playbackState == Player.STATE_READY }
        assertNull(PlaybackController.error.value)
        assertEquals("Lấy link 3 lần: lần đầu + 2 lần thử lại", 3, yt.playerCalls.count { it == A })
    }

    @Test
    fun givesUpAfterRetriesThenTapToRetryWorks() {
        yt.streamFailures = 10
        PlaybackController.play(video(A), resume = false)
        runUntil("báo lỗi sau khi thử lại vài lần", 60_000) { PlaybackController.error.value != null }
        assertTrue(PlaybackController.error.value!!.contains("Nhấn để thử lại"))
        yt.streamFailures = 0
        PlaybackController.retry()
        runUntil("phát lại được") { player.playbackState == Player.STATE_READY }
    }

    @Test
    fun relatedFailsThenTapToRetry() {
        yt.watchDown = true
        PlaybackController.play(video(A), resume = false)
        runUntil("video phát, đề xuất báo lỗi") { player.playbackState == Player.STATE_READY && now?.watchFailed == true }
        yt.watchDown = false
        PlaybackController.reloadWatch()
        runUntil("có đề xuất") { now?.watch != null }
        assertEquals(20, now!!.related.size)
    }

    // ------------------------------------------------------------------ Tạm dừng lâu / link hết hạn

    @Test
    fun expiredLinkIsRefreshedOnResumeKeepingPositionAndPause() {
        yt.expiresInMs = 10 * 60_000L
        openAndWaitReady(A)
        player.seekTo(42_000)
        PlaybackController.pause()
        runUntil("đã tạm dừng ở 42 giây") { !player.isPlaying && player.currentPosition in 41_000..43_000 }

        ShadowSystemClock.advanceBy(Duration.ofMinutes(30)) // tắt màn hình nửa tiếng → link hết hạn
        val before = yt.playerCalls.size
        PlaybackController.onResumeOrReconnect()
        runUntil("đã lấy link mới") { yt.playerCalls.size == before + 1 && player.playbackState == Player.STATE_READY }
        assertFalse("Vẫn giữ trạng thái tạm dừng", player.playWhenReady)
        assertTrue("Giữ nguyên chỗ đang xem", player.currentPosition in 41_000..43_000)

        // Bấm phát khi link còn hạn → phát luôn, không tải lại.
        PlaybackController.togglePlayPause()
        runUntil("đang phát") { player.isPlaying }
        assertEquals(before + 1, yt.playerCalls.size)
    }

    @Test
    fun pressingPlayAfterLongPauseFetchesNewLink() {
        yt.expiresInMs = 10 * 60_000L
        openAndWaitReady(A)
        player.seekTo(30_000)
        PlaybackController.pause()
        runUntil("tạm dừng") { !player.isPlaying }
        ShadowSystemClock.advanceBy(Duration.ofHours(7))
        val before = yt.playerCalls.size
        PlaybackController.togglePlayPause()
        runUntil("phát tiếp bằng link mới") { yt.playerCalls.size == before + 1 && player.isPlaying }
        assertTrue(player.currentPosition >= 29_000)
    }

    // ------------------------------------------------------------------ Đề xuất

    @Test
    fun relatedListLoadsMoreWithoutDuplicates() {
        openAndWaitReady(A)
        assertEquals(20, now!!.related.size)
        PlaybackController.loadMoreRelated()
        runUntil("tải thêm đề xuất") { now!!.related.size > 20 }
        assertEquals(40, now!!.related.size)
        assertEquals(now!!.related.size, now!!.related.map { it.id }.toSet().size)
        assertEquals("token2-$A", now!!.relatedContinuation)
        assertFalse(PlaybackController.relatedLoading.value)
    }

    @Test
    fun relatedLoadMoreFailureKeepsTokenForRetry() {
        openAndWaitReady(A)
        yt.networkDown = true
        PlaybackController.loadMoreRelated()
        runUntil("xong lần tải lỗi") { yt.moreCalls == 1 && !PlaybackController.relatedLoading.value }
        assertEquals("token-$A", now!!.relatedContinuation)
        yt.networkDown = false
        PlaybackController.loadMoreRelated()
        runUntil("tải thêm được") { now!!.related.size == 40 }
    }

    // ------------------------------------------------------------------ Shorts dọc

    @Test
    fun portraitShortsDataSaverLimitsWidthNotHeight() {
        app.store.updateSettings { it.copy(quality = QualityMode.DATA_SAVER) }
        yt.portrait = true
        PlaybackController.playShort(S1)
        runUntil("Shorts sẵn sàng") { player.playbackState == Player.STATE_READY }
        val p = player.trackSelectionParameters
        assertEquals(360, p.maxVideoWidth)
        assertEquals(Int.MAX_VALUE, p.maxVideoHeight)
        assertEquals(listOf(1080, 720, 480, 360), now!!.availableHeights)
        // Video ngang: giới hạn chiều cao như cũ.
        yt.portrait = false
        PlaybackController.play(video(H), resume = false)
        runUntil("video ngang sẵn sàng") { player.playbackState == Player.STATE_READY && now?.video?.id == H }
        assertEquals(360, player.trackSelectionParameters.maxVideoHeight)
        assertEquals(Int.MAX_VALUE, player.trackSelectionParameters.maxVideoWidth)
    }

    // ------------------------------------------------------------------ Tắt / bật màn hình (MainActivity thật)

    @Test
    fun screenOffThenOnRecoversVideoAndRecommendations() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            openAndWaitReady(A)

            // Tắt màn hình: tiếp tục phát tiếng, tắt hình để đỡ tốn mạng.
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes)
            assertTrue("Vẫn phát tiếng khi tắt màn hình", player.playWhenReady)

            // Trong lúc tắt màn hình: mất mạng, video kế tiếp không tải được đề xuất.
            yt.networkDown = true
            PlaybackController.play(video(B), resume = false)
            runUntil("B lỗi vì mất mạng") { PlaybackController.error.value != null && now?.watchFailed == true }

            // 1 phút sau bật màn hình, mạng đã có lại.
            ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
            yt.networkDown = false
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertFalse(C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes)
            runUntil("bật màn hình → tự phát lại B và có đề xuất") {
                player.playbackState == Player.STATE_READY && now?.video?.id == B && now?.watch != null
            }
            assertNull(PlaybackController.error.value)
            assertEquals(20, now!!.related.size)
        }
    }

    @Test
    fun videoEndsWhileScreenOffNextStartsAndScreenOnShowsIt() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            openAndWaitReady(A)
            player.seekTo(player.duration - 45_000)
            scenario.moveToState(Lifecycle.State.CREATED) // tắt màn hình, nghe tiếp
            runUntil("tắt màn hình vẫn tự chuyển sang video kế tiếp", 90_000) {
                now?.video?.id == rel(A, 1) && player.isPlaying
            }
            assertTrue("Vẫn chỉ tải tiếng khi tắt màn hình", C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes)
            ShadowSystemClock.advanceBy(Duration.ofMinutes(2))
            scenario.moveToState(Lifecycle.State.RESUMED) // bật màn hình
            assertFalse(C.TRACK_TYPE_VIDEO in player.trackSelectionParameters.disabledTrackTypes)
            runUntil("có đề xuất của video mới") { now?.watch != null }
            assertEquals(rel(rel(A, 1), 1), now!!.related.first().id)
            assertTrue(player.isPlaying)
        }
    }

    @Test
    fun shortScreenOffDoesNotReloadAnything() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            openAndWaitReady(A)
            val calls = yt.playerCalls.size
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED) // tắt/bật nhanh (< 30 giây)
            advance(1_000)
            assertEquals(calls, yt.playerCalls.size)
            assertEquals(Player.STATE_READY, player.playbackState)
            assertNotNull(now?.watch)
        }
    }

    companion object {
        // Mã video YouTube luôn dài 11 ký tự.
        const val A = "videoAAAAAA"
        const val B = "videoBBBBBB"
        const val H = "videoHHHHHH"
        const val S1 = "shortS1S1S1"

        /** Video liên quan thứ [n] của video [id] (mã 11 ký tự, cố định). */
        fun rel(id: String, n: Int): String = "r" + Integer.toHexString((id + "#" + n).hashCode()).padStart(8, '0') + "%02d".format(n % 100)

        fun video(id: String) = VideoItem.ofId(id, title = "Video $id", uploader = "Kênh").copy(durationSec = 120)

        fun playerData(id: String, expiresInMs: Long, portrait: Boolean): PlayerData {
            fun fmt(itag: Int, w: Int, h: Int) = StreamFormat(
                itag, "https://x/$id/$itag", "video/mp4", "avc1.4d401f", w * h, w, h, 30, 0, 700, 701, 1500, 0, 0, null, false,
            )
            val sizes = listOf(1920 to 1080, 1280 to 720, 854 to 480, 640 to 360)
            val video = sizes.mapIndexed { i, (w, h) -> if (portrait) fmt(137 - i, h, w) else fmt(137 - i, w, h) }
            val audio = StreamFormat(140, "https://x/$id/a", "audio/mp4", "mp4a.40.2", 128_000, 0, 0, 0, 0, 600, 601, 900, 44100, 2, null, false)
            return PlayerData(
                id, "Video $id", "Kênh", "UCx", 120, 1, false, null, video + audio,
                null, null, null, android.os.SystemClock.elapsedRealtime() + expiresInMs,
            )
        }
    }
}
