package com.luzart.tubesach.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.luzart.tubesach.data.VideoItem

/**
 * Lớp bọc trình phát dùng cho MediaSession (thông báo, màn hình khoá, Android Auto, tai nghe…).
 *
 * Android Auto chỉ gửi "mã video" khi người dùng chọn một video trong danh sách. Trình phát thật không tự phát được
 * mã đó: phải gọi YouTube để lấy link phát rồi dựng manifest. Vì vậy mọi yêu cầu "phát video này" từ bên ngoài
 * được chuyển cho [PlaybackController.play], giống hệt như khi bấm vào video trong app.
 */
@OptIn(UnstableApi::class)
class SessionPlayer(
    player: Player,
    private val playVideo: (VideoItem) -> Unit = { PlaybackController.play(it) },
) : ForwardingSimpleBasePlayer(player) {

    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val item = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
        val video = item?.let(MediaIds::videoOf)
        if (video != null) playVideo(video)
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        // Không có hàng chờ: "thêm vào" khi chưa phát gì thì phát luôn video đầu tiên.
        if (player.mediaItemCount == 0) {
            mediaItems.firstNotNullOfOrNull(MediaIds::videoOf)?.let(playVideo)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>): ListenableFuture<*> {
        mediaItems.firstNotNullOfOrNull(MediaIds::videoOf)?.let(playVideo)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        // Video đang được lấy link (chưa có nguồn phát): chuẩn bị lúc này sẽ làm trình phát báo "đã hết video".
        if (player.mediaItemCount == 0) return Futures.immediateVoidFuture()
        return super.handlePrepare()
    }
}

/** Quy ước mã cho các mục trong thư viện Android Auto. */
object MediaIds {
    const val VIDEO_PREFIX = "v:"

    fun ofVideo(id: String) = VIDEO_PREFIX + id

    /** Lấy lại video từ một mục do Android Auto gửi về (có thể chỉ có mã, không có tiêu đề). */
    fun videoOf(item: MediaItem): VideoItem? {
        val id = item.mediaId.removePrefix(VIDEO_PREFIX).takeIf { item.mediaId.startsWith(VIDEO_PREFIX) }
            ?: VideoItem.videoIdOf(item.mediaId)
            ?: item.localConfiguration?.uri?.toString()?.let(VideoItem::videoIdOf)
            ?: return null
        if (!Regex("[A-Za-z0-9_-]{11}").matches(id)) return null
        val m = item.mediaMetadata
        return VideoItem.ofId(
            id = id,
            title = m.title?.toString().orEmpty(),
            uploader = m.artist?.toString().orEmpty(),
        ).let { v ->
            val thumb = m.extras?.getString(EXTRA_THUMBNAIL)
            if (thumb != null) v.copy(thumbnail = thumb) else v
        }
    }

    /** Ảnh gốc (https) – ảnh hiển thị trên Android Auto đi qua ArtworkProvider. */
    const val EXTRA_THUMBNAIL = "com.luzart.tubesach.THUMBNAIL"
}
