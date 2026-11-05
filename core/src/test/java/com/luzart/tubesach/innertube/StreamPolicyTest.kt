package com.luzart.tubesach.innertube

import com.luzart.tubesach.player.CodecSupport
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Chọn luồng hình: điện thoại H.264 ≤ 1080p; TV có chip VP9 → tới 2K / 4K / 8K. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamPolicyTest {

    private fun fmt(itag: Int, mime: String, w: Int, h: Int, fps: Int = 30, br: Int = h * 5000) = JSONObject()
        .put("itag", itag).put("url", "https://rr.googlevideo.com/videoplayback?itag=$itag").put("mimeType", mime)
        .put("bitrate", br).put("width", w).put("height", h).put("fps", fps)
        .put("initRange", JSONObject().put("start", "0").put("end", "200"))
        .put("indexRange", JSONObject().put("start", "201").put("end", "900"))

    /** Giống phản hồi thật của YouTube cho một video 4K (xem FormatProbe trong app TV). */
    private fun video4k(): PlayerData {
        val formats = JSONArray()
            .put(fmt(315, "video/webm; codecs=\"vp9\"", 3840, 2160, 60))
            .put(fmt(337, "video/webm; codecs=\"vp9.2\"", 3840, 2160, 60)) // HDR – bỏ qua
            .put(fmt(308, "video/webm; codecs=\"vp9\"", 2560, 1440, 60))
            .put(fmt(299, "video/mp4; codecs=\"avc1.64002A\"", 1920, 1080, 60))
            .put(fmt(303, "video/webm; codecs=\"vp9\"", 1920, 1080, 60))
            .put(fmt(136, "video/mp4; codecs=\"avc1.4D401F\"", 1280, 720))
            .put(fmt(247, "video/webm; codecs=\"vp9\"", 1280, 720))
            .put(fmt(140, "audio/mp4; codecs=\"mp4a.40.2\"", 0, 0, 0, 130_000).put("audioSampleRate", "44100"))
        val json = JSONObject()
            .put("playabilityStatus", JSONObject().put("status", "OK"))
            .put("videoDetails", JSONObject().put("title", "4K").put("lengthSeconds", "300"))
            .put("streamingData", JSONObject().put("adaptiveFormats", formats))
        return PlayerData.parse(json, "LXb3EKWsInQ")
    }

    @Test
    fun phoneStaysOnH264Max1080() {
        val d = video4k()
        assertEquals(listOf(1080, 720), d.availableHeights(StreamPolicy.PHONE))
        val mpd = d.dashManifest(StreamPolicy.PHONE)!!
        assertTrue(mpd.contains("mimeType=\"video/mp4\""))
        assertFalse(mpd.contains("vp9"))
    }

    @Test
    fun tvWith4kChipGets2160pVp9_withoutHdr() {
        val d = video4k()
        val policy = StreamPolicy(VideoCodec.VP9, 2160)
        assertEquals(listOf(2160, 1440, 1080, 720), d.availableHeights(policy))
        val mpd = d.dashManifest(policy)!!
        assertTrue(mpd.contains("mimeType=\"video/webm\""))
        assertTrue(mpd.contains("height=\"2160\""))
        assertTrue(mpd.contains("frameRate=\"60\""))
        assertFalse("Không dùng HDR (vp9.2)", mpd.contains("vp9.2"))
        assertFalse("Một loại mã hoá duy nhất cho hình", mpd.contains("avc1"))
        assertTrue("Vẫn có tiếng AAC", mpd.contains("mp4a.40.2"))
    }

    @Test
    fun tvWith2kChipCapsAt1440() {
        assertEquals(1440, video4k().availableHeights(StreamPolicy(VideoCodec.VP9, 1440)).first())
    }

    @Test
    fun vp9OnlyUpTo1080_prefersH264() {
        // Video chỉ có tới 1080p: dùng H.264 (TV nào cũng mượt), không đổi sang VP9 vô ích.
        val d = video4k()
        val formats = d.formats.filter { it.height <= 1080 }
        val small = d.copy(formats = formats)
        val mpd = small.dashManifest(StreamPolicy(VideoCodec.VP9, 2160))!!
        assertTrue(mpd.contains("avc1"))
    }

    @Test
    fun codecSupportPicksHighestHardwareLevel() {
        assertEquals(2160, CodecSupport.pickHeight { _, h -> h <= 2160 })
        assertEquals(1440, CodecSupport.pickHeight { w, _ -> w <= 2560 })
        assertEquals(0, CodecSupport.pickHeight { _, _ -> false })
        assertEquals(StreamPolicy(VideoCodec.VP9, 2160), CodecSupport.policyFor(2160))
        assertEquals(StreamPolicy.PHONE, CodecSupport.policyFor(0))
        assertEquals("4K", CodecSupport.label(2160))
        assertEquals("2K", CodecSupport.label(1440))
        assertEquals("8K", CodecSupport.label(4320))
        // Robolectric không có chip giải mã phần cứng → an toàn: H.264 1080p.
        assertEquals(StreamPolicy.PHONE, CodecSupport.tvPolicy())
    }
}
