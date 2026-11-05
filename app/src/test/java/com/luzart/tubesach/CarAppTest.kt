package com.luzart.tubesach

import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.AppManager
import androidx.car.app.model.GridTemplate
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.SessionController
import androidx.car.app.testing.TestAppManager
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.carapp.DriveState
import com.luzart.tubesach.carapp.PlayerScreen
import com.luzart.tubesach.carapp.SpeedLogic
import com.luzart.tubesach.carapp.TubeCarAppService
import com.luzart.tubesach.carapp.TubeCarSession
import com.luzart.tubesach.carapp.VideoGridScreen
import com.luzart.tubesach.carapp.VideoSurfaceRenderer
import com.luzart.tubesach.player.PlaybackController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Màn hình Android Auto kiểu "ứng dụng bản đồ" cho điện thoại Android cũ (ví dụ Android 11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CarAppTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        PlaybackController.close()
    }

    // ---------------------------------------------------------------- Khoá hình khi xe chạy

    private val kmh = 1f / 3.6f

    @Test
    fun videoOnlyAfterCarStoodStillForThreeSeconds() {
        val logic = SpeedLogic()
        assertEquals(DriveState.UNKNOWN, logic.update(null, 0))
        assertEquals(DriveState.MOVING, logic.update(40 * kmh, 1_000))
        assertEquals(DriveState.MOVING, logic.update(0f, 2_000)) // vừa dừng đèn đỏ
        assertEquals(DriveState.MOVING, logic.update(0f, 4_000))
        assertEquals(DriveState.PARKED, logic.update(0f, 5_000)) // đứng yên đủ 3 giây
        assertEquals(DriveState.PARKED, logic.update(3 * kmh, 6_000)) // nhích nhẹ: chưa tắt hình
        assertEquals(DriveState.MOVING, logic.update(6 * kmh, 7_000)) // chạy: tắt hình ngay
    }

    @Test
    fun noSpeedMeansNoVideo() {
        val logic = SpeedLogic()
        assertEquals(DriveState.UNKNOWN, logic.update(null, 1_000))
        assertEquals(DriveState.UNAVAILABLE, logic.update(null, 9_000))
        // Đang đỗ mà mất tín hiệu tốc độ → không coi là đỗ nữa.
        val l2 = SpeedLogic()
        l2.update(0f, 0); l2.update(0f, 3_000)
        assertEquals(DriveState.PARKED, l2.update(0f, 3_500))
        assertEquals(DriveState.UNKNOWN, l2.update(null, 4_000))
    }

    @Test
    fun onlyParkedShowsVideo() {
        DriveState.entries.forEach { s ->
            val video = PlayerScreen.modeFor(s) == VideoSurfaceRenderer.Mode.Video
            assertEquals(s.name, s == DriveState.PARKED, video)
        }
    }

    // ---------------------------------------------------------------- Màn hình trên xe

    private fun session(): Pair<TubeCarSession, TestCarContext> {
        val ctx = TestCarContext.createCarContext(app)
        val session = TubeCarSession()
        SessionController(session, ctx, Intent()).moveToState(Lifecycle.State.CREATED)
        return session to ctx
    }

    @Test
    fun sessionOpensHomeAndClaimsTheMapSurface() {
        val (session, ctx) = session()
        val appManager = ctx.getCarService(AppManager::class.java) as TestAppManager
        assertSame(session.renderer, appManager.surfaceCallback)
        // Chưa cho phép đọc tốc độ xe → chưa hiện hình.
        assertEquals(DriveState.NO_PERMISSION, session.gate.state.value)

        val home = VideoGridScreen(ctx, session, "Tube Sạch", isHome = true) { emptyList() }
        val template = ScreenController(home).moveToState(Lifecycle.State.CREATED).screen.onGetTemplate()
        assertTrue(template is GridTemplate)
    }

    @Test
    fun playerScreenUsesNavigationTemplateWithBigControls() {
        val (session, ctx) = session()
        val controller = ScreenController(PlayerScreen(ctx, session)).moveToState(Lifecycle.State.RESUMED)
        val t = controller.screen.onGetTemplate() as NavigationTemplate
        val titles = t.actionStrip!!.actions.mapNotNull { it.title?.toString() }
        assertTrue(titles.contains("Quay lại"))
        assertTrue(titles.contains("Bật hình")) // xin quyền đọc tốc độ xe
        assertEquals(2, t.mapActionStrip!!.actions.size) // lùi / tới 10 giây
        // Không có bề mặt / chưa đỗ → không giành hình của điện thoại.
        assertFalse(PlaybackController.carScreenActive.value)
        controller.moveToState(Lifecycle.State.DESTROYED)
    }

    // ---------------------------------------------------------------- Cài để Android Auto nhận

    @Test
    fun reinstallTellsInstallerItCameFromPlayStore() {
        val result = com.luzart.tubesach.update.PlayInstaller.reinstallSelf(app)
        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val started = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
        @Suppress("DEPRECATION")
        assertEquals(Intent.ACTION_INSTALL_PACKAGE, started.action)
        @Suppress("DEPRECATION")
        assertEquals("com.android.vending", started.getStringExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME))
        @Suppress("DEPRECATION")
        assertTrue(started.getBooleanExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false))
        assertEquals("content", started.data?.scheme)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(com.luzart.tubesach.update.PlayInstaller.supported) // Android 11
        assertFalse(com.luzart.tubesach.update.PlayInstaller.isTrusted(app))
    }

    @Test
    fun manifestDeclaresNavigationCarApp() {
        val pm = app.packageManager
        val intent = Intent("androidx.car.app.CarAppService").addCategory("androidx.car.app.category.NAVIGATION").setPackage(app.packageName)
        assertEquals(TubeCarAppService::class.java.name, pm.queryIntentServices(intent, 0).single().serviceInfo.name)
        val perms = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions!!.toList()
        assertTrue(perms.containsAll(listOf("androidx.car.app.NAVIGATION_TEMPLATES", "androidx.car.app.ACCESS_SURFACE", "com.google.android.gms.permission.CAR_SPEED")))
    }

    /** Android 15+: dùng màn hình chính thức khi đỗ xe, tắt chế độ bản đồ để không hiện 2 biểu tượng. */
    @Test
    @Config(sdk = [35])
    fun templateVideoDisabledOnAndroid15() {
        val intent = Intent("androidx.car.app.CarAppService").setPackage(app.packageName)
        assertTrue(app.packageManager.queryIntentServices(intent, 0).isEmpty())
    }
}
