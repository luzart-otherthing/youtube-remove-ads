package com.luzart.tubesach.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

/** Phím "chọn" trên remote: nút OK ở giữa, Enter (bàn phím / chuột bay), nút A (tay cầm). */
fun isSelectKey(keyCode: Int) = when (keyCode) {
    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
    AndroidKeyEvent.KEYCODE_BUTTON_A,
    -> true
    else -> false
}

/**
 * Ô bấm được bằng remote.
 *
 * - Bấm **OK** (thả tay) để mở; **giữ OK** để mở menu ([onLongClick]).
 * - Chỉ tính là bấm khi ô này nhận được cả lúc nhấn lẫn lúc thả. Nhờ vậy khi một nút OK vừa
 *   mở trình phát, lần "thả tay" không vô tình bấm luôn vào nút mới được chọn bên trong.
 * - Vẫn bấm được bằng chuột / cảm ứng (một số TV có chuột bay).
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    this
        .onKeyEvent { e ->
            val native = e.nativeKeyEvent
            if (!enabled || !isSelectKey(native.keyCode)) return@onKeyEvent false
            when (e.type) {
                KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        pressed = true
                        longFired = false
                    } else if (pressed && !longFired && onLongClick != null && (native.isLongPress || native.repeatCount >= 3)) {
                        longFired = true
                        onLongClick()
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val wasPressed = pressed
                    pressed = false
                    if (wasPressed && !longFired) onClick()
                    wasPressed
                }
                else -> false
            }
        }
        .pointerInput(enabled, onLongClick != null) {
            detectTapGestures(
                onTap = { if (enabled) click() },
                onLongPress = if (onLongClick != null) ({ if (enabled) longClick?.invoke() }) else null,
            )
        }
        .focusable(enabled)
}
