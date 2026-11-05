import Foundation

/// Đọc dữ liệu trả về từ InnerTube thành các mục hiển thị.
/// Hỗ trợ cả dạng cũ (videoRenderer) lẫn dạng mới (lockupViewModel) mà YouTube đang dùng.
public enum Parser {

    // MARK: - Các mục đơn lẻ

    static func lockup(_ l: JSON) -> VideoItem? {
        let type = l["contentType"]?.stringValue ?? ""
        guard let md = l.o("metadata", "lockupMetadataViewModel"),
              let title = textOf(md["title"]) else { return nil }
        let isVideo = type == "LOCKUP_CONTENT_TYPE_VIDEO"
        let watch = findFirst(l["rendererContext"], "watchEndpoint")
        let contentId = l["contentId"]?.stringValue ?? ""
        guard let videoId = isVideo ? (contentId.isEmpty ? nil : contentId) : watch?.s("videoId") else { return nil }

        let image = l["contentImage"]
        let thumbVm = image?.o("thumbnailViewModel") ?? image?.o("collectionThumbnailViewModel", "primaryThumbnail", "thumbnailViewModel")
        let thumb = bestImage(thumbVm?.a("image", "sources"))
        var durationText: String?
        var live = false
        var badge: String?
        for b in findAll(thumbVm?["overlays"], "thumbnailBadgeViewModel") {
            let style = b["badgeStyle"]?.stringValue ?? ""
            let text = b["text"]?.stringValue ?? ""
            if style.contains("LIVE") || text.uppercased() == "TRỰC TIẾP" || text.uppercased() == "LIVE" {
                live = true
            } else if text.range(of: "^\\d+(:\\d{2})+$", options: .regularExpression) != nil {
                durationText = text
            } else if !text.isEmpty && !isVideo {
                badge = text
            }
        }

        let rows = md.a("metadata", "contentMetadataViewModel", "metadataRows") ?? []
        var parts = rows.map { $0.a("metadataParts") ?? [] }
        // Trong trang kênh chỉ có một hàng "116 N lượt xem • 4 tháng trước" (không có tên kênh).
        if parts.count == 1, let only = parts.first?.first, findFirst(only, "browseEndpoint") == nil,
           looksLikeViews(textOf(only["text"])) {
            parts.insert([], at: 0)
        }
        let firstPart = parts.first?.first
        let channelName = textOf(firstPart?["text"]) ?? ""
        let channelId = findFirst(firstPart, "browseEndpoint")?.s("browseId")
            ?? findFirst(md["image"], "browseEndpoint")?.s("browseId")
        let secondRow = (parts.count > 1 ? parts[1] : []).compactMap { textOf($0["text"]) }
        let viewText = secondRow.first
        let published = secondRow.count > 1 ? secondRow[1] : nil
        let avatar = bestImage(findFirst(md["image"], "avatarViewModel")?.a("image", "sources"), target: 68)

        if let viewText, viewText.contains("đang xem") || viewText.contains("watching") { live = true }
        return VideoItem(
            id: videoId,
            title: title,
            uploader: channelName,
            thumbnail: thumb,
            durationSec: Format.parseDuration(durationText),
            uploadedText: published,
            isLive: live,
            channelId: (channelId?.hasPrefix("UC") == true) ? channelId : nil,
            channelAvatar: avatar,
            viewText: viewText,
            durationText: durationText,
            badge: badge ?? (isVideo ? nil : "Danh sách kết hợp")
        )
    }

    private static func looksLikeViews(_ text: String?) -> Bool {
        guard let t = text?.lowercased() else { return false }
        return t.contains("lượt xem") || t.contains("đang xem") || t.contains("người xem") || t.contains("view") || t.contains("watching")
    }

