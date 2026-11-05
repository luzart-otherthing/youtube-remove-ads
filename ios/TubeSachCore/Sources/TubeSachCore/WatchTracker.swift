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

    public init(innerTube: InnerTube) {
        self.innerTube = innerTube
    }

    public func begin(_ player: PlayerData, startPosition: Double) {
        data = player
        cpn = Self.randomCpn()
        startedAt = Int64(max(0, startPosition))
        reportedPlayback = false
        lastWatchtime = -1
    }

    public func clear() { data = nil }

    /// Gọi định kỳ khi đang phát (vị trí tính bằng giây).
    public func onProgress(position: Double, enabled: Bool) {
        guard let d = data, enabled else { return }
        let pos = Int64(max(0, position))
        var urls: [String] = []
        if !reportedPlayback {
            reportedPlayback = true
            if let u = d.playbackTrackingURL {
                urls.append(Self.withParams(u, ["ver": "2", "cpn": cpn, "cmt": String(pos)]))
            }
        }
        // Báo thời lượng đã xem sau 30 giây, rồi mỗi 2 phút một lần.
        let watched = pos - startedAt
        let due = (lastWatchtime < 0 && watched >= 30) || (lastWatchtime >= 0 && pos - lastWatchtime >= 120)
        if due {
            lastWatchtime = pos
            if let u = d.watchtimeTrackingURL {
                urls.append(Self.withParams(u, [
                    "ver": "2", "cpn": cpn, "cmt": String(pos),
                    "st": String(startedAt), "et": String(pos), "state": "playing",
                ]))
            }
        }
        guard !urls.isEmpty else { return }
        let tube = innerTube
        Task.detached {
            for u in urls { await tube.ping(u) }
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
