package com.luzart.tubesach

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.luzart.tubesach.car.CarApp
import com.luzart.tubesach.car.CarFeed
import com.luzart.tubesach.car.CarSection
import com.luzart.tubesach.car.CarViewModel
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.ThemeMode
import com.luzart.tubesach.innertube.Parser
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.theme.TubeTheme
import org.json.JSONObject
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.zip.GZIPInputStream

/**
 * Ảnh giao diện trên màn hình ô tô (800×480, cỡ màn hình mặc định của Android Auto).
 * Chạy: ./gradlew :app:testDebugUnitTest -Dscreenshots=true --tests '*CarScreenshotTest*'
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h480dp-land-mdpi")
class CarScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("screenshots") == "true")
    }

    @After
    fun tearDown() {
        PlaybackController.showForPreview(null)
    }

    private fun fixture(name: String): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name.json.gz")
        return JSONObject(GZIPInputStream(stream).bufferedReader().readText())
    }

    private val videos by lazy {
        Parser.browseGrid(fixture("home")).entries.filterIsInstance<FeedEntry.Video>().map { it.video }.filterNot { it.isShort }
    }
    private val watch by lazy { Parser.watch(fixture("next"), "fbiZrIlPNx8") }

    private fun settle() {
        repeat(12) {
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
    }

    private fun shot(name: String, vm: CarViewModel) {
        compose.setContent { TubeTheme(ThemeMode.DARK, 1f) { CarApp(vm) } }
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun vm() = CarViewModel(app).also { it.feeds[CarViewModel.KEY_HOME] = CarFeed(videos = videos, loaded = true) }

    @Test
    fun carHome() = shot("20_o_to_trang_chu", vm())

    @Test
    fun carSearch() {
        val vm = vm()
        vm.select(CarSection.SEARCH)
        shot("21_o_to_tim_kiem", vm)
    }

    @Test
    fun carPlayer() {
        val vm = vm()
        PlaybackController.showForPreview(
            NowPlaying(videos.first().copy(title = watch.title, uploader = watch.channel!!.name), watch = watch, related = watch.related),
        )
        vm.playerOpen = true
        shot("22_o_to_xem_video", vm)
    }
}
