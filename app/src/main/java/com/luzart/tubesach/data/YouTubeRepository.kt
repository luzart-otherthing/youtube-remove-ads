package com.luzart.tubesach.data

import android.util.Xml
import com.luzart.tubesach.account.AccountInfo
import com.luzart.tubesach.innertube.InnerTube
import com.luzart.tubesach.innertube.Parser
import com.luzart.tubesach.innertube.PlayerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.xmlpull.v1.XmlPullParser
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Nơi lấy mọi dữ liệu YouTube.
 * - Trang chủ, tìm kiếm, trang xem, kênh, Shorts: gọi thẳng InnerTube (nhanh, chữ tiếng Việt y như YouTube).
 * - Link phát: máy khách visionOS (1 yêu cầu); dự phòng bằng NewPipeExtractor nếu YouTube thay đổi.
 * - Bình luận, gợi ý tìm kiếm: NewPipeExtractor.
 */
object YouTubeRepository {

    lateinit var innerTube: InnerTube
        private set
    private lateinit var http: OkHttpClient

    fun init(innerTube: InnerTube, http: OkHttpClient) {
        this.innerTube = innerTube
        this.http = http
    }

    private val youtube get() = ServiceList.YouTube
    private val vietnamese = Localization("vi")
    private val vietnam = ContentCountry("VN")

    /** Danh sách phát "20 video thịnh hành tại Việt Nam" trên bảng xếp hạng YouTube. */
    private const val TRENDING_VN = "VLOLAK5uy_lEos0zuYBvGC9C0FSGG3pZ6gO4a82P6zg"
    private const val TOP_MUSIC_VN = "VLPL4fGSI1pDJn57DkisEwlIpcs9FAt5yudJ"

    /** Gọi mạng và phân tích JSON (có thể vài trăm KB) ở luồng nền. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    // ------------------------------------------------------------ Trang chủ

    suspend fun home(): FeedPage = io { Parser.browseGrid(innerTube.browse(browseId = "FEwhat_to_watch")) }

    suspend fun feedContinuation(token: String): FeedPage = io { Parser.continuation(innerTube.browse(continuation = token)) }

    suspend fun trendingVietnam(): FeedPage = io { Parser.browseGrid(innerTube.browse(browseId = TRENDING_VN)) }

    suspend fun topMusicVietnam(): FeedPage = io { Parser.browseGrid(innerTube.browse(browseId = TOP_MUSIC_VN)) }

    // ------------------------------------------------------------ Tìm kiếm

    enum class SearchFilter(val label: String, val params: String?) {
        ALL("Tất cả", null),
        VIDEOS("Video", "EgIQAQ%3D%3D"),
        LIVE("Trực tiếp", "EgJAAQ%3D%3D"),
        THIS_WEEK("Tuần này", "EgIIAw%3D%3D"),
        CHANNELS("Kênh", "EgIQAg%3D%3D"),
    }

    suspend fun search(query: String, filter: SearchFilter = SearchFilter.ALL): FeedPage =
        try {
            io { Parser.search(innerTube.search(query = query, params = filter.params)) }.also {
                if (it.entries.isEmpty()) throw IllegalStateException("empty")
            }
        } catch (e: Exception) {
            if (filter != SearchFilter.ALL && filter != SearchFilter.VIDEOS) throw e
            newPipeSearch(query) // dự phòng
        }

    suspend fun searchMore(token: String): FeedPage = io { Parser.search(innerTube.search(continuation = token)) }

    private suspend fun newPipeSearch(query: String): FeedPage = withContext(Dispatchers.IO) {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val extractor = youtube.getSearchExtractor(handler).apply {
            forceLocalization(vietnamese)
            forceContentCountry(vietnam)
            fetchPage()
        }
        val info = SearchInfo.getInfo(extractor)
        FeedPage(info.relatedItems.filterIsInstance<StreamInfoItem>().map { FeedEntry.Video(VideoItem.from(it)) }, null)
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching {
            youtube.suggestionExtractor.apply {
                forceLocalization(vietnamese)
                forceContentCountry(vietnam)
            }.suggestionList(query)
        }.getOrDefault(emptyList()).take(10)
    }

    // ------------------------------------------------------------ Trang xem

    suspend fun watch(videoId: String): WatchInfo = io { Parser.watch(innerTube.next(videoId = videoId), videoId) }

    suspend fun relatedMore(token: String): FeedPage = io { Parser.continuation(innerTube.next(continuation = token)) }

    suspend fun player(videoId: String): PlayerData = io { PlayerData.parse(innerTube.player(videoId), videoId) }

    /** Dự phòng: lấy link phát qua NewPipeExtractor. */
    suspend fun streamInfo(url: String): StreamInfo = withContext(Dispatchers.IO) {
        val extractor = youtube.getStreamExtractor(url).apply {
            forceLocalization(vietnamese)
            forceContentCountry(vietnam)
            fetchPage()
        }
        StreamInfo.getInfo(extractor)
    }

