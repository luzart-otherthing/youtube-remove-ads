package com.luzart.tubesach.tv.player

import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.Settings
import com.luzart.tubesach.player.CodecSupport
import com.luzart.tubesach.player.PlaybackController

/** Một lựa chọn trong bảng "Chất lượng" (dùng chung cho trình phát và trang Cài đặt). */
data class QualityOption(val label: String, val description: String?, val mode: QualityMode, val height: Int?) {
    companion object {
        val heights = listOf(4320, 2160, 1440, 1080, 720, 480, 360)

        /** "2160p (4K)", "1440p (2K)", "720p". */
        fun heightLabel(h: Int) = when {
            h >= 1440 -> "${h}p (${CodecSupport.label(h)})"
            else -> "${h}p"
        }

        /** Các lựa chọn cho máy giải mã được tới [maxHeight] (TV 4K: tới 2160p). */
        fun options(maxHeight: Int): List<QualityOption> {
            val top = maxOf(maxHeight, 1080)
            val best = if (top >= 1440) " (tới ${CodecSupport.label(top)} nếu video có)" else ""
            return listOf(
                QualityOption("Tự động (khuyên dùng)", "Nét nhất mà mạng nhà bạn chạy mượt$best", QualityMode.AUTO, null),
                QualityOption("Cao nhất có thể", "Luôn phát nét nhất$best, cần mạng mạnh", QualityMode.HIGHER, null),
                QualityOption("Tiết kiệm dữ liệu", "Tối đa 360p", QualityMode.DATA_SAVER, null),
            ) + heights.filter { it <= top }.map { QualityOption(heightLabel(it), null, QualityMode.ADVANCED, it) }
        }

        /** Theo khả năng giải mã của máy đang chạy. */
        val all: List<QualityOption> get() = options(PlaybackController.streamPolicy.maxHeight)

        fun current(s: Settings): QualityOption =
            all.firstOrNull { it.mode == s.quality && (it.mode != QualityMode.ADVANCED || it.height == s.fixedHeight) }
                ?: all.first { it.mode == s.quality }
    }
}

/** Ngôn ngữ phụ đề tự bật khi mở video. */
enum class CaptionLanguageOption(val code: String, val label: String) {
    OFF("", "Tắt"),
    VI("vi", "Tiếng Việt (tự dịch nếu video không có)"),
    EN("en", "Tiếng Anh"),
    ;

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: OFF
    }
}

val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** 1f → "Bình thường", 1.25f → "1,25x", 2f → "2x". */
fun speedLabel(speed: Float): String {
    if (speed == 1f) return "Bình thường"
    val number = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').replace('.', ',')
    return "${number}x"
}

/** Hẹn giờ tắt (phút); 0 = khi hết video này; -1 = tắt hẹn giờ. */
val SLEEP_OPTIONS = listOf(-1, 15, 30, 45, 60, 90, 120, 0)

fun sleepLabel(minutes: Int) = when (minutes) {
    -1 -> "Tắt"
    0 -> "Khi hết video này"
    else -> if (minutes % 60 == 0) "${minutes / 60} giờ" else "$minutes phút"
}
