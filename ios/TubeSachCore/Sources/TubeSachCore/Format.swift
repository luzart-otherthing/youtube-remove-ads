import Foundation

/// Các hàm định dạng kiểu YouTube Việt Nam và đọc link YouTube.
public enum Format {

    /// "1:02:03" → 3723 giây. Trả về 0 nếu không đọc được.
    public static func parseDuration(_ text: String?) -> Int64 {
        guard let text = text?.trimmingCharacters(in: .whitespaces), !text.isEmpty else { return 0 }
        var total: Int64 = 0
        for part in text.split(separator: ":", omittingEmptySubsequences: false) {
            guard let n = Int64(part) else { return 0 }
            total = total * 60 + n
        }
        return total
    }

    /// 3723 → "1:02:03"; 185 → "3:05".
    public static func duration(_ seconds: Int64) -> String {
        let s = max(0, seconds)
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        if h > 0 { return String(format: "%d:%02d:%02d", Int(h), Int(m), Int(sec)) }
        return String(format: "%d:%02d", Int(m), Int(sec))
    }

    /// Định dạng số kiểu YouTube Việt Nam: "999", "12 N", "1,2 Tr", "2 T".
    public static func count(_ n: Int64) -> String {
        func fmt(_ v: Double) -> String {
            if v < 10 {
                var s = String(format: "%.1f", v).replacingOccurrences(of: ".", with: ",")
                if s.hasSuffix(",0") { s.removeLast(2) }
                return s
            }
            return String(Int64(v))
        }
        if n >= 1_000_000_000 { return "\(fmt(Double(n) / 1e9)) T" }
        if n >= 1_000_000 { return "\(fmt(Double(n) / 1e6)) Tr" }
        if n >= 1_000 { return "\(fmt(Double(n) / 1e3)) N" }
        return String(n)
    }

    public static func views(_ n: Int64, suffix: String = "lượt xem") -> String { "\(count(n)) \(suffix)" }

    /// "3 ngày trước", "2 tuần trước"…
    public static func relativeTime(_ date: Date, now: Date = Date()) -> String {
        let minutes = Int64(max(0, now.timeIntervalSince(date)) / 60)
        switch minutes {
        case ..<1: return "Vừa xong"
        case ..<60: return "\(minutes) phút trước"
        case ..<(60 * 24): return "\(minutes / 60) giờ trước"
        case ..<(60 * 24 * 7): return "\(minutes / (60 * 24)) ngày trước"
        case ..<(60 * 24 * 30): return "\(minutes / (60 * 24 * 7)) tuần trước"
        case ..<(60 * 24 * 365): return "\(minutes / (60 * 24 * 30)) tháng trước"
        default: return "\(minutes / (60 * 24 * 365)) năm trước"
        }
    }

    /// Ước lượng tuổi (giây) từ chữ YouTube như "3 ngày trước", "Đã phát trực tiếp 2 tuần trước".
    public static func approxAge(_ text: String?) -> TimeInterval? {
        guard let text = text?.lowercased(),
              let r = text.range(of: "\\d+\\s*(giây|phút|giờ|ngày|tuần|tháng|năm)", options: .regularExpression) else { return nil }
        let part = String(text[r])
        let digits = part.prefix { $0.isNumber }
        guard let n = Double(digits) else { return nil }
        let unit: Double
        if part.hasSuffix("giây") { unit = 1 }
        else if part.hasSuffix("phút") { unit = 60 }
        else if part.hasSuffix("giờ") { unit = 3600 }
        else if part.hasSuffix("ngày") { unit = 86400 }
        else if part.hasSuffix("tuần") { unit = 7 * 86400 }
        else if part.hasSuffix("tháng") { unit = 30 * 86400 }
        else { unit = 365 * 86400 }
        return n * unit
    }

    private static let idPatterns: [NSRegularExpression] = [
        "[?&]v=([A-Za-z0-9_-]{11})",
        "youtu\\.be/([A-Za-z0-9_-]{11})",
        "/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})",
    ].compactMap { try? NSRegularExpression(pattern: $0) }

    /// Lấy mã video từ một link YouTube bất kỳ.
    public static func videoId(fromURL url: String) -> String? {
        let range = NSRange(url.startIndex..., in: url)
        for re in idPatterns {
            if let m = re.firstMatch(in: url, range: range), let r = Range(m.range(at: 1), in: url) {
                return String(url[r])
            }
        }
        return nil
    }

    /// Tìm link YouTube trong một đoạn văn bản (ví dụ tin nhắn được chia sẻ từ Zalo).
    public static func findYoutubeURL(_ text: String?) -> String? {
        guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let id = videoId(fromURL: text) else { return nil }
        return "https://www.youtube.com/watch?v=\(id)"
    }

    /// Chỉ chấp nhận link có tên miền YouTube (dùng khi người dùng dán link vào ô tìm kiếm).
    public static func youtubeVideoId(inText text: String) -> String? {
        let lower = text.lowercased()
        guard lower.contains("youtu.be/") || lower.contains("youtube.com/") else { return nil }
        return videoId(fromURL: text)
    }

    /// So sánh phiên bản "1.0.12" > "1.0.9".
    public static func isNewer(_ remote: String, than local: String) -> Bool {
        func nums(_ s: String) -> [Int] {
            s.split(whereSeparator: { $0 == "." || $0 == "-" }).compactMap { Int($0) }
        }
        let r = nums(remote), l = nums(local)
        for i in 0..<max(r.count, l.count) {
            let a = i < r.count ? r[i] : 0
            let b = i < l.count ? l[i] : 0
            if a != b { return a > b }
        }
        return false
    }
}