    // ------------------------------------------------------------ Bình luận

    class CommentsSession internal constructor(internal val extractor: CommentsExtractor, internal var next: Page?) {
        val hasMore get() = Page.isValid(next)
    }

    suspend fun comments(videoUrl: String): Pair<List<CommentItem>, CommentsSession> = withContext(Dispatchers.IO) {
        val extractor = youtube.getCommentsExtractor(videoUrl).apply {
            forceLocalization(vietnamese)
            forceContentCountry(vietnam)
            fetchPage()
        }
        val page = extractor.initialPage
        page.items.map(::comment) to CommentsSession(extractor, page.nextPage)
    }

    suspend fun moreComments(session: CommentsSession): List<CommentItem> = withContext(Dispatchers.IO) {
        val p = session.next ?: return@withContext emptyList()
        val page = session.extractor.getPage(p)
        session.next = page.nextPage
        page.items.map(::comment)
    }

    private fun comment(c: CommentsInfoItem) = CommentItem(
        author = c.uploaderName.orEmpty(),
        avatar = VideoItem.bestThumbnail(c.uploaderAvatars, 68),
        text = htmlToText(c.commentText?.content.orEmpty()),
        time = c.textualUploadDate,
        likes = c.textualLikeCount?.takeIf { it.isNotBlank() && it != "0" },
        replies = c.replyCount.coerceAtLeast(0),
        pinned = c.isPinned,
    )

    private fun htmlToText(html: String): String = html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    // ------------------------------------------------------------ Kênh

    suspend fun channel(id: String): ChannelPage = io { Parser.channel(innerTube.browse(browseId = id), id) }

    suspend fun channelVideos(id: String, params: String?): FeedPage =
        io { Parser.browseGrid(innerTube.browse(browseId = id, params = params ?: "EgZ2aWRlb3PyBgQKAjoA")) }

    // ------------------------------------------------------------ Shorts

    suspend fun shortsSequence(sequenceParams: String?, continuation: String? = null): Pair<List<String>, String?> =
        io { Parser.reelSequence(innerTube.reelSequence(sequenceParams = sequenceParams, continuation = continuation)) }

    /** Lấy một ít Shorts để bắt đầu (khi trang chủ chưa có kệ Shorts). */
    suspend fun shortsSeed(): List<ShortItem> {
        val page = runCatching { search("shorts việt nam") }.getOrNull() ?: return emptyList()
        return page.entries.filterIsInstance<FeedEntry.ShortsShelf>().flatMap { it.shorts }
    }

    // ------------------------------------------------------------ Tài khoản Google (thử nghiệm)

    val signedIn: Boolean get() = innerTube.signedIn

    suspend fun accountInfo(): AccountInfo? = io { Parser.accountInfo(innerTube.accountMenu()) }

    /** Các kênh tài khoản đã đăng ký (tối đa ~10 trang). */
    suspend fun accountChannels(): List<ChannelRef> = io {
        val out = LinkedHashMap<String, ChannelRef>()
        var (list, token) = Parser.accountChannels(innerTube.browse(browseId = "FEchannels"))
        list.forEach { out[it.id] = it }
        var pages = 0
        while (token != null && pages++ < 10) {
            val r = Parser.accountChannels(innerTube.browse(continuation = token))
            r.first.forEach { out[it.id] = it }
            token = r.second
        }
        out.values.toList()
    }

