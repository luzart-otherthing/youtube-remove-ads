package com.luzart.tubesach.tv.player

import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.Settings

/** Một lựa chọn trong bảng "Chất lượng" (dùng chung cho trình phát và trang Cài đặt). */
data class QualityOption(val label: String, val description: String?, val mode: QualityMode, val height: Int?) {
    companion object {
        /** Luồng H.264 tối đa 1080p – TV nào cũng giải mã được mượt bằng phần cứng. */
        val heights = listOf(1080, 720, 480, 360)

        val all: List<QualityOption> = listOf(
            QualityOption("Tự động (khuyên dùng)", "Tự chọn theo tốc độ mạng", QualityMode.AUTO, null),
            QualityOption("Chất lượng cao hơn", "Nét nhất có thể, tốn mạng hơn", QualityMode.HIGHER, null),
            QualityOption("Tiết kiệm dữ liệu", "Tối đa 360p", QualityMode.DATA_SAVER, null),
        ) + heights.map { QualityOption("${it}p", null, QualityMode.ADVANCED, it) }

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
