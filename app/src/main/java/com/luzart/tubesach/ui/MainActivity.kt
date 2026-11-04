package com.luzart.tubesach.ui

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.luzart.tubesach.R
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.components.MiniPlayer
import com.luzart.tubesach.ui.screens.FavoritesScreen
import com.luzart.tubesach.ui.screens.HistoryScreen
import com.luzart.tubesach.ui.screens.HomeScreen
import com.luzart.tubesach.ui.screens.PlayerScreen
import com.luzart.tubesach.ui.screens.ResultsScreen
import com.luzart.tubesach.ui.screens.SearchInputScreen
import com.luzart.tubesach.ui.screens.SettingsScreen
import com.luzart.tubesach.ui.theme.TubeSachTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private var inPip by mutableStateOf(false)

    private val pipSupported: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        inPip = info.isInPictureInPictureMode
        if (!inPip && lifecycle.currentState == Lifecycle.State.CREATED) {
            // Người dùng bấm nút X để đóng cửa sổ nhỏ → dừng video.
            PlaybackController.pause()
        }
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                CONTROL_PLAY_PAUSE -> PlaybackController.togglePlayPause()
                CONTROL_REWIND -> PlaybackController.seekBy(-PlaybackController.SEEK_MS)
                CONTROL_FORWARD -> PlaybackController.seekBy(PlaybackController.SEEK_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT

        addOnPictureInPictureModeChangedListener(pipListener)
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        if (savedInstanceState == null) {
            handleIntent(intent)
            vm.checkUpdate()
        }

        setContent {
            val settings by vm.store.settings.collectAsState()
            TubeSachTheme(textScale = settings.textScale) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }

        // Cập nhật nút trên cửa sổ nhỏ & chế độ tự thu nhỏ theo trạng thái phát.
        if (pipSupported) {
            lifecycleScope.launch {
                combine(
                    snapshotFlow { vm.top },
                    PlaybackController.isPlaying,
                    vm.store.settings,
                ) { _, _, _ -> Unit }.collect { updatePipParams() }
            }
        }
    }

    @Composable
    private fun AppRoot() {
        val now by PlaybackController.nowPlaying.collectAsState()
        val isPlaying by PlaybackController.isPlaying.collectAsState()
        val loading by PlaybackController.loading.collectAsState()

        val voiceLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) vm.search(text)
        }
        val startVoice: () -> Unit = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Hãy nói tên video bạn muốn xem")
            try {
                voiceLauncher.launch(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "Máy này chưa hỗ trợ tìm bằng giọng nói", Toast.LENGTH_LONG).show()
            }
        }

        BackHandler(enabled = vm.fullscreen || vm.stack.size > 1) {
            if (vm.fullscreen) exitFullscreen() else vm.back()
        }

        val screen = vm.top
        if (inPip) {
            // Cửa sổ nhỏ: chỉ hiện video.
            PlayerScreen(vm, inPip = true, pipSupported, {}, {}, {})
            return
        }

        val showMini = now != null && screen != Screen.Player
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .then(if (!showMini && screen != Screen.Player) Modifier.navigationBarsPadding() else Modifier),
            ) {
                val pad = PaddingValues(bottom = if (showMini) 8.dp else 24.dp)
                when (screen) {
                    Screen.Home -> HomeScreen(vm, startVoice, ::openUrl, pad)
                    Screen.SearchInput -> SearchInputScreen(vm, startVoice)
                    is Screen.Results -> ResultsScreen(vm, screen.query, pad)
                    Screen.History -> HistoryScreen(vm, pad)
                    Screen.Favorites -> FavoritesScreen(vm, pad)
                    Screen.Settings -> SettingsScreen(vm, pipSupported, pad)
                    Screen.Player -> PlayerScreen(
                        vm,
                        inPip = false,
                        pipSupported = pipSupported,
                        onEnterPip = ::enterPip,
                        onEnterFullscreen = ::enterFullscreen,
                        onExitFullscreen = ::exitFullscreen,
                    )
                }
            }
            now?.let { current ->
                if (showMini) {
                    MiniPlayer(
                        now = current,
                        isPlaying = isPlaying,
                        loading = loading,
                        onOpen = { vm.openPlayerScreen() },
                        onPlayPause = { PlaybackController.togglePlayPause() },
                        onClose = { PlaybackController.close() },
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- Mở link

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_OPEN_PLAYER -> vm.openPlayerScreen()
            Intent.ACTION_SEND -> openShared(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> openShared(intent.dataString)
        }
    }

    private fun openShared(text: String?) {
        val url = VideoItem.findYoutubeUrl(text)
        if (url == null) {
            if (text != null) Toast.makeText(this, "Không tìm thấy link video YouTube", Toast.LENGTH_LONG).show()
            return
        }
        exitFullscreen()
        vm.openVideo(VideoItem(url = url, title = "Đang mở video…", uploader = "", thumbnail = null, durationSec = 0))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Không mở được trình duyệt", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- Toàn màn hình

    private fun enterFullscreen() {
        vm.fullscreen = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun exitFullscreen() {
        if (!vm.fullscreen) return
        vm.fullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    // ---------------------------------------------------------------- Hình trong hình

    private fun shouldAutoPip(): Boolean =
        pipSupported && vm.top == Screen.Player && PlaybackController.isPlaying.value &&
            vm.store.settings.value.autoPip

    private fun enterPip() {
        if (!pipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (PlaybackController.nowPlaying.value == null) return
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (_: Exception) {
            Toast.makeText(this, "Máy chưa cho phép cửa sổ nổi. Hãy bật trong Cài đặt của điện thoại.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 8–11: tự vào chế độ cửa sổ nhỏ khi bấm Home lúc đang xem.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldAutoPip()) enterPip()
    }

    private fun updatePipParams() {
        if (!pipSupported || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(): PictureInPictureParams {
        val size = PlaybackController.player.videoSize
        val ratio = if (size.width > 0 && size.height > 0) {
            val r = size.width.toFloat() / size.height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> Rational(size.width, size.height)
            }
        } else {
            Rational(16, 9)
        }
        val playing = PlaybackController.isPlaying.value
        val actions = listOf(
            pipAction(R.drawable.ic_pip_rewind, "Lùi 10 giây", CONTROL_REWIND),
            if (playing) pipAction(R.drawable.ic_pip_pause, "Tạm dừng", CONTROL_PLAY_PAUSE)
            else pipAction(R.drawable.ic_pip_play, "Phát", CONTROL_PLAY_PAUSE),
            pipAction(R.drawable.ic_pip_forward, "Tới 10 giây", CONTROL_FORWARD),
        )
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(actions)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldAutoPip())
            builder.setSeamlessResizeEnabled(false)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pipAction(icon: Int, title: String, control: Int): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL)
            .setPackage(packageName)
            .putExtra(EXTRA_CONTROL, control)
        val pending = PendingIntent.getBroadcast(
            this,
            control,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return RemoteAction(Icon.createWithResource(this, icon), title, title, pending)
    }

    // ---------------------------------------------------------------- Vòng đời

    override fun onStart() {
        super.onStart()
        PlaybackController.setVideoEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        // Chạy nền hoặc tắt màn hình: chỉ tải tiếng cho đỡ tốn mạng và pin.
        if (!isChangingConfigurations) PlaybackController.setVideoEnabled(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipParams()
    }

    override fun onDestroy() {
        removeOnPictureInPictureModeChangedListener(pipListener)
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.luzart.tubesach.OPEN_PLAYER"
        private const val ACTION_PIP_CONTROL = "com.luzart.tubesach.PIP_CONTROL"
        private const val EXTRA_CONTROL = "control"
        private const val CONTROL_PLAY_PAUSE = 1
        private const val CONTROL_REWIND = 2
        private const val CONTROL_FORWARD = 3
    }
}
