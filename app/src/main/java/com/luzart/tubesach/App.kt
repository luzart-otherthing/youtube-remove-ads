package com.luzart.tubesach

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.innertube.InnerTube
import com.luzart.tubesach.net.OkHttpDownloader
import com.luzart.tubesach.player.PlaybackController
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class App : Application(), SingletonImageLoader.Factory {

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
        PlaybackController.init(this, store)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .crossfade(true)
            .build()

    companion object {
        fun of(context: Context) = context.applicationContext as App
    }
}
