import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

/// Bộ lọc tìm kiếm giống YouTube.
public enum SearchFilter: String, CaseIterable, Identifiable {
    case all, videos, live, thisWeek, channels

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .all: return "Tất cả"
        case .videos: return "Video"
        case .live: return "Trực tiếp"
        case .thisWeek: return "Tuần này"
        case .channels: return "Kênh"
        }
    }

    var params: String? {
        switch self {
        case .all: return nil
        case .videos: return "EgIQAQ=="
        case .live: return "EgJAAQ=="
        case .thisWeek: return "EgIIAw=="
        case .channels: return "EgIQAg=="
        }
    }
}

/// Nơi lấy mọi dữ liệu YouTube: trang chủ, tìm kiếm, trang xem, bình luận, kênh, Shorts, kênh đăng ký (RSS).
public final class YouTube {
    public let innerTube: InnerTube

    /// Danh sách phát "Thịnh hành tại Việt Nam" và "Nhạc hàng đầu Việt Nam" trên bảng xếp hạng YouTube.
    static let trendingVN = "VLOLAK5uy_lEos0zuYBvGC9C0FSGG3pZ6gO4a82P6zg"
    static let topMusicVN = "VLPL4fGSI1pDJn57DkisEwlIpcs9FAt5yudJ"

    public init(innerTube: InnerTube) {
        self.innerTube = innerTube
    }

    // MARK: Trang chủ

    public func home() async throws -> FeedPage {
        Parser.browseGrid(try await innerTube.browse(browseId: "FEwhat_to_watch"))
    }

    public func feedContinuation(_ token: String) async throws -> FeedPage {
        Parser.continuation(try await innerTube.browse(continuation: token))
    }

    public func trendingVietnam() async throws -> FeedPage {
        Parser.browseGrid(try await innerTube.browse(browseId: Self.trendingVN))
    }

    public func topMusicVietnam() async throws -> FeedPage {
        Parser.browseGrid(try await innerTube.browse(browseId: Self.topMusicVN))
    }

    // MARK: Tìm kiếm

    public func search(_ query: String, filter: SearchFilter = .all) async throws -> FeedPage {
        Parser.search(try await innerTube.search(query: query, params: filter.params))
    }

    public func searchMore(_ token: String) async throws -> FeedPage {
        Parser.search(try await innerTube.search(continuation: token))
    }

