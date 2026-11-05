package com.luzart.tubesach.tv

import android.os.Looper
import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.player.PlaybackController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Chạy app TV thật với YouTube thật (cần mạng), điều khiển hoàn toàn bằng phím remote:
 * ./gradlew :tv:testDebugUnitTest -Donline=true --tests '*OnlineTvTest*'
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class OnlineTvTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun onlyWhenRequested() {
        assumeTrue(System.getProperty("online") == "true")
        markAsTv()
        resetStore()
    }

    @After
    fun tearDown() {
        if (System.getProperty("online") == "true") PlaybackController.close()
    }

    /** Chờ mạng (tối đa [seconds] giây) trong khi vẫn cho giao diện chạy. */
    private fun waitFor(what: String, seconds: Int = 30, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            if (condition()) return
            Thread.sleep(150)
        }
        throw AssertionError("Hết thời gian chờ: $what")
    }

    @Test
    fun browseWatchAndNavigateWithRemoteOnly() {
        ActivityScenario.launch(TvActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            lateinit var vm: TvViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[TvViewModel::class.java] }

            // 1. Trang chủ tải xong (khách mới: "Thịnh hành tại Việt Nam").
            waitFor("trang chủ") { vm.home.loaded && (vm.home.entries.isNotEmpty() || vm.homeTrending.isNotEmpty()) }
            val homeCount = vm.home.entries.size
            println("Trang chủ: $homeCount mục, thịnh hành: ${vm.homeTrending.size} video")
            waitFor("ô sáng vào video đầu tiên", 10) { compose.focusedTag()?.startsWith("video:") == true }
            val firstCard = compose.focusedTag()!!
            println("Ô sáng đầu tiên: $firstCard")

            // 2. OK → mở video, thông tin trang xem + video tiếp theo được tải.
            compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
            assertTrue(vm.playerOpen)
            waitFor("thông tin video") { PlaybackController.nowPlaying.value?.watch != null }
            val np = PlaybackController.nowPlaying.value!!
            println("Đang xem: ${np.video.title} | ${np.video.uploader} | liên quan: ${np.related.size} | phụ đề: ${np.captions.size} | ${np.availableHeights}")
            assertEquals(firstCard.removePrefix("video:"), np.video.id)
            assertTrue(np.video.title.isNotBlank())
            assertTrue(np.related.isNotEmpty())
            waitFor("link phát", 20) { PlaybackController.player.currentMediaItem != null || PlaybackController.error.value != null }
            println("Lỗi phát (Robolectric không có bộ giải mã video thật nên có thể có): ${PlaybackController.error.value}")

            // 3. ▲ hiện điều khiển → ▼ xuống "Video tiếp theo" → OK mở video đó.
            if (PlaybackController.error.value == null) {
                compose.press(KeyEvent.KEYCODE_DPAD_UP)
                assertTrue(vm.controlsVisible)
                compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
                val next = compose.focusedTag()!!
                assertTrue("Ô sáng ở Video tiếp theo: $next", next.startsWith("video:"))
                compose.press(KeyEvent.KEYCODE_DPAD_CENTER)
                waitFor("video tiếp theo") { PlaybackController.nowPlaying.value?.video?.id == next.removePrefix("video:") }
                assertNotEquals(np.video.id, PlaybackController.nowPlaying.value?.video?.id)
            }

            // 4. Quay lại → thoát video, ô sáng về đúng thẻ đã chọn.
            scenario.onActivity {
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            }
            compose.settle()
            if (vm.playerOpen) {
                scenario.onActivity {
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                it.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            }
                compose.settle()
            }
            assertFalse(vm.playerOpen)
            assertEquals(firstCard, compose.focusedTag())
            // Video vừa xem nằm trong lịch sử.
            assertTrue(testApp.store.history.value.any { "video:${it.video.id}" == firstCard })

            // 5. Tìm kiếm tiếng Việt.
            scenario.onActivity { vm.search("cải lương") }
            waitFor("kết quả tìm kiếm") { vm.results.loaded }
            val videos = vm.results.entries.filterIsInstance<FeedEntry.Video>()
            println("Tìm 'cải lương': ${videos.size} video, ví dụ: ${videos.firstOrNull()?.video?.title}")
            assertTrue(videos.size >= 5)
            compose.press(KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue(compose.focusedTag()!!.matches(Regex("(video|channel|short):.*")))
        }
    }
}
