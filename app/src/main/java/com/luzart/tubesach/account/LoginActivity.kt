package com.luzart.tubesach.account

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.luzart.tubesach.App

/**
 * Trang đăng nhập Google (thử nghiệm). Người dùng đăng nhập trực tiếp trên trang của Google trong
 * WebView – app không thấy mật khẩu. Khi YouTube đã đặt cookie đăng nhập thì đóng lại và báo thành công.
 */
class LoginActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var done = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Đăng nhập Google"
        val account = App.of(this).account
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true }

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // Google từ chối đăng nhập nếu nhận ra WebView ("; wv"), nên dùng chuỗi trình duyệt Chrome thường.
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "https" && scheme != "http" // chặn intent://, market://…
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    progress.visibility = android.view.View.VISIBLE
                    check(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    progress.visibility = android.view.View.GONE
                    check(url)
                }

                private fun check(url: String?) {
                    if (done) return
                    val host = url?.let { Uri.parse(it).host }.orEmpty()
                    if (YouTubeAccount.isYoutube(host) && account.hasLoginCookies()) {
                        done = true
                        CookieManager.getInstance().flush()
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                }
            }
        }

        val root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.fitsSystemWindows = true
        setContentView(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (web.canGoBack()) web.goBack() else finish()
                }
            },
        )

        if (savedInstanceState == null) web.loadUrl(YouTubeAccount.LOGIN_URL) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
