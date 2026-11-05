package com.luzart.tubesach.player

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.innertube.Vp9Policy
import com.luzart.tubesach.net.OkHttpDownloader
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream

/**
 * Dựng nguồn phát cho ExoPlayer từ thông tin video của NewPipeExtractor.
 *
 * Giống cách NewPipe làm: lấy riêng luồng hình + luồng tiếng (chất lượng cao) rồi ghép lại.
 * Tất cả đều là file video gốc trên máy chủ googlevideo → không có quảng cáo chèn vào.
 */
@OptIn(UnstableApi::class)
class StreamSourceFactory(context: Context, http: OkHttpClient) {

    class NoPlayableStreamException : Exception("Không tìm thấy luồng phát phù hợp")

    /**
     * Tải video bằng chung OkHttp với phần còn lại của app → khi bật màn hình / đổi mạng,
     * [com.luzart.tubesach.net.NetworkMonitor] bỏ được cả các kết nối cũ của trình phát.
     */
    private val httpFactory: HttpDataSource.Factory = OkHttpDataSource.Factory(http)

    /** Mỗi loại máy khách YouTube (Android/iOS/visionOS/Web) cần User-Agent tương ứng. */
    private val youtubeFactory = ResolvingDataSource.Factory(httpFactory) { spec ->
        val url = spec.uri.toString()
        val headers = HashMap<String, String>()
        headers["User-Agent"] = userAgentFor(url)
        if (YoutubeParsingHelper.isWebStreamingUrl(url) ||
            YoutubeParsingHelper.isWebEmbeddedPlayerStreamingUrl(url)
        ) {
            headers["Origin"] = "https://www.youtube.com"
            headers["Referer"] = "https://www.youtube.com/"
        }
        spec.withAdditionalHeaders(headers)
    }