    /// Gợi ý khi gõ tìm kiếm (giống ô tìm kiếm của YouTube).
    public func suggestions(_ query: String) async -> [String] {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return [] }
        var comps = URLComponents(string: "https://suggestqueries.google.com/complete/search")!
        comps.queryItems = [
            URLQueryItem(name: "client", value: "firefox"),
            URLQueryItem(name: "ds", value: "yt"),
            URLQueryItem(name: "hl", value: "vi"),
            URLQueryItem(name: "gl", value: "vn"),
            URLQueryItem(name: "q", value: q),
        ]
        guard let url = comps.url?.absoluteString,
              let r = try? await HTTP.get(url, headers: ["User-Agent": InnerTube.webUA]), r.1 == 200 else { return [] }
        return Self.parseSuggestions(r.0)
    }

    static func parseSuggestions(_ data: Data) -> [String] {
        // Máy chủ có thể trả ISO-8859-1 cho một số máy khách; thử UTF-8 trước.
        let json = (try? JSON.parse(data))
            ?? String(data: data, encoding: .isoLatin1).flatMap { try? JSON.parse($0) }
        return Array((json?[1]?.arrayValue ?? []).compactMap(\.rawString).prefix(10))
    }

    // MARK: Trang xem

    public func watch(_ videoId: String) async throws -> WatchInfo {
        Parser.watch(try await innerTube.next(videoId: videoId), videoId: videoId)
    }

    public func relatedMore(_ token: String) async throws -> FeedPage {
        Parser.continuation(try await innerTube.next(continuation: token))
    }

    public func player(_ videoId: String) async throws -> PlayerData {
        try PlayerData.parse(try await innerTube.player(videoId: videoId), videoId: videoId)
    }

    // MARK: Bình luận

    public func comments(token: String) async throws -> CommentsPage {
        Parser.comments(try await innerTube.next(continuation: token))
    }

    // MARK: Kênh

    public func channel(_ id: String) async throws -> ChannelPage {
        Parser.channel(try await innerTube.browse(browseId: id), id: id)
    }

    public func channelVideos(_ id: String, params: String?) async throws -> FeedPage {
        Parser.browseGrid(try await innerTube.browse(browseId: id, params: params ?? "EgZ2aWRlb3PyBgQKAjoA"))
    }

    // MARK: Shorts

    public func shortsSequence(sequenceParams: String?, continuation: String? = nil) async throws -> (ids: [String], continuation: String?) {
        Parser.reelSequence(try await innerTube.reelSequence(sequenceParams: sequenceParams, continuation: continuation))
    }

    /// Lấy một ít Shorts để bắt đầu (khi trang chủ chưa có kệ Shorts).
    public func shortsSeed() async -> [ShortItem] {
        guard let page = try? await search("shorts việt nam") else { return [] }
        return page.entries.flatMap { e -> [ShortItem] in
            if case .shortsShelf(_, let s) = e { return s }
            return []
        }
    }

    // MARK: Kênh đăng ký (RSS)

    /// Video mới của các kênh đã đăng ký, lấy qua RSS công khai của YouTube (không cần đăng nhập).
    public func subscriptionFeed(_ channels: [ChannelRef]) async -> [VideoItem] {
        var all: [(VideoItem, Date)] = []
        // Gọi tối đa 6 kênh cùng lúc.
        for start in stride(from: 0, to: channels.count, by: 6) {
            let batch = channels[start..<min(start + 6, channels.count)]
            await withTaskGroup(of: [(VideoItem, Date)].self) { group in
                for ch in batch {
                    group.addTask { [self] in
                        let items = await Self.rss(ch)
                        return items.isEmpty ? await self.latestUploads(ch) : items
                    }
                }
                for await items in group { all.append(contentsOf: items) }
            }
        }
        return all.sorted { $0.1 > $1.1 }.map(\.0)
    }

    /// Dự phòng khi RSS lỗi (YouTube đôi khi trả 404): đọc tab "Video" của kênh qua InnerTube.
    func latestUploads(_ channel: ChannelRef, now: Date = Date()) async -> [(VideoItem, Date)] {
        guard let page = try? await channelVideos(channel.id, params: nil) else { return [] }
        return page.videos.prefix(10).map { v in
            var video = v
            if video.uploader.isEmpty { video.uploader = channel.name }
            video.channelId = video.channelId ?? channel.id
            video.channelAvatar = video.channelAvatar ?? channel.avatar
            let age = Format.approxAge(v.uploadedText) ?? 365 * 86400
            return (video, now.addingTimeInterval(-age))
        }
    }

    static func rss(_ channel: ChannelRef) async -> [(VideoItem, Date)] {
        guard let r = try? await HTTP.get("https://www.youtube.com/feeds/videos.xml?channel_id=\(channel.id)"),
              r.1 == 200 else { return [] }
        return parseRSS(r.0, channel: channel)
    }

    public static func parseRSS(_ data: Data, channel: ChannelRef, now: Date = Date()) -> [(VideoItem, Date)] {
        let delegate = RSSDelegate()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        _ = parser.parse()
        let iso = ISO8601DateFormatter()
        return delegate.entries.compactMap { e in
            guard let id = e["id"], !id.isEmpty else { return nil }
            let date = e["published"].flatMap { iso.date(from: $0) }
            let views = Int64(e["views"] ?? "") ?? -1
            let video = VideoItem(
                id: id,
                title: e["title"] ?? "",
                uploader: channel.name,
                thumbnail: e["thumb"] ?? "https://i.ytimg.com/vi/\(id)/hqdefault.jpg",
                viewCount: views,
                uploadedText: date.map { Format.relativeTime($0, now: now) },
                channelId: channel.id,
                channelAvatar: channel.avatar,
                viewText: views >= 0 ? Format.views(views) : nil,
                isShort: (e["link"] ?? "").contains("/shorts/")
            )
            return (video, date ?? .distantPast)
        }
    }

    // MARK: Cập nhật

    /// Kiểm tra bản iOS mới trên GitHub Releases (thẻ "ios-v…"). Chỉ chạy được khi kho GitHub để công khai.
    public static func latestIOSRelease(repo: String) async -> (version: String, page: String)? {
        guard let r = try? await HTTP.get(
            "https://api.github.com/repos/\(repo)/releases?per_page=20",
            headers: ["Accept": "application/vnd.github+json", "User-Agent": "TubeSach-iOS"]
        ), r.1 == 200, let json = try? JSON.parse(r.0) else { return nil }
        for rel in json.arrayValue ?? [] {
            guard let tag = rel["tag_name"]?.stringValue, tag.hasPrefix("ios-v"),
                  rel["draft"]?.boolValue != true else { continue }
            return (String(tag.dropFirst("ios-v".count)), rel["html_url"]?.stringValue ?? "")
        }
        return nil
    }
}

/// Đọc RSS của kênh (Atom): mỗi <entry> có yt:videoId, title, published, media:thumbnail, media:statistics.
final class RSSDelegate: NSObject, XMLParserDelegate {
    var entries: [[String: String]] = []
    private var current: [String: String]?
    private var text = ""

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?,
                qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        text = ""
        switch elementName {
        case "entry": current = [:]
        case "media:thumbnail": if current != nil { current?["thumb"] = attributeDict["url"] }
        case "media:statistics": if current != nil { current?["views"] = attributeDict["views"] }
        case "link":
            if current != nil, attributeDict["rel"] == "alternate" { current?["link"] = attributeDict["href"] }
        default: break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        text += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        guard current != nil else { return }
        let value = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch elementName {
        case "yt:videoId": current?["id"] = value
        case "title": current?["title"] = value
        case "published": current?["published"] = value
        case "entry":
            if let c = current { entries.append(c) }
            current = nil
        default: break
        }
        text = ""
    }
}
