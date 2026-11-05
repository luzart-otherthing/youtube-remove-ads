package com.luzart.tubesach

import android.net.ConnectivityManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.net.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork

/** Theo dõi mạng: chỉ báo "có mạng lại" khi thật sự có mạng mới / hết bị chặn, không báo nhầm lúc mở app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NetworkMonitorTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @Test
    fun reportsOnlyRealReconnects() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val callback = shadowOf(cm).networkCallbacks.single()
        var events = 0
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { NetworkMonitor.reconnected.collect { events++ } }
        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        idle()

        // Android gọi onAvailable ngay khi đăng ký cho mạng đang dùng → không phải "có mạng lại".
        callback.onAvailable(cm.activeNetwork!!)
        idle()
        assertEquals(0, events)

        // Đổi từ Wi-Fi sang 4G.
        val cellular = ShadowNetwork.newInstance(77)
        callback.onAvailable(cellular)
        idle()
        assertEquals(1, events)

        // Mất mạng rồi có lại.
        callback.onLost(cellular)
        idle()
        callback.onAvailable(cellular)
        idle()
        assertEquals(2, events)

        // Doze: bị chặn mạng khi tắt màn hình lâu, hết bị chặn khi bật màn hình.
        callback.onBlockedStatusChanged(cellular, false) // trạng thái ban đầu, không tính
        idle()
        assertEquals(2, events)
        callback.onBlockedStatusChanged(cellular, true)
        idle()
        callback.onBlockedStatusChanged(cellular, false)
        idle()
        assertEquals(3, events)
        scope.cancel()
    }
}
