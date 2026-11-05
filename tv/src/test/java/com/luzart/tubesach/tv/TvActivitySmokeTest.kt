package com.luzart.tubesach.tv

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.luzart.tubesach.player.PlaybackController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Mở app TV thật (TvActivity) để chắc chắn không bị văng khi mở, chạy nền, nhận link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class TvActivitySmokeTest {

    @Before
    fun setUp() {
        markAsTv()
        resetStore()
    }

    @After
    fun tearDown() = PlaybackController.close()

    @Test
    fun launchesFromGoogleTvHome_backgroundAndResume() {
        val launch = testApp.packageManager.getLeanbackLaunchIntentForPackage(testApp.packageName)
        // Robolectric không tự đọc LEANBACK_LAUNCHER; kiểm tra manifest bằng aapt trong CI. Ở đây mở trực tiếp.
        val intent = launch ?: Intent(testApp, TvActivity::class.java).setAction(Intent.ACTION_MAIN)
        ActivityScenario.launch<TvActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.CREATED) // bấm nút Home trên remote
            scenario.moveToState(Lifecycle.State.RESUMED) // mở lại app
            scenario.recreate() // TV đổi độ phân giải / chế độ hình
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun opensYoutubeLinkDirectlyInPlayer() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/dQw4w9WgXcQ?si=abc"))
            .setClass(testApp, TvActivity::class.java)
        ActivityScenario.launch<TvActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals("dQw4w9WgXcQ", PlaybackController.nowPlaying.value?.video?.id)
            scenario.onActivity { a ->
                val vm = androidx.lifecycle.ViewModelProvider(a)[TvViewModel::class.java]
                assertTrue(vm.playerOpen)
            }
            // Rời app khi đang xem → video tạm dừng.
            scenario.moveToState(Lifecycle.State.CREATED)
            assertFalse(PlaybackController.player.playWhenReady)
        }
    }

    /** Lỗi thật trên TV: bấm phím Quay lại khi đang xem video không thoát ra được. */
    @Test
    fun remoteBackKeyLeavesPlayer() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/dQw4w9WgXcQ"))
            .setClass(testApp, TvActivity::class.java)
        ActivityScenario.launch<TvActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            fun pressBack() = scenario.onActivity { a ->
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            }
            lateinit var vm: TvViewModel
            scenario.onActivity { vm = androidx.lifecycle.ViewModelProvider(it)[TvViewModel::class.java] }
            assertTrue(vm.playerOpen)
            // Hiện thanh điều khiển rồi bấm Quay lại 2 lần: ẩn điều khiển → thoát video.
            scenario.onActivity { vm.controlsVisible = true }
            pressBack()
            assertFalse(vm.controlsVisible)
            assertTrue(vm.playerOpen)
            pressBack()
            assertFalse("Phím Quay lại phải thoát video", vm.playerOpen)
            scenario.onActivity { assertFalse(it.isFinishing) } // chưa thoát app
        }
    }

    @Test
    fun sharedTextWithoutLinkDoesNotCrash() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "không có link")
            .setClass(testApp, TvActivity::class.java)
        ActivityScenario.launch<TvActivity>(intent).use { it.moveToState(Lifecycle.State.RESUMED) }
    }

    @Test
    fun remoteSearchKeyOpensSearch() {
        ActivityScenario.launch(TvActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH))
                a.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SEARCH))
                val vm = androidx.lifecycle.ViewModelProvider(a)[TvViewModel::class.java]
                assertEquals(Section.SEARCH, vm.section)
            }
        }
    }

    @Test
    fun voiceSearchWithoutRecognizerShowsMessageInsteadOfCrashing() {
        ActivityScenario.launch(TvActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { a ->
                val m = TvActivity::class.java.getDeclaredMethod("startVoiceSearch").apply { isAccessible = true }
                m.invoke(a)
                assertNotNull(shadowOf(a).nextStartedActivity ?: org.robolectric.shadows.ShadowToast.getLatestToast())
            }
        }
    }
}
