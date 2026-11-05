package com.luzart.tubesach.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.tv.ui.TvRoot
import com.luzart.tubesach.tv.ui.TvTheme

/**
 * Màn hình duy nhất của app TV. Mọi trang (trang chủ, tìm kiếm, trình phát…) vẽ bằng Compose.
 */
@OptIn(UnstableApi::class)
class TvActivity : ComponentActivity() {

    private val vm: TvViewModel by viewModels()

    /** Cho Google Assistant / remote Bluetooth / "Đang phát" của Google TV điều khiển video. */
    private var mediaSession: MediaSession? = null

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) vm.search(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Luôn gọi (kể cả khi TV đã tắt app ngầm rồi mở lại): đã tải rồi thì không tải lại.
        vm.loadHome()
        if (savedInstanceState == null) {
            handleIntent(intent)
            // Kiểm tra bản mới tối đa 1 lần mỗi 12 giờ.
            if (System.currentTimeMillis() - vm.store.lastUpdateCheck > 12 * 60 * 60 * 1000L) vm.checkUpdate()
        }

        setContent {
            TvTheme {
                TvRoot(
                    vm = vm,
                    onVoiceSearch = ::startVoiceSearch,
                    onExit = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val text = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> return
        }
        val url = VideoItem.findYoutubeUrl(text)
        if (url == null) {
            Toast.makeText(this, "Không tìm thấy link video YouTube", Toast.LENGTH_LONG).show()
            return
        }
        vm.openVideo(VideoItem.ofId(VideoItem.videoIdOf(url)!!))
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên video bạn muốn tìm…")
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "TV chưa hỗ trợ tìm bằng giọng nói. Hãy dùng bàn phím.", Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Nút Tìm kiếm trên remote (nếu có) → mở trang Tìm kiếm.
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (vm.playerOpen) vm.closePlayer()
                vm.selectSection(Section.SEARCH)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        // Chỉ nhận phím ⏯ / Google Assistant khi app đang mở; rời app thì nhả ra (không phát ngầm).
        if (mediaSession == null) {
            mediaSession = runCatching {
                MediaSession.Builder(this, PlaybackController.player).setId("tubesach-tv").build()
            }.getOrNull()
        }
    }

    override fun onStop() {
        mediaSession?.release()
        mediaSession = null
        super.onStop()
        // Rời app (bấm Home, tắt TV…) → tạm dừng, giống YouTube trên TV. Vị trí xem được lưu lại.
        if (!isChangingConfigurations) PlaybackController.pause()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        if (isFinishing) PlaybackController.close()
        super.onDestroy()
    }
}
