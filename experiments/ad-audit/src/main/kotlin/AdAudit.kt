// Công cụ đo quảng cáo (thử nghiệm riêng, không thuộc app Tube Sạch).
//
// 1. Gọi YouTube thật bằng ĐÚNG lớp InnerTube của Tube Sạch (trang chủ, thịnh hành, tìm kiếm, trang xem, /player).
// 2. Đếm mọi quảng cáo YouTube gửi kèm trong phản hồi (adSlotRenderer, adPlacements, playerAds…,
//    mục có nhãn "Được tài trợ", dấu hiệu quảng cáo chèn thẳng vào luồng video – SSAI).
// 3. Chạy ĐÚNG bộ đọc Parser / PlayerData của Tube Sạch trên cùng phản hồi đó và kiểm tra
//    có mục quảng cáo nào lọt ra danh sách hiển thị không.
// 4. Gọi thêm bằng máy khách ANDROID (thứ app YouTube thật nhận được – cũng là thứ ReVanced phải lọc),
//    thử cả mẹo của ReVanced: khai osName = "Android Automotive" để YouTube không gửi quảng cáo.

import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.innertube.InnerTube
import com.luzart.tubesach.innertube.Parser
import com.luzart.tubesach.innertube.PlayerData
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Tên khoá JSON YouTube dùng cho quảng cáo. Gặp khoá này = phần bên trong là quảng cáo. */
val AD_KEYS = setOf(
    // Quảng cáo trong danh sách (trang chủ, tìm kiếm, video liên quan)
    "adSlotRenderer", "inFeedAdLayoutRenderer", "promotedVideoRenderer", "compactPromotedVideoRenderer",
    "promotedSparklesWebRenderer", "promotedSparklesTextSearchRenderer", "searchPyvRenderer",
    "displayAdRenderer", "adActionInterstitialRenderer", "fullscreenEngagementAdSlotRenderer",
    "brandVideoShelfRenderer", "brandVideoSingletonRenderer", "bannerPromoRenderer",
    "statementBannerRenderer", "mealbarPromoRenderer", "primetimePromoRenderer",
    "merchandiseShelfRenderer", "movieOfferModuleRenderer", "productListRenderer",
    "adsEngagementPanelContentRenderer", "companionAdRenderer", "actionCompanionAdRenderer",
    "topBannerImageTextIconButtonedLayoutViewModel",
    // Quảng cáo trong trình phát (trước/giữa/sau video)
    "adPlacements", "playerAds", "adSlots", "adBreakHeartbeatParams", "instreamVideoAdRenderer",
    "linearAdSequenceRenderer", "playerLegacyDesktopWatchAdsRenderer", "adPlacementRenderer",
)

/** Khoá có tên "giống quảng cáo" nhưng không nằm trong danh sách trên → liệt kê ra để xem tay. */
val SUSPECT = Regex("(^ad[A-Z])|([a-z]Ad[A-Z])|(Ads?$)|(?i:promoted|sparkles|pyv|merchandise|mealbar|upsell|sponsor)")
val NOT_ADS = setOf("adSignalsInfo", "adaptiveFormats", "hideMembersOnlyAds")

/** Nhãn đánh dấu mục quảng cáo/tài trợ. */
val SPONSORED_TEXTS = setOf("được tài trợ", "quảng cáo", "sponsored", "tài trợ", "ad")

/** Mẫu quảng cáo trong bộ lọc của ReVanced (AdsFilter.java): tên thành phần giao diện của app Android. */
val REVANCED_PATTERNS = listOf(
    "_ad_with", "_buttoned_layout", "ads_video_with_context", "banner_text_icon", "brand_video_shelf",
    "brand_video_singleton", "carousel_footered_layout", "carousel_headered_layout", "carousel_ad",
    "compact_landscape_image_layout", "composite_concurrent_carousel_layout", "full_width_portrait_image_layout",
    "full_width_square_image_carousel_layout", "full_width_square_image_layout", "hero_promo_image",
    "image_button_group_layout", "landscape_image_carousel_layout", "landscape_image_wide_button_layout",
    "primetime_promo", "product_details", "square_image_layout", "text_image_button_layout",
    "text_image_no_button_layout", "video_display_button_group_layout",
    "video_display_carousel_button_group_layout", "video_display_carousel_buttoned_short_dr_layout",
    "video_display_full_buttoned_short_dr_layout", "video_display_full_layout", "watch_metadata_app_promo",
    "shopping_timely_shelf", "statement_banner", "cta_shelf_card", "product_carousel", "shopping_carousel",
    "products_in_video", "_interstitial",
)

