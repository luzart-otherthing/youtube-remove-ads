package com.luzart.tubesach.car

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.luzart.tubesach.data.ThemeMode
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.theme.TubeTheme

/**
 * Tube Sạch trên màn hình ô tô (Android Auto, khi xe đang đỗ).
 *
 * Android Auto mở màn hình này trực tiếp trên màn hình xe (nhờ `CAR_LAUNCHER` trong manifest).
 * Khi xe bắt đầu chạy, Android Auto tự đóng nó vì an toàn; video vẫn tiếp tục phát tiếng
 * và điều khiển được qua mục Media của Android Auto (vô lăng, giọng nói).
 */
class CarActivity : ComponentActivity() {

    private val vm: CarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            vm.loadHome()
            // Đang nghe dở trên xe → mở luôn video đang phát.
            if (PlaybackController.nowPlaying.value?.isShort == false) vm.playerOpen = true
        }
        setContent {
            val settings by vm.store.settings.collectAsState()
            // Màn hình xe luôn dùng giao diện tối (đỡ chói khi lái ban đêm).
            TubeTheme(ThemeMode.DARK, settings.textScale) {
                BackHandler { if (!vm.back()) finish() }
                CarApp(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR, true)
    }

    override fun onStop() {
        super.onStop()
        // Xe chạy (Android Auto đóng màn hình) hoặc rút cáp: chỉ phát tiếng, điện thoại lấy lại khung hình.
        if (!isChangingConfigurations) PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR, false)
    }

    /** Nút trên vô lăng / bàn phím của xe. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> PlaybackController.togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!PlaybackController.isPlaying.value) PlaybackController.togglePlayPause()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlaybackController.pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackController.next()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackController.previous()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlaybackController.seekBy(PlaybackController.SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> PlaybackController.seekBy(-PlaybackController.SEEK_MS)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }
}
