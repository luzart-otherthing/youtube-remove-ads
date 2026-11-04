package com.luzart.tubesach.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Lấy dữ liệu từ YouTube bằng NewPipeExtractor: tìm kiếm, gợi ý, thông tin video.
 *
 * Vì không dùng trình phát của YouTube nên không bao giờ tải quảng cáo.
 */
object YouTubeRepository {

    private val youtube get() = ServiceList.YouTube

    // NewPipeExtractor mặc định chỉ dùng en-GB. Ép sang tiếng Việt để tiêu đề không bị dịch.
    private val vietnamese = Localization("vi")
    private val vietnam = ContentCountry("VN")

    class SearchSession internal constructor(
        val query: String,
        internal val extractor: SearchExtractor,
        internal var nextPage: Page?,
    ) {
        val hasMore: Boolean get() = Page.isValid(nextPage)
    }

    suspend fun search(query: String): Pair<List<VideoItem>, SearchSession> =
        withContext(Dispatchers.IO) {
            val handler = youtube.searchQHFactory.fromQuery(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                "",
            )
            val extractor = youtube.getSearchExtractor(handler).apply {
                forceLocalization(vietnamese)
                forceContentCountry(vietnam)
                fetchPage()
            }
            val info = SearchInfo.getInfo(extractor)
            val items = info.relatedItems.filterIsInstance<StreamInfoItem>().map(VideoItem::from)
            items to SearchSession(query, extractor, info.nextPage)
        }

    suspend fun loadMore(session: SearchSession): List<VideoItem> = withContext(Dispatchers.IO) {
        val page = session.nextPage
        if (!Page.isValid(page)) return@withContext emptyList()
        val result = session.extractor.getPage(page)
        session.nextPage = result.nextPage
        result.items.filterIsInstance<StreamInfoItem>().map(VideoItem::from)
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            youtube.suggestionExtractor.apply {
                forceLocalization(vietnamese)
                forceContentCountry(vietnam)
            }.suggestionList(query)
        }.getOrDefault(emptyList()).take(8)
    }

    private data class CachedInfo(val info: StreamInfo, val time: Long)

    private val cache = object : LinkedHashMap<String, CachedInfo>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedInfo>?) =
            size > 20
    }

    /** Link phát của YouTube hết hạn sau vài giờ → chỉ dùng bộ nhớ đệm trong 1 giờ. */
    private const val CACHE_MS = 60 * 60 * 1000L

    suspend fun streamInfo(url: String, forceRefresh: Boolean = false): StreamInfo =
        withContext(Dispatchers.IO) {
            val key = VideoItem.videoIdOf(url) ?: url
            if (!forceRefresh) {
                synchronized(cache) { cache[key] }?.let {
                    if (System.currentTimeMillis() - it.time < CACHE_MS) return@withContext it.info
                }
            }
            val extractor = youtube.getStreamExtractor(url).apply {
                forceLocalization(vietnamese)
                forceContentCountry(vietnam)
                fetchPage()
            }
            val info = StreamInfo.getInfo(extractor)
            synchronized(cache) { cache[key] = CachedInfo(info, System.currentTimeMillis()) }
            info
        }

    fun relatedOf(info: StreamInfo): List<VideoItem> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().map(VideoItem::from)
}
