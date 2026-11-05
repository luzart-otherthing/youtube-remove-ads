package com.luzart.tubesach.innertube

import android.os.SystemClock
import org.json.JSONObject

/** Một định dạng (luồng hình hoặc tiếng) trong streamingData của YouTube. */
data class StreamFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val initStart: Long,
    val initEnd: Long,
    val indexStart: Long,
    val indexEnd: Long,
    val audioSampleRate: Int,
    val audioChannels: Int,
    val audioTrackId: String?,
    val audioIsDefault: Boolean,
) {
    val isVideo get() = mimeType.startsWith("video/")
    val isAudio get() = mimeType.startsWith("audio/")

    /**
     * "Độ phân giải" theo cách YouTube gọi (720p, 1080p…) = cạnh ngắn của khung hình.
     * Video dọc (Shorts) 1080×1920 vẫn là "1080p", không phải 1920p.
     */
    val quality: Int get() = if (width > 0 && height > 0) minOf(width, height) else height

    val isPortrait: Boolean get() = width in 1 until height

    /** Luồng H.264 (MP4) – máy nào cũng giải mã phần cứng được; YouTube chỉ có tới 1080p. */
    val isAvc: Boolean get() = isVideo && mimeType == "video/mp4" && codecs.startsWith("avc1")

    /**
     * Luồng VP9 thường (8-bit, WebM) – có tới 4K, tốn ít dữ liệu hơn H.264.
     * Bỏ VP9 HDR (vp09.02, 10-bit) vì nhiều máy không giải mã được.
     */
    val isVp9: Boolean get() = isVideo && mimeType == "video/webm" && (codecs == "vp9" || codecs.startsWith("vp09.00"))
}

/** Máy có phát được luồng VP9 này không (null = không dùng VP9). */
typealias Vp9Policy = (StreamFormat) -> Boolean

/** Một phụ đề có sẵn của video. */
data class CaptionTrack(
    val name: String,
    val languageCode: String,
    val url: String,
    val isAuto: Boolean,
) {
    /** Link WebVTT; [translateTo] = mã ngôn ngữ để YouTube tự động dịch (ví dụ "vi"). */
    fun vttUrl(translateTo: String? = null): String {
        val base = url.replace(Regex("&fmt=[^&]*"), "") + "&fmt=vtt"
        return if (translateTo != null) "$base&tlang=$translateTo" else base
    }
}