    /** Video mới từ các kênh đã đăng ký của tài khoản (giống tab "Kênh đăng ký" của YouTube). */
    suspend fun accountSubscriptionFeed(): FeedPage = io { Parser.browseGrid(innerTube.browse(browseId = "FEsubscriptions")) }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) {
        io { innerTube.subscribe(channelId, subscribed) }
    }

    /** [action]: "like", "dislike" hoặc "removelike". */
    suspend fun rate(videoId: String, action: String) {
        io { innerTube.rate(videoId, action) }
    }

    private val createParams = HashMap<String, String>()

    /** Đăng bình luận bằng tài khoản. Ném lỗi nếu YouTube không cho phép (tắt bình luận…). */
    suspend fun postComment(videoId: String, text: String) = io {
        val params = createParams[videoId] ?: run {
            val token = Parser.commentsToken(innerTube.next(videoId = videoId))
                ?: throw IllegalStateException("Video này đã tắt bình luận")
            Parser.createCommentParams(innerTube.next(continuation = token))
                ?: throw IllegalStateException("Không bình luận được (chưa đăng nhập hoặc video đã tắt bình luận)")
        }.also { createParams[videoId] = it }
        val r = innerTube.createComment(params, text)
        val status = findStatus(r)
        if (status != null && status != "STATUS_SUCCEEDED") throw IllegalStateException("YouTube không nhận bình luận")
    }

    private fun findStatus(json: org.json.JSONObject): String? =
        json.optJSONArray("actionResults")?.optJSONObject(0)?.optString("status")?.ifEmpty { null }

    /** Link thống kê lượt xem gắn với tài khoản (để ghi lịch sử xem). */
    suspend fun accountTracking(videoId: String): Pair<String?, String?> = io {
        val j = innerTube.webPlayer(videoId).optJSONObject("playbackTracking")
        j?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.ifEmpty { null } to
            j?.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.ifEmpty { null }
    }

    // ------------------------------------------------------------ Kênh đăng ký (RSS)

    /** Video mới của các kênh đã đăng ký, lấy qua RSS công khai của YouTube (không cần đăng nhập). */
    suspend fun subscriptionFeed(channels: List<ChannelRef>): List<VideoItem> = coroutineScope {
        val limit = Semaphore(6)
        channels.map { ch ->
            async(Dispatchers.IO) { limit.withPermit { runCatching { rss(ch) }.getOrDefault(emptyList()) } }
        }.awaitAll().flatten().sortedByDescending { it.second }.map { it.first }
    }

    private fun rss(channel: ChannelRef): List<Pair<VideoItem, Long>> {
        val req = Request.Builder().url("https://www.youtube.com/feeds/videos.xml?channel_id=${channel.id}").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(r.body!!.charStream())
            val out = mutableListOf<Pair<VideoItem, Long>>()
            var id: String? = null
            var title: String? = null
            var published: String? = null
            var thumb: String? = null
            var views = -1L
            var inEntry = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> { inEntry = true; id = null; title = null; published = null; thumb = null; views = -1 }
                        "yt:videoId" -> if (inEntry) id = parser.nextText()
                        "title" -> if (inEntry) title = parser.nextText()
                        "published" -> if (inEntry) published = parser.nextText()
                        "media:thumbnail" -> if (inEntry) thumb = parser.getAttributeValue(null, "url")
                        "media:statistics" -> if (inEntry) views = parser.getAttributeValue(null, "views")?.toLongOrNull() ?: -1
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                        inEntry = false
                        val vid = id
                        if (vid != null) {
                            val time = runCatching { OffsetDateTime.parse(published) }.getOrNull()
                            out += VideoItem(
                                url = "https://www.youtube.com/watch?v=$vid",
                                title = title.orEmpty(),
                                uploader = channel.name,
                                thumbnail = thumb ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg",
                                durationSec = 0,
                                viewCount = views,
                                uploadedText = time?.let(::relativeTime),
                                channelId = channel.id,
                                channelAvatar = channel.avatar,
                                viewText = if (views >= 0) formatViewsShort(views) else null,
                            ) to (time?.toInstant()?.toEpochMilli() ?: 0L)
                        }
                    }
                }
            }
            return out
        }
    }

    fun relativeTime(t: OffsetDateTime): String {
        val d = Duration.between(t, OffsetDateTime.now()).coerceAtLeast(Duration.ZERO)
        val minutes = d.toMinutes()
        return when {
            minutes < 1 -> "Vừa xong"
            minutes < 60 -> "$minutes phút trước"
            minutes < 60 * 24 -> "${minutes / 60} giờ trước"
            minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)} ngày trước"
            minutes < 60 * 24 * 30 -> "${minutes / (60 * 24 * 7)} tuần trước"
            minutes < 60 * 24 * 365 -> "${minutes / (60 * 24 * 30)} tháng trước"
            else -> "${minutes / (60 * 24 * 365)} năm trước"
        }
    }

    /** Định dạng lượt xem kiểu YouTube Việt Nam: "999 lượt xem", "12 N lượt xem", "1,2 Tr lượt xem". */
    fun formatViewsShort(views: Long, suffix: String = "lượt xem"): String = "${formatCount(views)} $suffix"

    fun formatCount(n: Long): String {
        fun fmt(v: Double): String =
            if (v < 10) String.format(java.util.Locale.forLanguageTag("vi"), "%.1f", v).removeSuffix(",0")
            else v.toLong().toString()
        return when {
            n >= 1_000_000_000 -> "${fmt(n / 1e9)} T"
            n >= 1_000_000 -> "${fmt(n / 1e6)} Tr"
            n >= 1_000 -> "${fmt(n / 1e3)} N"
            else -> n.toString()
        }
    }
}