    static func videoRenderer(_ v: JSON) -> VideoItem? {
        guard let id = v.s("videoId"),
              let title = textOf(v["title"]) ?? textOf(v["headline"]) else { return nil }
        let owner = v.o("ownerText") ?? v.o("shortBylineText") ?? v.o("longBylineText")
        let badges = v.a("badges") ?? []
        let live = badges.contains { $0.s("metadataBadgeRenderer", "style")?.contains("LIVE") == true }
            || (v.a("thumbnailOverlays") ?? []).contains { $0.s("thumbnailOverlayTimeStatusRenderer", "style") == "LIVE" }
        let lengthText = textOf(v["lengthText"])
        let avatar = bestImage(
            v.a("channelThumbnailSupportedRenderers", "channelThumbnailWithLinkRenderer", "thumbnail", "thumbnails")
                ?? v.a("channelThumbnail", "thumbnails")
                ?? findFirst(v["avatar"], "avatarViewModel")?.a("image", "sources"),
            target: 68
        )
        let badge = badges.lazy.compactMap { $0.s("metadataBadgeRenderer", "label") }.first
        return VideoItem(
            id: id,
            title: title,
            uploader: textOf(owner) ?? "",
            thumbnail: bestImage(v.a("thumbnail", "thumbnails")),
            durationSec: Format.parseDuration(lengthText),
            uploadedText: textOf(v["publishedTimeText"]),
            isLive: live,
            channelId: findFirst(owner, "browseEndpoint")?.s("browseId"),
            channelAvatar: avatar,
            viewText: textOf(v["shortViewCountText"]) ?? textOf(v["viewCountText"]),
            durationText: lengthText,
            badge: badge
        )
    }

    static func shortOf(_ item: JSON) -> ShortItem? {
        if let s = item.o("shortsLockupViewModel") {
            let reel = findFirst(s["onTap"], "reelWatchEndpoint")
            var id = reel?.s("videoId")
            if id == nil, let entity = s["entityId"]?.stringValue {
                let trimmed = entity.hasPrefix("shorts-shelf-item-") ? String(entity.dropFirst("shorts-shelf-item-".count)) : entity
                if trimmed.count == 11 { id = trimmed }
            }
            guard let id else { return nil }
            return ShortItem(
                id: id,
                title: textOf(s.o("overlayMetadata", "primaryText")) ?? "",
                viewText: textOf(s.o("overlayMetadata", "secondaryText")),
                thumbnail: bestImage(s.a("thumbnailViewModel", "thumbnailViewModel", "image", "sources"), target: 405),
                sequenceParams: reel?.s("sequenceParams")
            )
        }
        if let r = item.o("reelItemRenderer") {
            guard let id = r.s("videoId") else { return nil }
            return ShortItem(
                id: id,
                title: textOf(r["headline"]) ?? "",
                viewText: textOf(r["viewCountText"]),
                thumbnail: bestImage(r.a("thumbnail", "thumbnails"), target: 405),
                sequenceParams: r.s("navigationEndpoint", "reelWatchEndpoint", "sequenceParams")
            )
        }
        return nil
    }

    static func channelRenderer(_ c: JSON) -> ChannelRef? {
        guard let id = c.s("channelId") else { return nil }
        // YouTube hiện để @handle trong subscriberCountText và số người đăng ký trong videoCountText.
        let sub = [textOf(c["subscriberCountText"]), textOf(c["videoCountText"])].compactMap { $0 }.joined(separator: " • ")
        return ChannelRef(
            id: id,
            name: textOf(c["title"]) ?? "",
            avatar: bestImage(c.a("thumbnail", "thumbnails"), target: 88),
            subtitle: sub.isEmpty ? nil : sub
        )
    }

    // MARK: - Danh sách

    private static func tokenOf(_ cont: JSON?) -> String? {
        findFirst(cont, "continuationCommand")?.s("token") ?? findFirst(cont, "nextContinuationData")?.s("continuation")
    }

    /// Đọc một mảng các mục (lưới trang chủ, kết quả tìm kiếm, video liên quan…).
    static func parseItems(_ items: [JSON]?, into out: inout [FeedEntry]) -> String? {
        var continuation: String?
        var strayShorts: [ShortItem] = []
        for item in items ?? [] {
            continuation = parseOne(item, &out, &strayShorts) ?? continuation
        }
        if !strayShorts.isEmpty { out.append(.shortsShelf(title: "Shorts", shorts: strayShorts)) }
        return continuation
    }

    static func parseItems(_ items: [JSON]?) -> ([FeedEntry], String?) {
        var out: [FeedEntry] = []
        let token = parseItems(items, into: &out)
        return (out, token)
    }

