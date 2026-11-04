package com.luzart.tubesach.data

import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Thông tin gọn của một video, dùng cho danh sách, lịch sử, yêu thích. */
data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Long,
    val viewCount: Long = -1,
    val uploadedText: String? = null,
    val isLive: Boolean = false,
) {
    val id: String get() = videoIdOf(url) ?: url

    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("uploader", uploader)
        .put("thumbnail", thumbnail ?: "")
        .put("duration", durationSec)
        .put("views", viewCount)
        .put("uploaded", uploadedText ?: "")
        .put("live", isLive)

    companion object {
        fun fromJson(o: JSONObject) = VideoItem(
            url = o.getString("url"),
            title = o.optString("title"),
            uploader = o.optString("uploader"),
            thumbnail = o.optString("thumbnail").ifBlank { null },
            durationSec = o.optLong("duration", 0),
            viewCount = o.optLong("views", -1),
            uploadedText = o.optString("uploaded").ifBlank { null },
            isLive = o.optBoolean("live", false),
        )

        fun from(item: StreamInfoItem) = VideoItem(
            url = item.url,
            title = item.name,
            uploader = item.uploaderName ?: "",
            thumbnail = bestThumbnail(item.thumbnails),
            durationSec = item.duration,
            viewCount = item.viewCount,
            uploadedText = item.textualUploadDate,
            isLive = item.streamType == StreamType.LIVE_STREAM ||
                item.streamType == StreamType.AUDIO_LIVE_STREAM,
        )

        fun from(info: StreamInfo) = VideoItem(
            url = info.url,
            title = info.name,
            uploader = info.uploaderName ?: "",
            thumbnail = bestThumbnail(info.thumbnails),
            durationSec = info.duration,
            viewCount = info.viewCount,
            uploadedText = info.textualUploadDate,
            isLive = info.streamType == StreamType.LIVE_STREAM ||
                info.streamType == StreamType.AUDIO_LIVE_STREAM,
        )

        /** Chọn ảnh thu nhỏ có chiều rộng gần 480px nhất (đủ nét, không quá nặng). */
        fun bestThumbnail(images: List<Image>?): String? {
            if (images.isNullOrEmpty()) return null
            val known = images.filter { it.width > 0 }
            if (known.isEmpty()) return images.last().url
            return known.minByOrNull { kotlin.math.abs(it.width - 480) }?.url
        }

        private val ID_PATTERNS = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})"),
        )

        fun videoIdOf(url: String): String? =
            ID_PATTERNS.firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

        /** Tìm link YouTube trong một đoạn văn bản (ví dụ tin nhắn được chia sẻ từ Zalo). */
        fun findYoutubeUrl(text: String?): String? {
            if (text.isNullOrBlank()) return null
            val id = videoIdOf(text) ?: return null
            return "https://www.youtube.com/watch?v=$id"
        }
    }
}