    private val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context, youtubeFactory)

    private val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    /**
     * Đường nhanh: dùng dữ liệu /player (visionOS). Dựng manifest DASH có nhiều độ phân giải
     * → ExoPlayer tự đổi chất lượng theo mạng. Phụ đề (nếu chọn) được gắn kèm.
     */
    fun buildDirect(
        data: PlayerData,
        metadata: MediaMetadata,
        subtitle: MediaItem.SubtitleConfiguration?,
        vp9: Vp9Policy? = null,
    ): MediaSource {
        val builder = MediaItem.Builder().setMediaId(data.videoId).setMediaMetadata(metadata)
        val manifest = if (data.isLive) null else data.dashManifest(vp9 = vp9)
        when {
            manifest != null -> builder
                .setUri("data:application/dash+xml;base64," + Base64.encodeToString(manifest.toByteArray(), Base64.NO_WRAP))
                .setMimeType(MimeTypes.APPLICATION_MPD)
            data.hlsUrl != null -> builder.setUri(data.hlsUrl).setMimeType(MimeTypes.APPLICATION_M3U8)
            else -> throw NoPlayableStreamException()
        }
        if (subtitle != null) builder.setSubtitleConfigurations(listOf(subtitle))
        return mediaSourceFactory.createMediaSource(builder.build())
    }

    private fun userAgentFor(url: String): String = when {
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        YoutubeParsingHelper.isIosStreamingUrl(url) -> YoutubeParsingHelper.getIosUserAgent(null)
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        else -> OkHttpDownloader.USER_AGENT
    }

    fun build(info: StreamInfo, maxHeight: Int, metadata: MediaMetadata): MediaSource {
        val mediaItemBuilder = { uri: String ->
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(info.id)
                .setMediaMetadata(metadata)
        }

        // Video trực tiếp (live): dùng HLS/DASH do YouTube cung cấp.
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            if (info.hlsUrl.isNotEmpty()) {
                return HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItemBuilder(info.hlsUrl).build())
            }
            if (info.dashMpdUrl.isNotEmpty()) {
                return DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItemBuilder(info.dashMpdUrl).build())
            }
        }

        val video = pickVideo(info.videoOnlyStreams, maxHeight)
        val audio = pickAudio(info.audioStreams)

        if (video != null && audio != null) {
            val videoSource = buildSingle(video, info, mediaItemBuilder(video.content).build())
            val audioSource = buildSingle(audio, info, mediaItemBuilder(audio.content).build())
            return MergingMediaSource(true, true, videoSource, audioSource)
        }

        // Dự phòng: luồng đã ghép sẵn hình + tiếng (thường 360p).
        val muxed = pickVideo(info.videoStreams, maxHeight)
        if (muxed != null) {
            return ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItemBuilder(muxed.content).build())
        }

        // Dự phòng cuối: chỉ có tiếng.
        if (audio != null) {
            return buildSingle(audio, info, mediaItemBuilder(audio.content).build())
        }

        if (info.hlsUrl.isNotEmpty()) {
            return HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItemBuilder(info.hlsUrl).build())
        }
        throw NoPlayableStreamException()
    }

    /**
     * Luồng "progressive" của YouTube được bọc trong một manifest DASH tự tạo,
     * giúp tua nhanh và tải từng đoạn nhỏ (giống NewPipe).
     */
    private fun buildSingle(stream: Stream, info: StreamInfo, item: MediaItem): MediaSource {
        val itag = stream.itagItem
        if (stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && itag != null) {
            try {
                val manifestText = YoutubeProgressiveDashManifestCreator
                    .fromProgressiveStreamingUrl(stream.content, itag, info.duration)
                val manifest = DashManifestParser().parse(
                    Uri.parse(stream.content),
                    ByteArrayInputStream(manifestText.toByteArray(Charsets.UTF_8)),
                )
                return DashMediaSource.Factory(dataSourceFactory).createMediaSource(manifest, item)
            } catch (_: Exception) {
                // rơi xuống phát progressive bình thường
            }
        }
        return when (stream.deliveryMethod) {
            DeliveryMethod.HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(item)
            DeliveryMethod.DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(item)
            else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(item)
        }
    }

    companion object {
        /** Cạnh ngắn của khung hình (video dọc 1080×1920 vẫn là 1080p). */
        private fun heightOf(v: VideoStream): Int =
            if (v.height > 0 && v.width > 0) minOf(v.width, v.height)
            else if (v.height > 0) v.height else v.getResolution().filter { it.isDigit() }.take(4).toIntOrNull() ?: 0

        private fun isUsable(s: Stream) = s.isUrl && s.content.isNotEmpty() &&
            (s.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || s.deliveryMethod == DeliveryMethod.HLS)

        /**
         * Chọn luồng hình cao nhất nhưng không vượt [maxHeight].
         * Ưu tiên H.264 (MP4) vì máy cũ giải mã phần cứng tốt, đỡ nóng máy, đỡ tốn pin.
         */
        fun pickVideo(streams: List<VideoStream>, maxHeight: Int): VideoStream? {
            val usable = streams.filter(::isUsable)
            if (usable.isEmpty()) return null
            val withinLimit = usable.filter { heightOf(it) in 1..maxHeight }
            val pool = withinLimit.ifEmpty { listOf(usable.minBy { heightOf(it).takeIf { h -> h > 0 } ?: Int.MAX_VALUE }) }
            val bestHeight = pool.maxOf { heightOf(it) }
            val candidates = pool.filter { heightOf(it) == bestHeight }
            return candidates.firstOrNull { it.format == MediaFormat.MPEG_4 && it.codec?.startsWith("avc") == true }
                ?: candidates.firstOrNull { it.format == MediaFormat.MPEG_4 }
                ?: candidates.first()
        }

        /** Chọn luồng tiếng: ưu tiên tiếng gốc (hoặc tiếng Việt nếu có), AAC ~128kbps. */
        fun pickAudio(streams: List<AudioStream>): AudioStream? {
            val usable = streams.filter(::isUsable)
            if (usable.isEmpty()) return null
            val vietnamese = usable.filter { it.audioLocale?.language == "vi" }
            val original = usable.filter {
                it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
            }
            val pool = vietnamese.ifEmpty { original.ifEmpty { usable } }
            val m4a = pool.filter { it.format == MediaFormat.M4A }
            val chosenPool = m4a.ifEmpty { pool }
            return chosenPool.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
        }
    }
}
