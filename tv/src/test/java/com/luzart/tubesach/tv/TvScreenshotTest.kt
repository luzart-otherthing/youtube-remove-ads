package com.luzart.tubesach.tv

import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.luzart.tubesach.data.ChannelPage
import com.luzart.tubesach.data.ChannelRef
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.tv.ui.TvRoot
import com.luzart.tubesach.tv.ui.TvTheme
import com.luzart.tubesach.update.UpdateInfo
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowHardware
import coil3.request.crossfade
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

/**
 * Chụp ảnh màn hình app TV (1920×1080) với dữ liệu YouTube thật đã lưu sẵn.
 * ./gradlew :tv:testDebugUnitTest -Dscreenshots=true --tests '*TvScreenshotTest*'
 * Thêm -Donline=true để tải cả ảnh thu nhỏ thật. Ảnh nằm ở tv/build/outputs/roborazzi/.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: TvViewModel
    private val videos get() = Fixtures.home.entries.filterIsInstance<FeedEntry.Video>().map { it.video }
    private val online get() = System.getProperty("online") == "true"

    @Before
    fun setUp() {
        assumeTrue(System.getProperty("screenshots") == "true")
        markAsTv()
        resetStore()
        vm = TvViewModel(testApp)
        vm.previewHome(FeedState(entries = Fixtures.home.entries, chips = Fixtures.home.chips, loaded = true))
        if (online) {
            // Robolectric không có "hardware bitmap" → tải ảnh thu nhỏ thật bằng bitmap thường.
            SingletonImageLoader.setUnsafe(
                ImageLoader.Builder(testApp)
                    .components {
                        add(OkHttpNetworkFetcherFactory(callFactory = { testApp.http }))
                        add(BitmapFactoryDecoder.Factory()) // ImageDecoder không chạy được trong Robolectric
                    }
                    .allowHardware(false)
                    .crossfade(false)
                    .build(),
            )
        }
    }

    @After
    fun tearDown() {
        PlaybackController.showForPreview(null)
        PlaybackController.segmentsForPreview(emptyList())
    }

    private val opts = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5))

    private fun launch() {
        compose.setContent { TvTheme { TvRoot(vm, onVoiceSearch = {}, onExit = {}) } }
        compose.settle(5)
    }

    private fun shot(name: String) {
        // Chờ ảnh thu nhỏ tải về (chỉ khi có mạng).
        val rounds = if (online) 30 else 4
        repeat(rounds) {
            Thread.sleep(if (online) 200 else 50)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png", roborazziOptions = opts)
    }

    private fun previewPlayer(controls: Boolean) {
        val first = videos.first()
        PlaybackController.showForPreview(
            NowPlaying(
                video = first.copy(title = Fixtures.watch.title, uploader = Fixtures.watch.channel!!.name, viewText = Fixtures.watch.shortViewText),
                watch = Fixtures.watch,
                related = Fixtures.watch.related,
                captions = Fixtures.player.captions,
                availableHeights = Fixtures.player.availableHeights,
            ),
        )
        PlaybackController.segmentsForPreview(listOf(Segment("sponsor", 30_000, 75_000, "a")))
        vm.previewPlayer(open = true, controls = controls)
    }

    @Test
    fun s01_home() {
        launch()
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        shot("tv_01_trang_chu")
    }

    @Test
    fun s02_sidebar() {
        launch()
        compose.press(KeyEvent.KEYCODE_DPAD_LEFT)
        shot("tv_02_thanh_ben_trai")
    }

    @Test
    fun s03_player() {
        launch()
        previewPlayer(controls = true)
        compose.settle()
        shot("tv_03_trinh_phat")
    }

    @Test
    fun s04_upNext() {
        launch()
        previewPlayer(controls = true)
        compose.settle()
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        shot("tv_04_video_tiep_theo")
    }

    @Test
    fun s05_quality() {
        launch()
        previewPlayer(controls = true)
        compose.settle()
        vm.panel = PlayerPanel.QUALITY
        compose.settle()
        shot("tv_05_chat_luong")
    }

    @Test
    fun s06_search() {
        listOf("cải lương vũ linh", "thời sự hôm nay", "nhạc bolero").forEach { testApp.store.addRecentSearch(it) }
        launch()
        vm.previewResults("cải lương", FeedState(entries = Fixtures.search.entries, loaded = true))
        compose.settle()
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        shot("tv_06_tim_kiem")
    }

    @Test
    fun s07_subscriptions() {
        videos.mapNotNull { v -> v.channelId?.let { ChannelRef(it, v.uploader, v.channelAvatar) } }.distinctBy { it.id }.take(7)
            .forEach { testApp.store.toggleSubscription(it) }
        vm.previewSubscriptions(FeedState(entries = videos.drop(3).take(8).map { FeedEntry.Video(it) }, loaded = true))
        launch()
        vm.previewSection(Section.SUBSCRIPTIONS)
        compose.settle()
        shot("tv_07_kenh_dang_ky")
    }

    @Test
    fun s08_library() {
        videos.take(8).forEachIndexed { i, v -> testApp.store.addToHistory(v, v.durationSec * 1000 * (i + 1) / 10) }
        launch()
        vm.selectSection(Section.LIBRARY)
        compose.settle()
        shot("tv_08_thu_vien")
    }

    @Test
    fun s09_settings() {
        launch()
        vm.selectSection(Section.SETTINGS)
        compose.settle()
        shot("tv_09_cai_dat")
    }

    @Test
    fun s10_channel() {
        launch()
        val v = videos.first { it.channelId != null }
        vm.channels[v.channelId!!] = ChannelState(
            page = ChannelPage(ChannelRef(v.channelId!!, v.uploader, v.channelAvatar), "@" + v.uploader.replace(" ", ""), "1,2 Tr người đăng ký", "850 video", null, null, null, "x"),
            loading = false,
            tab = 0,
            videos = FeedState(entries = videos.take(8).map { FeedEntry.Video(it) }, loaded = true),
        )
        vm.channelStack.add(v.channelId!!)
        compose.settle()
        shot("tv_10_trang_kenh")
    }

    @Test
    fun s11_videoMenu() {
        launch()
        compose.longPressCenter()
        shot("tv_11_menu_video")
    }

    @Test
    fun s12_firstLaunch() {
        vm.previewHome(FeedState(loaded = true), trending = videos.take(12))
        vm.previewUpdate(UpdateInfo("2.0.99", "https://example.com/x.apk"))
        launch()
        shot("tv_12_lan_dau_mo_app")
    }
}
