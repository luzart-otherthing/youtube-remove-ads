package com.luzart.tubesach

import android.app.Activity
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import com.luzart.tubesach.account.LoginActivity
import com.luzart.tubesach.account.YouTubeAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowCookieManager

/** Màn hình đăng nhập Google (thử nghiệm). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoginActivityTest {

    @Before
    fun clearCookies() = ShadowCookieManager.resetCookies()

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    @Test
    fun opensGoogleLoginAndFinishesWhenYoutubeCookiesAppear() {
        ActivityScenario.launchActivityForResult(LoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
                val web = root.getChildAt(0) as WebView
                val shadow = shadowOf(web)
                assertEquals(YouTubeAccount.LOGIN_URL, shadow.lastLoadedUrl)
                assertFalse("Không để lộ là WebView (Google sẽ chặn)", web.settings.userAgentString.contains("; wv"))
                val client = shadow.webViewClient

                // Link lạ (intent://, market://) không được mở.
                assertTrue(client.shouldOverrideUrlLoading(web, request("intent://x#Intent;end")))
                assertFalse(client.shouldOverrideUrlLoading(web, request("https://accounts.google.com/signin")))

                // Đang ở trang Google, chưa có cookie YouTube → chưa xong.
                client.onPageFinished(web, "https://accounts.google.com/signin/v2")
                assertFalse(activity.isFinishing)
                // Về tới YouTube nhưng chưa đăng nhập (không có SAPISID) → chưa xong.
                client.onPageFinished(web, "https://m.youtube.com/")
                assertFalse(activity.isFinishing)

                // Đăng nhập xong: YouTube đặt cookie SAPISID → đóng và báo thành công.
                CookieManager.getInstance().setCookie("https://www.youtube.com", "SAPISID=abc; domain=.youtube.com; path=/")
                client.onPageFinished(web, "https://m.youtube.com/")
                assertTrue(activity.isFinishing)
            }
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        }
    }
}