val ITEM_KEYS = setOf(
    "videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "lockupViewModel",
    "reelItemRenderer", "shortsLockupViewModel", "playlistVideoRenderer",
)
val ID_KEYS = setOf("videoId", "contentId", "adVideoId", "externalVideoId")

class Scan {
    val adCounts = sortedMapOf<String, Int>()
    val adIds = mutableSetOf<String>()
    val organicIds = mutableSetOf<String>()
    val sponsoredIds = mutableSetOf<String>()
    val sponsoredPaths = mutableListOf<String>()
    val suspect = sortedMapOf<String, Int>()
    val revancedHits = sortedMapOf<String, Int>()

    val adTotal get() = adCounts.values.sum()
}

fun scan(root: JSONObject): Scan {
    val s = Scan()
    walk(root, "$", false, s)
    val raw = root.toString()
    for (p in REVANCED_PATTERNS) {
        val n = Regex(Regex.escape(p)).findAll(raw).count()
        if (n > 0) s.revancedHits[p] = n
    }
    return s
}

private fun isNonEmpty(v: Any?) = when (v) {
    is JSONArray -> v.length() > 0
    is JSONObject -> v.length() > 0
    null, JSONObject.NULL -> false
    else -> true
}

private fun walk(node: Any?, path: String, inAd: Boolean, s: Scan) {
    when (node) {
        is JSONObject -> for (key in node.keys().asSequence().toList()) {
            val v = node.opt(key)
            val isAd = key in AD_KEYS && isNonEmpty(v)
            if (isAd && !inAd) s.adCounts.merge(key, 1, Int::plus)
            if (!isAd && key !in AD_KEYS && key !in NOT_ADS && SUSPECT.containsMatchIn(key)) s.suspect.merge(key, 1, Int::plus)
            if (key in ID_KEYS && v is String && v.length == 11) {
                (if (inAd || isAd) s.adIds else s.organicIds).add(v)
            }
            if (key in ITEM_KEYS && v is JSONObject && hasSponsoredText(v)) {
                s.sponsoredPaths.add("$path.$key")
                collectIds(v, s.sponsoredIds)
            }
            walk(v, "$path.$key", inAd || isAd, s)
        }
        is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), "$path[$i]", inAd, s)
    }
}

private fun hasSponsoredText(node: Any?): Boolean = when (node) {
    is JSONObject -> node.keys().asSequence().any { k ->
        val v = node.opt(k)
        (v is String && (k == "text" || k == "label" || k == "content" || k == "simpleText") &&
            v.trim().lowercase() in SPONSORED_TEXTS) || hasSponsoredText(v)
    }
    is JSONArray -> (0 until node.length()).any { hasSponsoredText(node.opt(it)) }
    else -> false
}

private fun collectIds(node: Any?, out: MutableSet<String>) {
    when (node) {
        is JSONObject -> for (k in node.keys()) {
            val v = node.opt(k)
            if (k in ID_KEYS && v is String && v.length == 11) out.add(v)
            collectIds(v, out)
        }
        is JSONArray -> for (i in 0 until node.length()) collectIds(node.opt(i), out)
    }
}

/** Mã video của mọi mục Tube Sạch sẽ hiển thị. */
fun shownIds(page: FeedPage): Set<String> = page.entries.flatMap { e ->
    when (e) {
        is FeedEntry.Video -> listOf(e.video.url)
        is FeedEntry.Shelf -> e.videos.map { it.url }
        is FeedEntry.ShortsShelf -> e.shorts.map { "https://www.youtube.com/watch?v=${it.id}" }
        is FeedEntry.Channel -> emptyList()
    }
}.mapNotNull { VideoItem.videoIdOf(it) }.toSet()

