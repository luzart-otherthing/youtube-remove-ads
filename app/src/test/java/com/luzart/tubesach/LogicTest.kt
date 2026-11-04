package com.luzart.tubesach

import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.StreamSourceFactory
import com.luzart.tubesach.ui.components.formatDuration
import com.luzart.tubesach.ui.components.formatViews
import com.luzart.tubesach.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale

class LogicTest {

    @Test
    fun findsYoutubeLinksInSharedText() {
        val id = "dQw4w9WgXcQ"
        val expected = "https://www.youtube.com/watch?v=$id"
        assertEquals(expected, VideoItem.findYoutubeUrl("Xem cái này hay lắm https://youtu.be/$id?si=abc"))
        assertEquals(expected, VideoItem.findYoutubeUrl("https://m.youtube.com/watch?v=$id&t=30"))
        assertEquals(expected, VideoItem.findYoutubeUrl("https://www.youtube.com/shorts/$id"))
        assertEquals(expected, VideoItem.findYoutubeUrl("https://youtube.com/live/$id?feature=share"))
        assertNull(VideoItem.findYoutubeUrl("https://zalo.me/abc"))
        assertNull(VideoItem.findYoutubeUrl(null))
    }

    @Test
    fun comparesVersions() {
        assertTrue(UpdateChecker.isNewer("1.0.12", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.99"))
        assertFalse(UpdateChecker.isNewer("1.0.5", "1.0.5"))
        assertFalse(UpdateChecker.isNewer("1.0.4", "1.0.5-debug"))
    }

    @Test
    fun formatsForHumans() {
        assertEquals("3:05", formatDuration(185))
        assertEquals("1:02:03", formatDuration(3723))
        assertEquals("", formatDuration(0))
        assertEquals("1,2 triệu lượt xem", formatViews(1_234_567))
        assertEquals("15 nghìn lượt xem", formatViews(15_000))
        assertEquals("999 lượt xem", formatViews(999))
        assertNull(formatViews(-1))
    }

    private fun video(res: String, format: MediaFormat, videoOnly: Boolean = true) = VideoStream.Builder()
        .setId("$res-${format.name}")
        .setContent("https://rr1.googlevideo.com/videoplayback?itag=1&res=$res&f=${format.name}", true)
        .setMediaFormat(format)
        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
        .setIsVideoOnly(videoOnly)
        .setResolution(res)
        .build()

    private fun audio(kbps: Int, format: MediaFormat, type: AudioTrackType? = null, locale: Locale? = null) =
        AudioStream.Builder()
            .setId("$kbps-${format.name}-$type")
            .setContent("https://rr1.googlevideo.com/videoplayback?itag=2&kbps=$kbps&t=$type", true)
            .setMediaFormat(format)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .setAverageBitrate(kbps)
            .apply { if (type != null) setAudioTrackType(type) }
            .apply { if (locale != null) setAudioLocale(locale) }
            .build()

    @Test
    fun picksBestVideoWithinLimitPreferringMp4() {
        val streams = listOf(
            video("1080p", MediaFormat.WEBM), video("1080p", MediaFormat.MPEG_4),
            video("720p", MediaFormat.WEBM), video("720p", MediaFormat.MPEG_4),
            video("360p", MediaFormat.MPEG_4), video("144p", MediaFormat.WEBM),
        )
        val p720 = StreamSourceFactory.pickVideo(streams, 720)!!
        assertEquals("720p", p720.getResolution())
        assertEquals(MediaFormat.MPEG_4, p720.format)
        assertEquals("360p", StreamSourceFactory.pickVideo(streams, 480)!!.getResolution())
        // Không có luồng nào thấp hơn giới hạn → lấy luồng thấp nhất.
        assertEquals("144p", StreamSourceFactory.pickVideo(streams, 100)!!.getResolution())
        assertNull(StreamSourceFactory.pickVideo(emptyList(), 720))
    }

    @Test
    fun picksOriginalAacAudio() {
        val streams = listOf(
            audio(160, MediaFormat.WEBMA_OPUS, AudioTrackType.ORIGINAL),
            audio(128, MediaFormat.M4A, AudioTrackType.DUBBED, Locale.forLanguageTag("es")),
            audio(48, MediaFormat.M4A, AudioTrackType.ORIGINAL),
            audio(128, MediaFormat.M4A, AudioTrackType.ORIGINAL),
        )
        val a = StreamSourceFactory.pickAudio(streams)!!
        assertEquals(128, a.averageBitrate)
        assertEquals(AudioTrackType.ORIGINAL, a.audioTrackType)
        assertEquals(MediaFormat.M4A, a.format)

        val withVietnamese = streams + audio(128, MediaFormat.M4A, AudioTrackType.DUBBED, Locale.forLanguageTag("vi"))
        assertEquals("vi", StreamSourceFactory.pickAudio(withVietnamese)!!.audioLocale!!.language)
    }
}
