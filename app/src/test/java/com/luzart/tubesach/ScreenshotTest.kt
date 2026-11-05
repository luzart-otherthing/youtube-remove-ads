package com.luzart.tubesach

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.luzart.tubesach.data.ChannelRef
import com.luzart.tubesach.data.CommentItem
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.ThemeMode
import com.luzart.tubesach.innertube.Parser
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.CommentsState
import com.luzart.tubesach.ui.FeedState
import com.luzart.tubesach.ui.Screen
import com.luzart.tubesach.ui.Tab
import com.luzart.tubesach.ui.TubeApp
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
 * Chụp ảnh các màn hình (chạy trên JVM bằng Robolectric, dữ liệu YouTube thật đã lưu sẵn).
 * Chạy: ./gradlew :app:testDebugUnitTest -Dscreenshots=true --tests '*ScreenshotTest*'
 * Ảnh nằm ở app/build/outputs/roborazzi/.
 */
@OptIn(ExperimentalRoborazziApi::class)
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

    @After
    fun tearDown() {
        PlaybackController.showForPreview(null)
    }

    private fun fixture(name: String): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name.json.gz")
        return JSONObject(GZIPInputStream(stream).bufferedReader().readText())
    }

    private val homePage by lazy { Parser.browseGrid(fixture("home")) }
    private val watch by lazy { Parser.watch(fixture("next"), "fbiZrIlPNx8") }
    private val player by lazy { PlayerData.parse(fixture("player"), "dQw4w9WgXcQ") }

    private fun settle() {
        repeat(16) {
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
    }

    private val opts = RoborazziOptions(recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5))

    private fun shot(name: String, dark: Boolean = false, screen: Boolean = false, content: @Composable () -> Unit) {
        app.store.updateSettings { it.copy(theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        compose.setContent { TubeTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, 1f) { content() } }
        settle()
        if (screen) captureScreenRoboImage("build/outputs/roborazzi/$name.png", opts)
        else compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png", roborazziOptions = opts)
    }

    private fun app(vm: AppViewModel) = @Composable { TubeApp(vm, inPip = false, pipSupported = true, onVoice = {}, onOpenUrl = {}) }

    private fun vmWithHome(): AppViewModel = AppViewModel(app).also {
        it.previewHome(FeedState(entries = homePage.entries, chips = homePage.chips, loaded = true))
    }

    private fun nowPlaying() = NowPlaying(
        video = homePage.entries.filterIsInstance<FeedEntry.Video>().first().video.copy(title = watch.title, uploader = watch.channel!!.name),
        watch = watch,
        related = watch.related,
        captions = player.captions,
        availableHeights = player.availableHeights,
    )

    @Test
    fun home() = shot("01_trang_chu", content = app(vmWithHome()))

    @Test
    fun homeDark() = shot("02_trang_chu_toi", dark = true, content = app(vmWithHome()))

    @Test
    fun homeEmpty() {
        val vm = AppViewModel(app)
        val trending = homePage.entries.filterIsInstance<FeedEntry.Video>().map { it.video }.take(5)
        vm.previewHome(FeedState(loaded = true), trending)
        shot("03_trang_chu_lan_dau", content = app(vm))
    }

    @Test
    fun watchPage() {
        val vm = vmWithHome()
        PlaybackController.showForPreview(nowPlaying())
        PlaybackController.segmentsForPreview(listOf(Segment("sponsor", 30_000, 60_000, "a")))
        vm.previewComments(
            CommentsState(
                "x",
                listOf(CommentItem("@cailuongfan", null, "Vũ Linh – Tài Linh diễn hay quá, xem hoài không chán!", "2 ngày trước", "15", 2, false)),
            ),
        )
        vm.watchExpanded = true
        shot("04_xem_video", content = app(vm))
    }

    @Test
    fun watchPageDark() {
        val vm = vmWithHome()
        PlaybackController.showForPreview(nowPlaying())
        vm.watchExpanded = true
        shot("05_xem_video_toi", dark = true, content = app(vm))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w850dp-h392dp-land-xxhdpi")
    fun fullscreen() {
        val vm = vmWithHome()
        PlaybackController.showForPreview(nowPlaying())
        PlaybackController.segmentsForPreview(listOf(Segment("sponsor", 30_000, 60_000, "a")))
        vm.watchExpanded = true
        vm.fullscreen = true
        shot("06_toan_man_hinh", content = app(vm))
    }

    @Test
    fun miniPlayer() {
        val vm = vmWithHome()
        PlaybackController.showForPreview(nowPlaying())
        vm.watchExpanded = false
        shot("07_trinh_phat_mini", content = app(vm))
    }

    @Test
    fun searchResults() {
        val vm = AppViewModel(app)
        vm.previewHome(FeedState(loaded = true))
        val page = Parser.search(fixture("search"))
        vm.results["cải lương"] = FeedState(entries = page.entries, continuation = page.continuation, loaded = true)
        vm.push(Screen.Results("cải lương"))
        shot("08_ket_qua_tim_kiem", content = app(vm))
    }

    @Test
    fun searchInput() {
        val vm = AppViewModel(app)
        vm.previewHome(FeedState(loaded = true))
        listOf("cải lương vũ linh", "thời sự hôm nay", "nhạc bolero").forEach { app.store.addRecentSearch(it) }
        vm.push(Screen.SearchInput)
        shot("09_tim_kiem", content = app(vm))
    }

    @Test
    fun subscriptions() {
        val vm = AppViewModel(app)
        val vids = homePage.entries.filterIsInstance<FeedEntry.Video>().map { it.video }
        vids.mapNotNull { v -> v.channelId?.let { ChannelRef(it, v.uploader, v.channelAvatar) } }.distinctBy { it.id }.take(6)
            .forEach { app.store.toggleSubscription(it) }
        vm.previewTab(Tab.SUBSCRIPTIONS)
        shot("10_kenh_dang_ky", content = app(vm))
    }

    @Test
    fun you() {
        val vm = AppViewModel(app)
        homePage.entries.filterIsInstance<FeedEntry.Video>().map { it.video }.take(6).forEachIndexed { i, v ->
            app.store.addToHistory(v, v.durationSec * 1000 * (i + 1) / 8)
        }
        vm.previewTab(Tab.YOU)
        shot("11_ban", content = app(vm))
    }

    @Test
    fun settings() {
        val vm = AppViewModel(app)
        vm.previewHome(FeedState(loaded = true))
        vm.push(Screen.Settings)
        shot("12_cai_dat", content = app(vm))
    }

    @Test
    fun relatedFailedShowsRetry() {
        val vm = vmWithHome()
        PlaybackController.showForPreview(nowPlaying().copy(watch = null, related = emptyList(), watchFailed = true))
        vm.watchExpanded = true
        shot("13_de_xuat_loi_thu_lai", content = app(vm))
    }

    @Test
    fun commentsWhenSignedIn() {
        app.account.onLoggedIn(com.luzart.tubesach.account.AccountInfo("Bà Nội", "@banoi", null))
        try {
            val vm = AppViewModel(app)
            vm.previewComments(
                CommentsState(
                    "x",
                    listOf(CommentItem("@cailuongfan", null, "Vũ Linh – Tài Linh diễn hay quá, xem hoài không chán!", "2 ngày trước", "15", 2, false)),
                ),
            )
            shot("14_binh_luan_khi_dang_nhap", screen = true) { com.luzart.tubesach.ui.screens.CommentsSheet(vm) {} }
        } finally {
            app.account.logout()
        }
    }

    @Test
    fun loginWarning() {
        app.account.logout()
        val vm = AppViewModel(app)
        vm.previewHome(FeedState(loaded = true))
        vm.push(Screen.Settings)
        app.store.updateSettings { it.copy(theme = ThemeMode.LIGHT) }
        compose.setContent { TubeTheme(ThemeMode.LIGHT, 1f) { app(vm)() } }
        settle()
        compose.onNodeWithText("Đăng nhập (không khuyến khích)").performScrollTo()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/15_cai_dat_tai_khoan.png", opts)
        compose.onNodeWithText("Đăng nhập (không khuyến khích)").performClick()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/16_canh_bao_dang_nhap.png", opts)
    }

    private fun withVp9Device(broken: Boolean = false, block: () -> Unit) {
        com.luzart.tubesach.player.VideoCodecSupport.override = { _, _, _ -> true } // máy có VP9 phần cứng
        app.store.setVp9Broken(broken)
        try {
            block()
        } finally {
            com.luzart.tubesach.player.VideoCodecSupport.resetForTest()
            app.store.setVp9Broken(false)
        }
    }

    private fun settingsVm() = AppViewModel(app).also {
        it.previewHome(FeedState(loaded = true))
        it.push(Screen.Settings)
    }

    @Test
    fun vp9Setting() = withVp9Device { shot("17_cai_dat_vp9", content = app(settingsVm())) }

    @Test
    fun vp9SettingAfterAutoFallback() = withVp9Device(broken = true) { shot("18_cai_dat_vp9_da_tu_tat", content = app(settingsVm())) }

    @Test
    fun advancedQualityWith4k() = withVp9Device {
        // Menu ⚙ → Chất lượng → Nâng cao: có 4K/2K kèm ghi chú.
        val now = nowPlaying().copy(availableHeights = listOf(2160, 1440, 1080, 720, 480, 360, 240, 144))
        app.store.updateSettings { it.copy(theme = ThemeMode.LIGHT) }
        compose.setContent {
            TubeTheme(ThemeMode.LIGHT, 1f) {
                com.luzart.tubesach.ui.player.PlayerSettingsSheet(now, app.store, 1080, onDismiss = {}, onOpenAppSettings = {})
            }
        }
        settle()
        compose.onNodeWithText("Chất lượng").performClick()
        settle()
        compose.onNodeWithText("Nâng cao").performClick()
        settle()
        captureScreenRoboImage("build/outputs/roborazzi/19_chat_luong_nang_cao_4k.png", opts)
    }
}
