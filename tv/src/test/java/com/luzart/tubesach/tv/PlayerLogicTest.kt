package com.luzart.tubesach.tv

import android.view.KeyEvent
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.Settings
import com.luzart.tubesach.tv.player.PlayerKeyAction
import com.luzart.tubesach.tv.player.QualityOption
import com.luzart.tubesach.tv.player.SeekAccumulator
import com.luzart.tubesach.tv.player.isMediaKey
import com.luzart.tubesach.tv.player.mapPlayerKey
import com.luzart.tubesach.tv.player.sleepLabel
import com.luzart.tubesach.tv.player.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logic điều khiển bằng remote của trình phát (không cần giao diện). */
class PlayerLogicTest {

    @Test
    fun hiddenControls_dpadControlsPlayback() {
        assertEquals(PlayerKeyAction.TOGGLE_PLAY, mapPlayerKey(KeyEvent.KEYCODE_DPAD_CENTER, controlsVisible = false))
        assertEquals(PlayerKeyAction.TOGGLE_PLAY, mapPlayerKey(KeyEvent.KEYCODE_ENTER, controlsVisible = false))
        assertEquals(PlayerKeyAction.SEEK_BACK, mapPlayerKey(KeyEvent.KEYCODE_DPAD_LEFT, controlsVisible = false))
        assertEquals(PlayerKeyAction.SEEK_FORWARD, mapPlayerKey(KeyEvent.KEYCODE_DPAD_RIGHT, controlsVisible = false))
        assertEquals(PlayerKeyAction.SHOW_CONTROLS, mapPlayerKey(KeyEvent.KEYCODE_DPAD_UP, controlsVisible = false))
        assertEquals(PlayerKeyAction.SHOW_CONTROLS, mapPlayerKey(KeyEvent.KEYCODE_DPAD_DOWN, controlsVisible = false))
        // Nút Quay lại do BackHandler xử lý, không phải ở đây.
        assertEquals(PlayerKeyAction.NONE, mapPlayerKey(KeyEvent.KEYCODE_BACK, controlsVisible = false))
    }

    @Test
    fun visibleControls_dpadMovesFocusInstead() {
        for (code in listOf(
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BACK,
        )) {
            assertEquals("key $code", PlayerKeyAction.NONE, mapPlayerKey(code, controlsVisible = true))
        }
    }

    @Test
    fun mediaKeysAlwaysWork() {
        for (visible in listOf(true, false)) {
            assertEquals(PlayerKeyAction.TOGGLE_PLAY, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, visible))
            assertEquals(PlayerKeyAction.PLAY, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_PLAY, visible))
            assertEquals(PlayerKeyAction.PAUSE, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_PAUSE, visible))
            assertEquals(PlayerKeyAction.SEEK_FORWARD, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, visible))
            assertEquals(PlayerKeyAction.SEEK_BACK, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_REWIND, visible))
            assertEquals(PlayerKeyAction.NEXT, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_NEXT, visible))
            assertEquals(PlayerKeyAction.PREVIOUS, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, visible))
            assertEquals(PlayerKeyAction.CLOSE, mapPlayerKey(KeyEvent.KEYCODE_MEDIA_STOP, visible))
        }
        assertTrue(isMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertFalse(isMediaKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(isMediaKey(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun seekAccumulator_groupsPressesAndClamps() {
        val s = SeekAccumulator()
        // Bấm ▶ 3 lần liền: 1:00 → 1:30, chỉ tua một lần khi ngừng bấm.
        assertEquals(70_000L, s.press(forward = true, currentPositionMs = 60_000, durationMs = 600_000, repeatCount = 0))
        assertEquals(80_000L, s.press(true, 60_000, 600_000, 0))
        assertEquals(90_000L, s.press(true, 60_000, 600_000, 0))
        assertEquals(90_000L, s.commit())
        assertNull(s.commit())
        // Không lùi quá đầu video.
        assertEquals(0L, s.press(false, 4_000, 600_000, 0))
        s.cancel()
        assertNull(s.target)
        // Không tua quá cuối video (dừng trước 1 giây để không nhảy sang video khác).
        assertEquals(599_000L, s.press(true, 595_000, 600_000, 0))
        s.commit()
        // Video chưa biết độ dài (đang tải): vẫn tua tới được.
        assertEquals(20_000L, s.press(true, 10_000, 0, 0))
    }

    @Test
    fun seekAccumulator_holdingKeySpeedsUp() {
        assertEquals(10_000L, SeekAccumulator.stepFor(0))
        assertEquals(10_000L, SeekAccumulator.stepFor(7))
        assertEquals(30_000L, SeekAccumulator.stepFor(8))
        assertEquals(60_000L, SeekAccumulator.stepFor(25))
    }

    @Test
    fun labels() {
        assertEquals("Bình thường", speedLabel(1f))
        assertEquals("1,25x", speedLabel(1.25f))
        assertEquals("0,75x", speedLabel(0.75f))
        assertEquals("2x", speedLabel(2f))
        assertEquals("Tắt", sleepLabel(-1))
        assertEquals("Khi hết video này", sleepLabel(0))
        assertEquals("30 phút", sleepLabel(30))
        assertEquals("1 giờ", sleepLabel(60))
        assertEquals("90 phút", sleepLabel(90))
    }

    @Test
    fun qualityOptionMatchesSettings() {
        assertEquals(QualityMode.AUTO, QualityOption.current(Settings()).mode)
        val fixed = QualityOption.current(Settings(quality = QualityMode.ADVANCED, fixedHeight = 480))
        assertEquals("480p", fixed.label)
        // Độ phân giải lạ (ví dụ cài từ app điện thoại cũ) vẫn không làm app lỗi.
        assertEquals(QualityMode.ADVANCED, QualityOption.current(Settings(quality = QualityMode.ADVANCED, fixedHeight = 144)).mode)
        assertEquals(7, QualityOption.all.size)
    }
}
