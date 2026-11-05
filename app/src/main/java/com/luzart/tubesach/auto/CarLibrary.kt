package com.luzart.tubesach.auto

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.MediaIds
import java.util.concurrent.ConcurrentHashMap

/**
 * Thư viện video hiện trên màn hình Android Auto (phần "nghe" – dùng được cả khi đang lái xe).
 *
 * Cấu trúc giống các tab của app:
 * ```
 * Trang chủ | Kênh đăng ký | Đã xem | Thư viện
 *                                     ├─ Thịnh hành tại Việt Nam
 *                                     ├─ Nhạc hàng đầu Việt Nam
 *                                     ├─ Xem sau
 *                                     └─ Video đã thích
 * ```
 * Mỗi video là một mục "phát được"; chọn vào thì [com.luzart.tubesach.player.SessionPlayer] phát nó.
 */
@OptIn(UnstableApi::class)
class CarLibrary(
    private val store: LocalStore,
    private val artworkUri: (String?) -> Uri?,
    private val source: Source = Source.YouTube,
) {

    /** Nơi lấy dữ liệu (tách riêng để test không cần mạng). */
    interface Source {
        suspend fun home(): FeedPage
        suspend fun trending(): FeedPage
        suspend fun topMusic(): FeedPage
        suspend fun search(query: String): FeedPage
        suspend fun subscriptions(store: LocalStore): List<VideoItem>

        object YouTube : Source {
            override suspend fun home() = YouTubeRepository.home()
            override suspend fun trending() = YouTubeRepository.trendingVietnam()
            override suspend fun topMusic() = YouTubeRepository.topMusicVietnam()
            override suspend fun search(query: String) = YouTubeRepository.search(query, YouTubeRepository.SearchFilter.VIDEOS)
            override suspend fun subscriptions(store: LocalStore) = YouTubeRepository.subscriptionFeed(store.subscriptions.value)
        }
    }

    /** Video đã đưa lên màn hình xe, để khi chọn phát còn biết tiêu đề/ảnh (Android Auto chỉ gửi lại mã). */
    private val known = ConcurrentHashMap<String, VideoItem>()
    private val searchCache = ConcurrentHashMap<String, List<VideoItem>>()

    fun root(): MediaItem = folder(ROOT, "Tube Sạch", browsableStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)

    /** Thêm vào gốc: hỗ trợ tìm kiếm, kiểu hiển thị mặc định. */
    fun rootExtras(): Bundle = Bundle().apply {
        putBoolean(EXTRA_SEARCH_SUPPORTED, true)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        ROOT -> listOf(
            folder(HOME, "Trang chủ"),
            folder(SUBSCRIPTIONS, "Kênh đăng ký"),
            folder(HISTORY, "Đã xem"),
            folder(LIBRARY, "Thư viện"),
        )
        LIBRARY -> listOf(
            folder(TRENDING, "Thịnh hành tại Việt Nam"),
            folder(TOP_MUSIC, "Nhạc hàng đầu Việt Nam"),
            folder(WATCH_LATER, "Xem sau"),
            folder(LIKED, "Video đã thích"),
        )
        HOME -> homeVideos().map(::playable)
        SUBSCRIPTIONS -> source.subscriptions(store).let(::visible).take(MAX_ITEMS).map(::playable)
        HISTORY -> store.history.value.filterNot { it.video.isShort }.take(MAX_ITEMS).map { h ->
            val dur = h.video.durationSec * 1000
            val fraction = if (dur > 0) (h.positionMs.toDouble() / dur).coerceIn(0.0, 1.0) else null
            playable(h.video, fraction)
        }
        WATCH_LATER -> store.favorites.value.take(MAX_ITEMS).map(::playable)
        LIKED -> store.liked.value.take(MAX_ITEMS).map(::playable)
        TRENDING -> videosOf(source.trending()).map(::playable)
        TOP_MUSIC -> videosOf(source.topMusic()).map(::playable)
        else -> emptyList()
    }

    /** Trang chủ trống (chưa xem gì) thì hiện "Thịnh hành" như trên điện thoại. */
    private suspend fun homeVideos(): List<VideoItem> {
        val home = runCatching { videosOf(source.home()) }.getOrDefault(emptyList())
        return home.ifEmpty { videosOf(source.trending()) }
    }

    fun item(mediaId: String): MediaItem? = when {
        mediaId == ROOT -> root()
        mediaId.startsWith(MediaIds.VIDEO_PREFIX) -> known[mediaId.removePrefix(MediaIds.VIDEO_PREFIX)]?.let { playable(it) }
        else -> null
    }

    suspend fun search(query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val videos = searchCache[q] ?: videosOf(source.search(q)).also {
            searchCache[q] = it
            store.addRecentSearch(q)
        }
        return videos.map(::playable)
    }

    /**
     * Chuẩn bị các mục trước khi phát: điền lại tiêu đề, ảnh (nếu đã biết) để màn hình "Đang phát" hiện ngay.
     * Mục chỉ có câu tìm kiếm (nói "Ok Google, mở … trên Tube Sạch") → tìm rồi phát kết quả đầu tiên.
     */
    suspend fun resolve(items: List<MediaItem>): List<MediaItem> {
        val query = items.firstOrNull()?.requestMetadata?.searchQuery
        if (items.size == 1 && items[0].mediaId.isEmpty() && !query.isNullOrBlank()) {
            return search(query)
        }
        return items.mapNotNull { item ->
            val video = MediaIds.videoOf(item) ?: return@mapNotNull null
            playable(known[video.id] ?: video)
        }
    }

    private fun videosOf(page: FeedPage): List<VideoItem> = visible(
        page.entries.flatMap { e ->
            when (e) {
                is FeedEntry.Video -> listOf(e.video)
                is FeedEntry.Shelf -> e.videos
                else -> emptyList()
            }
        },
    ).distinctBy { it.id }.take(MAX_ITEMS)

    /** Bỏ Shorts, video đã bấm "Không quan tâm"; giữ video trực tiếp (nghe radio, tin tức trực tiếp). */
    private fun visible(list: List<VideoItem>): List<VideoItem> {
        val hidden = store.hidden.value
        return list.filter { !it.isShort && it.id !in hidden && VideoItem.videoIdOf(it.url) != null }
    }

    private fun folder(id: String, title: String, browsableStyle: Int? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            if (browsableStyle != null) putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsableStyle)
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_VIDEOS)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    fun playable(video: VideoItem, completion: Double? = null): MediaItem {
        known[video.id] = video
        val subtitle = listOfNotNull(
            video.uploader.ifBlank { null },
            if (video.isLive) "Trực tiếp" else video.viewText,
            video.uploadedText,
        ).joinToString(" • ")
        val extras = Bundle().apply {
            video.thumbnail?.let { putString(MediaIds.EXTRA_THUMBNAIL, it) }
            if (completion != null) {
                val status = when {
                    completion >= 0.95 -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                    completion > 0.0 -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                    else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                }
                putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, status)
                if (status == MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED) {
                    putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, completion)
                }
            }
        }
        return MediaItem.Builder()
            .setMediaId(MediaIds.ofVideo(video.id))
            // Trình phát không dùng link này (SessionPlayer tự lấy link phát); chỉ để Media3 coi mục là hợp lệ.
            .setUri(video.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title.ifBlank { "Video YouTube" })
                    .setDisplayTitle(video.title.ifBlank { "Video YouTube" })
                    .setArtist(video.uploader)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artworkUri(video.thumbnail))
                    .setDurationMs(if (video.durationSec > 0) video.durationSec * 1000 else null)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val SUBSCRIPTIONS = "subscriptions"
        const val HISTORY = "history"
        const val LIBRARY = "library"
        const val TRENDING = "trending"
        const val TOP_MUSIC = "top_music"
        const val WATCH_LATER = "watch_later"
        const val LIKED = "liked"

        /** Android Auto chỉ hiện được một số mục; nhiều hơn chỉ tốn thời gian tải ảnh. */
        const val MAX_ITEMS = 50

        /** Khoá cũ của MediaBrowserService: báo cho Android Auto biết app có ô tìm kiếm. */
        const val EXTRA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
    }
}
