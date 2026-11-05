package com.luzart.tubesach.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MimeTypes

/**
 * Máy này giải mã VP9 bằng PHẦN CỨNG được tới đâu?
 *
 * YouTube có hai loại luồng hình: H.264 (tối đa 1080p, máy nào cũng chạy) và VP9 (tới 4K, tốn ít
 * dữ liệu hơn nhiều ở cùng độ phân giải). VP9 chỉ được dùng khi máy có bộ giải mã phần cứng xử lý được
 * đúng kích thước + tốc độ khung hình đó – giải mã bằng phần mềm thì nóng máy, hao pin, dễ giật.
 */
object VideoCodecSupport {

    const val VP9 = MimeTypes.VIDEO_VP9

    /** Android quá cũ (trước 7.0): luôn dùng H.264. */
    private const val MIN_SDK = Build.VERSION_CODES.N

    /** Test: giả lập khả năng của máy (rộng, cao, khung hình/giây) → có giải mã được không. */
    @VisibleForTesting
    @Volatile
    internal var override: ((width: Int, height: Int, fps: Int) -> Boolean)? = null

    private val cache = HashMap<Long, Boolean>()

    @Volatile
    private var decoders: List<MediaCodecInfo>? = null

    private val hardwareDecoders: List<MediaCodecInfo>
        get() = decoders ?: runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals(VP9, ignoreCase = true) } && isHardware(info)
            }
        }.getOrDefault(emptyList()).also { decoders = it }

    /** Test: quên kết quả đã kiểm tra (sau khi đổi danh sách bộ giải mã giả lập). */
    @VisibleForTesting
    internal fun resetForTest() {
        override = null
        decoders = null
        synchronized(cache) { cache.clear() }
    }

    /** Có bộ giải mã VP9 phần cứng xử lý được video [width]×[height] ở [fps] khung hình/giây không. */
    fun vp9Supported(width: Int, height: Int, fps: Int): Boolean {
        override?.let { return it(width, height, fps) }
        if (Build.VERSION.SDK_INT < MIN_SDK || width <= 0 || height <= 0) return false
        val key = (width.toLong() shl 32) or (height.toLong() shl 12) or fps.coerceIn(0, 4095).toLong()
        return synchronized(cache) { cache.getOrPut(key) { check(width, height, fps) } }
    }

    /** Máy có giải mã VP9 phần cứng được ít nhất 1080p không (để hiện/ẩn tuỳ chọn trong Cài đặt). */
    fun vp9Available(): Boolean = vp9Supported(1920, 1080, 30)

    private fun check(width: Int, height: Int, fps: Int): Boolean {
        val rate = fps.coerceAtLeast(24).toDouble()
        return hardwareDecoders.any { info ->
            val caps = runCatching { info.getCapabilitiesForType(VP9).videoCapabilities }.getOrNull() ?: return@any false
            // Video dọc (Shorts): nhiều bộ giải mã chỉ khai báo kích thước ngang → thử cả hai chiều.
            runCatching { caps.areSizeAndRateSupported(width, height, rate) || caps.areSizeAndRateSupported(height, width, rate) }
                .getOrDefault(false)
        }
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated && !info.isSoftwareOnly
        val name = info.name.lowercase()
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.startsWith("omx.ffmpeg.") ||
            name.startsWith("c2.google.") || name.contains(".sw.") || name.endsWith(".sw"))
    }
}
