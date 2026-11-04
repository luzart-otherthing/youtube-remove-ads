package com.luzart.tubesach

import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Mở ứng dụng thật (MainActivity) để chắc chắn không bị văng khi khởi động / nhận link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivitySmokeTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Test
    fun launchesNormally() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.CREATED) // chạy nền
            scenario.moveToState(Lifecycle.State.RESUMED) // quay lại
        }
    }

    @Test
    fun opensLinkSharedFromZalo() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        val intent = Intent(app, MainActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Bà xem video này nhé https://youtu.be/dQw4w9WgXcQ?si=xyz")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals("dQw4w9WgXcQ", PlaybackController.nowPlaying.value?.video?.id)
            PlaybackController.close()
        }
    }
}