    private static func parseOne(_ item: JSON, _ out: inout [FeedEntry], _ strayShorts: inout [ShortItem]) -> String? {
        if let r = item.o("richItemRenderer") {
            guard let content = r.o("content") else { return nil }
            return parseOne(content, &out, &strayShorts)
        }
        if let r = item.o("richSectionRenderer") {
            if let shelf = r.o("content", "richShelfRenderer") {
                let title = textOf(shelf["title"]) ?? ""
                var inner: [FeedEntry] = []
                var shorts: [ShortItem] = []
                for c in shelf.a("contents") ?? [] { _ = parseOne(c, &inner, &shorts) }
                if !shorts.isEmpty { out.append(.shortsShelf(title: title.isEmpty ? "Shorts" : title, shorts: shorts)) }
                let videos = inner.compactMap(\.video)
                if !videos.isEmpty { out.append(.shelf(title: title, videos: videos)) }
            }
            return nil
        }
        if let c = item.o("continuationItemRenderer") { return tokenOf(c) }
        if let r = item.o("itemSectionRenderer") {
            var token: String?
            for c in r.a("contents") ?? [] { token = parseOne(c, &out, &strayShorts) ?? token }
            return token
        }
        for key in ["videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "playlistVideoRenderer"] {
            if let v = item.o(key) {
                if let video = videoRenderer(v) { out.append(.video(video)) }
                return nil
            }
        }
        if let l = item.o("lockupViewModel") {
            if let video = lockup(l) { out.append(.video(video)) }
            return nil
        }
        if item.has("shortsLockupViewModel") || item.has("reelItemRenderer") {
            if let s = shortOf(item) { strayShorts.append(s) }
            return nil
        }
        if let g = item.o("gridShelfViewModel") {
            let shorts = (g.a("contents") ?? []).compactMap(shortOf)
            if !shorts.isEmpty {
                out.append(.shortsShelf(title: textOf(g.o("header", "sectionHeaderViewModel", "headline")) ?? "Shorts", shorts: shorts))
            }
            return nil
        }
        if let r = item.o("reelShelfRenderer") {
            let shorts = (r.a("items") ?? []).compactMap(shortOf)
            if !shorts.isEmpty { out.append(.shortsShelf(title: textOf(r["title"]) ?? "Shorts", shorts: shorts)) }
            return nil
        }
        if let c = item.o("channelRenderer") {
            if let ch = channelRenderer(c) { out.append(.channel(ch)) }
            return nil
        }
        if let s = item.o("shelfRenderer") {
            let list = s.a("content", "verticalListRenderer", "items") ?? s.a("content", "horizontalListRenderer", "items")
            var inner: [FeedEntry] = []
            for c in list ?? [] { _ = parseOne(c, &inner, &strayShorts) }
            let videos = inner.compactMap(\.video)
            if !videos.isEmpty { out.append(.shelf(title: textOf(s["title"]) ?? "", videos: videos)) }
            return nil
        }
        return nil
    }

    private static func chipsOf(_ root: JSON?) -> [Chip] {
        var seen = Set<String>()
        var out: [Chip] = []
        for c in findAll(root, "chipCloudChipRenderer") {
            guard let title = textOf(c["text"]), !seen.contains(title) else { continue }
            seen.insert(title)
            out.append(Chip(title: title, token: findFirst(c["navigationEndpoint"], "continuationCommand")?.s("token")))
        }
        return out
    }

    /// Trang chủ (FEwhat_to_watch) hoặc trang kênh/danh sách phát dạng lưới.
    public static func browseGrid(_ json: JSON) -> FeedPage {
        if let grid = findFirst(json["contents"], "richGridRenderer") {
            let (entries, cont) = parseItems(grid.a("contents"))
            return FeedPage(entries: entries, continuation: cont, chips: chipsOf(grid["header"]))
        }
        // Danh sách phát (playlistVideoListRenderer) hoặc sectionList
        if let pl = findFirst(json["contents"], "playlistVideoListRenderer") {
            let (entries, cont) = parseItems(pl.a("contents"))
            return FeedPage(entries: entries, continuation: cont)
        }
        let section = findFirst(json["contents"], "sectionListRenderer")
        let (entries, cont) = parseItems(section?.a("contents"))
        return FeedPage(entries: entries, continuation: cont)
    }

    /// Trang tiếp theo khi cuộn xuống, hoặc kết quả khi bấm một chip.
    public static func continuation(_ json: JSON) -> FeedPage {
        let actions = json.a("onResponseReceivedActions") ?? json.a("onResponseReceivedCommands") ?? []
        var out: [FeedEntry] = []
        var token: String?
        var chips: [Chip] = []
        for a in actions {
            let items = a.a("appendContinuationItemsAction", "continuationItems")
                ?? a.a("reloadContinuationItemsCommand", "continuationItems")
            if a.has("reloadContinuationItemsCommand"),
               a.s("reloadContinuationItemsCommand", "slot") == "RELOAD_CONTINUATION_SLOT_HEADER" {
                chips = chipsOf(items.map { JSON.array($0) })
                continue
            }
            token = parseItems(items, into: &out) ?? token
        }
        return FeedPage(entries: out, continuation: token, chips: chips)
    }

