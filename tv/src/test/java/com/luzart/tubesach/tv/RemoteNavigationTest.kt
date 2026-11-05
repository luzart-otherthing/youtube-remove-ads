package com.luzart.tubesach.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.tv.ui.TvRoot
import com.luzart.tubesach.tv.ui.TvTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Điều khiển toàn bộ app bằng các phím của remote TV (◀ ▶ ▲ ▼, OK, Quay lại),
 * trên màn hình TV 1920×1080 giả lập, với dữ liệu YouTube thật đã lưu sẵn.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class RemoteNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: TvViewModel
    private var exited = false

    private val homeVideos get() = Fixtures.home.entries.filterIsInstance<FeedEntry.Video>().map { it.video }

    @Before
    fun setUp() {
        markAsTv()
        resetStore()
        vm = TvViewModel(testApp)
        vm.previewHome(FeedState(entries = Fixtures.home.entries, chips = Fixtures.home.chips, loaded = true))
        exited = false
    }

    @After
    fun tearDown() {
        PlaybackController.showForPreview(null)
        PlaybackController.segmentsForPreview(emptyList())
    }

    private fun launch() {
        compose.setContent {
            TvTheme { TvRoot(vm, onVoiceSearch = {}, onExit = { exited = true }) }
        }
        compose.settle(5)
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.settle()
    }

    private fun previewPlayer(controls: Boolean) {
        val first = homeVideos.first()
        PlaybackController.showForPreview(
            NowPlaying(
                video = first.copy(title = Fixtures.watch.title),
                watch = Fixtures.watch,
                related = Fixtures.watch.related,
                captions = Fixtures.player.captions,
                availableHeights = Fixtures.player.availableHeights,
            ),
        )
        vm.previewPlayer(open = true, controls = controls)
    }

    @Test
    fun startsOnFirstVideo_andSidebarIsOneKeyAway() {
        launch()
        // Mở app: ô sáng tự vào video đầu tiên của trang chủ.
        assertEquals("video:${homeVideos.first().id}", compose.focusedTag())

        // ◀ → thanh bên trái, đúng mục Trang chủ, và thanh bên mở rộng hiện chữ.
        compose.press(KeyEvent.KEYCODE_DPAD_LEFT)
        assertEquals("nav:HOME", compose.focusedTag())
        compose.onNodeWithText("Kênh đăng ký").assertIsDisplayed()

        // ▼ ▼ OK → mở Thư viện.
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN, 2)
        assertEquals("nav:LIBRARY", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Section.LIBRARY, vm.section)

        // ▶ → vào nội dung của Thư viện.
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(compose.focusedTag()!!.startsWith("chip:"))
    }

    @Test
    fun dpadMovesThroughGrid() {
        launch()
        val start = compose.focusedTag()
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        val second = compose.focusedTag()
        assertNotEquals(start, second)
        assertTrue(second!!.startsWith("video:") || second.startsWith("short:"))
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN, 3)
        assertTrue("Ô sáng vẫn nằm trong lưới video: ${compose.focusedTag()}", compose.focusedTag()!!.matches(Regex("(video|short|channel):.*")))
        // ▲ lên tới hàng chip ở đầu trang.
        compose.press(KeyEvent.KEYCODE_DPAD_UP, 6)
        assertTrue(compose.focusedTag()!!.startsWith("chip:"))
    }

    @Test
    fun backButton_contentThenSidebarThenExitConfirmation() {
        launch()
        vm.selectSection(Section.SETTINGS)
        compose.settle()
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertTrue(compose.focusedTag()!!.startsWith("setting:"))

        back() // nội dung → thanh bên
        assertEquals("nav:SETTINGS", compose.focusedTag())
        back() // mục khác → về Trang chủ
        assertEquals(Section.HOME, vm.section)
        assertFalse(exited)
        back() // ở Trang chủ: hiện "Bấm Quay lại lần nữa để thoát"
        compose.onNodeWithText("Bấm Quay lại lần nữa để thoát").assertIsDisplayed()
        assertFalse(exited)
        back()
        assertTrue(exited)
    }

    @Test
    fun okOpensVideo_backReturnsToSameCard() {
        launch()
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        val card = compose.focusedTag()!!
        val id = card.removePrefix("video:")
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(vm.playerOpen)
        assertEquals(id, PlaybackController.nowPlaying.value?.video?.id)
        assertEquals("player", compose.focusedTag())
        // Lần "thả nút OK" không được vô tình tạm dừng video hay bấm nút khác.
        assertFalse(vm.controlsVisible)

        back()
        assertFalse(vm.playerOpen)
        assertNull(PlaybackController.nowPlaying.value)
        assertEquals(card, compose.focusedTag())
    }

    @Test
    fun player_okShowsControls_backHidesThenCloses() {
        launch()
        previewPlayer(controls = false)
        compose.settle()
        assertEquals("player", compose.focusedTag())

        compose.press(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(vm.controlsVisible)
        assertEquals("btn:Phát / Tạm dừng", compose.focusedTag())
        compose.onNodeWithTag("playerTitle").assertIsDisplayed()

        // Chọn qua lại các nút; chữ mô tả nút hiện bên dưới (cho người lớn tuổi dễ hiểu).
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("btn:Tua 10 giây", compose.focusedTag())
        compose.onNodeWithText("Tua tới 10 giây").assertIsDisplayed()

        back()
        assertFalse(vm.controlsVisible)
        assertTrue(vm.playerOpen)
        assertEquals("player", compose.focusedTag())
        back()
        assertFalse(vm.playerOpen)
    }

    @Test
    fun player_hiddenControls_leftRightSeekWithPreview() {
        launch()
        previewPlayer(controls = false)
        compose.settle()
        compose.mainClock.autoAdvance = false
        // Bấm ▶ 3 lần liền: thanh tua hiện ngay, chỉ tua thật khi ngừng bấm.
        repeat(3) {
            compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)))
            compose.onRoot().performKeyPress(androidx.compose.ui.input.key.KeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)))
            compose.mainClock.advanceTimeBy(100)
        }
        compose.onNodeWithTag("time", useUnmergedTree = true).assertIsDisplayed().assert(hasText("0:30", substring = true))
        assertFalse(vm.controlsVisible)
        assertEquals("player", compose.focusedTag())
        // Ngừng bấm → tua tới 0:30 và ẩn thanh tua.
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("time", useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun player_downFromButtonsReachesUpNext_andFocusCannotLeakToBrowse() {
        launch()
        previewPlayer(controls = true)
        compose.settle()
        assertEquals("btn:Phát / Tạm dừng", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        val tag = compose.focusedTag()!!
        assertTrue("▼ từ hàng nút phải tới 'Video tiếp theo', đang ở: $tag", tag.startsWith("video:"))
        assertTrue(Fixtures.watch.related.any { "video:${it.id}" == tag })

        // ▲ nhiều lần: lên thanh tua rồi dừng lại – không lọt ra trang chủ phía sau.
        compose.press(KeyEvent.KEYCODE_DPAD_UP, 6)
        val top = compose.focusedTag()!!
        assertTrue("Ô sáng phải ở trong trình phát, đang ở: $top", top == "seekBar" || top.startsWith("btn:"))
        // ◀ nhiều lần trên hàng nút: không lọt ra thanh bên trái.
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        compose.press(KeyEvent.KEYCODE_DPAD_LEFT, 10)
        assertTrue(compose.focusedTag()!!.startsWith("btn:"))
    }

    @Test
    fun player_captionsPanel_opensFocusedAndBackReturnsToButton() {
        launch()
        previewPlayer(controls = true)
        compose.settle()
        // Đi tới nút Phụ đề: Phát → Tua → Tiếp theo → Phụ đề
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT, 3)
        assertEquals("btn:Phụ đề", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(PlayerPanel.CAPTIONS, vm.panel)
        assertEquals("option:Tắt", compose.focusedTag())
        // Ô sáng bị giữ trong bảng chọn.
        compose.press(KeyEvent.KEYCODE_DPAD_LEFT, 3)
        assertTrue(compose.focusedTag()!!.startsWith("option:"))

        back()
        assertNull(vm.panel)
        assertTrue(vm.controlsVisible)
        assertEquals("btn:Phụ đề", compose.focusedTag())
    }

    @Test
    fun player_sponsorSegmentsShownAndQualityPanelWorks() {
        launch()
        previewPlayer(controls = true)
        PlaybackController.segmentsForPreview(listOf(Segment("sponsor", 0, 30_000, "x")))
        compose.settle()
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT, 4)
        assertEquals("btn:Chất lượng", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("option:Tự động (khuyên dùng)", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN, 4) // Tự động, Cao hơn, Tiết kiệm, 1080p → 720p
        assertEquals("option:720p", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertNull(vm.panel)
        assertEquals(720, testApp.store.settings.value.fixedHeight)
        assertEquals(com.luzart.tubesach.data.QualityMode.ADVANCED, testApp.store.settings.value.quality)
    }

    @Test
    fun longPressOk_opensMenu_saveToWatchLater() {
        launch()
        val id = compose.focusedTag()!!.removePrefix("video:")
        compose.longPressCenter()
        assertEquals("option:Phát video", compose.focusedTag())
        assertFalse("Giữ OK không được mở video", vm.playerOpen)
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("option:Lưu vào Xem sau", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(testApp.store.favorites.value.any { it.id == id })
        // Menu đóng, ô sáng quay lại đúng video.
        assertEquals("video:$id", compose.focusedTag())
    }

    @Test
    fun searchSection_focusesSearchBox_recentSearchesClickable() {
        listOf("cải lương", "thời sự").forEach { testApp.store.addRecentSearch(it) }
        launch()
        compose.press(KeyEvent.KEYCODE_DPAD_LEFT)
        compose.press(KeyEvent.KEYCODE_DPAD_UP) // Trang chủ → Tìm kiếm
        assertEquals("nav:SEARCH", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(Section.SEARCH, vm.section)
        assertEquals("searchField", compose.focusedTag())
        // ▼ rời ô nhập chữ (không bị kẹt trong ô) → tới các tìm kiếm gần đây.
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        val tag = compose.focusedTag()
        assertNotEquals("searchField", tag)
        compose.onNodeWithText("Tìm kiếm gần đây").assertIsDisplayed()
    }

    @Test
    fun searchResults_gridReachableFromSearchBox() {
        launch()
        vm.previewResults("cải lương", FeedState(entries = Fixtures.search.entries, continuation = null, loaded = true))
        compose.settle()
        // Chuyển sang trang Tìm kiếm: ô sáng vào ô nhập chữ.
        assertEquals("searchField", compose.focusedTag())
        // ▼ từ ô tìm kiếm: tới đúng kết quả đầu tiên (không nhảy vào giữa lưới).
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        val first = Fixtures.search.entries.first { it is FeedEntry.Video || it is FeedEntry.Channel }
        val expected = when (first) {
            is FeedEntry.Video -> "video:${first.video.id}"
            is FeedEntry.Channel -> "channel:${first.channel.id}"
            else -> error("")
        }
        assertEquals(expected, compose.focusedTag())
    }

    @Test
    fun settings_toggleWithOk() {
        launch()
        vm.selectSection(Section.SETTINGS)
        compose.settle()
        compose.press(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals("setting:Chất lượng video", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("setting:Độ nét tối đa TV phát được", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("setting:HDR (màu sắc và độ sáng rực rỡ hơn)", compose.focusedTag())
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER) // TV không hỗ trợ HDR: bấm không đổi gì
        assertTrue(testApp.store.settings.value.hdr)
        compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals("setting:Tự động phát video tiếp theo", compose.focusedTag())
        assertTrue(testApp.store.settings.value.autoplay)
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertFalse(testApp.store.settings.value.autoplay)

        // Bảng chọn trong Cài đặt: Quay lại đóng bảng, không thoát trang.
        compose.press(KeyEvent.KEYCODE_DPAD_UP, 3)
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.onNodeWithTag("panel:Chất lượng video").assertIsDisplayed()
        back()
        compose.onNodeWithTag("panel:Chất lượng video").assertDoesNotExist()
        assertEquals(Section.SETTINGS, vm.section)
        assertEquals("setting:Chất lượng video", compose.focusedTag())
    }

    @Test
    fun channelPage_subscribeAndBack() {
        launch()
        val v: VideoItem = homeVideos.first { it.channelId != null }
        // Trang kênh dùng dữ liệu đã có (không gọi mạng trong test này).
        vm.channels[v.channelId!!] = ChannelState(
            page = com.luzart.tubesach.data.ChannelPage(
                com.luzart.tubesach.data.ChannelRef(v.channelId!!, v.uploader, v.channelAvatar),
                "@kenh", "1,2 Tr người đăng ký", null, null, null, null, null,
            ),
            loading = false,
            videos = FeedState(entries = homeVideos.take(6).map { FeedEntry.Video(it) }, loaded = true),
        )
        vm.channelStack.add(v.channelId!!)
        compose.settle()
        // Mở trang kênh: ô sáng tự vào trang kênh, ở nút Đăng ký.
        compose.focused().assert(hasAnyDescendant(hasText("Đăng ký")))
        compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(testApp.store.isSubscribed(v.channelId!!))
        compose.focused().assert(hasAnyDescendant(hasText("Đã đăng ký")))
        back()
        assertTrue(vm.channelStack.isEmpty())
        assertEquals(Section.HOME, vm.section)
        // Quay lại trang chủ: ô sáng nằm trong lưới video, không bị mất.
        assertTrue(compose.focusedTag()!!.matches(Regex("(video|short|channel|chip):.*")))
    }
}
