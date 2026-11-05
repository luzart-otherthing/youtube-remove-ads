package com.luzart.tubesach.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.luzart.tubesach.account.YouTubeAccount
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.io.IOException

/** Lỗi do YouTube trả về (ví dụ video bị chặn). */
class InnerTubeException(message: String, val code: Int = 0) : IOException(message)

/**
 * Gọi thẳng API nội bộ của YouTube (InnerTube) – cùng API mà app YouTube và trang web dùng.
 *
 * - Ngôn ngữ tiếng Việt (hl=vi), khu vực Việt Nam (gl=VN) → đề xuất, thịnh hành đều tiếng Việt.
 * - Không đăng nhập. YouTube nhận diện "khách ẩn danh" qua [visitorData] (giống trình duyệt
 *   chưa đăng nhập), nhờ vậy trang chủ có đề xuất theo những video đã xem trên máy này.
 */
class InnerTube(
    private val http: OkHttpClient,
    private val loadVisitor: () -> String?,
    private val saveVisitor: (String) -> Unit,
    /** Tài khoản Google (thử nghiệm). null hoặc chưa đăng nhập = ẩn danh như trước. */
    private val account: YouTubeAccount? = null,
) {
    @Volatile
    private var visitor: String? = null

    val visitorData: String? get() = visitor ?: loadVisitor()?.also { visitor = it }

    fun resetVisitor() {
        visitor = null
        saveVisitor("")
    }

    private val webVersion: String by lazy {
        runCatching { YoutubeParsingHelper.getClientVersion() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: WEB_VERSION_FALLBACK
    }

    private fun webContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "WEB")
            .put("clientVersion", webVersion)
            .put("hl", HL)
            .put("gl", GL)
            .put("timeZone", "Asia/Ho_Chi_Minh")
            .put("utcOffsetMinutes", 420)
            .apply { visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) } },
    )

    private fun visionContext(): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", "VISIONOS")
            .put("clientVersion", VISIONOS_VERSION)
            .put("deviceMake", "Apple")
            .put("deviceModel", "RealityDevice14,1")
            .put("osName", "visionOS")
            .put("osVersion", "25.6.0.23O471")
            .put("hl", HL)
            .put("gl", GL)
            .apply { visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) } },
    )

    /**
     * Máy khách YouTube VR trên kính Oculus Quest 3 (giống bản vá "Spoof video streams" của ReVanced,
     * file ClientType.java). Chỉ dùng khi chưa đăng nhập.
     */
    private fun androidVrContext(version: String): JSONObject = JSONObject().put(
        "client",
        JSONObject()
            .put("clientName", ANDROID_VR_CLIENT_NAME)
            .put("clientVersion", version)
            .put("deviceMake", ANDROID_VR_DEVICE_MAKE)
            .put("deviceModel", ANDROID_VR_DEVICE_MODEL)
            .put("osName", "Android")
            .put("osVersion", ANDROID_VR_OS_VERSION)
            .put("androidSdkVersion", ANDROID_VR_SDK_VERSION)
            .put("hl", HL)
            .put("gl", GL)
            .apply { visitorData?.takeIf { it.isNotBlank() }?.let { put("visitorData", it) } },
    )

    private suspend fun post(
        url: String,
        body: JSONObject,
        headers: Map<String, String>,
        client: OkHttpClient = http,
    ): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("Accept-Language", "vi-VN,vi;q=0.9")
            .apply { visitorData?.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw InnerTubeException("YouTube trả lỗi HTTP ${response.code}", response.code)
            val json = JSONObject(text)
            json.s("responseContext", "visitorData")?.let { v ->
                if (visitorData.isNullOrBlank()) {
                    visitor = v
                    saveVisitor(v)
                }
            }
            json
        }
    }

    private suspend fun web(endpoint: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        // webVersion có thể cần gọi mạng ở lần đầu → luôn chuẩn bị yêu cầu ở luồng nền.
        body.put("context", webContext())
        // Đã đăng nhập → gửi kèm cookie + SAPISIDHASH giống trang web YouTube.
        val auth = account?.authHeaders()
        post(
            "https://www.youtube.com/youtubei/v1/$endpoint?prettyPrint=false",
            body,
            mapOf(
                "User-Agent" to WEB_UA,
                "X-YouTube-Client-Name" to "1",
                "X-YouTube-Client-Version" to webVersion,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/",
            ) + auth.orEmpty(),
            client = if (auth != null) account!!.http else http,
        )
    }

    /** Đang dùng tài khoản Google (có cookie hợp lệ). */
    val signedIn: Boolean get() = account?.authHeaders() != null

    // ------------------------------------------------------------ Chỉ khi đã đăng nhập

    suspend fun accountMenu(): JSONObject = web("account/account_menu", JSONObject())

    suspend fun subscribe(channelId: String, subscribe: Boolean): JSONObject =
        web(
            if (subscribe) "subscription/subscribe" else "subscription/unsubscribe",
            JSONObject().put("channelIds", JSONArray().put(channelId)),
        )

    /** [action]: "like", "dislike" hoặc "removelike". */
    suspend fun rate(videoId: String, action: String): JSONObject =
        web("like/$action", JSONObject().put("target", JSONObject().put("videoId", videoId)))

    suspend fun createComment(createCommentParams: String, text: String): JSONObject =
        web(
            "comment/create_comment",
            JSONObject().put("createCommentParams", createCommentParams).put("commentText", text),
        )

    /**
     * /player của máy khách WEB (có đăng nhập) – chỉ để lấy link thống kê lượt xem, nhờ đó video
     * đã xem được ghi vào "Video đã xem" của tài khoản (và YouTube đề xuất theo đó).
     */
    suspend fun webPlayer(videoId: String): JSONObject =
        web(
            "player",
            JSONObject().put("videoId", videoId).put("contentCheckOk", true).put("racyCheckOk", true),
        )

    /** Đảm bảo đã có mã khách ẩn danh trước khi gọi các API khác. */
    suspend fun ensureVisitor() {
        if (visitorData.isNullOrBlank()) browse(browseId = "FEwhat_to_watch")
    }

    suspend fun browse(browseId: String? = null, params: String? = null, continuation: String? = null): JSONObject =
        web(
            "browse",
            JSONObject().apply {
                browseId?.let { put("browseId", it) }
                params?.let { put("params", it) }
                continuation?.let { put("continuation", it) }
            },
        )

    suspend fun search(query: String? = null, params: String? = null, continuation: String? = null): JSONObject =
        web(
            "search",
            JSONObject().apply {
                query?.let { put("query", it) }
                params?.let { put("params", it) }
                continuation?.let { put("continuation", it) }
            },
        )

    suspend fun next(videoId: String? = null, continuation: String? = null): JSONObject =
        web(
            "next",
            JSONObject().apply {
                videoId?.let { put("videoId", it) }
                continuation?.let { put("continuation", it) }
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            },
        )

    suspend fun reelSequence(sequenceParams: String? = null, continuation: String? = null): JSONObject =
        web(
            "reel/reel_watch_sequence",
            JSONObject().apply {
                sequenceParams?.let { put("sequenceParams", it) }
                continuation?.let { put("continuation", it) }
            },
        )

    /** Lấy link phát bằng máy khách visionOS: chỉ 1 yêu cầu, link dùng được ngay (không cần giải mã). */
    suspend fun player(videoId: String): JSONObject = withContext(Dispatchers.IO) {
        ensureVisitor()
        playerRequest(
            videoId,
            visionContext(),
            mapOf(
                "User-Agent" to visionUserAgent,
                "X-YouTube-Client-Name" to VISIONOS_CLIENT_ID,
                "X-YouTube-Client-Version" to VISIONOS_VERSION,
            ),
        )
    }

    /**
     * Dự phòng khi visionOS hỏng: lấy link phát bằng máy khách ANDROID_VR (YouTube VR trên Oculus Quest 3).
     * Cũng chỉ 1 yêu cầu, link dùng được ngay (không cần giải mã chữ ký hay tham số "n").
     * Có thể thiếu phụ đề hoặc một số định dạng so với visionOS.
     */
    suspend fun playerAndroidVr(videoId: String, version: String = ANDROID_VR_VERSION): JSONObject =
        withContext(Dispatchers.IO) {
            // Mã khách chỉ là "có thì tốt": nếu API trang web (browse) đang lỗi thì vẫn thử lấy link phát.
            // Lỗi mạng thì vẫn ném ra – gọi /player cũng sẽ hỏng y như vậy.
            try {
                ensureVisitor()
            } catch (_: InnerTubeException) {
            } catch (_: org.json.JSONException) {
            }
            playerRequest(
                videoId,
                androidVrContext(version),
                mapOf(
                    "User-Agent" to androidVrUserAgent(version),
                    "X-YouTube-Client-Name" to ANDROID_VR_CLIENT_ID,
                    "X-YouTube-Client-Version" to version,
                ),
            )
        }

    private suspend fun playerRequest(videoId: String, context: JSONObject, headers: Map<String, String>): JSONObject {
        val body = JSONObject()
            .put("context", context)
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        return post("https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false", body, headers)
    }

    /**
     * Gửi một "ping" thống kê lượt xem (để YouTube biết khách ẩn danh này đã xem video nào).
     * [userAgent]: User-Agent của máy khách đã lấy link phát (mặc định visionOS).
     */
    suspend fun ping(url: String, authed: Boolean = false, userAgent: String? = null) = withContext(Dispatchers.IO) {
        runCatching {
            val auth = if (authed) account?.authHeaders() else null
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", if (auth != null) WEB_UA else userAgent ?: visionUserAgent)
                .apply { visitorData?.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) } }
                .apply { auth?.forEach { (k, v) -> header(k, v) } }
                .build()
            (if (auth != null) account!!.http else http).newCall(request).execute().close()
        }
    }

    val visionUserAgent: String by lazy {
        runCatching { YoutubeParsingHelper.getVisionOsUserAgent(null) }.getOrNull() ?: VISIONOS_UA_FALLBACK
    }

    companion object {
        const val HL = "vi"
        const val GL = "VN"
        const val WEB_VERSION_FALLBACK = "2.20261030.02.00"
        const val VISIONOS_VERSION = "1.02"
        const val VISIONOS_CLIENT_ID = "101"

        // ANDROID_VR – thông số lấy từ ReVanced (ClientType.java: ANDROID_VR_1_61_48 / ANDROID_VR_1_43_32).
        const val ANDROID_VR_CLIENT_NAME = "ANDROID_VR"
        const val ANDROID_VR_CLIENT_ID = "28"
        const val ANDROID_VR_VERSION = "1.61.48"

        /** Thử lần lượt: bản mới (có AV1) rồi bản cũ 1.43.32 (không AV1). */
        val ANDROID_VR_VERSIONS = listOf(ANDROID_VR_VERSION, "1.43.32")
        const val ANDROID_VR_PACKAGE = "com.google.android.apps.youtube.vr.oculus"
        const val ANDROID_VR_DEVICE_MAKE = "Oculus"
        const val ANDROID_VR_DEVICE_MODEL = "Quest 3"
        const val ANDROID_VR_OS_VERSION = "12" // Android 12.1
        const val ANDROID_VR_SDK_VERSION = 32
        const val ANDROID_VR_BUILD_ID = "SQ3A.220605.009.A1"

        /** Giống ReVanced: "<gói>/<phiên bản> (Linux; U; Android <os>; <ngôn ngữ>; <máy>; Build/<build>)". */
        fun androidVrUserAgent(version: String = ANDROID_VR_VERSION): String =
            "$ANDROID_VR_PACKAGE/$version (Linux; U; Android $ANDROID_VR_OS_VERSION; ${HL}_$GL; " +
                "$ANDROID_VR_DEVICE_MODEL; Build/$ANDROID_VR_BUILD_ID)"

        /** Link googlevideo do máy khách ANDROID_VR cấp (có tham số c=ANDROID_VR). */
        fun isAndroidVrStreamingUrl(url: String): Boolean = ANDROID_VR_URL.containsMatchIn(url)

        private val ANDROID_VR_URL = Regex("[?&]c=$ANDROID_VR_CLIENT_NAME(&|$)")

        const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        private const val VISIONOS_UA_FALLBACK =
            "Mozilla/5.0 (Apple Vision Pro; U; CPU visionOS 25_6_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/605.1.15"
        private val JSON = "application/json".toMediaType()
    }
}
