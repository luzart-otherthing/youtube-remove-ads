package com.luzart.tubesach

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.StreamSourceFactory
import com.luzart.tubesach.sponsor.SponsorBlock
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.json.JSONArray
import org.junit.Assert.assertEquals
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
 * Kiểm tra với YouTube thật (cần mạng):
 * ./gradlew :app:testDebugUnitTest -Donline=true --tests '*OnlinePipelineTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlinePipelineTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("online") == "true")
    }

    private fun loadTracks(source: MediaSource, textOn: Boolean = false): Tracks {
        val player = ExoPlayer.Builder(app).build()
        if (textOn) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage("en").build()
        }
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
        assertNotNull("ExoPlayer phải đọc được luồng (lỗi: ${error?.errorCodeName} ${error?.cause})", tracks)
        println("Các luồng: " + tracks!!.groups.joinToString { g -> "${g.type}:${g.getTrackFormat(0).sampleMimeType}x${g.length}" })
        return tracks!!
    }

    @Test
    fun directPlayerPathWithQualitiesAndCaptions() {
        val id = System.getProperty("videoId") ?: "dQw4w9WgXcQ"
        val t0 = System.currentTimeMillis()
        val data = runBlocking { YouTubeRepository.player(id) }
        println("visionOS /player: ${System.currentTimeMillis() - t0} ms, độ phân giải ${data.availableHeights}, phụ đề ${data.captions.size}")
        assertNotNull(data.dashManifest())
        assertNotNull(data.playbackTrackingUrl)
        val caption = data.captions.firstOrNull()
        val sub = caption?.let {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.vttUrl()))
                .setMimeType(MimeTypes.TEXT_VTT).setLanguage(it.languageCode).setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        }
        val source = StreamSourceFactory(app).buildDirect(data, MediaMetadata.Builder().setTitle(data.title).build(), sub)
        val tracks = loadTracks(source, textOn = sub != null)
        val video = tracks.groups.first { it.type == C.TRACK_TYPE_VIDEO }
        assertTrue("Phải có nhiều độ phân giải để tự đổi chất lượng", video.length >= 3)
        assertTrue(tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO })
        if (sub != null) assertTrue("Phải có luồng phụ đề", tracks.groups.any { it.type == C.TRACK_TYPE_TEXT })
    }

    /** Video 4K: ExoPlayer đọc được luồng VP9 2160p/1440p (dùng cho app TV có chip giải mã 4K). */
    @Test
    fun vp9UpTo4kForTv() {
        val id = "LXb3EKWsInQ"
        val data = runBlocking { YouTubeRepository.player(id) }
        val policy = com.luzart.tubesach.innertube.StreamPolicy(com.luzart.tubesach.innertube.VideoCodec.VP9, 2160)
        println("4K: độ phân giải cho TV ${data.availableHeights(policy)}, cho điện thoại ${data.availableHeights}")
        assertTrue(2160 in data.availableHeights(policy))
        val source = StreamSourceFactory(app).buildDirect(data, MediaMetadata.Builder().build(), null, policy)
        val tracks = loadTracks(source)
        val video = tracks.groups.first { it.type == C.TRACK_TYPE_VIDEO }
        val heights = (0 until video.length).map { video.getTrackFormat(it).height }
        println("ExoPlayer: ${video.getTrackFormat(0).sampleMimeType} $heights")
        assertEquals(MimeTypes.VIDEO_VP9, video.getTrackFormat(0).sampleMimeType)
        assertTrue(2160 in heights && 1440 in heights)
    }

    @Test
    fun newPipeFallbackPath() {
        val info = runBlocking { YouTubeRepository.streamInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
        val source = StreamSourceFactory(app).build(info, 720, MediaMetadata.Builder().build())
        val tracks = loadTracks(source)
        assertTrue(tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO })
    }

    @Test
    fun innerTubeBrowseSearchWatch() {
        val home = runBlocking { YouTubeRepository.home() }
        println("Trang chủ (khách mới): ${home.entries.size} mục")
        val search = runBlocking { YouTubeRepository.search("cải lương") }
        val videos = search.entries.filterIsInstance<FeedEntry.Video>().map { it.video }
        println("Tìm kiếm: ${videos.size} video, ví dụ: ${videos.firstOrNull()?.title} | ${videos.firstOrNull()?.viewText}")
        assertTrue(videos.size >= 10)
        val more = runBlocking { YouTubeRepository.searchMore(search.continuation!!) }
        assertTrue(more.entries.isNotEmpty())
        val w = runBlocking { YouTubeRepository.watch(videos.first().id) }
        println("Trang xem: ${w.title} | ${w.viewText} | ${w.likeText} | ${w.channel?.name} | liên quan ${w.related.size}")
        assertTrue(w.related.isNotEmpty())
        val trending = runBlocking { YouTubeRepository.trendingVietnam() }
        println("Thịnh hành VN: ${trending.entries.size} video")
        assertTrue(trending.entries.isNotEmpty())
        val sugg = runBlocking { YouTubeRepository.suggestions("cai luong") }
        assertTrue(sugg.isNotEmpty())
    }

    @Test
    fun sponsorBlockReturnsSegments() {
        // Lấy một video có đoạn tài trợ từ cơ sở dữ liệu, rồi tra lại bằng mã băm 4 ký tự.
        val bucket = app.http.newCall(
            Request.Builder().url("https://sponsor.ajay.app/api/skipSegments/5f6b?categories=%5B%22sponsor%22%5D").build(),
        ).execute().use { JSONArray(it.body!!.string()) }
        val id = bucket.getJSONObject(0).getString("videoID")
        val segs = runBlocking { SponsorBlock(app.http).segments(id, listOf("sponsor")) }
        println("SponsorBlock $id: $segs")
        assertTrue(segs.isNotEmpty())
    }

    @Test
    fun thumbnailsLoad() {
        val v = runBlocking { YouTubeRepository.search("bolero") }.entries.filterIsInstance<FeedEntry.Video>().first().video
        for (url in listOfNotNull(v.thumbnail, v.channelAvatar)) {
            app.http.newCall(Request.Builder().url(url).build()).execute().use { assertTrue("$url → ${it.code}", it.isSuccessful) }
        }
        assertNotNull(VideoItem.videoIdOf(v.url))
    }
}
