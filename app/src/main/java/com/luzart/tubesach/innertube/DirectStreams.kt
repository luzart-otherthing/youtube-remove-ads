package com.luzart.tubesach.innertube

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
}

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
    val expiresAtMs: Long,
    val captions: List<CaptionTrack> = emptyList(),
) {
    /** Các độ phân giải có thể chọn trong menu "Chất lượng". */
    val availableHeights: List<Int>
        get() = formats.filter { it.isVideo && it.mimeType == "video/mp4" && it.codecs.startsWith("avc1") && it.height in 1..1080 }
            .map { it.height }.distinct().sortedDescending()

    /**
     * Dựng manifest DASH gồm nhiều độ phân giải H.264 (≤ [maxHeight]) và một luồng tiếng AAC.
     * ExoPlayer sẽ tự chọn chất lượng theo tốc độ mạng – giống chế độ "Tự động" của YouTube.
     */
    fun dashManifest(maxHeight: Int = 1080): String? {
        val video = formats
            .filter { it.isVideo && it.mimeType == "video/mp4" && it.codecs.startsWith("avc1") && it.height in 1..maxHeight }
            .filter { it.indexEnd > 0 && it.initEnd > 0 }
            .distinctBy { it.height to it.fps }
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
        fun parse(json: JSONObject, videoId: String): PlayerData {
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
                expiresAtMs = System.currentTimeMillis() + expires * 1000,
                captions = captions,
            )
        }

        private fun xmlEscape(s: String) = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
