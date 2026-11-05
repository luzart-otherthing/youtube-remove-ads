package com.luzart.tubesach

import android.os.Looper
import android.webkit.CookieManager
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.account.YouTubeAccount
import com.luzart.tubesach.data.ChannelRef
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.InnerTube
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.CommentsState
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowCookieManager
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * Trang chủ + tài khoản Google với "mạng giả" (trả dữ liệu YouTube thật đã lưu sẵn):
 * - tải thêm trang chủ khi mạng chập chờn, tự tải lại khi có mạng;
 * - đăng nhập: cookie + SAPISIDHASH chỉ gửi cho API trang web, KHÔNG gửi cho link phát video;
 * - đồng bộ đăng ký / thích / không thích, đăng bình luận, đăng xuất.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountAndFeedTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    /** [cookie]: cookie OkHttp sẽ gắn vào yêu cầu này (lấy từ cookie jar của chính client đã gửi). */
    private class Sent(val url: String, val headers: okhttp3.Headers, val body: JSONObject?, val cookie: String?)

    private inner class FakeNet : Interceptor {
        val sent = mutableListOf<Sent>()
        @Volatile var down = false

        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }?.let { runCatching { JSONObject(it) }.getOrNull() }
            // Mô phỏng BridgeInterceptor của OkHttp: gắn cookie từ cookie jar của client đã gửi yêu cầu.
            val jar = (chain.call() as okhttp3.internal.connection.RealCall).client.cookieJar
            val cookie = jar.loadForRequest(req.url).joinToString("; ") { "${it.name}=${it.value}" }.ifEmpty { null }
            synchronized(sent) { sent += Sent(req.url.toString(), req.headers, body, cookie) }
            if (down) throw IOException("mất mạng")
            val path = req.url.encodedPath
            val json = when {
                path.endsWith("/browse") && body?.optString("browseId") == "FEchannels" -> CHANNELS
                path.endsWith("/browse") && body?.has("continuation") == true -> fixture("next_cont")
                path.endsWith("/browse") -> fixture("home")
                path.endsWith("/account/account_menu") -> MENU
                path.endsWith("/next") && body?.has("continuation") == true -> COMMENTS_HEADER
                path.endsWith("/next") -> fixture("next")
                path.endsWith("/comment/create_comment") -> """{"actionResults":[{"status":"STATUS_SUCCEEDED"}]}"""
                path.endsWith("/player") -> fixture("player")
                else -> "{}"
            }
            val response = Response.Builder().request(req).protocol(Protocol.HTTP_2).code(200).message("OK")
                .apply {
                    // Google xoay vòng cookie định kỳ: trả về cookie mới.
                    if (path.endsWith("/account/account_menu")) header("Set-Cookie", "__Secure-1PSIDTS=rotated789; Domain=.youtube.com; Path=/; Secure")
                }
                .body(json.toResponseBody("application/json".toMediaType())).build()
            // Mô phỏng OkHttp lưu cookie từ phản hồi vào cookie jar.
            jar.saveFromResponse(req.url, okhttp3.Cookie.parseAll(req.url, response.headers))
            return response
        }

        fun last(endpoint: String): Sent = synchronized(sent) { sent.last { it.url.contains(endpoint) } }
        fun count(endpoint: String): Int = synchronized(sent) { sent.count { it.url.contains(endpoint) } }
    }

    private lateinit var net: FakeNet
    private lateinit var vm: AppViewModel

    @Before
    fun setUp() {
        ShadowCookieManager.resetCookies()
        net = FakeNet()
        val http = OkHttpClient.Builder().addInterceptor(net).build()
        val account = YouTubeAccount(app, http)
        account.logout()
        app.account = account
        app.store.updateSettings { it.copy(personalize = false, sponsorBlock = false, returnDislike = false) }
        app.store.visitorData = "visitor123"
        YouTubeRepository.init(InnerTube(http, { app.store.visitorData }, { app.store.visitorData = it }, account), http)
        vm = AppViewModel(app)
    }

    @After
    fun tearDown() {
        app.account.logout()
    }

    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("Không đạt: $what")
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun signIn() {
        CookieManager.getInstance().setCookie("https://www.youtube.com", "SAPISID=abc123; domain=.youtube.com; path=/")
        CookieManager.getInstance().setCookie("https://www.youtube.com", "SID=sid456; domain=.youtube.com; path=/")
        assertTrue(app.account.hasLoginCookies())
        vm.onLoginFinished()
        waitFor("lấy thông tin tài khoản + kênh đã đăng ký") {
            app.account.info.value?.name == "Bà Nội" && app.store.subscriptions.value.any { it.id == "UCabcdefghijklmnopqrstuv" }
        }
    }

    // ------------------------------------------------------------------ Trang chủ

    @Test
    fun homeLoadMoreKeepsTokenWhenNetworkDrops() {
        vm.loadHome()
        waitFor("trang chủ") { vm.home.loaded && !vm.home.loading }
        val token = vm.home.continuation
        assertNotNull(token)
        val count = vm.home.entries.size

        net.down = true
        vm.loadMoreHome()
        waitFor("tải thêm lỗi xong") { !vm.home.loadingMore }
        assertEquals("Giữ token để cuộn xuống là thử lại", token, vm.home.continuation)
        assertEquals(count, vm.home.entries.size)

        net.down = false
        vm.loadMoreHome()
        waitFor("tải thêm được") { !vm.home.loadingMore && vm.home.entries.size > count }
        assertEquals(vm.home.entries.size, vm.home.entries.map { it.key }.toSet().size)
    }

    @Test
    fun homeErrorReloadsWhenAppComesBack() {
        net.down = true
        vm.loadHome()
        waitFor("báo lỗi") { vm.home.error != null }
        assertEquals("Không có kết nối mạng", vm.home.error)
        net.down = false
        vm.onResume() // bật màn hình / có mạng lại
        waitFor("tự tải lại") { vm.home.error == null && vm.home.entries.isNotEmpty() }
    }

    // ------------------------------------------------------------------ Tài khoản

    @Test
    fun notSignedInSendsNoCookies() {
        vm.loadHome()
        waitFor("trang chủ") { vm.home.loaded }
        val r = net.last("/browse")
        assertNull(r.headers["Authorization"])
        assertNull(r.cookie)
        assertFalse(YouTubeRepository.signedIn)
    }

    @Test
    fun signInAuthenticatesWebApiOnly() {
        signIn()
        assertTrue(YouTubeRepository.signedIn)
        assertEquals("@banoi", app.account.info.value?.handle)
        assertEquals(2, app.store.subscriptions.value.count { it.id.startsWith("UC") && it.name.startsWith("Kênh") })

        vm.refreshHome()
        waitFor("trang chủ theo tài khoản") { vm.home.loaded && !vm.home.loading && !vm.home.refreshing }
        val r = net.last("/browse")
        val auth = r.headers["Authorization"]!!
        assertTrue(auth, auth.startsWith("SAPISIDHASH "))
        val (ts, hash) = auth.removePrefix("SAPISIDHASH ").split("_")
        assertEquals("SAPISIDHASH = sha1(thời gian + SAPISID + origin)", YouTubeAccount.sha1("$ts abc123 https://www.youtube.com"), hash)
        assertEquals("0", r.headers["X-Goog-AuthUser"])
        val cookie = r.cookie!!
        assertTrue(cookie, cookie.contains("SAPISID=abc123") && cookie.contains("SID=sid456"))
        // Cookie mới YouTube gửi về (lúc lấy thông tin tài khoản) được lưu và gửi lại ở yêu cầu sau.
        assertTrue(cookie, cookie.contains("__Secure-1PSIDTS=rotated789"))
        assertTrue(CookieManager.getInstance().getCookie("https://www.youtube.com")!!.contains("rotated789"))

        // Link phát video (visionOS) vẫn ẩn danh: không cookie, không Authorization.
        runBlocking { YouTubeRepository.player("dQw4w9WgXcQ") }
        val p = net.last("youtubei.googleapis.com")
        assertNull(p.headers["Authorization"])
        assertNull(p.cookie)
        // Cookie của YouTube không bao giờ gửi tới máy chủ khác (googlevideo, googleapis…).
        val jar = app.account.http.cookieJar
        assertTrue(jar.loadForRequest(okhttp3.HttpUrl.Builder().scheme("https").host("rr1---sn-abc.googlevideo.com").build()).isEmpty())
        assertTrue(jar.loadForRequest(okhttp3.HttpUrl.Builder().scheme("https").host("youtubei.googleapis.com").build()).isEmpty())
    }

    @Test
    fun subscribeLikeDislikeAreSyncedToAccount() {
        signIn()
        val ch = ChannelRef("UCzzzzzzzzzzzzzzzzzzzzzz", "Kênh mới", null)
        vm.toggleSubscription(ch)
        waitFor("đăng ký") { net.count("/subscription/subscribe") == 1 }
        assertEquals(ch.id, net.last("/subscription/subscribe").body!!.getJSONArray("channelIds").getString(0))
        vm.toggleSubscription(ch)
        waitFor("huỷ đăng ký") { net.count("/subscription/unsubscribe") == 1 }

        val v = VideoItem.ofId("dQw4w9WgXcQ", "Video")
        vm.toggleLike(v)
        waitFor("thích") { net.count("/like/like") == 1 }
        assertEquals("dQw4w9WgXcQ", net.last("/like/like").body!!.getJSONObject("target").getString("videoId"))
        vm.toggleDislike(v.id)
        waitFor("không thích") { net.count("/like/dislike") == 1 }
        vm.toggleDislike(v.id)
        waitFor("bỏ không thích") { net.count("/like/removelike") == 1 }
        assertNotNull(net.last("/like/removelike").headers["Authorization"])
    }

    @Test
    fun notSignedInChangesStayOnDevice() {
        vm.toggleSubscription(ChannelRef("UCzzzzzzzzzzzzzzzzzzzzzz", "Kênh", null))
        vm.toggleLike(VideoItem.ofId("dQw4w9WgXcQ", "Video"))
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(200)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, net.count("/subscription/") + net.count("/like/"))
        assertTrue(app.store.isSubscribed("UCzzzzzzzzzzzzzzzzzzzzzz"))
    }

    @Test
    fun postCommentWhenSignedIn() {
        signIn()
        vm.previewComments(CommentsState(videoId = "fbiZrIlPNx8"))
        var result: Boolean? = null
        vm.postComment("  Hay quá!  ") { result = it }
        waitFor("đăng bình luận") { result != null }
        assertEquals(true, result)
        val sent = net.last("/comment/create_comment").body!!
        assertEquals("Hay quá!", sent.getString("commentText"))
        assertEquals("PARAMS123", sent.getString("createCommentParams"))
        assertEquals("Hay quá!", vm.comments.items.first().text)
        assertEquals("@banoi", vm.comments.items.first().author)
        assertFalse(vm.commentPosting)
    }

    @Test
    fun logoutRemovesCookiesAndAuth() {
        signIn()
        vm.logout()
        assertNull(app.account.info.value)
        assertFalse(app.account.hasLoginCookies())
        waitFor("trang chủ ẩn danh") { vm.home.loaded && !vm.home.loading }
        val r = net.last("/browse")
        assertNull(r.headers["Authorization"])
        assertNull(r.cookie)
        assertTrue("Kênh đăng ký trên máy vẫn giữ", app.store.subscriptions.value.isNotEmpty())
    }

    companion object {
        fun fixture(name: String): String {
            val stream = AccountAndFeedTest::class.java.classLoader!!.getResourceAsStream("fixtures/$name.json.gz")
            return GZIPInputStream(stream).bufferedReader().readText()
        }

        val MENU = """{"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":
            {"accountName":{"simpleText":"Bà Nội"},"channelHandle":{"simpleText":"@banoi"},
            "accountPhoto":{"thumbnails":[{"url":"https://yt3.ggpht.com/a=s88","width":88,"height":88}]}}}}}}}]}"""

        val CHANNELS = """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
            {"itemSectionRenderer":{"contents":[{"shelfRenderer":{"content":{"expandedShelfContentsRenderer":{"items":[
              {"channelRenderer":{"channelId":"UCabcdefghijklmnopqrstuv","title":{"simpleText":"Kênh Cải Lương"}}},
              {"channelRenderer":{"channelId":"UCbbbbbbbbbbbbbbbbbbbbbb","title":{"simpleText":"Kênh Tin Tức"}}}
            ]}}}}]}}]}}}}]}}}"""

        val COMMENTS_HEADER = """{"onResponseReceivedEndpoints":[{"reloadContinuationItemsCommand":{"continuationItems":[
            {"commentsHeaderRenderer":{"createRenderer":{"commentSimpleboxRenderer":{"submitButton":{"buttonRenderer":
            {"serviceEndpoint":{"createCommentEndpoint":{"createCommentParams":"PARAMS123"}}}}}}}}]}}]}"""
    }
}