data class Row(
    val page: String,
    val scan: Scan,
    val shown: Int,
    val leaks: Set<String>,
    val note: String = "",
)

fun leaksOf(scan: Scan, shown: Set<String>): Set<String> =
    (shown intersect (scan.adIds - scan.organicIds)) + (shown intersect scan.sponsoredIds)

// ------------------------------------------------------------------ /player

data class PlayerRow(
    val label: String,
    val status: String,
    val ads: Map<String, Int>,
    val placementKinds: List<String>,
    val ssaiHints: List<String>,
    val lengthSec: Long,
    val maxFormatSec: Double,
    val formatsUsable: Int,
    val dashOk: Boolean,
    val note: String = "",
)

fun analyzePlayer(label: String, json: JSONObject, videoId: String, parse: Boolean): PlayerRow {
    val s = scan(json)
    val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
    val kinds = json.optJSONArray("adPlacements")?.let { arr ->
        (0 until arr.length()).mapNotNull {
            arr.optJSONObject(it)?.optJSONObject("adPlacementRenderer")?.optJSONObject("config")
                ?.optJSONObject("adPlacementConfig")?.optString("kind")?.removePrefix("AD_PLACEMENT_KIND_")
        }
    }.orEmpty()
    val lengthSec = json.optJSONObject("videoDetails")?.optString("lengthSeconds")?.toLongOrNull() ?: 0
    val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
    val maxMs = formats?.let { arr ->
        (0 until arr.length()).maxOfOrNull { arr.optJSONObject(it)?.optString("approxDurationMs")?.toLongOrNull() ?: 0 }
    } ?: 0
    // Dấu hiệu quảng cáo chèn thẳng vào luồng (SSAI): khoá kiểu serverStitched…/ssai… mang giá trị bật,
    // hoặc luồng dài hơn video. (Chuỗi "ssaid" trong link theo dõi KHÔNG tính.)
    val ssai = mutableListOf<String>()
    fun hunt(node: Any?, path: String) {
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                val v = node.opt(k)
                if (Regex("(?i)serverStitch|^ssai|Ssai|stitchedAd|daiOnesie|enableServerStitchedDai").containsMatchIn(k) &&
                    v != false && v != JSONObject.NULL
                ) ssai.add("$k=$v")
                hunt(v, "$path.$k")
            }
            is JSONArray -> for (i in 0 until node.length()) hunt(node.opt(i), "$path[$i]")
        }
    }
    hunt(json, "$")
    if (json.optJSONObject("streamingData")?.has("serverAbrStreamingUrl") == true) ssai.add("(có link SABR)")
    if (lengthSec > 0 && maxMs > lengthSec * 1000 + 3000) ssai.add("LUỒNG DÀI HƠN VIDEO ${maxMs / 1000 - lengthSec}s")
    var usable = 0
    var dash = false
    var note = ""
    if (parse && status == "OK") {
        runCatching { PlayerData.parse(json, videoId) }
            .onSuccess { usable = it.formats.size; dash = it.dashManifest() != null }
            .onFailure { note = "Parser lỗi: ${it.message}" }
    }
    return PlayerRow(label, status, s.adCounts, kinds, ssai, lengthSec, maxMs / 1000.0, usable, dash, note)
}

// ------------------------------------------------------------------ Mẫu quảng cáo dựng sẵn
// Từ máy chủ đo, YouTube có thể không gửi quảng cáo danh sách (tuỳ IP/khu vực/khách). Vì vậy thử thêm
// Parser của Tube Sạch với các mẫu quảng cáo đúng hình dạng YouTube vẫn gửi cho trình duyệt.

