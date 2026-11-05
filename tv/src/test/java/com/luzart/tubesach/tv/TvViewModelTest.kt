package com.luzart.tubesach.tv

import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Thứ tự xử lý nút Quay lại và trạng thái điều hướng. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TvViewModelTest {

    private lateinit var vm: TvViewModel

    @Before
    fun setUp() {
        resetStore()
        vm = TvViewModel(testApp)
    }

    @After
    fun tearDown() = PlaybackController.showForPreview(null)

    @Test
    fun backOrder_panelThenControlsThenPlayerThenChannel() {
        vm.channelStack.add("UC1")
        PlaybackController.showForPreview(NowPlaying(VideoItem.ofId("dQw4w9WgXcQ")))
        vm.previewPlayer(open = true, controls = true, panel = PlayerPanel.QUALITY)

        assertEquals(BackResult.HANDLED, vm.onBack())
        assertNull(vm.panel)
        assertTrue(vm.controlsVisible)

        assertEquals(BackResult.HANDLED, vm.onBack())
        assertFalse(vm.controlsVisible)
        assertTrue(vm.playerOpen)

        assertEquals(BackResult.HANDLED, vm.onBack())
        assertFalse(vm.playerOpen)
        assertNull(PlaybackController.nowPlaying.value)

        assertEquals(BackResult.HANDLED, vm.onBack())
        assertTrue(vm.channelStack.isEmpty())

        assertEquals(BackResult.NOT_HANDLED, vm.onBack())
    }

    @Test
    fun openChannelFromPlayerClosesPlayer() {
        PlaybackController.showForPreview(NowPlaying(VideoItem.ofId("dQw4w9WgXcQ")))
        vm.previewPlayer(open = true, controls = true, panel = PlayerPanel.INFO)
        vm.channels["UC1"] = ChannelState(loading = false, error = "x") // không gọi mạng
        vm.channelStack.add("UC1")
        vm.openChannel("UC1")
        assertFalse(vm.playerOpen)
        assertNull(vm.panel)
        assertEquals(listOf("UC1"), vm.channelStack.toList()) // không mở trùng
    }

    @Test
    fun selectSectionClearsChannelPages() {
        vm.channelStack.add("UC1")
        vm.selectSection(Section.SETTINGS)
        assertTrue(vm.channelStack.isEmpty())
        assertEquals(Section.SETTINGS, vm.section)
    }

    @Test
    fun hideShortsAndNotInterestedFilterFeeds() {
        val normal = VideoItem.ofId("aaaaaaaaaaa")
        val short = VideoItem.ofId("bbbbbbbbbbb").copy(isShort = true)
        val hidden = VideoItem.ofId("ccccccccccc")
        val entries = listOf(normal, short, hidden).map { com.luzart.tubesach.data.FeedEntry.Video(it) }
        testApp.store.hide(hidden.id)
        assertEquals(2, vm.visible(entries).size)
        testApp.store.updateSettings { it.copy(hideShorts = true) }
        assertEquals(listOf(normal.id), vm.visible(entries).map { (it as com.luzart.tubesach.data.FeedEntry.Video).video.id })
    }

    @Test
    fun searchRemembersQueryAndSwitchesToSearch() {
        vm.selectSection(Section.LIBRARY)
        vm.search("  cải lương  ")
        assertEquals(Section.SEARCH, vm.section)
        assertEquals("cải lương", vm.resultsQuery)
        assertEquals("cải lương", testApp.store.recentSearches.value.first())
        vm.search("   ") // chuỗi trống: không làm gì
        assertEquals("cải lương", vm.resultsQuery)
    }
}
