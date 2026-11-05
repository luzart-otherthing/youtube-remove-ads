package com.luzart.tubesach.tv

import android.app.Application
import android.content.Context
import android.content.Intent
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.InnerTube
import com.luzart.tubesach.net.OkHttpDownloader
import com.luzart.tubesach.player.CodecSupport
import com.luzart.tubesach.player.PlaybackController
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class TvApp : Application(), SingletonImageLoader.Factory {

    lateinit var store: LocalStore
        private set

    lateinit var http: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        http = OkHttpDownloader.createClient()
        NewPipe.init(OkHttpDownloader(http), Localization("vi"), ContentCountry("VN"))
        store = LocalStore(this)
        val innerTube = InnerTube(http, loadVisitor = { store.visitorData }, saveVisitor = { store.visitorData = it })
        YouTubeRepository.init(innerTube, http)
        PlaybackController.sessionActivityIntent = { ctx ->
            Intent(ctx, TvActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Trên TV: rời app là dừng phát (giống YouTube trên TV), không chạy dịch vụ nền.
        PlaybackController.init(this, store, http, useBackgroundService = false)
        // Chất lượng cao nhất TV làm được: VP9 tới 2K / 4K / 8K nếu chip giải mã bằng phần cứng hỗ trợ.
        PlaybackController.streamPolicy = CodecSupport.tvPolicy()
        PlaybackController.ignoreDisplaySize = true
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            // Nhiều TV chỉ có 1,5–2 GB RAM: giới hạn bộ nhớ đệm ảnh, tắt hiệu ứng mờ dần cho mượt.
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .crossfade(false)
            .build()

    companion object {
        fun of(context: Context) = context.applicationContext as TvApp
    }
}
