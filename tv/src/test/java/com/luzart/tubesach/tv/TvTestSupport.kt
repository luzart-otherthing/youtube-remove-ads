package com.luzart.tubesach.tv

import android.content.pm.PackageManager
import android.os.Looper
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.innertube.Parser
import com.luzart.tubesach.innertube.PlayerData
import org.json.JSONObject
import org.robolectric.Shadows.shadowOf
import java.util.zip.GZIPInputStream

/** Dữ liệu YouTube thật đã lưu sẵn (dùng chung với test của app điện thoại). */
object Fixtures {
    fun json(name: String): JSONObject {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$name.json.gz")
        return JSONObject(GZIPInputStream(stream).bufferedReader().readText())
    }

    val home by lazy { Parser.browseGrid(json("home")) }
    val search by lazy { Parser.search(json("search")) }
    val watch by lazy { Parser.watch(json("next"), "fbiZrIlPNx8") }
    val player by lazy { PlayerData.parse(json("player"), "dQw4w9WgXcQ") }
}

val testApp: TvApp get() = ApplicationProvider.getApplicationContext()

/** Giả lập Google TV (Compose bật chế độ cuộn "giữ ô sáng ở giữa" dành cho TV). */
fun markAsTv() {
    shadowOf(testApp.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    shadowOf(testApp.packageManager).setSystemFeature(PackageManager.FEATURE_TOUCHSCREEN, false)
}

/** Xoá dữ liệu đã lưu giữa các test. */
fun resetStore() {
    val s = testApp.store
    s.clearHistory()
    s.clearRecentSearches()
    s.subscriptions.value.forEach { s.toggleSubscription(it) }
    s.favorites.value.forEach { s.toggleFavorite(it) }
    s.liked.value.forEach { s.toggleLike(it) }
    s.updateSettings { com.luzart.tubesach.data.Settings() }
}

/** Bấm một phím trên remote (nhấn + thả), rồi chờ giao diện cập nhật. */
fun ComposeTestRule.press(keyCode: Int, times: Int = 1) {
    repeat(times) {
        onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, keyCode)))
        onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_UP, keyCode)))
        settle()
    }
}

/** Giữ OK lâu (mở menu). */
fun ComposeTestRule.longPressCenter() {
    val code = AndroidKeyEvent.KEYCODE_DPAD_CENTER
    onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, code, 0)))
    for (i in 1..4) {
        val flags = if (i == 1) AndroidKeyEvent.FLAG_LONG_PRESS else 0
        onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_DOWN, code, i, 0, 0, 0, flags)))
    }
    onRoot().performKeyPress(KeyEvent(AndroidKeyEvent(0, 0, AndroidKeyEvent.ACTION_UP, code, 0)))
    settle()
}

fun ComposeTestRule.settle(rounds: Int = 3) {
    repeat(rounds) {
        shadowOf(Looper.getMainLooper()).idle()
        mainClock.advanceTimeBy(400)
        waitForIdle()
    }
}

/** Ô đang sáng (được chọn bằng remote). */
fun ComposeTestRule.focused(): SemanticsNodeInteraction = onNode(isFocused(), useUnmergedTree = true)

/** testTag của ô đang sáng, hoặc của ô cha gần nhất có testTag. */
fun ComposeTestRule.focusedTag(): String? {
    val node = focused().fetchSemanticsNode()
    var n: androidx.compose.ui.semantics.SemanticsNode? = node
    while (n != null) {
        n.config.getOrNull(SemanticsProperties.TestTag)?.let { return it }
        n = n.parent
    }
    return null
}