/** Kết quả gọi /player: thông tin video + link phát + link thống kê lượt xem. */
data class PlayerData(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String?,
    val lengthSec: Long,
    val viewCount: Long,
    val isLive: Boolean,
    val thumbnail: String?,
    val formats: List<StreamFormat>,
    val hlsUrl: String?,
    val playbackTrackingUrl: String?,
    val watchtimeTrackingUrl: String?,
    /** Thời điểm link hết hạn, theo [SystemClock.elapsedRealtime] (không bị lệch khi người dùng chỉnh giờ). */
    val expiresAtMs: Long,
    val captions: List<CaptionTrack> = emptyList(),
    /** User-Agent của máy khách YouTube đã cấp link (visionOS, ANDROID_VR…); null = visionOS. */
    val clientUserAgent: String? = null,
) {
    /**
     * Có luồng phát thẳng dùng được: video thường cần dựng được manifest DASH (H.264 + AAC),
     * video trực tiếp cần link HLS.
     */
    val isDirectPlayable: Boolean get() = if (isLive) hlsUrl != null else dashManifest() != null

    /** Các độ phân giải có thể chọn trong menu "Chất lượng" (chỉ H.264, tối đa 1080p). */
    val availableHeights: List<Int> get() = availableHeights(null)

    /**
     * Các độ phân giải có thể chọn: H.264 tới 1080p, cộng thêm VP9 (tới 2160p = 4K) mà [vp9] cho phép
     * – tức máy giải mã phần cứng được đúng kích thước đó.
     */
    fun availableHeights(vp9: Vp9Policy?): List<Int> =
        (avcFormats(1080) + vp9Formats(vp9)).map { it.quality }.distinct().sortedDescending()

    private fun avcFormats(maxQuality: Int) = formats
        .filter { it.isAvc && it.quality in 1..maxQuality && it.indexEnd > 0 && it.initEnd > 0 }
        .distinctBy { it.quality to it.fps }

    private fun vp9Formats(vp9: Vp9Policy?): List<StreamFormat> {
        vp9 ?: return emptyList()
        return formats
            .filter { it.isVp9 && it.quality in 1..MAX_VP9_QUALITY && it.indexEnd > 0 && it.initEnd > 0 }
            .distinctBy { it.quality to it.fps }
            .filter { runCatching { vp9(it) }.getOrDefault(false) }
    }

    /** Có luồng VP9 nào máy phát được không. */
    fun hasVp9(vp9: Vp9Policy?): Boolean = vp9Formats(vp9).isNotEmpty()

    /** Video dọc (Shorts, video quay dọc). */
    val isPortrait: Boolean get() = formats.firstOrNull { it.isVideo && it.width > 0 }?.isPortrait == true

    /**
     * Dựng manifest DASH gồm nhiều độ phân giải H.264 (≤ [maxHeight]) và một luồng tiếng AAC.
     * ExoPlayer sẽ tự chọn chất lượng theo tốc độ mạng – giống chế độ "Tự động" của YouTube.
     *
     * [vp9] khác null: thêm một nhóm luồng VP9 (chỉ những độ phân giải máy giải mã phần cứng được).
     * Trình phát chọn nhóm VP9 nếu có (tiết kiệm dữ liệu), nhóm H.264 vẫn còn để quay về khi cần.
     */
    fun dashManifest(maxHeight: Int = 1080, vp9: Vp9Policy? = null): String? {
        val video = avcFormats(maxHeight)
        val vp9Video = vp9Formats(vp9)
        val audioAll = formats.filter { it.isAudio && it.mimeType == "audio/mp4" && it.indexEnd > 0 && it.initEnd > 0 }
        val audio = audioAll.filter { it.audioIsDefault || it.audioTrackId == null }.ifEmpty { audioAll }
            .sortedByDescending { it.bitrate }
            .take(2)
        if (video.isEmpty() || audio.isEmpty()) return null

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.append("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\" profiles=\"urn:mpeg:dash:profile:isoff-on-demand:2011\" ")
        sb.append("type=\"static\" minBufferTime=\"PT1.500S\" mediaPresentationDuration=\"PT${lengthSec.coerceAtLeast(1)}S\">")
        sb.append("<Period>")
        sb.append("<AdaptationSet id=\"0\" contentType=\"video\" mimeType=\"video/mp4\" subsegmentAlignment=\"true\" startWithSAP=\"1\">")
        video.forEach { sb.representation(it) }
        sb.append("</AdaptationSet>")
        if (vp9Video.isNotEmpty()) {
            sb.append("<AdaptationSet id=\"2\" contentType=\"video\" mimeType=\"video/webm\" subsegmentAlignment=\"true\" startWithSAP=\"1\">")
            vp9Video.forEach { sb.representation(it) }
            sb.append("</AdaptationSet>")
        }
        sb.append("<AdaptationSet id=\"1\" contentType=\"audio\" mimeType=\"audio/mp4\" subsegmentAlignment=\"true\" startWithSAP=\"1\">")
        audio.forEach { sb.representation(it) }
        sb.append("</AdaptationSet>")
        sb.append("</Period></MPD>")
        return sb.toString()
    }

    private fun StringBuilder.representation(f: StreamFormat) {
        append("<Representation id=\"${f.itag}\" bandwidth=\"${f.bitrate}\" codecs=\"${f.codecs}\"")
        if (f.isVideo) {
            append(" width=\"${f.width}\" height=\"${f.height}\"")
            if (f.fps > 0) append(" frameRate=\"${f.fps}\"")
        } else if (f.audioSampleRate > 0) {
            append(" audioSamplingRate=\"${f.audioSampleRate}\"")
        }
        append(">")
        if (f.isAudio) {
            append("<AudioChannelConfiguration schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" value=\"${f.audioChannels.coerceAtLeast(1)}\"/>")
        }
        append("<BaseURL>").append(xmlEscape(f.url)).append("</BaseURL>")
        append("<SegmentBase indexRange=\"${f.indexStart}-${f.indexEnd}\"><Initialization range=\"${f.initStart}-${f.initEnd}\"/></SegmentBase>")
        append("</Representation>")
    }

    companion object {
        /** Tối đa 4K (2160p). 8K thường chỉ có AV1 – rất ít điện thoại giải mã nổi. */
        const val MAX_VP9_QUALITY = 2160

        /**
         * Đọc phản hồi /player. Chịu được phản hồi thiếu phụ đề, thiếu HLS hay thiếu một số định dạng
         * (ví dụ máy khách ANDROID_VR): khi đó danh sách tương ứng rỗng / null, không ném lỗi.
         */
        fun parse(json: JSONObject, videoId: String, clientUserAgent: String? = null): PlayerData {
            val status = json.s("playabilityStatus", "status")
            if (status != "OK") {
                val reason = textOf(json.o("playabilityStatus")?.opt("reason")) ?: json.s("playabilityStatus", "reason")
                throw InnerTubeException(reason ?: "Không phát được video ($status)")
            }
            val details = json.optJSONObject("videoDetails")
            val sd = json.optJSONObject("streamingData")
            val formats = sd?.a("adaptiveFormats").objects().mapNotNull { f ->
                val url = f.s("url") ?: return@mapNotNull null // bỏ qua định dạng cần giải mã chữ ký
                val mime = f.optString("mimeType")
                val type = mime.substringBefore(";").trim()
                val codecs = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.get(1).orEmpty()
                StreamFormat(
                    itag = f.optInt("itag"),
                    url = url,
                    mimeType = type,
                    codecs = codecs,
                    bitrate = f.optInt("bitrate"),
                    width = f.optInt("width"),
                    height = f.optInt("height"),
                    fps = f.optInt("fps"),
                    initStart = f.optJSONObject("initRange")?.optString("start")?.toLongOrNull() ?: 0,
                    initEnd = f.optJSONObject("initRange")?.optString("end")?.toLongOrNull() ?: 0,
                    indexStart = f.optJSONObject("indexRange")?.optString("start")?.toLongOrNull() ?: 0,
                    indexEnd = f.optJSONObject("indexRange")?.optString("end")?.toLongOrNull() ?: 0,
                    audioSampleRate = f.optString("audioSampleRate").toIntOrNull() ?: 0,
                    audioChannels = f.optInt("audioChannels", 2),
                    audioTrackId = f.s("audioTrack", "id"),
                    audioIsDefault = f.optJSONObject("audioTrack")?.optBoolean("audioIsDefault") == true,
                )
            }
            val expires = sd?.optString("expiresInSeconds")?.toLongOrNull() ?: 21_000L
            val captions = json.a("captions", "playerCaptionsTracklistRenderer", "captionTracks").objects().mapNotNull { c ->
                val url = c.s("baseUrl") ?: return@mapNotNull null
                CaptionTrack(
                    name = textOf(c.opt("name")) ?: c.optString("languageCode"),
                    languageCode = c.optString("languageCode"),
                    url = if (url.startsWith("/")) "https://www.youtube.com$url" else url,
                    isAuto = c.optString("kind") == "asr",
                )
            }
            return PlayerData(
                videoId = videoId,
                title = details?.optString("title").orEmpty(),
                author = details?.optString("author").orEmpty(),
                channelId = details?.s("channelId"),
                lengthSec = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0,
                viewCount = details?.optString("viewCount")?.toLongOrNull() ?: -1,
                isLive = details?.optBoolean("isLive") == true,
                thumbnail = bestImage(details?.a("thumbnail", "thumbnails")),
                formats = formats,
                hlsUrl = sd?.s("hlsManifestUrl"),
                playbackTrackingUrl = json.s("playbackTracking", "videostatsPlaybackUrl", "baseUrl"),
                watchtimeTrackingUrl = json.s("playbackTracking", "videostatsWatchtimeUrl", "baseUrl"),
                expiresAtMs = SystemClock.elapsedRealtime() + expires * 1000,
                captions = captions,
                clientUserAgent = clientUserAgent,
            )
        }

        private fun xmlEscape(s: String) = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
