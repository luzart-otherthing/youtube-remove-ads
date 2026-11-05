package com.luzart.tubesach.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.luzart.tubesach.innertube.StreamPolicy
import com.luzart.tubesach.innertube.VideoCodec

/**
 * Kiểm tra chip giải mã video của máy để chọn chất lượng cao nhất có thể.
 *
 * YouTube chỉ có 2K (1440p), 4K (2160p), 8K (4320p) ở dạng **VP9** (không có H.264).
 * TV Google TV / Android TV 4K đều có bộ giải mã VP9 bằng phần cứng (YouTube bắt buộc),
 * còn giải mã bằng phần mềm thì quá nặng cho TV → chỉ dùng VP9 khi có phần cứng.
 */
object CodecSupport {

    private const val VP9 = "video/x-vnd.on2.vp9"

    /** Các mức thử, từ cao xuống thấp: 8K, 4K, 2K, Full HD. */
    val SIZES = listOf(7680 to 4320, 3840 to 2160, 2560 to 1440, 1920 to 1080)

    /** Độ phân giải VP9 cao nhất mà phần cứng giải mã được ở 30 hình/giây (0 = không có). */
    val maxHardwareVp9Height: Int by lazy {
        runCatching {
            val decoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(VP9, ignoreCase = true) } && isHardware(it) }
            pickHeight { w, h ->
                decoders.any { d ->
                    runCatching {
                        d.getCapabilitiesForType(VP9).videoCapabilities?.areSizeAndRateSupported(w, h, 30.0) == true
                    }.getOrDefault(false)
                }
            }
        }.getOrDefault(0)
    }

    /** Chính sách cho TV: VP9 tới mức cao nhất chip làm được; không có thì H.264 1080p. */
    fun tvPolicy(): StreamPolicy = policyFor(maxHardwareVp9Height)

    fun policyFor(vp9Height: Int): StreamPolicy =
        if (vp9Height >= 1080) StreamPolicy(VideoCodec.VP9, vp9Height) else StreamPolicy.PHONE

    /** Mức cao nhất trong [SIZES] mà [supports] trả về true (0 nếu không mức nào). */
    fun pickHeight(supports: (width: Int, height: Int) -> Boolean): Int =
        SIZES.firstOrNull { (w, h) -> supports(w, h) }?.second ?: 0

    /** "4K", "2K"… để hiện cho người dùng. */
    fun label(height: Int): String = when {
        height >= 4320 -> "8K"
        height >= 2160 -> "4K"
        height >= 1440 -> "2K"
        height >= 1080 -> "Full HD"
        height >= 720 -> "HD"
        else -> "${height}p"
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 29) return info.isHardwareAccelerated && !info.isSoftwareOnly
        val n = info.name.lowercase()
        return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.contains(".sw.") || n.startsWith("omx.ffmpeg."))
    }
}