fun vr(id: String, title: String, badge: String? = null) = JSONObject()
    .put("videoId", id)
    .put("title", JSONObject().put("simpleText", title))
    .apply { badge?.let { put("badges", JSONArray().put(JSONObject().put("metadataBadgeRenderer", JSONObject().put("label", it)))) } }

fun adSlot(inner: JSONObject) = JSONObject().put(
    "adSlotRenderer",
    JSONObject().put(
        "fulfillmentContent",
        JSONObject().put("fulfilledLayout", JSONObject().put("inFeedAdLayoutRenderer", JSONObject().put("renderingContent", inner))),
    ),
)

data class SyntheticCase(val name: String, val json: JSONObject, val adIds: Set<String>, val shown: (JSONObject) -> Set<String>)

fun syntheticCases(): List<SyntheticCase> {
    val organic = vr("ORGANIC0001", "Video thường")
    val home = JSONObject().put("contents", JSONObject().put("richGridRenderer", JSONObject().put(
        "contents", JSONArray()
            .put(JSONObject().put("richItemRenderer", JSONObject().put("content", JSONObject().put("videoRenderer", organic))))
            .put(JSONObject().put("richItemRenderer", JSONObject().put("content", adSlot(JSONObject().put("promotedVideoRenderer", vr("ADHOME00001", "Quảng cáo trang chủ", "Được tài trợ"))))))
            .put(JSONObject().put("richItemRenderer", JSONObject().put("content", adSlot(JSONObject().put("videoRenderer", vr("ADHOME00002", "Quảng cáo dạng video", "Được tài trợ"))))))
            .put(JSONObject().put("richSectionRenderer", JSONObject().put("content", JSONObject().put("statementBannerRenderer", JSONObject().put("title", JSONObject().put("simpleText", "YouTube Premium"))))))
    )))
    val search = JSONObject().put("contents", JSONObject().put("sectionListRenderer", JSONObject().put(
        "contents", JSONArray().put(JSONObject().put("itemSectionRenderer", JSONObject().put(
            "contents", JSONArray()
                .put(JSONObject().put("searchPyvRenderer", JSONObject().put("ads", JSONArray().put(JSONObject().put("promotedVideoRenderer", vr("ADSEARCH001", "Quảng cáo tìm kiếm", "Được tài trợ"))))))
                .put(adSlot(JSONObject().put("videoRenderer", vr("ADSEARCH002", "Quảng cáo tìm kiếm 2", "Được tài trợ"))))
                .put(JSONObject().put("videoRenderer", organic))
        ))),
    )))
    val watch = JSONObject().put("contents", JSONObject().put("twoColumnWatchNextResults", JSONObject()
        .put("results", JSONObject().put("results", JSONObject().put("contents", JSONArray())))
        .put("secondaryResults", JSONObject().put("secondaryResults", JSONObject().put("results", JSONArray()
            .put(adSlot(JSONObject().put("compactPromotedVideoRenderer", vr("ADWATCH0001", "Quảng cáo bên phải", "Được tài trợ"))))
            .put(JSONObject().put("compactVideoRenderer", organic)))))))
    // Giả định: quảng cáo KHÔNG bọc trong adSlotRenderer mà giả làm video thường, chỉ có nhãn "Được tài trợ".
    val disguised = JSONObject().put("contents", JSONObject().put("sectionListRenderer", JSONObject().put(
        "contents", JSONArray().put(JSONObject().put("itemSectionRenderer", JSONObject().put(
            "contents", JSONArray()
                .put(JSONObject().put("videoRenderer", vr("ADDISGUISE1", "Quảng cáo giả làm video", "Được tài trợ")))
                .put(JSONObject().put("videoRenderer", organic))
        ))),
    )))
    return listOf(
        SyntheticCase("Trang chủ: adSlotRenderer, promotedVideo, banner Premium", home, setOf("ADHOME00001", "ADHOME00002")) { shownIds(Parser.browseGrid(it)) },
        SyntheticCase("Tìm kiếm: searchPyvRenderer + adSlotRenderer", search, setOf("ADSEARCH001", "ADSEARCH002")) { shownIds(Parser.search(it)) },
        SyntheticCase("Video liên quan: adSlotRenderer", watch, setOf("ADWATCH0001")) { j ->
            Parser.watch(j, "x").related.mapNotNull { VideoItem.videoIdOf(it.url) }.toSet()
        },
        SyntheticCase("Giả định: quảng cáo giả làm video thường (chỉ có nhãn \"Được tài trợ\")", disguised, setOf("ADDISGUISE1")) { shownIds(Parser.search(it)) },
    )
}

