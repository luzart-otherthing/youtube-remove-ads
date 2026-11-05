package com.luzart.tubesach.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import com.luzart.tubesach.innertube.HdrType
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

    /**
     * Chính sách cho TV: VP9 tới mức cao nhất chip làm được (có HDR nếu màn hình + chip hỗ trợ
     * và [allowHdr]); không có VP9 phần cứng thì H.264 1080p.
     */
    fun tvPolicy(context: Context? = null, allowHdr: Boolean = true): StreamPolicy {
        val base = policyFor(maxHardwareVp9Height)
        if (base.codec != VideoCodec.VP9 || context == null || !allowHdr) return base
        return base.copy(hdr = hdrTypes(context))
    }

    /**
     * Loại HDR phát được = màn hình hiển thị được **và** chip giải mã VP9 profile 2 (10‑bit) bằng phần cứng.
     * Thiếu một trong hai mà vẫn phát HDR thì màu nhạt / sai hoặc không phát được → không bật.
     */
    fun hdrTypes(context: Context): Set<HdrType> = runCatching {
        val display = displayHdrTypes(context)
        val decoder = decoderHdrTypes
        display.intersect(decoder)
    }.getOrDefault(emptySet())

    /** Loại HDR màn hình hiển thị được (đọc từ hệ thống; TV đặt "Định dạng HDR: Tự động"). */
    @Suppress("DEPRECATION")
    fun displayHdrTypes(context: Context): Set<HdrType> {
        if (Build.VERSION.SDK_INT < 24) return emptySet()
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return emptySet()
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptySet()
        val types = buildSet {
            if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes.forEach { add(it) }
            display.hdrCapabilities?.supportedHdrTypes?.forEach { add(it) }
        }
        return hdrFromDisplayTypes(types)
    }

    /** HDR10+ cũng phát được HDR10 thường. */
    fun hdrFromDisplayTypes(types: Collection<Int>): Set<HdrType> = buildSet {
        if (Display.HdrCapabilities.HDR_TYPE_HDR10 in types || Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types) add(HdrType.HDR10)
        if (Display.HdrCapabilities.HDR_TYPE_HLG in types) add(HdrType.HLG)
    }

    /** Loại HDR chip giải mã VP9 phần cứng hỗ trợ (profile 2 HDR / 10-bit). */
    val decoderHdrTypes: Set<HdrType> by lazy {
        runCatching {
            val profiles = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(VP9, ignoreCase = true) } && isHardware(it) }
                .flatMap { d -> runCatching { d.getCapabilitiesForType(VP9).profileLevels.map { it.profile } }.getOrDefault(emptyList()) }
            hdrFromDecoderProfiles(profiles)
        }.getOrDefault(emptySet())
    }

    fun hdrFromDecoderProfiles(profiles: Collection<Int>): Set<HdrType> = buildSet {
        val hdr10 = MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR in profiles ||
            MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus in profiles
        val tenBit = hdr10 || MediaCodecInfo.CodecProfileLevel.VP9Profile2 in profiles
        if (hdr10) add(HdrType.HDR10)
        if (tenBit) add(HdrType.HLG)
    }

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
