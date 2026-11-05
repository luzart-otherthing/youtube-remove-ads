import Foundation

/// Báo cho YouTube biết "khách ẩn danh" này đã xem video nào (giống trình duyệt chưa đăng nhập).
/// Nhờ đó trang chủ có đề xuất tiếng Việt hợp với sở thích. Có thể tắt trong Cài đặt.
public final class WatchTracker {
    private let innerTube: InnerTube
    private var data: PlayerData?
    private var cpn = ""
    private var startedAt: Int64 = 0
    private var reportedPlayback = false
    private var lastWatchtime: Int64 = -1
    private var playbackURL: String?
    private var watchtimeURL: String?
    private var authed = false

    public init(innerTube: InnerTube) {
        self.innerTube = innerTube
    }

    public func begin(_ player: PlayerData, startPosition: Double) {
        data = player
        playbackURL = player.playbackTrackingURL
        watchtimeURL = player.watchtimeTrackingURL
        authed = false
        cpn = Self.randomCpn()
        startedAt = Int64(max(0, startPosition))
        reportedPlayback = false
        lastWatchtime = -1
    }

    public func clear() { data = nil }

    /// Đã đăng nhập: dùng link thống kê của /player WEB (gắn với tài khoản) để video được ghi
    /// vào lịch sử xem của tài khoản Google.
    public func useAccountURLs(videoId: String, playback: String?, watchtime: String?) {
        guard data?.videoId == videoId, let playback else { return }
        playbackURL = playback
        watchtimeURL = watchtime
        authed = true
        reportedPlayback = false // báo lại lượt xem cho tài khoản (lần trước là ẩn danh)
    }

    /// Gọi định kỳ khi đang phát (vị trí tính bằng giây).
    public func onProgress(position: Double, enabled: Bool) {
        guard data != nil, enabled else { return }
        let pos = Int64(max(0, position))
        var urls: [String] = []
        if !reportedPlayback {
            reportedPlayback = true
            if let u = playbackURL {
                urls.append(Self.withParams(u, ["ver": "2", "cpn": cpn, "cmt": String(pos)]))
            }
        }
        // Báo thời lượng đã xem sau 30 giây, rồi mỗi 2 phút một lần.
        let watched = pos - startedAt
        let due = (lastWatchtime < 0 && watched >= 30) || (lastWatchtime >= 0 && pos - lastWatchtime >= 120)
        if due {
            lastWatchtime = pos
            if let u = watchtimeURL {
                urls.append(Self.withParams(u, [
                    "ver": "2", "cpn": cpn, "cmt": String(pos),
                    "st": String(startedAt), "et": String(pos), "state": "playing",
                ]))
            }
        }
        guard !urls.isEmpty else { return }
        let tube = innerTube
        let withAccount = authed
        Task.detached {
            for u in urls { await tube.ping(u, authed: withAccount) }
        }
    }

    static func withParams(_ url: String, _ params: [String: String]) -> String {
        guard var comps = URLComponents(string: url) else { return url }
        var items = (comps.queryItems ?? []).filter { params[$0.name] == nil }
        for k in params.keys.sorted() { items.append(URLQueryItem(name: k, value: params[k])) }
        comps.queryItems = items
        return comps.url?.absoluteString ?? url
    }

    static func randomCpn() -> String {
        let chars = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_")
        return String((0..<16).map { _ in chars.randomElement()! })
    }
}
