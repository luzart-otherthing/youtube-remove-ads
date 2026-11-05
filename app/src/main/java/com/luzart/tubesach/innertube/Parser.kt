package com.luzart.tubesach.innertube

import com.luzart.tubesach.data.ChannelPage
import com.luzart.tubesach.data.ChannelRef
import com.luzart.tubesach.data.Chip
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.FeedPage
import com.luzart.tubesach.data.ShortItem
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.WatchInfo
import org.json.JSONArray
import org.json.JSONObject

/**
 * Đọc dữ liệu trả về từ InnerTube thành các mục hiển thị.
 * Hỗ trợ cả dạng cũ (videoRenderer) lẫn dạng mới (lockupViewModel) mà YouTube đang dùng.
 */
object Parser {

    // ------------------------------------------------------------ Các mục đơn lẻ

    fun lockup(l: JSONObject): VideoItem? {
        val type = l.optString("contentType")
        val md = l.o("metadata", "lockupMetadataViewModel") ?: return null
        val title = textOf(md.opt("title")) ?: return null
        val isVideo = type == "LOCKUP_CONTENT_TYPE_VIDEO"
        val watch = findFirst(l.opt("rendererContext"), "watchEndpoint")
        val videoId = if (isVideo) l.optString("contentId").ifEmpty { null } else watch?.s("videoId")
        videoId ?: return null

        val image = l.optJSONObject("contentImage")
        val thumbVm = image?.optJSONObject("thumbnailViewModel")
            ?: image?.o("collectionThumbnailViewModel", "primaryThumbnail", "thumbnailViewModel")
        val thumb = bestImage(thumbVm?.a("image", "sources"))
        val badges = findAll(thumbVm?.opt("overlays"), "thumbnailBadgeViewModel")
        var durationText: String? = null
        var live = false
        var badge: String? = null
        for (b in badges) {
            val style = b.optString("badgeStyle")
            val text = b.optString("text")
            when {
                style.contains("LIVE") || text.equals("TRỰC TIẾP", true) || text.equals("LIVE", true) -> live = true
                Regex("^\\d+(:\\d{2})+$").matches(text) -> durationText = text
                text.isNotEmpty() && !isVideo -> badge = text
            }
        }

        val rows = md.a("metadata", "contentMetadataViewModel", "metadataRows").objects()
        val parts = rows.map { row -> row.a("metadataParts").objects() }
        val firstPart = parts.firstOrNull()?.firstOrNull()
        val channelName = textOf(firstPart?.opt("text")).orEmpty()
        val channelId = findFirst(firstPart, "browseEndpoint")?.s("browseId")
            ?: findFirst(md.opt("image"), "browseEndpoint")?.s("browseId")
        val secondRow = parts.getOrNull(1).orEmpty().mapNotNull { textOf(it.opt("text")) }
        val viewText = secondRow.getOrNull(0)
        val published = secondRow.getOrNull(1)
        val avatar = bestImage(findFirst(md.opt("image"), "avatarViewModel")?.a("image", "sources"), 68)

        if (viewText != null && (viewText.contains("đang xem") || viewText.contains("watching"))) live = true
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            uploader = channelName,
            thumbnail = thumb,
            durationSec = VideoItem.parseDuration(durationText),
            uploadedText = published,
            isLive = live,
            channelId = channelId?.takeIf { it.startsWith("UC") },
            channelAvatar = avatar,
            viewText = viewText,
            durationText = durationText,
            badge = badge ?: if (!isVideo) "Danh sách kết hợp" else null,
        )
    }

    fun videoRenderer(v: JSONObject): VideoItem? {
        val id = v.s("videoId") ?: return null
        val title = textOf(v.opt("title")) ?: textOf(v.opt("headline")) ?: return null
        val owner = v.optJSONObject("ownerText") ?: v.optJSONObject("shortBylineText") ?: v.optJSONObject("longBylineText")
        val live = v.a("badges").objects().any { it.s("metadataBadgeRenderer", "style")?.contains("LIVE") == true } ||
            v.a("thumbnailOverlays").objects().any { it.s("thumbnailOverlayTimeStatusRenderer", "style") == "LIVE" }
        val lengthText = textOf(v.opt("lengthText"))
        val avatar = bestImage(
            v.a("channelThumbnailSupportedRenderers", "channelThumbnailWithLinkRenderer", "thumbnail", "thumbnails")
                ?: v.a("channelThumbnail", "thumbnails")
                ?: findFirst(v.opt("avatar"), "avatarViewModel")?.a("image", "sources"),
            68,
        )
        val badge = v.a("badges").objects().firstNotNullOfOrNull { it.s("metadataBadgeRenderer", "label") }
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = title,
            uploader = textOf(owner).orEmpty(),
            thumbnail = bestImage(v.a("thumbnail", "thumbnails")),
            durationSec = VideoItem.parseDuration(lengthText),
            uploadedText = textOf(v.opt("publishedTimeText")),
            isLive = live,
            channelId = findFirst(owner, "browseEndpoint")?.s("browseId"),
            channelAvatar = avatar,
            viewText = textOf(v.opt("shortViewCountText")) ?: textOf(v.opt("viewCountText")),
            durationText = lengthText,
            badge = badge,
        )
    }

    fun shortOf(item: JSONObject): ShortItem? {
        item.optJSONObject("shortsLockupViewModel")?.let { s ->
            val reel = findFirst(s.opt("onTap"), "reelWatchEndpoint")
            val id = reel?.s("videoId") ?: s.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.length == 11 }
            id ?: return null
            return ShortItem(
                id = id,
                title = textOf(s.o("overlayMetadata", "primaryText")).orEmpty(),
                viewText = textOf(s.o("overlayMetadata", "secondaryText")),
                thumbnail = bestImage(s.a("thumbnailViewModel", "thumbnailViewModel", "image", "sources"), 405),
                sequenceParams = reel?.s("sequenceParams"),
            )
        }
        item.optJSONObject("reelItemRenderer")?.let { r ->
            val id = r.s("videoId") ?: return null
            return ShortItem(
                id = id,
                title = textOf(r.opt("headline")).orEmpty(),
                viewText = textOf(r.opt("viewCountText")),
                thumbnail = bestImage(r.a("thumbnail", "thumbnails"), 405),
                sequenceParams = r.s("navigationEndpoint", "reelWatchEndpoint", "sequenceParams"),
            )
        }
        return null
    }

    fun channelRenderer(c: JSONObject): ChannelRef? {
        val id = c.s("channelId") ?: return null
        // YouTube hiện để @handle trong subscriberCountText và số người đăng ký trong videoCountText.
        val sub = listOfNotNull(textOf(c.opt("subscriberCountText")), textOf(c.opt("videoCountText")))
            .joinToString(" • ")
        return ChannelRef(
            id = id,
            name = textOf(c.opt("title")).orEmpty(),
            avatar = bestImage(c.a("thumbnail", "thumbnails"), 88),
            subtitle = sub.ifEmpty { null },
        )
    }

    // ------------------------------------------------------------ Danh sách

    private fun tokenOf(cont: JSONObject?): String? =
        findFirst(cont, "continuationCommand")?.s("token")
            ?: findFirst(cont, "nextContinuationData")?.s("continuation")

    /** Đọc một mảng các mục (lưới trang chủ, kết quả tìm kiếm, video liên quan…). */
    fun parseItems(items: JSONArray?, out: MutableList<FeedEntry> = mutableListOf()): Pair<MutableList<FeedEntry>, String?> {
        var continuation: String? = null
        val strayShorts = mutableListOf<ShortItem>()
        for (item in items.objects()) {
            continuation = parseOne(item, out, strayShorts) ?: continuation
        }
        if (strayShorts.isNotEmpty()) out.add(FeedEntry.ShortsShelf("Shorts", strayShorts))
        return out to continuation
    }

    private fun parseOne(item: JSONObject, out: MutableList<FeedEntry>, strayShorts: MutableList<ShortItem>): String? {
        item.optJSONObject("richItemRenderer")?.let { r ->
            return r.optJSONObject("content")?.let { parseOne(it, out, strayShorts) }
        }
        item.optJSONObject("richSectionRenderer")?.let { r ->
            r.o("content", "richShelfRenderer")?.let { shelf ->
                val title = textOf(shelf.opt("title")) ?: ""
                val inner = mutableListOf<FeedEntry>()
                val shorts = mutableListOf<ShortItem>()
                shelf.a("contents").objects().forEach { parseOne(it, inner, shorts) }
                if (shorts.isNotEmpty()) out.add(FeedEntry.ShortsShelf(title.ifEmpty { "Shorts" }, shorts))
                val videos = inner.filterIsInstance<FeedEntry.Video>().map { it.video }
                if (videos.isNotEmpty()) out.add(FeedEntry.Shelf(title, videos))
            }
            return null
        }
        item.optJSONObject("continuationItemRenderer")?.let { return tokenOf(it) }
        item.optJSONObject("itemSectionRenderer")?.let { r ->
            var token: String? = null
            r.a("contents").objects().forEach { token = parseOne(it, out, strayShorts) ?: token }
            return token
        }
        item.optJSONObject("videoRenderer")?.let { v -> videoRenderer(v)?.let { out.add(FeedEntry.Video(it)) }; return null }
        item.optJSONObject("compactVideoRenderer")?.let { v -> videoRenderer(v)?.let { out.add(FeedEntry.Video(it)) }; return null }
        item.optJSONObject("gridVideoRenderer")?.let { v -> videoRenderer(v)?.let { out.add(FeedEntry.Video(it)) }; return null }
        item.optJSONObject("playlistVideoRenderer")?.let { v -> videoRenderer(v)?.let { out.add(FeedEntry.Video(it)) }; return null }
        item.optJSONObject("lockupViewModel")?.let { l -> lockup(l)?.let { out.add(FeedEntry.Video(it)) }; return null }
        if (item.has("shortsLockupViewModel") || item.has("reelItemRenderer")) {
            shortOf(item)?.let { strayShorts.add(it) }
            return null
        }
        item.optJSONObject("gridShelfViewModel")?.let { g ->
            val shorts = g.a("contents").objects().mapNotNull { shortOf(it) }
            if (shorts.isNotEmpty()) {
                out.add(FeedEntry.ShortsShelf(textOf(g.o("header", "sectionHeaderViewModel", "headline")) ?: "Shorts", shorts))
            }
            return null
        }
        item.optJSONObject("reelShelfRenderer")?.let { r ->
            val shorts = r.a("items").objects().mapNotNull { shortOf(it) }
            if (shorts.isNotEmpty()) out.add(FeedEntry.ShortsShelf(textOf(r.opt("title")) ?: "Shorts", shorts))
            return null
        }
        item.optJSONObject("channelRenderer")?.let { c -> channelRenderer(c)?.let { out.add(FeedEntry.Channel(it)) }; return null }
        item.optJSONObject("shelfRenderer")?.let { s ->
            val list = s.a("content", "verticalListRenderer", "items") ?: s.a("content", "horizontalListRenderer", "items")
            val inner = mutableListOf<FeedEntry>()
            list.objects().forEach { parseOne(it, inner, strayShorts) }
            val videos = inner.filterIsInstance<FeedEntry.Video>().map { it.video }
            if (videos.isNotEmpty()) out.add(FeedEntry.Shelf(textOf(s.opt("title")).orEmpty(), videos))
            return null
        }
        return null
    }

    private fun chipsOf(root: Any?): List<Chip> =
        findAll(root, "chipCloudChipRenderer").mapNotNull { c ->
            val title = textOf(c.opt("text")) ?: return@mapNotNull null
            Chip(title, findFirst(c.opt("navigationEndpoint"), "continuationCommand")?.s("token"))
        }.distinctBy { it.title }

    /** Trang chủ (FEwhat_to_watch) hoặc trang kênh/danh sách phát dạng lưới. */
    fun browseGrid(json: JSONObject): FeedPage {
        val grid = findFirst(json.opt("contents"), "richGridRenderer")
        if (grid != null) {
            val (entries, cont) = parseItems(grid.optJSONArray("contents"))
            return FeedPage(entries, cont, chipsOf(grid.opt("header")))
        }
        // Danh sách phát (playlistVideoListRenderer) hoặc sectionList
        val pl = findFirst(json.opt("contents"), "playlistVideoListRenderer")
        if (pl != null) {
            val (entries, cont) = parseItems(pl.optJSONArray("contents"))
            return FeedPage(entries, cont)
        }
        val section = findFirst(json.opt("contents"), "sectionListRenderer")
        val (entries, cont) = parseItems(section?.optJSONArray("contents"))
        return FeedPage(entries, cont)
    }

    /** Trang tiếp theo khi cuộn xuống, hoặc kết quả khi bấm một chip. */
    fun continuation(json: JSONObject): FeedPage {
        // Trang chủ/tìm kiếm dùng "Actions"/"Commands"; video liên quan (/next) dùng "Endpoints".
        val actions = (
            json.optJSONArray("onResponseReceivedActions")
                ?: json.optJSONArray("onResponseReceivedCommands")
                ?: json.optJSONArray("onResponseReceivedEndpoints")
            ).objects()
        val out = mutableListOf<FeedEntry>()
        var token: String? = null
        var chips = emptyList<Chip>()
        for (a in actions) {
            val items = a.a("appendContinuationItemsAction", "continuationItems")
                ?: a.a("reloadContinuationItemsCommand", "continuationItems")
            if (a.has("reloadContinuationItemsCommand") && a.s("reloadContinuationItemsCommand", "slot") == "RELOAD_CONTINUATION_SLOT_HEADER") {
                chips = chipsOf(items)
                continue
            }
            val (_, t) = parseItems(items, out)
            token = t ?: token
        }
        return FeedPage(out, token, chips)
    }

    fun search(json: JSONObject): FeedPage {
        if (json.has("onResponseReceivedCommands") || json.has("onResponseReceivedActions")) return continuation(json)
        val section = findFirst(json.opt("contents"), "sectionListRenderer")
        val (entries, cont) = parseItems(section?.optJSONArray("contents"))
        return FeedPage(entries, cont)
    }

    // ------------------------------------------------------------ Trang xem video

    fun watch(json: JSONObject, videoId: String): WatchInfo {
        val results = json.a("contents", "twoColumnWatchNextResults", "results", "results", "contents")
        val primary = results.objects().firstNotNullOfOrNull { it.optJSONObject("videoPrimaryInfoRenderer") }
        val secondary = results.objects().firstNotNullOfOrNull { it.optJSONObject("videoSecondaryInfoRenderer") }
        val owner = secondary?.o("owner", "videoOwnerRenderer")

        val viewCount = primary?.o("viewCount", "videoViewCountRenderer")
        val likeVm = findFirst(primary?.opt("videoActions"), "likeButtonViewModel")
        val likeText = findFirst(likeVm, "buttonViewModel")?.s("title")

        val channelName = textOf(owner?.opt("title")) ?: textOf(owner?.opt("attributedTitle"))
        val channelId = findAll(owner, "browseEndpoint").firstNotNullOfOrNull { it.s("browseId")?.takeIf { id -> id.startsWith("UC") } }
        val avatar = bestImage(owner?.a("thumbnail", "thumbnails"), 88)
            ?: bestImage(findFirst(owner?.opt("avatarStack"), "avatarViewModel")?.a("image", "sources"), 88)

        val secondaryResults = json.a("contents", "twoColumnWatchNextResults", "secondaryResults", "secondaryResults", "results")
        val (relatedEntries, relatedCont) = parseItems(secondaryResults)

        var commentCount: String? = null
        for (p in json.a("engagementPanels").objects()) {
            val r = p.optJSONObject("engagementPanelSectionListRenderer") ?: continue
            if (r.optString("panelIdentifier") == "engagement-panel-comments-section") {
                commentCount = textOf(r.o("header", "engagementPanelTitleHeaderRenderer", "contextualInfo"))
            }
        }

        val isLive = viewCount?.optBoolean("isLive") == true
        return WatchInfo(
            videoId = videoId,
            title = textOf(primary?.opt("title")).orEmpty(),
            viewText = textOf(viewCount?.opt("viewCount")),
            shortViewText = textOf(viewCount?.opt("shortViewCount")),
            dateText = textOf(primary?.opt("dateText")),
            relativeDateText = textOf(primary?.opt("relativeDateText")),
            likeText = likeText,
            description = secondary?.o("attributedDescription")?.optString("content")
                ?: textOf(secondary?.opt("description")).orEmpty(),
            channel = if (channelId != null) ChannelRef(channelId, channelName.orEmpty(), avatar) else null,
            subscriberText = textOf(owner?.opt("subscriberCountText")),
            commentCountText = commentCount,
            isLive = isLive,
            related = relatedEntries.flatMap {
                when (it) {
                    is FeedEntry.Video -> listOf(it.video)
                    is FeedEntry.Shelf -> it.videos
                    else -> emptyList()
                }
            },
            relatedContinuation = relatedCont,
        )
    }

    // ------------------------------------------------------------ Kênh

    fun channel(json: JSONObject, id: String): ChannelPage {
        val header = findFirst(json.opt("header"), "pageHeaderViewModel")
        val legacy = json.o("header", "c4TabbedHeaderRenderer")
        val name = textOf(header?.o("title", "dynamicTextViewModel", "text"))
            ?: textOf(findFirst(header?.opt("title"), "text"))
            ?: textOf(legacy?.opt("title"))
            ?: json.s("metadata", "channelMetadataRenderer", "title").orEmpty()
        val rows = findFirst(header?.opt("metadata"), "contentMetadataViewModel")?.a("metadataRows").objects()
            .flatMap { it.a("metadataParts").objects() }
            .mapNotNull { textOf(it.opt("text")) }
        val avatar = bestImage(findFirst(header?.opt("image"), "avatarViewModel")?.a("image", "sources"), 176)
            ?: bestImage(legacy?.a("avatar", "thumbnails"), 176)
            ?: bestImage(json.a("metadata", "channelMetadataRenderer", "avatar", "thumbnails"), 176)
        val banner = bestImage(findFirst(header?.opt("banner"), "imageBannerViewModel")?.a("image", "sources"), 1280)
            ?: bestImage(legacy?.a("banner", "thumbnails"), 1280)

        var videosParams: String? = null
        var liveParams: String? = null
        for (t in json.a("contents", "twoColumnBrowseResultsRenderer", "tabs").objects()) {
            val tab = t.optJSONObject("tabRenderer") ?: continue
            val url = tab.s("endpoint", "commandMetadata", "webCommandMetadata", "url").orEmpty()
            val params = tab.s("endpoint", "browseEndpoint", "params")
            when {
                url.endsWith("/videos") -> videosParams = params
                url.endsWith("/streams") -> liveParams = params
            }
        }
        return ChannelPage(
            channel = ChannelRef(id, name, avatar),
            handle = rows.firstOrNull { it.startsWith("@") },
            subscriberText = rows.firstOrNull { it.contains("đăng ký") || it.contains("subscriber") },
            videoCountText = rows.firstOrNull { it.contains("video") && !it.startsWith("@") },
            banner = banner,
            description = json.s("metadata", "channelMetadataRenderer", "description"),
            videosParams = videosParams,
            liveParams = liveParams,
        )
    }

    // ------------------------------------------------------------ Shorts

    fun reelSequence(json: JSONObject): Pair<List<String>, String?> {
        val ids = json.a("entries").objects().mapNotNull { findFirst(it, "reelWatchEndpoint")?.s("videoId") }
        val token = findFirst(json.opt("continuationEndpoint"), "continuationCommand")?.s("token")
        return ids to token
    }
}
