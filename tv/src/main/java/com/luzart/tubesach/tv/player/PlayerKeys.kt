package com.luzart.tubesach.tv.player

import android.view.KeyEvent

/** Việc cần làm khi bấm một phím trên remote lúc đang xem video. */
enum class PlayerKeyAction {
    /** Không xử lý: để Compose tự chuyển ô sáng (focus) giữa các nút. */
    NONE,
    TOGGLE_PLAY,
    PLAY,
    PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    NEXT,
    PREVIOUS,
    SHOW_CONTROLS,
    CLOSE,
}

/**
 * Ánh xạ phím remote → hành động, giống YouTube trên TV:
 *
 * Khi **ẩn** thanh điều khiển:
 * - OK: tạm dừng / phát tiếp (và hiện thanh điều khiển);
 * - ◀ ▶: lùi / tua 10 giây (giữ phím để tua nhanh hơn);
 * - ▲ ▼: hiện thanh điều khiển.
 *
 * Khi **hiện** thanh điều khiển: ◀ ▶ ▲ ▼ OK để chọn nút (không xử lý ở đây).
 *
 * Các phím đa phương tiện (⏯ ⏪ ⏩ ⏭ ⏮ trên remote, tai nghe Bluetooth…) luôn hoạt động.
 * Nút Quay lại xử lý riêng (xem [TvViewModel.onBack]).
 */
fun mapPlayerKey(keyCode: Int, controlsVisible: Boolean): PlayerKeyAction {
    when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> return PlayerKeyAction.TOGGLE_PLAY
        KeyEvent.KEYCODE_MEDIA_PLAY -> return PlayerKeyAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> return PlayerKeyAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_REWIND -> return PlayerKeyAction.SEEK_BACK
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> return PlayerKeyAction.SEEK_FORWARD
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> return PlayerKeyAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> return PlayerKeyAction.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_STOP -> return PlayerKeyAction.CLOSE
        KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE ->
            return if (controlsVisible) PlayerKeyAction.NONE else PlayerKeyAction.SHOW_CONTROLS
    }
    if (controlsVisible) return PlayerKeyAction.NONE
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A,
        -> PlayerKeyAction.TOGGLE_PLAY
        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerKeyAction.SEEK_BACK
        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerKeyAction.SEEK_FORWARD
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKeyAction.SHOW_CONTROLS
        else -> PlayerKeyAction.NONE
    }
}

/** Phím đa phương tiện: xử lý ở mọi nơi khi đang có video (kể cả lúc đang mở bảng chọn). */
fun isMediaKey(keyCode: Int) = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_STOP,
    -> true
    else -> false
}

/**
 * Gom các lần bấm ◀ ▶ liên tiếp thành **một** lần tua (như YouTube trên TV):
 * thanh tua chạy theo ngay, nhưng chỉ tải lại video sau khi người dùng ngừng bấm.
 * Giữ phím càng lâu thì mỗi bước tua càng dài (10 giây → 30 giây → 1 phút).
 */
class SeekAccumulator {

    /** Vị trí sẽ tua tới (ms); null = không đang tua. */
    var target: Long? = null
        private set

    fun press(forward: Boolean, currentPositionMs: Long, durationMs: Long, repeatCount: Int): Long {
        val base = target ?: currentPositionMs
        val step = stepFor(repeatCount)
        val max = if (durationMs > 0) (durationMs - 1_000).coerceAtLeast(0) else Long.MAX_VALUE
        val t = (base + if (forward) step else -step).coerceIn(0, max)
        target = t
        return t
    }

    /** Trả về vị trí cần tua tới và kết thúc lượt tua. */
    fun commit(): Long? = target.also { target = null }

    fun cancel() {
        target = null
    }

    companion object {
        const val COMMIT_DELAY_MS = 700L

        fun stepFor(repeatCount: Int): Long = when {
            repeatCount >= 25 -> 60_000L
            repeatCount >= 8 -> 30_000L
            else -> 10_000L
        }
    }
}
