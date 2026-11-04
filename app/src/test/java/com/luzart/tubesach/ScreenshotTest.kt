package com.luzart.tubesach

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.ResultsState
import com.luzart.tubesach.ui.components.MiniPlayer
import com.luzart.tubesach.ui.screens.HomeScreen
import com.luzart.tubesach.ui.screens.PlayerScreen
import com.luzart.tubesach.ui.screens.ResultsScreen
import com.luzart.tubesach.ui.screens.SearchInputScreen
import com.luzart.tubesach.ui.screens.SettingsScreen
import com.luzart.tubesach.ui.theme.TubeSachTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Chụp ảnh các màn hình chính (chạy trên JVM bằng Robolectric, không cần điện thoại).
 * Chạy: ./gradlew :app:testDebugUnitTest -Dscreenshots=true --tests '*ScreenshotTest*'
 * Ảnh nằm ở app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h850dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("screenshots") == "true")
    }

    private fun sampleVideos(query: String): List<VideoItem> = runCatching {
        runBlocking { YouTubeRepository.search(query).first }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: List(8) {
        VideoItem(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXc$it",
            title = "Video mẫu số ${it + 1} – tiêu đề dài để xem chữ xuống dòng thế nào",
            uploader = "Kênh mẫu",
            thumbnail = null,
            durationSec = 600L + it * 125,
            viewCount = 12_345L * (it + 1),
            uploadedText = "${it + 1} ngày trước",
        )
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            TubeSachTheme(textScale = 1f) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        // Chờ ảnh thu nhỏ tải xong (nếu có mạng).
        repeat(24) {
            Thread.sleep(250)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage(
            "build/outputs/roborazzi/$name.png",
            roborazziOptions = RoborazziOptions(
                recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
            ),
        )
    }

    @Test
    fun home() {
        val vm = AppViewModel(app)
        val videos = sampleVideos("cải lương")
        videos.take(3).forEachIndexed { i, v -> app.store.addToHistory(v, v.durationSec * 1000 * (i + 1) / 4) }
        videos.drop(3).take(2).forEach { app.store.toggleFavorite(it) }
        shot("1_trang_chu") { HomeScreen(vm, {}, {}, PaddingValues()) }
    }

    @Test
    fun searchInput() {
        val vm = AppViewModel(app)
        listOf("cải lương xưa", "thời sự hôm nay", "nhạc bolero").forEach { app.store.addRecentSearch(it) }
        shot("2_tim_kiem") { SearchInputScreen(vm) {} }
    }

    @Test
    fun results() {
        val vm = AppViewModel(app)
        val q = "nhạc trữ tình"
        vm.results[q] = ResultsState(items = sampleVideos(q), loading = false)
        shot("3_ket_qua") { ResultsScreen(vm, q, PaddingValues()) }
    }

    @Test
    fun player() {
        val vm = AppViewModel(app)
        val videos = sampleVideos("thời sự VTV")
        PlaybackController.showForPreview(NowPlaying(videos.first(), null, videos.drop(1)))
        shot("4_xem_video") {
            PlayerScreen(vm, inPip = false, pipSupported = true, {}, {}, {})
        }
        PlaybackController.showForPreview(null)
    }

    @Test
    fun playerWithMiniBar() {
        val vm = AppViewModel(app)
        val videos = sampleVideos("bolero")
        val now = NowPlaying(videos.first(), null, emptyList())
        shot("5_dang_phat_thu_nho") {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f)) { HomeScreen(vm, {}, {}, PaddingValues()) }
                MiniPlayer(now, isPlaying = true, loading = false, onOpen = {}, onPlayPause = {}, onClose = {})
            }
        }
    }

    @Test
    fun settings() {
        val vm = AppViewModel(app)
        shot("6_cai_dat") { SettingsScreen(vm, pipSupported = true, PaddingValues()) }
    }
}
