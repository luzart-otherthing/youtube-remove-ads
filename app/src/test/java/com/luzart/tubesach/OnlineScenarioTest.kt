package com.luzart.tubesach

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.player.StreamSourceFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Kịch bản với YouTube THẬT (mạng thật, luồng DASH thật qua OkHttp; chỉ bộ giải mã là giả):
 * ./gradlew :app:testDebugUnitTest -Donline=true --tests '*OnlineScenarioTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlineScenarioTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()
    private val clock = FakeClock(false)
    private val player get() = PlaybackController.player
    private val now get() = PlaybackController.nowPlaying.value

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("online") == "true")
        app.store.updateSettings {
            it.copy(autoplay = true, sponsorBlock = false, returnDislike = false, personalize = false, quality = QualityMode.DATA_SAVER, captionLanguage = "")
        }
        val sources = StreamSourceFactory(app, app.http)
        PlaybackController.resetForTest(
            object : PlaybackController.Backend {
                override suspend fun player(videoId: String) = YouTubeRepository.player(videoId)
                override suspend fun watch(videoId: String) = YouTubeRepository.watch(videoId)
                override suspend fun relatedMore(token: String) = YouTubeRepository.relatedMore(token)
                override suspend fun streamInfo(url: String) = YouTubeRepository.streamInfo(url)
                override fun buildDirect(data: PlayerData, metadata: MediaMetadata, subtitle: MediaItem.SubtitleConfiguration?, vp9: com.luzart.tubesach.innertube.Vp9Policy?) =
                    sources.buildDirect(data, metadata, subtitle, vp9)
                override fun createPlayer(context: Context): ExoPlayer = TestExoPlayerBuilder(context).setClock(clock).build()
            },
        )
    }

    @After
    fun tearDown() {
        if (System.getProperty("online") == "true") {
            com.luzart.tubesach.player.VideoCodecSupport.resetForTest()
            app.store.setVp9Broken(false)
            PlaybackController.resetForTest(null)
        }
    }

    /** Nhóm hình đang được chọn + độ phân giải (cạnh ngắn) của luồng đang chọn trong nhóm. */
    private fun selectedVideo(): Pair<String?, Int>? {
        val g = player.currentTracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && it.isSelected } ?: return null
        val i = (0 until g.length).firstOrNull { g.isTrackSelected(it) } ?: return null
        val f = g.getTrackFormat(i)
        return f.sampleMimeType to minOf(f.width, f.height)
    }

    @Test
    fun realVp9StreamsIncluding4k() {
        // Giả lập điện thoại có bộ giải mã VP9 phần cứng tới 4K.
        com.luzart.tubesach.player.VideoCodecSupport.override = { _, _, _ -> true }
        app.store.setVp9Broken(false)
        app.store.updateSettings { it.copy(preferVp9 = true, quality = QualityMode.ADVANCED, fixedHeight = 2160) }
        PlaybackController.play(VideoItem.ofId("dQw4w9WgXcQ"), resume = false)
        runUntil("phát luồng VP9 4K thật") {
            player.playbackState == Player.STATE_READY && selectedVideo() == (androidx.media3.common.MimeTypes.VIDEO_VP9 to 2160)
        }
        println("Độ phân giải có thể chọn: ${now!!.availableHeights}")
        assertEquals(2160, now!!.availableHeights.first())
        // Tải thật được vài giây hình 4K từ máy chủ YouTube (tạm dừng để đo phần đã tải trước).
        val loaded = java.util.concurrent.atomic.AtomicLong()
        player.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onLoadCompleted(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
            ) {
                if (mediaLoadData.trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO &&
                    mediaLoadData.trackFormat?.sampleMimeType == androidx.media3.common.MimeTypes.VIDEO_VP9
                ) loaded.addAndGet(loadEventInfo.bytesLoaded)
            }
        })
        player.seekTo(0)
        player.pause()
        runUntil("đã tải ≥ 5 giây hình 4K") { player.bufferedPosition - player.currentPosition >= 5_000 && loaded.get() > 1_000_000 }
        println("Đã tải ${loaded.get() / 1024} KB hình VP9 4K, phía trước ${(player.bufferedPosition - player.currentPosition) / 1000} giây")
        player.play()
        assertNull(PlaybackController.error.value)

        // Về "Tự động": vẫn VP9 (tiết kiệm dữ liệu) nhưng không quá 1080p.
        PlaybackController.setQuality(QualityMode.AUTO)
        runUntil("Tự động: VP9 ≤ 1080p") {
            val v = selectedVideo()
            player.playbackState == Player.STATE_READY && v?.first == androidx.media3.common.MimeTypes.VIDEO_VP9 && v.second <= 1080
        }
        println("Tự động đang chọn: ${selectedVideo()}")
        assertNull(PlaybackController.error.value)
    }


    /** Thời gian giả lập trôi theo bước 50 ms; mỗi bước chờ thật 5 ms để mạng thật kịp tải. */
    private fun runUntil(what: String, fakeTimeoutMs: Long = 600_000, condition: () -> Boolean) {
        var waited = 0L
        while (!condition()) {
            if (waited > fakeTimeoutMs) {
                throw AssertionError("Không đạt: $what (now=${now?.video?.id}, state=${player.playbackState}, pos=${player.currentPosition}/${player.duration}, error=${PlaybackController.error.value})")
            }
            clock.advanceTime(50)
            Thread.sleep(5)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            waited += 50
        }
    }

    @Test
    fun realVideoRelatedPagingAndAutoplayToNext() {
        val id = System.getProperty("videoId") ?: "dQw4w9WgXcQ"
        PlaybackController.play(VideoItem.ofId(id), resume = false)
        val autoTransitions = mutableListOf<String?>()
        val allTransitions = mutableListOf<String?>()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                println("LỖI TRÌNH PHÁT: ${error.errorCodeName} item=${player.currentMediaItem?.mediaId} cause=${error.cause}")
                error.printStackTrace(System.out)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                println("CHUYỂN: ${mediaItem?.mediaId} reason=$reason")
                allTransitions += mediaItem?.mediaId
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) autoTransitions += mediaItem?.mediaId
            }
        })
        runUntil("video thật sẵn sàng + có đề xuất") { player.playbackState == Player.STATE_READY && now?.watch != null }
        assertNull(PlaybackController.error.value)
        val first = now!!.related.size
        println("Đề xuất ban đầu: $first, token=${now!!.relatedContinuation != null}")
        assertTrue(first >= 10)

        // 1. Cuộn xuống → tải thêm đề xuất thật.
        PlaybackController.loadMoreRelated()
        runUntil("tải thêm đề xuất") { !PlaybackController.relatedLoading.value && now!!.related.size > first }
        println("Sau khi tải thêm: ${now!!.related.size}")
        assertEquals(now!!.related.size, now!!.related.map { it.id }.toSet().size)

        // 2. Tua gần hết → app xếp sẵn video kế tiếp (link thật) → hết video ExoPlayer tự chuyển sang nó.
        player.seekTo(player.duration - 35_000)
        runUntil("xếp sẵn video kế tiếp") { PlaybackController.queuedVideoId != null }
        val queued = PlaybackController.queuedVideoId
        println("Đã xếp sẵn: $queued")
        runUntil("ExoPlayer tự chuyển sang video đã xếp sẵn") { queued in autoTransitions }
        assertTrue(PlaybackController.hasPrevious())

        // 3. Sau đó: phát được video khác, HOẶC (YouTube chặn video từ IP máy chủ test – hay gặp) dừng lại
        //    và báo lỗi sau vài lần tự bỏ qua. Không được treo, không thử mãi.
        runUntil("phát được video khác hoặc dừng có báo lỗi") {
            (now?.video?.id != id && player.isPlaying) || PlaybackController.error.value != null
        }
        if (PlaybackController.error.value == null) {
            println("Đang phát: ${now!!.video.id} ${now!!.video.title}")
            assertEquals(1, player.mediaItemCount)
        } else {
            val loads = allTransitions.size
            println("YouTube chặn video từ máy chủ này → app dừng sau khi bỏ qua: ${PlaybackController.error.value}")
            var waited = 0L
            while (waited < 60_000) { // để 1 phút trôi qua: không được tự tải thêm gì
                clock.advanceTime(50)
                Thread.sleep(2)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
                waited += 50
            }
            assertEquals("Không thử mãi", loads, allTransitions.size)
            assertTrue("Tối đa 1 video xếp sẵn + 1 tự phát + 3 lần bỏ qua", allTransitions.filterNotNull().distinct().size <= 5)
        }
    }
}
