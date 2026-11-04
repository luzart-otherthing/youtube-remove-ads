package com.luzart.tubesach

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.StreamSourceFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Kiểm tra toàn bộ đường đi từ YouTube tới trình phát (cần mạng):
 * tìm kiếm → lấy thông tin video → dựng nguồn DASH → ExoPlayer đọc được luồng hình + tiếng.
 * Chạy: ./gradlew :app:testDebugUnitTest -Donline=true --tests '*OnlinePipelineTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlinePipelineTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("online") == "true")
    }

    @Test
    fun searchReturnsVietnameseResultsWithThumbnails() {
        val (items, session) = runBlocking { YouTubeRepository.search("cải lương") }
        assertTrue("Phải có kết quả", items.size >= 10)
        val first = items.first()
        println("Kết quả đầu: ${first.title} | ${first.thumbnail}")
        assertNotNull(first.thumbnail)
        app.http.newCall(Request.Builder().url(first.thumbnail!!).build()).execute().use {
            println("Ảnh thu nhỏ HTTP ${it.code} ${it.header("Content-Type")}")
            assertTrue(it.isSuccessful)
        }
        val more = runBlocking { YouTubeRepository.loadMore(session) }
        assertTrue("Trang 2 phải có kết quả", more.isNotEmpty())
        val sugg = runBlocking { YouTubeRepository.suggestions("cai luong") }
        println("Gợi ý: $sugg")
        assertTrue(sugg.isNotEmpty())
    }

    @Test
    fun exoPlayerLoadsVideoAndAudioTracks() {
        val url = System.getProperty("videoUrl") ?: "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val info = runBlocking { YouTubeRepository.streamInfo(url) }
        val video = VideoItem.from(info)
        println("Video: ${video.title} (${video.durationSec}s), liên quan: ${YouTubeRepository.relatedOf(info).size}")
        val chosenVideo = StreamSourceFactory.pickVideo(info.videoOnlyStreams, 720)
        val chosenAudio = StreamSourceFactory.pickAudio(info.audioStreams)
        println("Chọn hình: ${chosenVideo?.getResolution()} ${chosenVideo?.format} ${chosenVideo?.codec}")
        println("Chọn tiếng: ${chosenAudio?.averageBitrate}kbps ${chosenAudio?.format}")

        val source = StreamSourceFactory(app).build(info, 720, MediaMetadata.Builder().setTitle(video.title).build())
        val player = ExoPlayer.Builder(app).build()
        var tracks: Tracks? = null
        var error: PlaybackException? = null
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(t: Tracks) {
                if (!t.isEmpty) tracks = t
            }

            override fun onPlayerError(e: PlaybackException) {
                error = e
            }
        })
        player.setMediaSource(source)
        player.prepare()

        val deadline = System.currentTimeMillis() + 30_000
        while (tracks == null && error == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            shadowOf(Looper.getMainLooper()).idle()
        }
        player.release()

        error?.let { println("Lỗi phát: ${it.errorCodeName} ${it.cause}") }
        val t = tracks
        assertNotNull("ExoPlayer phải đọc được danh sách luồng (lỗi: ${error?.errorCodeName} ${error?.cause})", t)
        val types = t!!.groups.map { it.type }
        println("Các luồng ExoPlayer đọc được: " + t.groups.joinToString { g -> "${g.type}:${g.getTrackFormat(0).sampleMimeType}" })
        assertTrue("Phải có luồng hình", C.TRACK_TYPE_VIDEO in types)
        assertTrue("Phải có luồng tiếng", C.TRACK_TYPE_AUDIO in types)
    }
}
