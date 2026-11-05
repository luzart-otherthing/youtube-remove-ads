package com.luzart.tubesach

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaConstants
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.luzart.tubesach.auto.ArtworkProvider
import com.luzart.tubesach.auto.CarLibrary
import com.luzart.tubesach.car.CarActivity
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.MediaIds
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.player.PlaybackService
import com.luzart.tubesach.player.SessionPlayer
import com.luzart.tubesach.ui.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Kiểm tra phần Android Auto: thư viện video trên xe, ảnh, trình phát, màn hình ô tô. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarTest {

    private val app: App get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR, false)
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_PHONE, false)
        PlaybackController.close()
    }

    private fun video(id: String, short: Boolean = false, title: String = "Video $id") = VideoItem.ofId(id, title = title, uploader = "Kênh A")
        .copy(isShort = short, viewText = "1,2 Tr lượt xem", uploadedText = "2 ngày trước", durationSec = 600)

    private val v1 = video("aaaaaaaaaaa")
    private val v2 = video("bbbbbbbbbbb")
    private val short = video("ccccccccccc", short = true)

    private class FakeSource(
        var home: FeedPage = FeedPage(emptyList(), null),
        val trending: FeedPage = FeedPage(emptyList(), null),
        val results: Map<String, FeedPage> = emptyMap(),
    ) : CarLibrary.Source {
        var searches = 0
        override suspend fun home() = home
        override suspend fun trending() = trending
        override suspend fun topMusic() = trending
        override suspend fun search(query: String): FeedPage {
            searches++
            return results[query] ?: FeedPage(emptyList(), null)
        }
        override suspend fun subscriptions(store: LocalStore) = emptyList<VideoItem>()
    }

    private fun library(source: CarLibrary.Source) =
        CarLibrary(app.store, artworkUri = { ArtworkProvider.uriFor(app, it) }, source = source)

    // ---------------------------------------------------------------- Thư viện trên xe

    @Test
    fun rootHasFourTabsLikeTheApp() = runBlocking {
        val lib = library(FakeSource())
        val tabs = lib.children(CarLibrary.ROOT)
        assertEquals(listOf("Trang chủ", "Kênh đăng ký", "Đã xem", "Thư viện"), tabs.map { it.mediaMetadata.title.toString() })
        assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
        assertTrue(lib.rootExtras().getBoolean(CarLibrary.EXTRA_SEARCH_SUPPORTED))
        assertEquals(4, lib.children(CarLibrary.LIBRARY).size)
    }

    @Test
    fun homeShowsPlayableVideosWithoutShorts() = runBlocking {
        val shelf = FeedEntry.Shelf("Tin nổi bật", listOf(v2, v1))
        val lib = library(FakeSource(home = FeedPage(listOf(FeedEntry.Video(v1), FeedEntry.Video(short), shelf), null)))
        val items = lib.children(CarLibrary.HOME)
        assertEquals(listOf(MediaIds.ofVideo(v1.id), MediaIds.ofVideo(v2.id)), items.map { it.mediaId })
        val m = items.first().mediaMetadata
        assertEquals(true, m.isPlayable)
        assertEquals("Video aaaaaaaaaaa", m.title.toString())
        assertEquals("Kênh A • 1,2 Tr lượt xem • 2 ngày trước", m.subtitle.toString())
        assertEquals(600_000L, m.durationMs)
        assertEquals("content", m.artworkUri?.scheme)
        assertEquals(ArtworkProvider.authority(app), m.artworkUri?.authority)
    }

    @Test
    fun emptyHomeFallsBackToTrending() = runBlocking {
        val lib = library(FakeSource(trending = FeedPage(listOf(FeedEntry.Video(v2)), null)))
        assertEquals(listOf(MediaIds.ofVideo(v2.id)), lib.children(CarLibrary.HOME).map { it.mediaId })
    }

    @Test
    fun historyShowsProgress() = runBlocking {
        app.store.clearHistory()
        app.store.addToHistory(v1, 300_000) // xem được một nửa
        val item = library(FakeSource()).children(CarLibrary.HISTORY).single()
        val extras = item.mediaMetadata.extras!!
        assertEquals(MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED, extras.getInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS))
        assertEquals(0.5, extras.getDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE), 0.001)
        app.store.clearHistory()
    }

    @Test
    fun voiceSearchResolvesToResults() = runBlocking {
        val source = FakeSource(results = mapOf("nhạc bolero" to FeedPage(listOf(FeedEntry.Video(v1), FeedEntry.Video(v2)), null)))
        val lib = library(source)
        // "Ok Google, mở nhạc bolero trên Tube Sạch": Android Auto gửi một mục chỉ có câu tìm kiếm.
        val request = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("nhạc bolero").build())
            .build()
        val resolved = lib.resolve(listOf(request))
        assertEquals(listOf(MediaIds.ofVideo(v1.id), MediaIds.ofVideo(v2.id)), resolved.map { it.mediaId })
        // Tìm lại cùng câu → dùng kết quả đã có (đỡ tốn mạng trên xe).
        lib.search("nhạc bolero")
        assertEquals(1, source.searches)
    }

    @Test
    fun resolveRestoresKnownTitleFromBareId() = runBlocking {
        val lib = library(FakeSource(home = FeedPage(listOf(FeedEntry.Video(v1)), null)))
        lib.children(CarLibrary.HOME)
        val bare = MediaItem.Builder().setMediaId(MediaIds.ofVideo(v1.id)).build()
        val resolved = lib.resolve(listOf(bare)).single()
        assertEquals("Video aaaaaaaaaaa", resolved.mediaMetadata.title.toString())
        assertNotNull(lib.item(MediaIds.ofVideo(v1.id)))
        assertNull(lib.item("v:zzzzzzzzzzz"))
    }

    @Test
    fun mediaIdsAreParsedSafely() {
        assertEquals("aaaaaaaaaaa", MediaIds.videoOf(MediaItem.Builder().setMediaId("v:aaaaaaaaaaa").build())?.id)
        assertEquals("dQw4w9WgXcQ", MediaIds.videoOf(MediaItem.Builder().setMediaId("https://youtu.be/dQw4w9WgXcQ").build())?.id)
        assertNull(MediaIds.videoOf(MediaItem.Builder().setMediaId("home").build()))
        assertNull(MediaIds.videoOf(MediaItem.Builder().setMediaId("v:../../etc").build()))
    }

    @Test
    fun pagingForAndroidAuto() {
        val list = (1..25).toList()
        assertEquals((1..10).toList(), PlaybackService.paged(list, 0, 10))
        assertEquals((21..25).toList(), PlaybackService.paged(list, 2, 10))
        assertEquals(emptyList<Int>(), PlaybackService.paged(list, 5, 10))
        assertEquals(list, PlaybackService.paged(list, 0, Int.MAX_VALUE))
    }

    // ---------------------------------------------------------------- Ảnh cho xe

    @Test
    fun artworkUrisOnlyForYoutubeImages() {
        val url = "https://i.ytimg.com/vi/aaaaaaaaaaa/hqdefault.jpg?sqp=abc&rs=xyz"
        val uri = ArtworkProvider.uriFor(app, url)!!
        assertEquals(url, ArtworkProvider.decode(uri))
        assertEquals("https://yt3.ggpht.com/a.jpg", ArtworkProvider.decode(ArtworkProvider.uriFor(app, "//yt3.ggpht.com/a.jpg")!!))
        assertNull(ArtworkProvider.uriFor(app, "https://evil.example.com/a.jpg"))
        assertNull(ArtworkProvider.uriFor(app, "https://i.ytimg.com.evil.com/a.jpg"))
        assertNull(ArtworkProvider.uriFor(app, "file:///data/data/x"))
        assertNull(ArtworkProvider.uriFor(app, null))
        // Link giả mạo gửi thẳng vào provider cũng bị từ chối.
        val forged = Uri.parse("content://${ArtworkProvider.authority(app)}/i/" +
            android.util.Base64.encodeToString("https://evil.example.com/x".toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        assertNull(ArtworkProvider.decode(forged))
    }

    // ---------------------------------------------------------------- Trình phát cho MediaSession

    @Test
    fun sessionPlayerRoutesCarRequestsToController() {
        val exo = ExoPlayer.Builder(app).build()
        val played = mutableListOf<String>()
        val session = SessionPlayer(exo) { played += it.id }
        val item = MediaItem.Builder()
            .setMediaId(MediaIds.ofVideo(v1.id))
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Bài hát").build())
            .build()
        session.setMediaItem(item)
        session.prepare() // chưa có nguồn phát: không được báo "hết video"
        session.play()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(v1.id), played)
        assertEquals(0, exo.mediaItemCount) // nguồn phát do PlaybackController dựng, không phải link của xe
        assertTrue(exo.playbackState != Player.STATE_ENDED)
        session.release()
    }

    // ---------------------------------------------------------------- Màn hình điện thoại / ô tô

    @Test
    fun videoTrackStaysOnWhileAnyScreenShowsIt() {
        PlaybackController.player // tạo trình phát
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_PHONE, true)
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR, true)
        assertTrue(PlaybackController.carScreenActive.value)
        // Tắt màn hình điện thoại khi đang chiếu trên xe: vẫn phải có hình.
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_PHONE, false)
        assertTrue(PlaybackController.isVideoTrackEnabled())
        // Xe chạy → Android Auto đóng màn hình xem: chỉ phát tiếng.
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR, false)
        assertFalse(PlaybackController.carScreenActive.value)
        assertFalse(PlaybackController.isVideoTrackEnabled())
    }

    @Test
    fun carActivityLifecycleHandsVideoBackToPhone() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        ActivityScenario.launch(MainActivity::class.java).use { phone ->
            phone.moveToState(Lifecycle.State.RESUMED)
            ActivityScenario.launch(CarActivity::class.java).use { car ->
                car.moveToState(Lifecycle.State.RESUMED)
                assertTrue(PlaybackController.carScreenActive.value)
                car.moveToState(Lifecycle.State.CREATED) // xe chạy
                assertFalse(PlaybackController.carScreenActive.value)
                assertTrue(PlaybackController.isVideoTrackEnabled()) // điện thoại vẫn đang mở
                car.moveToState(Lifecycle.State.RESUMED)
            }
        }
    }

    @Test
    fun manifestDeclaresAndroidAutoEntryPoints() {
        val pm = app.packageManager
        val appInfo = pm.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA)
        assertTrue(appInfo.metaData.containsKey("com.google.android.gms.car.application"))
        val carLauncher = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory("android.intent.category.CAR_LAUNCHER").setPackage(app.packageName)
        assertEquals(CarActivity::class.java.name, pm.queryIntentActivities(carLauncher, 0).single().activityInfo.name)
        val browse = android.content.Intent("android.media.browse.MediaBrowserService").setPackage(app.packageName)
        assertEquals(PlaybackService::class.java.name, pm.queryIntentServices(browse, 0).single().serviceInfo.name)
        // Màn hình xe không được hiện thêm biểu tượng trên điện thoại.
        val launcher = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER).setPackage(app.packageName)
        assertEquals(listOf(MainActivity::class.java.name), pm.queryIntentActivities(launcher, 0).map { it.activityInfo.name })
    }
}