// ------------------------------------------------------------------ Máy khách ANDROID (giống app YouTube thật)

const val ANDROID_VERSION = "20.40.45"
val JSON_TYPE = "application/json".toMediaType()

fun androidCall(http: OkHttpClient, endpoint: String, body: JSONObject, osName: String, visitor: String?): JSONObject {
    val client = JSONObject()
        .put("clientName", "ANDROID")
        .put("clientVersion", ANDROID_VERSION)
        .put("androidSdkVersion", 34)
        .put("osName", osName)
        .put("osVersion", "14")
        .put("hl", InnerTube.HL)
        .put("gl", InnerTube.GL)
    visitor?.let { client.put("visitorData", it) }
    body.put("context", JSONObject().put("client", client))
    val req = Request.Builder()
        .url("https://youtubei.googleapis.com/youtubei/v1/$endpoint?prettyPrint=false")
        .post(body.toString().toRequestBody(JSON_TYPE))
        .header("User-Agent", "com.google.android.youtube/$ANDROID_VERSION (Linux; U; Android 14; vi_VN) gzip")
        .header("X-YouTube-Client-Name", "3")
        .header("X-YouTube-Client-Version", ANDROID_VERSION)
        .apply { visitor?.let { header("X-Goog-Visitor-Id", it) } }
        .build()
    http.newCall(req).execute().use { r ->
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) return JSONObject().put("_httpError", r.code).put("_body", text.take(300))
        return JSONObject(text)
    }
}

// ------------------------------------------------------------------ Chạy

fun fmt(m: Map<String, Int>) = if (m.isEmpty()) "—" else m.entries.joinToString(", ") { "${it.key}×${it.value}" }

