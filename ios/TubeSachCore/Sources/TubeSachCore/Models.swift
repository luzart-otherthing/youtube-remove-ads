import Foundation

/// Một video trong danh sách (trang chủ, tìm kiếm, liên quan, lịch sử…).
/// Các chữ hiển thị (lượt xem, ngày đăng) giữ nguyên câu chữ tiếng Việt do YouTube trả về.
public struct VideoItem: Codable, Hashable, Identifiable {
    /// Mã video 11 ký tự.
    public var id: String
    public var title: String
    public var uploader: String
    public var thumbnail: String?
    public var durationSec: Int64
    public var viewCount: Int64
    public var uploadedText: String?
    public var isLive: Bool
    public var channelId: String?
    public var channelAvatar: String?
    /// Ví dụ "1,2 Tr lượt xem" – lấy nguyên văn từ YouTube.
    public var viewText: String?
    /// Ví dụ "12:34"; dùng khi không có `durationSec`.
    public var durationText: String?
    public var isShort: Bool
    /// Nhãn phụ như "Danh sách kết hợp", "Mới".
    public var badge: String?

    public init(
        id: String,
        title: String,
        uploader: String = "",
        thumbnail: String? = nil,
        durationSec: Int64 = 0,
        viewCount: Int64 = -1,
        uploadedText: String? = nil,
        isLive: Bool = false,
        channelId: String? = nil,
        channelAvatar: String? = nil,
        viewText: String? = nil,
        durationText: String? = nil,
        isShort: Bool = false,
        badge: String? = nil
    ) {
        self.id = id
        self.title = title
        self.uploader = uploader
        self.thumbnail = thumbnail ?? "https://i.ytimg.com/vi/\(id)/hqdefault.jpg"
        self.durationSec = durationSec
        self.viewCount = viewCount
        self.uploadedText = uploadedText
        self.isLive = isLive
        self.channelId = channelId
        self.channelAvatar = channelAvatar
        self.viewText = viewText
        self.durationText = durationText
        self.isShort = isShort
        self.badge = badge
    }

    public var url: String { "https://www.youtube.com/watch?v=\(id)" }

    /// Chữ thời lượng để hiện trên ảnh thu nhỏ ("12:34").
    public var durationLabel: String? {
        if let durationText, !durationText.isEmpty { return durationText }
        return durationSec > 0 ? Format.duration(durationSec) : nil
    }

    /// Giữ lại thông tin đầy đủ hơn khi cập nhật (ví dụ ảnh đại diện kênh).
    public func merged(with old: VideoItem) -> VideoItem {
        var v = self
        v.thumbnail = thumbnail ?? old.thumbnail
        v.channelAvatar = channelAvatar ?? old.channelAvatar
        v.channelId = channelId ?? old.channelId
        v.viewText = viewText ?? old.viewText
        v.uploadedText = uploadedText ?? old.uploadedText
        v.durationSec = durationSec > 0 ? durationSec : old.durationSec
        v.durationText = durationText ?? old.durationText
        v.title = title.trimmingCharacters(in: .whitespaces).isEmpty ? old.title : title
        v.uploader = uploader.trimmingCharacters(in: .whitespaces).isEmpty ? old.uploader : uploader
        return v
    }
}

/// Một video Shorts (dọc).
public struct ShortItem: Codable, Hashable, Identifiable {
    public var id: String
    public var title: String
    public var viewText: String?
    public var thumbnail: String?
    public var sequenceParams: String?

    public init(id: String, title: String, viewText: String? = nil, thumbnail: String? = nil, sequenceParams: String? = nil) {
        self.id = id
        self.title = title
        self.viewText = viewText
        self.thumbnail = thumbnail ?? "https://i.ytimg.com/vi/\(id)/oar2.jpg"
        self.sequenceParams = sequenceParams
    }
}

/// Thông tin gọn của một kênh.
public struct ChannelRef: Codable, Hashable, Identifiable {
    public var id: String
    public var name: String
    public var avatar: String?
    public var subtitle: String?

    public init(id: String, name: String, avatar: String? = nil, subtitle: String? = nil) {
        self.id = id
        self.name = name
        self.avatar = avatar
        self.subtitle = subtitle
    }
}

/// Một khối trong trang chủ / kết quả tìm kiếm.
public enum FeedEntry: Hashable, Identifiable {
    case video(VideoItem)
    case shortsShelf(title: String, shorts: [ShortItem])
    case shelf(title: String, videos: [VideoItem])
    case channel(ChannelRef)

    /// Khoá để bỏ trùng khi nối trang mới.
    public var key: String {
        switch self {
        case .video(let v): return "v:" + v.id
        case .shortsShelf(_, let s): return "s:" + (s.first?.id ?? "")
        case .shelf(let t, let v): return "h:" + t + (v.first?.id ?? "")
        case .channel(let c): return "c:" + c.id
        }
    }

    public var id: String { key }

    public var video: VideoItem? {
        if case .video(let v) = self { return v }
        return nil
    }
}

public struct Chip: Hashable {
    public var title: String
    public var token: String?

    public init(title: String, token: String?) {
        self.title = title
        self.token = token
    }
}

public struct FeedPage {
    public var entries: [FeedEntry]
    public var continuation: String?
    public var chips: [Chip]

    public init(entries: [FeedEntry], continuation: String?, chips: [Chip] = []) {
        self.entries = entries
        self.continuation = continuation
        self.chips = chips
    }

    public var videos: [VideoItem] { entries.compactMap(\.video) }
}

/// Thông tin trang xem video (tiêu đề, kênh, lượt thích, mô tả, video liên quan).
public struct WatchInfo {
    public var videoId: String
    public var title: String
    public var viewText: String?
    public var shortViewText: String?
    public var dateText: String?
    public var relativeDateText: String?
    public var likeText: String?
    public var description: String
    public var channel: ChannelRef?
    public var subscriberText: String?
    public var commentCountText: String?
    public var isLive: Bool
    public var related: [VideoItem]
    public var relatedContinuation: String?
    /// Mã để tải bình luận (gọi lại /next với continuation này).
    public var commentsToken: String?
}

public struct ChannelPage {
    public var channel: ChannelRef
    public var handle: String?
    public var subscriberText: String?
    public var videoCountText: String?
    public var banner: String?
    public var description: String?
    public var videosParams: String?
    public var liveParams: String?
}

public struct CommentItem: Hashable, Identifiable {
    public var id: String
    public var author: String
    public var avatar: String?
    public var text: String
    public var time: String?
    public var likes: String?
    public var replies: Int
    public var pinned: Bool
}

public struct CommentsPage {
    public var items: [CommentItem]
    public var continuation: String?
}