    public static func search(_ json: JSON) -> FeedPage {
        if json.has("onResponseReceivedCommands") || json.has("onResponseReceivedActions") { return continuation(json) }
        let section = findFirst(json["contents"], "sectionListRenderer")
        let (entries, cont) = parseItems(section?.a("contents"))
        return FeedPage(entries: entries, continuation: cont)
    }

    // MARK: - Trang xem video

    public static func watch(_ json: JSON, videoId: String) -> WatchInfo {
        let results = json.a("contents", "twoColumnWatchNextResults", "results", "results", "contents") ?? []
        let primary = results.lazy.compactMap { $0.o("videoPrimaryInfoRenderer") }.first
        let secondary = results.lazy.compactMap { $0.o("videoSecondaryInfoRenderer") }.first
        let owner = secondary?.o("owner", "videoOwnerRenderer")

        let viewCount = primary?.o("viewCount", "videoViewCountRenderer")
        let likeVm = findFirst(primary?["videoActions"], "likeButtonViewModel")
        let likeText = findFirst(likeVm, "buttonViewModel")?.s("title")

        let channelName = textOf(owner?["title"]) ?? textOf(owner?["attributedTitle"])
        let channelId = findAll(owner, "browseEndpoint").lazy.compactMap { e -> String? in
            guard let id = e.s("browseId"), id.hasPrefix("UC") else { return nil }
            return id
        }.first
        let avatar = bestImage(owner?.a("thumbnail", "thumbnails"), target: 88)
            ?? bestImage(findFirst(owner?["avatarStack"], "avatarViewModel")?.a("image", "sources"), target: 88)

        let secondaryResults = json.a("contents", "twoColumnWatchNextResults", "secondaryResults", "secondaryResults", "results")
        let (relatedEntries, relatedCont) = parseItems(secondaryResults)

        var commentCount: String?
        var commentsToken: String?
        for p in json.a("engagementPanels") ?? [] {
            guard let r = p.o("engagementPanelSectionListRenderer") else { continue }
            if r["panelIdentifier"]?.stringValue == "engagement-panel-comments-section" {
                commentCount = textOf(r.o("header", "engagementPanelTitleHeaderRenderer", "contextualInfo"))
                commentsToken = commentsToken ?? findFirst(r["content"], "continuationCommand")?.s("token")
            }
        }
        for c in results {
            if let isr = c.o("itemSectionRenderer"), isr["sectionIdentifier"]?.stringValue == "comment-item-section" {
                commentsToken = findFirst(isr, "continuationCommand")?.s("token") ?? commentsToken
            }
        }

        var related: [VideoItem] = []
        for e in relatedEntries {
            switch e {
            case .video(let v): related.append(v)
            case .shelf(_, let videos): related.append(contentsOf: videos)
            default: break
            }
        }

        let description = secondary?.o("attributedDescription")?["content"]?.stringValue
            ?? textOf(secondary?["description"]) ?? ""
        return WatchInfo(
            videoId: videoId,
            title: textOf(primary?["title"]) ?? "",
            viewText: textOf(viewCount?["viewCount"]),
            shortViewText: textOf(viewCount?["shortViewCount"]),
            dateText: textOf(primary?["dateText"]),
            relativeDateText: textOf(primary?["relativeDateText"]),
            likeText: likeText,
            description: description,
            channel: channelId.map { ChannelRef(id: $0, name: channelName ?? "", avatar: avatar) },
            subscriberText: textOf(owner?["subscriberCountText"]),
            commentCountText: commentCount,
            isLive: viewCount?["isLive"]?.boolValue == true,
            related: related,
            relatedContinuation: relatedCont,
            commentsToken: commentsToken
        )
    }

    // MARK: - Bình luận