fun main(args: Array<String>) = runBlocking {
    val outFile = File(args.firstOrNull() ?: "experiments/ad-audit/ket-qua.md")
    val rawDir = File("experiments/ad-audit/build/raw").apply { mkdirs() }
    val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    var visitor: String? = null
    val yt = InnerTube(http, { visitor }, { visitor = it.ifEmpty { null } })

    val rows = mutableListOf<Row>()
    val players = mutableListOf<PlayerRow>()
    val errors = mutableListOf<String>()

    suspend fun page(label: String, fetch: suspend () -> JSONObject, parse: (JSONObject) -> Set<String>) {
        try {
            val json = fetch()
            File(rawDir, label.replace(Regex("[^\\p{L}0-9]+"), "_") + ".json").writeText(json.toString(1))
            val s = scan(json)
            val shown = parse(json)
            rows.add(Row(label, s, shown.size, leaksOf(s, shown)))
        } catch (e: Exception) {
            errors.add("$label: ${e.message}")
        }
    }

    // Trang chủ (lần đầu thường trống với khách mới) + thịnh hành + nhạc – đúng các trang app mở.
    page("Trang chủ", { yt.browse(browseId = "FEwhat_to_watch") }) { shownIds(Parser.browseGrid(it)) }
    page("Thịnh hành VN", { yt.browse(browseId = "VLOLAK5uy_lEos0zuYBvGC9C0FSGG3pZ6gO4a82P6zg") }) { shownIds(Parser.browseGrid(it)) }
    page("Top nhạc VN", { yt.browse(browseId = "VLPL4fGSI1pDJn57DkisEwlIpcs9FAt5yudJ") }) { shownIds(Parser.browseGrid(it)) }

    // Từ khoá hay có quảng cáo (mua bán, tài chính, mỹ phẩm, du lịch…) và vài từ khoá thường.
    val queries = listOf(
        "điện thoại samsung", "vay tiền online", "mua ô tô", "bảo hiểm nhân thọ", "kem chống nắng",
        "du lịch đà nẵng", "học tiếng anh", "giày thể thao", "laptop gaming", "nhạc trẻ",
    )
    for (q in queries) page("Tìm: $q", { yt.search(query = q) }) { shownIds(Parser.search(it)) }

    // Trang xem (video liên quan) + /player.
    val videos = listOf("kJQP7kiw5Fk", "dQw4w9WgXcQ", "JGwWNGJdvx8", "9bZkp7q19f0", "RgKAFK5djSk")
    for (v in videos) {
        page("Xem: $v", { yt.next(videoId = v) }) { json ->
            Parser.watch(json, v).related.mapNotNull { VideoItem.videoIdOf(it.url) }.toSet()
        }
    }
    for (v in videos) {
        runCatching { yt.player(v) }
            .onSuccess {
                File(rawDir, "player_visionos_$v.json").writeText(it.toString(1))
                players.add(analyzePlayer("Tube Sạch (visionOS) $v", it, v, parse = true))
            }
            .onFailure { errors.add("player visionOS $v: ${it.message}") }
    }

    // Máy khách ANDROID = app YouTube thật. So sánh osName thường và mẹo "Android Automotive" của ReVanced.
    val androidRows = mutableListOf<Row>()
    for (os in listOf("Android", "Android Automotive")) {
        for ((label, ep, body) in listOf(
            Triple("Trang chủ", "browse", JSONObject().put("browseId", "FEwhat_to_watch")),
            Triple("Tìm: điện thoại samsung", "search", JSONObject().put("query", "điện thoại samsung")),
            Triple("Tìm: vay tiền online", "search", JSONObject().put("query", "vay tiền online")),
            Triple("Xem: kJQP7kiw5Fk", "next", JSONObject().put("videoId", "kJQP7kiw5Fk")),
        )) {
            runCatching { androidCall(http, ep, body, os, visitor) }
                .onSuccess { json ->
                    File(rawDir, "android_${os.replace(' ', '_')}_${ep}_${label.hashCode()}.json").writeText(json.toString(1))
                    val note = if (json.has("_httpError")) "HTTP ${json.opt("_httpError")}" else ""
                    androidRows.add(Row("[$os] $label", scan(json), 0, emptySet(), note))
                }
                .onFailure { errors.add("ANDROID $os $label: ${it.message}") }
        }
        for (v in videos.take(3)) {
            runCatching {
                androidCall(http, "player", JSONObject().put("videoId", v).put("contentCheckOk", true).put("racyCheckOk", true), os, visitor)
            }.onSuccess { json ->
                File(rawDir, "player_android_${os.replace(' ', '_')}_$v.json").writeText(json.toString(1))
                val r = analyzePlayer("App YouTube [$os] $v", json, v, parse = false)
                players.add(if (json.has("_httpError")) r.copy(note = "HTTP ${json.opt("_httpError")}") else r)
            }.onFailure { errors.add("player ANDROID $os $v: ${it.message}") }
        }
    }

    // ------------------------------------------------------------------ Báo cáo
    val sb = StringBuilder()
    sb.appendLine("# Kết quả đo quảng cáo (${java.time.LocalDateTime.now().withNano(0)})")
    sb.appendLine()
    sb.appendLine("Gọi YouTube thật (hl=vi, gl=VN, khách ẩn danh) bằng đúng mã `InnerTube` + `Parser` của Tube Sạch.")
    sb.appendLine("Lượng quảng cáo YouTube gửi phụ thuộc IP/khu vực/lịch sử xem của máy gọi, nên mục 4 thử thêm bằng mẫu dựng sẵn.")
    sb.appendLine()
    sb.appendLine("## 1. Danh sách (trang chủ, tìm kiếm, video liên quan) – Tube Sạch")
    sb.appendLine()
    sb.appendLine("| Trang | Quảng cáo YouTube gửi kèm | Mục \"được tài trợ\" | Tube Sạch hiện | Quảng cáo lọt ra |")
    sb.appendLine("|---|---|---|---|---|")
    for (r in rows) {
        sb.appendLine("| ${r.page} | ${fmt(r.scan.adCounts)} | ${r.scan.sponsoredPaths.size} | ${r.shown} mục | ${if (r.leaks.isEmpty()) "**0**" else "**${r.leaks.size}**: ${r.leaks}"} |")
    }
    val totalAds = rows.sumOf { it.scan.adTotal }
    val totalLeaks = rows.sumOf { it.leaks.size }
    sb.appendLine()
    sb.appendLine("Tổng: YouTube gửi **$totalAds** khối quảng cáo trong ${rows.size} trang; lọt ra màn hình Tube Sạch: **$totalLeaks**.")
    val suspect = sortedMapOf<String, Int>()
    rows.forEach { r -> r.scan.suspect.forEach { (k, v) -> suspect.merge(k, v, Int::plus) } }
    if (suspect.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("Khoá có tên giống quảng cáo (xem tay, thường là cờ/điểm theo dõi, không phải quảng cáo hiển thị): ${fmt(suspect)}")
    }

    sb.appendLine()
    sb.appendLine("## 2. Trình phát (/player)")
    sb.appendLine()
    sb.appendLine("| Máy khách / video | Trạng thái | Quảng cáo trong phản hồi | Vị trí quảng cáo | Dấu hiệu chèn vào luồng (SSAI/SABR) | Độ dài video / luồng dài nhất | Luồng Tube Sạch dùng được | Ghi chú |")
    sb.appendLine("|---|---|---|---|---|---|---|---|")
    for (p in players) {
        val dur = if (p.lengthSec > 0) "${p.lengthSec}s / ${"%.1f".format(p.maxFormatSec)}s" else "—"
        val usable = if (p.label.startsWith("Tube")) "${p.formatsUsable} luồng, DASH ${if (p.dashOk) "OK" else "KHÔNG"}" else "—"
        sb.appendLine("| ${p.label} | ${p.status.ifEmpty { "—" }} | ${fmt(p.ads)} | ${p.placementKinds.joinToString().ifEmpty { "—" }} | ${p.ssaiHints.joinToString().ifEmpty { "không" }} | $dur | $usable | ${p.note} |")
    }

    sb.appendLine()
    sb.appendLine("## 3. Máy khách ANDROID (thứ app YouTube thật nhận) và mẹo \"Android Automotive\" của ReVanced")
    sb.appendLine()
    sb.appendLine("| Trang | Quảng cáo YouTube gửi kèm | Mục \"được tài trợ\" | Mẫu quảng cáo của ReVanced tìm thấy | Ghi chú |")
    sb.appendLine("|---|---|---|---|---|")
    for (r in androidRows) {
        sb.appendLine("| ${r.page} | ${fmt(r.scan.adCounts)} | ${r.scan.sponsoredPaths.size} | ${fmt(r.scan.revancedHits)} | ${r.note} |")
    }
    sb.appendLine()
    sb.appendLine("## 4. Mẫu quảng cáo dựng sẵn (đúng hình dạng YouTube gửi cho trình duyệt)")
    sb.appendLine()
    sb.appendLine("| Trường hợp | Tube Sạch hiện | Quảng cáo lọt ra |")
    sb.appendLine("|---|---|---|")
    for (c in syntheticCases()) {
        val shown = runCatching { c.shown(c.json) }.getOrElse { emptySet() }
        val leaked = shown intersect c.adIds
        sb.appendLine("| ${c.name} | ${shown.size} mục (${shown.joinToString()}) | ${if (leaked.isEmpty()) "**0**" else "**${leaked.size}**: $leaked"} |")
    }
    if (errors.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("## Lỗi khi gọi")
        errors.forEach { sb.appendLine("- $it") }
    }
    outFile.parentFile?.mkdirs()
    outFile.writeText(sb.toString())
    println(sb)
}
