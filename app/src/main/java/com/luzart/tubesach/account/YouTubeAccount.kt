package com.luzart.tubesach.account

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.security.MessageDigest

/** Thông tin tài khoản đang đăng nhập (lấy từ YouTube sau khi đăng nhập). */
data class AccountInfo(val name: String, val handle: String?, val photo: String?)

/**
 * Đăng nhập tài khoản Google (THỬ NGHIỆM, KHÔNG KHUYẾN KHÍCH).
 *
 * Cách làm giống trình duyệt: người dùng đăng nhập trong WebView, app dùng lại cookie của
 * youtube.com để gọi InnerTube "như trình duyệt đã đăng nhập" (trang chủ theo tài khoản,
 * đăng ký kênh, thích, bình luận, lịch sử xem).
 *
 * - Cookie nằm trong kho cookie của WebView ([CookieManager]) – không chép ra chỗ khác.
 *   Cookie mới YouTube gửi về (Google xoay vòng cookie định kỳ) được lưu lại vào đó, nhờ vậy
 *   phiên đăng nhập không tự hết hạn.
 * - Chỉ các yêu cầu API trang web (www.youtube.com) mới mang cookie. Link phát video (visionOS)
 *   và NewPipeExtractor vẫn ẩn danh như trước.
 * - Đăng xuất = xoá toàn bộ cookie của WebView.
 */
class YouTubeAccount(context: Context, baseHttp: OkHttpClient) {

    private val prefs: SharedPreferences = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val _info = MutableStateFlow(loadInfo())
    /** null = chưa đăng nhập. */
    val info: StateFlow<AccountInfo?> = _info.asStateFlow()

    val isLoggedIn: Boolean get() = _info.value != null

    private val cookieManager: CookieManager? by lazy { runCatching { CookieManager.getInstance() }.getOrNull() }

    /** Kho cookie dùng chung với WebView, chỉ cho các tên miền youtube.com. */
    private val jar = object : CookieJar {
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            if (!isLoggedIn || !isYoutube(url.host)) return emptyList()
            val header = cookieManager?.getCookie(url.toString()) ?: return emptyList()
            return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (!isLoggedIn || !isYoutube(url.host)) return
            val cm = cookieManager ?: return
            cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
        }
    }

    /** OkHttp dùng chung kết nối với app nhưng có thêm cookie đăng nhập. */
    val http: OkHttpClient = baseHttp.newBuilder().cookieJar(jar).build()

    /**
     * Tiêu đề xác thực giống trang web YouTube: `Authorization: SAPISIDHASH <thời gian>_<sha1>`.
     * Trả về null nếu chưa đăng nhập hoặc thiếu cookie.
     */
    fun authHeaders(origin: String = ORIGIN): Map<String, String>? {
        if (!isLoggedIn) return null
        val sapisid = sapisid() ?: return null
        val ts = System.currentTimeMillis() / 1000
        val hash = sha1("$ts $sapisid $origin")
        return mapOf(
            "Authorization" to "SAPISIDHASH ${ts}_$hash",
            "X-Origin" to origin,
            "X-Goog-AuthUser" to "0",
        )
    }

    private fun sapisid(): String? {
        val header = cookieManager?.getCookie(ORIGIN) ?: return null
        val map = header.split(';').mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i).trim() to it.substring(i + 1).trim() else null
        }.toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"]
    }

    /** Gọi sau mỗi lần trang trong WebView tải xong: true nếu đã có cookie đăng nhập YouTube. */
    fun hasLoginCookies(): Boolean = sapisid() != null

    /** Đánh dấu đã đăng nhập (thông tin tài khoản sẽ được cập nhật sau). */
    fun onLoggedIn(info: AccountInfo?) {
        cookieManager?.flush()
        setInfo(info ?: AccountInfo("Tài khoản Google", null, null))
    }

    fun updateInfo(info: AccountInfo) {
        if (isLoggedIn) setInfo(info)
    }

    /** Đăng xuất: xoá cookie của WebView và thông tin tài khoản. */
    fun logout() {
        cookieManager?.let {
            it.removeAllCookies(null)
            it.flush()
        }
        prefs.edit().clear().apply()
        _info.value = null
    }

    private fun setInfo(info: AccountInfo) {
        prefs.edit()
            .putBoolean("loggedIn", true)
            .putString("name", info.name)
            .putString("handle", info.handle)
            .putString("photo", info.photo)
            .apply()
        _info.value = info
    }

    private fun loadInfo(): AccountInfo? {
        if (!prefs.getBoolean("loggedIn", false)) return null
        return AccountInfo(
            prefs.getString("name", null) ?: "Tài khoản Google",
            prefs.getString("handle", null),
            prefs.getString("photo", null),
        )
    }

    companion object {
        const val ORIGIN = "https://www.youtube.com"

        /** Trang đăng nhập Google, xong thì quay về YouTube (bản di động). */
        const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&hl=vi" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Dvi%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

        fun isYoutube(host: String) = host == "youtube.com" || host.endsWith(".youtube.com")

        internal fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
