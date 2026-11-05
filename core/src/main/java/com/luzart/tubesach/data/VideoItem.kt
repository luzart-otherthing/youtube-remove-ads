package com.luzart.tubesach.data

import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Một video trong danh sách (trang chủ, tìm kiếm, liên quan, lịch sử…).
 * Các chữ hiển thị (lượt xem, ngày đăng) giữ nguyên câu chữ tiếng Việt do YouTube trả về.
 */
data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String?,
    val durationSec: Long,
    val viewCount: Long = -1,
    val uploadedText: String? = null,
    val isLive: Boolean = false,
    val channelId: String? = null,
    val channelAvatar: String? = null,
    /** Ví dụ "1,2 Tr lượt xem" – lấy nguyên văn từ YouTube. */
    val viewText: String? = null,
    /** Ví dụ "12:34"; dùng khi không có [durationSec]. */
    val durationText: String? = null,
    val isShort: Boolean = false,
    /** Nhãn phụ như "Danh sách kết hợp", "Mới". */
    val badge: String? = null,
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
        .put("channelId", channelId ?: "")
        .put("avatar", channelAvatar ?: "")
        .put("viewText", viewText ?: "")
        .put("durationText", durationText ?: "")
        .put("short", isShort)

    companion object {
        fun ofId(id: String, title: String = "", uploader: String = "", thumbnail: String? = null) = VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = title,
            uploader = uploader,
            thumbnail = thumbnail ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            durationSec = 0,
        )

        fun fromJson(o: JSONObject) = VideoItem(
            url = o.getString("url"),
            title = o.optString("title"),
            uploader = o.optString("uploader"),
            thumbnail = o.optString("thumbnail").ifBlank { null },
            durationSec = o.optLong("duration", 0),
            viewCount = o.optLong("views", -1),
            uploadedText = o.optString("uploaded").ifBlank { null },
            isLive = o.optBoolean("live", false),
            channelId = o.optString("channelId").ifBlank { null },
            channelAvatar = o.optString("avatar").ifBlank { null },
            viewText = o.optString("viewText").ifBlank { null },
            durationText = o.optString("durationText").ifBlank { null },
            isShort = o.optBoolean("short", false),
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
            channelId = item.uploaderUrl?.substringAfterLast("/channel/", "")?.ifBlank { null },
            channelAvatar = bestThumbnail(item.uploaderAvatars, 68),
            isShort = item.isShortFormContent,
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
            channelId = info.uploaderUrl?.substringAfterLast("/channel/", "")?.ifBlank { null },
            channelAvatar = bestThumbnail(info.uploaderAvatars, 68),
        )

        /** Chọn ảnh có chiều rộng gần [target] nhất. */
        fun bestThumbnail(images: List<Image>?, target: Int = 480): String? {
            if (images.isNullOrEmpty()) return null
            val known = images.filter { it.width > 0 }
            if (known.isEmpty()) return images.last().url
            return known.minByOrNull { kotlin.math.abs(it.width - target) }?.url
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

        /** "1:02:03" → 3723 giây. */
        fun parseDuration(text: String?): Long {
            if (text.isNullOrBlank()) return 0
            val parts = text.trim().split(':').map { it.toLongOrNull() ?: return 0 }
            return parts.fold(0L) { acc, p -> acc * 60 + p }
        }
    }
}

/** Một video Shorts (dọc). */
data class ShortItem(
    val id: String,
    val title: String,
    val viewText: String?,
    val thumbnail: String?,
    val sequenceParams: String? = null,
)

/** Thông tin gọn của một kênh. */
data class ChannelRef(
    val id: String,
    val name: String,
    val avatar: String?,
    val subtitle: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("avatar", avatar ?: "")

    companion object {
        fun fromJson(o: JSONObject) = ChannelRef(
            o.getString("id"),
            o.optString("name"),
            o.optString("avatar").ifBlank { null },
        )
    }
}

/** Một khối trong trang chủ / kết quả tìm kiếm. */
sealed interface FeedEntry {
    val key: String

    data class Video(val video: VideoItem) : FeedEntry {
        override val key get() = "v:" + video.id
    }

    data class ShortsShelf(val title: String, val shorts: List<ShortItem>) : FeedEntry {
        override val key get() = "s:" + shorts.firstOrNull()?.id
    }

    data class Shelf(val title: String, val videos: List<VideoItem>) : FeedEntry {
        override val key get() = "h:" + title + videos.firstOrNull()?.id
    }

    data class Channel(val channel: ChannelRef) : FeedEntry {
        override val key get() = "c:" + channel.id
    }
}

data class Chip(val title: String, val token: String?)

data class FeedPage(
    val entries: List<FeedEntry>,
    val continuation: String?,
    val chips: List<Chip> = emptyList(),
)

/** Thông tin trang xem video (tiêu đề, kênh, lượt thích, mô tả, video liên quan). */
data class WatchInfo(
    val videoId: String,
    val title: String,
    val viewText: String?,
    val shortViewText: String?,
    val dateText: String?,
    val relativeDateText: String?,
    val likeText: String?,
    val description: String,
    val channel: ChannelRef?,
    val subscriberText: String?,
    val commentCountText: String?,
    val isLive: Boolean,
    val related: List<VideoItem>,
    val relatedContinuation: String?,
)

data class ChannelPage(
    val channel: ChannelRef,
    val handle: String?,
    val subscriberText: String?,
    val videoCountText: String?,
    val banner: String?,
    val description: String?,
    val videosParams: String?,
    val liveParams: String?,
)

data class CommentItem(
    val author: String,
    val avatar: String?,
    val text: String,
    val time: String?,
    val likes: String?,
    val replies: Int,
    val pinned: Boolean,
)
