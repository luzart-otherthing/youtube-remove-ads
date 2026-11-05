import Foundation

/// Một đoạn cần bỏ qua (tài trợ, nhắc đăng ký…).
public struct Segment: Hashable {
    public var category: String
    public var start: Double
    public var end: Double
    public var uuid: String
}

/// Các loại đoạn của SponsorBlock, tên tiếng Việt và màu trên thanh tua.
public enum SegmentCategory: String, CaseIterable, Identifiable {
    case sponsor
    case selfpromo
    case interaction
    case intro
    case outro
    case preview
    case filler
    case musicOfftopic = "music_offtopic"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .sponsor: return "Tài trợ"
        case .selfpromo: return "Tự quảng cáo"
        case .interaction: return "Nhắc tương tác"
        case .intro: return "Đoạn mở đầu"
        case .outro: return "Đoạn kết"
        case .preview: return "Xem trước / tóm tắt"
        case .filler: return "Lạc đề"
        case .musicOfftopic: return "Đoạn không phải nhạc"
        }
    }

    public var detail: String {
        switch self {
        case .sponsor: return "Đoạn quảng cáo của nhà tài trợ do người làm video tự chèn"
        case .selfpromo: return "Giới thiệu sản phẩm, áo, khoá học… của chính kênh"
        case .interaction: return "Nhắc bấm thích, đăng ký, bật chuông"
        case .intro: return "Hoạt cảnh mở đầu, giới thiệu lặp lại"
        case .outro: return "Lời chào cuối, màn hình kết thúc"
        case .preview: return "Đoạn tóm tắt hoặc xem trước nội dung"
        case .filler: return "Đoạn nói chuyện ngoài lề, không liên quan"
        case .musicOfftopic: return "Trong video ca nhạc: đoạn nói chuyện, không có nhạc"
        }
    }

    /// Màu dạng 0xRRGGBB.
    public var rgb: UInt32 {
        switch self {
        case .sponsor: return 0x00D400
        case .selfpromo: return 0xFFFF00
        case .interaction: return 0xCC00FF
        case .intro: return 0x00FFFF
        case .outro: return 0x0202ED
        case .preview: return 0x008FD6
        case .filler: return 0x7300FF
        case .musicOfftopic: return 0xFF9900
        }
    }

    public var defaultOn: Bool {
        switch self {
        case .sponsor, .selfpromo, .interaction: return true
        default: return false
        }
    }

    public static var defaults: Set<String> { Set(allCases.filter(\.defaultOn).map(\.rawValue)) }
}

/// SponsorBlock (sponsor.ajay.app): cơ sở dữ liệu cộng đồng đánh dấu các đoạn tài trợ trong video.
/// Chỉ gửi 4 ký tự đầu của mã băm SHA-256 → máy chủ không biết bạn đang xem video nào.
public enum SponsorBlock {

    public static func segments(videoId: String, categories: Set<String>) async -> [Segment] {
        guard !categories.isEmpty else { return [] }
        let prefix = String(sha256Hex(videoId).prefix(4))
        var comps = URLComponents(string: "https://sponsor.ajay.app/api/skipSegments/\(prefix)")!
        let cats = "[" + categories.sorted().map { "\"\($0)\"" }.joined(separator: ",") + "]"
        comps.queryItems = [
            URLQueryItem(name: "categories", value: cats),
            URLQueryItem(name: "actionTypes", value: "[\"skip\",\"mute\"]"),
        ]
        guard let url = comps.url?.absoluteString,
              let r = try? await HTTP.get(url, headers: ["User-Agent": "TubeSach-iOS"]),
              r.1 == 200 else { return [] }
        return parse(r.0, videoId: videoId)
    }

    public static func parse(_ data: Data, videoId: String) -> [Segment] {
        guard let json = try? JSON.parse(data), let arr = json.arrayValue else { return [] }
        for v in arr where v["videoID"]?.stringValue == videoId {
            let segs: [Segment] = (v.a("segments") ?? []).compactMap { s in
                guard let range = s.a("segment"), range.count >= 2,
                      let start = range[0].doubleValue, let end = range[1].doubleValue, end > start else { return nil }
                return Segment(category: s["category"]?.stringValue ?? "", start: start, end: end, uuid: s["UUID"]?.stringValue ?? "")
            }
            return segs.sorted { $0.start < $1.start }
        }
        return []
    }

    public static func sha256Hex(_ s: String) -> String {
        SHA256Digest.hash(Array(s.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

/// Return YouTube Dislike: số lượt "không thích" (YouTube đã ẩn con số này từ 2021).
public enum Dislikes {
    public struct Votes {
        public var likes: Int64
        public var dislikes: Int64
    }

    public static func votes(videoId: String) async -> Votes? {
        guard let r = try? await HTTP.get(
            "https://returnyoutubedislikeapi.com/votes?videoId=\(videoId)",
            headers: ["User-Agent": "TubeSach-iOS"]
        ), r.1 == 200, let json = try? JSON.parse(r.0) else { return nil }
        return Votes(likes: json["likes"]?.int64Value ?? 0, dislikes: json["dislikes"]?.int64Value ?? 0)
    }
}

/// SHA-256 viết bằng Swift thuần (CryptoKit không có trên Linux, nơi chạy test).
enum SHA256Digest {
    private static let k: [UInt32] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ]

    static func hash(_ input: [UInt8]) -> [UInt8] {
        var h: [UInt32] = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19]
        var msg = input
        let bitLen = UInt64(input.count) * 8
        msg.append(0x80)
        while msg.count % 64 != 56 { msg.append(0) }
        for i in (0..<8).reversed() { msg.append(UInt8((bitLen >> (UInt64(i) * 8)) & 0xff)) }

        func rotr(_ x: UInt32, _ n: UInt32) -> UInt32 { (x >> n) | (x << (32 - n)) }

        var w = [UInt32](repeating: 0, count: 64)
        for chunk in stride(from: 0, to: msg.count, by: 64) {
            for i in 0..<16 {
                let j = chunk + i * 4
                w[i] = UInt32(msg[j]) << 24 | UInt32(msg[j + 1]) << 16 | UInt32(msg[j + 2]) << 8 | UInt32(msg[j + 3])
            }
            for i in 16..<64 {
                let s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >> 3)
                let s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >> 10)
                w[i] = w[i - 16] &+ s0 &+ w[i - 7] &+ s1
            }
            var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5], g = h[6], hh = h[7]
            for i in 0..<64 {
                let s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
                let ch = (e & f) ^ (~e & g)
                let t1 = hh &+ s1 &+ ch &+ k[i] &+ w[i]
                let s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
                let maj = (a & b) ^ (a & c) ^ (b & c)
                let t2 = s0 &+ maj
                hh = g; g = f; f = e; e = d &+ t1
                d = c; c = b; b = a; a = t1 &+ t2
            }
            h[0] = h[0] &+ a; h[1] = h[1] &+ b; h[2] = h[2] &+ c; h[3] = h[3] &+ d
            h[4] = h[4] &+ e; h[5] = h[5] &+ f; h[6] = h[6] &+ g; h[7] = h[7] &+ hh
        }
        var out: [UInt8] = []
        for v in h {
            out.append(UInt8(v >> 24)); out.append(UInt8((v >> 16) & 0xff))
            out.append(UInt8((v >> 8) & 0xff)); out.append(UInt8(v & 0xff))
        }
        return out
    }
}
