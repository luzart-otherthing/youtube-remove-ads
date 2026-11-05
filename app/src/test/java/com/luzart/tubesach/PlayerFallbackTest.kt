package com.luzart.tubesach

import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.InnerTube
import com.luzart.tubesach.innertube.InnerTubeException
import com.luzart.tubesach.innertube.PlayerData
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Chuỗi dự phòng lấy link phát: visionOS → ANDROID_VR (1.61.48 → 1.43.32) → (PlaybackController) NewPipe.
 * Mạng giả: mỗi máy khách (theo context.client.clientName + clientVersion) trả phản hồi tuỳ ý.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerFallbackTest {

    private class Sent(val headers: okhttp3.Headers, val body: JSONObject) {
        val client: JSONObject get() = body.getJSONObject("context").getJSONObject("client")
        val key: String get() = client.getString("clientName") + "/" + client.getString("clientVersion")
    }

    /** Cách một máy khách trả lời. */
    private sealed interface Reply {
        data class Json(val body: String) : Reply
        data class Http(val code: Int) : Reply
        data object NetworkDown : Reply
    }

    private val sent = mutableListOf<Sent>()
    private val replies = HashMap<String, Reply>()

    private val vision = "VISIONOS/${InnerTube.VISIONOS_VERSION}"
    private val vr = "ANDROID_VR/1.61.48"
    private val vrOld = "ANDROID_VR/1.43.32"

    @Before
    fun setUp() {
        val net = Interceptor { chain ->
            val req = chain.request()
            assertTrue(req.url.toString(), req.url.encodedPath.endsWith("/youtubei/v1/player"))
            val body = JSONObject(Buffer().also { req.body!!.writeTo(it) }.readUtf8())
            val s = Sent(req.headers, body)
            synchronized(sent) { sent += s }
            when (val r = replies[s.key] ?: Reply.Http(400)) {
                Reply.NetworkDown -> throw IOException("mất mạng")
                is Reply.Http -> Response.Builder().request(req).protocol(Protocol.HTTP_2).code(r.code).message("err")
                    .body("{}".toResponseBody(JSON)).build()
                is Reply.Json -> Response.Builder().request(req).protocol(Protocol.HTTP_2).code(200).message("OK")
                    .body(r.body.toResponseBody(JSON)).build()
            }
        }
        val http = OkHttpClient.Builder().addInterceptor(net).build()
        var visitor: String? = "visitor123" // có sẵn mã khách → không gọi /browse
        YouTubeRepository.init(InnerTube(http, { visitor }, { visitor = it }), http)
    }

    private fun player(): PlayerData = runBlocking { YouTubeRepository.player(ID) }

    private fun called(): List<String> = synchronized(sent) { sent.map { it.key } }

    // ------------------------------------------------------------------ Đường nhanh

    @Test
    fun visionOsOkUsesOnlyOneRequest() {
        replies[vision] = Reply.Json(VISION.toString())
        replies[vr] = Reply.Json(ANDROID_VR.toString())
        val data = player()
        assertEquals("visionOS dùng được → không gọi thêm máy khách nào", listOf(vision), called())
        assertNotNull(data.dashManifest())
        assertNull(data.clientUserAgent)
        val h = sent.single().headers
        assertEquals("101", h["X-YouTube-Client-Name"])
    }

    // ------------------------------------------------------------------ Khi nào chuyển sang ANDROID_VR

    @Test
    fun httpErrorFallsBackToAndroidVrWithReVancedParameters() {
        replies[vision] = Reply.Http(400)
        replies[vr] = Reply.Json(ANDROID_VR.toString())
        val data = player()
        assertEquals(listOf(vision, vr), called())
        assertNotNull(data.dashManifest())
        assertEquals("Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster)", data.title)

        // Thông số giống ReVanced (ClientType.ANDROID_VR_1_61_48).
        val s = sent.last()
        val ua = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; vi_VN; Quest 3; Build/SQ3A.220605.009.A1)"
        assertEquals(ua, s.headers["User-Agent"])
        assertEquals("28", s.headers["X-YouTube-Client-Name"])
        assertEquals("1.61.48", s.headers["X-YouTube-Client-Version"])
        assertEquals("visitor123", s.headers["X-Goog-Visitor-Id"])
        assertNull("ANDROID_VR không đăng nhập", s.headers["Authorization"])
        val c = s.client
        assertEquals("ANDROID_VR", c.getString("clientName"))
        assertEquals("1.61.48", c.getString("clientVersion"))
        assertEquals("Oculus", c.getString("deviceMake"))
        assertEquals("Quest 3", c.getString("deviceModel"))
        assertEquals("Android", c.getString("osName"))
        assertEquals("12", c.getString("osVersion"))
        assertEquals(32, c.getInt("androidSdkVersion"))
        assertEquals("vi", c.getString("hl"))
        assertEquals("VN", c.getString("gl"))
        assertEquals(ID, s.body.getString("videoId"))
        assertTrue(s.body.getBoolean("contentCheckOk") && s.body.getBoolean("racyCheckOk"))
        // Ping thống kê lượt xem dùng cùng User-Agent với máy khách đã cấp link.
        assertEquals(ua, data.clientUserAgent)
    }

    @Test
    fun unplayableStatusFallsBackToAndroidVr() {
        replies[vision] = Reply.Json(
            """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Đăng nhập để xác nhận bạn không phải là bot"}}""",
        )
        replies[vr] = Reply.Json(ANDROID_VR.toString())
        val data = player()
        assertEquals(listOf(vision, vr), called())
        assertTrue(data.isDirectPlayable)
    }

    @Test
    fun noUsableFormatsFallsBackToAndroidVr() {
        // OK nhưng không có định dạng nào (formats rỗng → không dựng được DASH, cũng không có HLS).
        replies[vision] = Reply.Json(VISION.apply { remove("streamingData") }.toString())
        replies[vr] = Reply.Json(ANDROID_VR.toString())
        val data = player()
        assertEquals(listOf(vision, vr), called())
        assertTrue(data.formats.isNotEmpty())
        assertNotNull(data.dashManifest())
    }

    @Test
    fun formatsWithoutDashIndexFallBackToAndroidVr() {
        // Có định dạng nhưng thiếu initRange/indexRange (dashManifest null) và không có HLS.
        val broken = VISION.apply {
            val sd = getJSONObject("streamingData")
            sd.remove("hlsManifestUrl")
            val af = sd.getJSONArray("adaptiveFormats")
            for (i in 0 until af.length()) af.getJSONObject(i).apply { remove("initRange"); remove("indexRange") }
        }
        replies[vision] = Reply.Json(broken.toString())
        replies[vr] = Reply.Json(ANDROID_VR.toString())
        val data = player()
        assertEquals(listOf(vision, vr), called())
        assertNotNull(data.dashManifest())
    }

    @Test
    fun triesOlderAndroidVrVersionNext() {
        replies[vision] = Reply.Http(403)
        replies[vr] = Reply.Json("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"x"}}""")
        replies[vrOld] = Reply.Json(ANDROID_VR.toString())
        val data = player()
        assertEquals(listOf(vision, vr, vrOld), called())
        assertNotNull(data.dashManifest())
        assertEquals("1.43.32", sent.last().headers["X-YouTube-Client-Version"])
        assertTrue(data.clientUserAgent!!.startsWith("com.google.android.apps.youtube.vr.oculus/1.43.32 "))
    }

    // ------------------------------------------------------------------ Khi nào KHÔNG thử tiếp

    @Test
    fun networkErrorIsThrownWithoutTryingOtherClients() {
        replies[vision] = Reply.NetworkDown
        replies[vr] = Reply.Json(ANDROID_VR.toString())
        try {
            player()
            fail("phải ném lỗi mạng")
        } catch (e: IOException) {
            assertFalse("lỗi mạng (không phải lỗi YouTube)", e is InnerTubeException)
        }
        assertEquals("mất mạng → không thử ANDROID_VR (chỉ làm người dùng chờ lâu hơn)", listOf(vision), called())
    }

    @Test
    fun allClientsFailThrowsYouTubeReasonFromVisionOs() {
        replies[vision] = Reply.Json("""{"playabilityStatus":{"status":"ERROR","reason":"Video này không còn nữa"}}""")
        replies[vr] = Reply.Json("""{"playabilityStatus":{"status":"ERROR","reason":"Video unavailable"}}""")
        replies[vrOld] = Reply.Http(500)
        try {
            player()
            fail("phải ném lỗi")
        } catch (e: InnerTubeException) {
            // code = 0: lỗi do YouTube báo → PlaybackController chuyển sang NewPipe, và nếu NewPipe cũng hỏng
            // thì hiện đúng lời YouTube (tiếng Việt).
            assertEquals("Video này không còn nữa", e.message)
            assertEquals(0, e.code)
            assertEquals(2, e.suppressed.size)
        }
        assertEquals(listOf(vision, vr, vrOld), called())
    }

    @Test
    fun keepsHlsOnlyResultWhenAndroidVrFails() {
        // visionOS chỉ có HLS (không dựng được DASH) và ANDROID_VR hỏng → vẫn phát HLS, không phải dùng NewPipe.
        val hlsOnly = VISION.apply { getJSONObject("streamingData").remove("adaptiveFormats") }
        replies[vision] = Reply.Json(hlsOnly.toString())
        replies[vr] = Reply.Http(400)
        replies[vrOld] = Reply.Http(400)
        val data = player()
        assertEquals(listOf(vision, vr, vrOld), called())
        assertNull(data.dashManifest())
        assertNotNull(data.hlsUrl)
    }

    @Test
    fun liveWithHlsNeedsNoFallback() {
        val live = VISION.apply {
            getJSONObject("videoDetails").put("isLive", true)
            getJSONObject("streamingData").remove("adaptiveFormats")
        }
        replies[vision] = Reply.Json(live.toString())
        val data = player()
        assertEquals(listOf(vision), called())
        assertTrue(data.isLive && data.isDirectPlayable)
    }

    // ------------------------------------------------------------------ Phản hồi ANDROID_VR thiếu dữ liệu

    @Test
    fun androidVrWithoutCaptionsStillParses() {
        val noCaptions = ANDROID_VR.apply {
            remove("captions")
            remove("playbackTracking")
            val sd = getJSONObject("streamingData")
            // Chỉ giữ H.264 720p + AAC (thiếu VP9/AV1/Opus và các độ phân giải khác).
            val keep = JSONArray()
            val af = sd.getJSONArray("adaptiveFormats")
            for (i in 0 until af.length()) af.getJSONObject(i).let { if (it.getInt("itag") in setOf(136, 140)) keep.put(it) }
            sd.put("adaptiveFormats", keep)
        }
        replies[vision] = Reply.Http(400)
        replies[vr] = Reply.Json(noCaptions.toString())
        val data = player()
        assertTrue(data.captions.isEmpty())
        assertNull(data.playbackTrackingUrl)
        assertEquals(listOf(720), data.availableHeights)
        val mpd = data.dashManifest()!!
        assertTrue(mpd.contains("codecs=\"avc1.4D401F\"") && mpd.contains("codecs=\"mp4a.40.2\""))
    }

    @Test
    fun realAndroidVrResponseParses() {
        val data = PlayerData.parse(ANDROID_VR, ID, InnerTube.androidVrUserAgent())
        assertTrue(data.formats.any { it.isVideo && it.mimeType == "video/mp4" && it.codecs.startsWith("avc1") })
        assertTrue(data.formats.any { it.isAudio && it.mimeType == "audio/mp4" })
        assertTrue("ANDROID_VR có H.264 tới 1080p", data.availableHeights.containsAll(listOf(1080, 720, 480, 360)))
        assertNotNull(data.dashManifest())
        assertTrue(data.isDirectPlayable)
        assertNull("ANDROID_VR không có HLS cho video thường", data.hlsUrl)
        assertTrue(data.lengthSec > 200)
        // Link googlevideo của ANDROID_VR được nhận ra để tải bằng đúng User-Agent.
        val url = data.formats.first().url
        assertTrue(InnerTube.isAndroidVrStreamingUrl(url))
        assertFalse(InnerTube.isAndroidVrStreamingUrl(VISION.getJSONObject("streamingData").getJSONArray("adaptiveFormats").getJSONObject(0).getString("url")))
        assertFalse(InnerTube.isAndroidVrStreamingUrl("https://x.googlevideo.com/videoplayback?c=ANDROID&cver=20"))
    }

    @Test
    fun isDirectPlayableRules() {
        val ok = PlayerData.parse(VISION, ID)
        assertTrue(ok.isDirectPlayable)
        assertTrue("có DASH là đủ", ok.copy(hlsUrl = null).isDirectPlayable)
        assertFalse("không định dạng, không HLS", ok.copy(formats = emptyList(), hlsUrl = null).isDirectPlayable)
        assertFalse("video trực tiếp cần HLS", ok.copy(isLive = true, hlsUrl = null).isDirectPlayable)
    }

    companion object {
        const val ID = "dQw4w9WgXcQ"
        private val JSON = "application/json".toMediaType()

        private fun fixture(name: String): JSONObject {
            val stream = PlayerFallbackTest::class.java.classLoader!!.getResourceAsStream("fixtures/$name.json.gz")
            return JSONObject(GZIPInputStream(stream).bufferedReader().readText())
        }

        /** Phản hồi /player thật của visionOS (mỗi lần gọi là một bản sao mới để sửa thoải mái). */
        val VISION: JSONObject get() = fixture("player")

        /** Phản hồi /player thật của ANDROID_VR 1.61.48. */
        val ANDROID_VR: JSONObject get() = fixture("player_android_vr")
    }
}