    /// Đọc trang bình luận (phản hồi của /next với continuation bình luận).
    /// Nội dung bình luận nằm ở frameworkUpdates (commentEntityPayload), tra theo commentKey.
    public static func comments(_ json: JSON) -> CommentsPage {
        var entities: [String: JSON] = [:]
        for m in json.a("frameworkUpdates", "entityBatchUpdate", "mutations") ?? [] {
            if let key = m.s("entityKey"), let p = m.o("payload", "commentEntityPayload") { entities[key] = p }
        }
        let actions = (json.a("onResponseReceivedEndpoints") ?? []) + (json.a("onResponseReceivedActions") ?? [])
        var items: [CommentItem] = []
        var token: String?
        for a in actions {
            let list = a.a("reloadContinuationItemsCommand", "continuationItems")
                ?? a.a("appendContinuationItemsAction", "continuationItems") ?? []
            for item in list {
                if let thread = item.o("commentThreadRenderer") {
                    let vm = thread.o("commentViewModel", "commentViewModel") ?? findFirst(thread, "commentViewModel")
                    guard let vm, let key = vm.s("commentKey"), let p = entities[key] else { continue }
                    let props = p["properties"]
                    let likes = p.s("toolbar", "likeCountNotliked")?.trimmingCharacters(in: .whitespaces)
                    let replies = Int(p.s("toolbar", "replyCount") ?? "") ?? 0
                    items.append(CommentItem(
                        id: props?.s("commentId") ?? key,
                        author: p.s("author", "displayName") ?? "",
                        avatar: fixUrl(p.s("author", "avatarThumbnailUrl")),
                        text: (props?.s("content", "content") ?? "").replacingOccurrences(of: "\u{00A0}", with: " "),
                        time: props?.s("publishedTime"),
                        likes: (likes?.isEmpty == false && likes != "0") ? likes : nil,
                        replies: replies,
                        pinned: vm.has("pinnedText")
                    ))
                } else if let c = item.o("continuationItemRenderer") {
                    token = tokenOf(c) ?? token
                }
            }
        }
        return CommentsPage(items: items, continuation: token)
    }

    // MARK: - Kênh

    public static func channel(_ json: JSON, id: String) -> ChannelPage {
        let header = findFirst(json["header"], "pageHeaderViewModel")
        let legacy = json.o("header", "c4TabbedHeaderRenderer")
        let name = textOf(header?.o("title", "dynamicTextViewModel", "text"))
            ?? textOf(findFirst(header?["title"], "text"))
            ?? textOf(legacy?["title"])
            ?? json.s("metadata", "channelMetadataRenderer", "title") ?? ""
        let rows = (findFirst(header?["metadata"], "contentMetadataViewModel")?.a("metadataRows") ?? [])
            .flatMap { $0.a("metadataParts") ?? [] }
            .compactMap { textOf($0["text"]) }
        let avatar = bestImage(findFirst(header?["image"], "avatarViewModel")?.a("image", "sources"), target: 176)
            ?? bestImage(legacy?.a("avatar", "thumbnails"), target: 176)
            ?? bestImage(json.a("metadata", "channelMetadataRenderer", "avatar", "thumbnails"), target: 176)
        let banner = bestImage(findFirst(header?["banner"], "imageBannerViewModel")?.a("image", "sources"), target: 1280)
            ?? bestImage(legacy?.a("banner", "thumbnails"), target: 1280)

        var videosParams: String?
        var liveParams: String?
        for t in json.a("contents", "twoColumnBrowseResultsRenderer", "tabs") ?? [] {
            guard let tab = t.o("tabRenderer") else { continue }
            let url = tab.s("endpoint", "commandMetadata", "webCommandMetadata", "url") ?? ""
            let params = tab.s("endpoint", "browseEndpoint", "params")
            if url.hasSuffix("/videos") { videosParams = params } else if url.hasSuffix("/streams") { liveParams = params }
        }
        return ChannelPage(
            channel: ChannelRef(id: id, name: name, avatar: avatar),
            handle: rows.first { $0.hasPrefix("@") },
            subscriberText: rows.first { $0.contains("đăng ký") || $0.contains("subscriber") },
            videoCountText: rows.first { $0.contains("video") && !$0.hasPrefix("@") },
            banner: banner,
            description: json.s("metadata", "channelMetadataRenderer", "description"),
            videosParams: videosParams,
            liveParams: liveParams
        )
    }

    // MARK: - Shorts

    public static func reelSequence(_ json: JSON) -> (ids: [String], continuation: String?) {
        let ids = (json.a("entries") ?? []).compactMap { findFirst($0, "reelWatchEndpoint")?.s("videoId") }
        let token = findFirst(json["continuationEndpoint"], "continuationCommand")?.s("token")
        return (ids, token)
    }
}
