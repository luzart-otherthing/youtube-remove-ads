package com.luzart.tubesach.sponsor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Một đoạn cần bỏ qua (tài trợ, nhắc đăng ký…). */
data class Segment(val category: String, val startMs: Long, val endMs: Long, val uuid: String)

/** Các loại đoạn của SponsorBlock, tên tiếng Việt và màu trên thanh tua. */
enum class SegmentCategory(val key: String, val label: String, val description: String, val color: Long, val defaultOn: Boolean) {
    SPONSOR("sponsor", "Tài trợ", "Đoạn quảng cáo của nhà tài trợ do người làm video tự chèn", 0xFF00D400, true),
    SELF_PROMO("selfpromo", "Tự quảng cáo", "Giới thiệu sản phẩm, áo, khoá học… của chính kênh", 0xFFFFFF00, true),
    INTERACTION("interaction", "Nhắc tương tác", "Nhắc bấm thích, đăng ký, bật chuông", 0xFFCC00FF, true),
    INTRO("intro", "Đoạn mở đầu", "Hoạt cảnh mở đầu, giới thiệu lặp lại", 0xFF00FFFF, false),
    OUTRO("outro", "Đoạn kết", "Lời chào cuối, màn hình kết thúc", 0xFF0202ED, false),
    PREVIEW("preview", "Xem trước / tóm tắt", "Đoạn tóm tắt hoặc xem trước nội dung", 0xFF008FD6, false),
    FILLER("filler", "Lạc đề", "Đoạn nói chuyện ngoài lề, không liên quan", 0xFF7300FF, false),
    MUSIC_OFFTOPIC("music_offtopic", "Đoạn không phải nhạc", "Trong video ca nhạc: đoạn nói chuyện, không có nhạc", 0xFFFF9900, false);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

/**
 * SponsorBlock (sponsor.ajay.app): cơ sở dữ liệu cộng đồng đánh dấu các đoạn tài trợ trong video.
 * Chỉ gửi 4 ký tự đầu của mã băm SHA-256 → máy chủ không biết bạn đang xem video nào.
 */
class SponsorBlock(private val http: OkHttpClient) {

    suspend fun segments(videoId: String, categories: Collection<String>): List<Segment> = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext emptyList()
        val prefix = sha256(videoId).take(4)
        val url = "https://sponsor.ajay.app/api/skipSegments/$prefix".toHttpUrl().newBuilder()
            .addQueryParameter("categories", JSONArray(categories.toList()).toString())
            .addQueryParameter("actionTypes", JSONArray(listOf("skip", "mute")).toString())
            .build()
        runCatching {
            http.newCall(Request.Builder().url(url).header("User-Agent", "TubeSach").build()).execute().use { r ->
                if (!r.isSuccessful) return@use emptyList()
                parse(r.body?.string().orEmpty(), videoId)
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        fun parse(body: String, videoId: String): List<Segment> {
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                if (v.optString("videoID") != videoId) continue
                val segs = v.optJSONArray("segments") ?: return emptyList()
                return (0 until segs.length()).mapNotNull { j ->
                    val s = segs.optJSONObject(j) ?: return@mapNotNull null
                    val range = s.optJSONArray("segment") ?: return@mapNotNull null
                    val start = (range.optDouble(0) * 1000).toLong()
                    val end = (range.optDouble(1) * 1000).toLong()
                    if (end <= start) null else Segment(s.optString("category"), start, end, s.optString("UUID"))
                }.sortedBy { it.startMs }
            }
            return emptyList()
        }

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

/** Return YouTube Dislike: số lượt "không thích" (YouTube đã ẩn con số này từ 2021). */
class Dislikes(private val http: OkHttpClient) {
    data class Votes(val likes: Long, val dislikes: Long)

    suspend fun votes(videoId: String): Votes? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://returnyoutubedislikeapi.com/votes?videoId=$videoId")
                .header("User-Agent", "TubeSach")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val o = JSONObject(r.body?.string().orEmpty())
                Votes(o.optLong("likes"), o.optLong("dislikes"))
            }
        }.getOrNull()
    }
}
