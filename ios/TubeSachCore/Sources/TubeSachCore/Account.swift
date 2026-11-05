import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Thông tin tài khoản đang đăng nhập (lấy từ YouTube sau khi đăng nhập).
public struct AccountInfo: Codable, Hashable {
    public var name: String
    public var handle: String?
    public var photo: String?

    public init(name: String, handle: String? = nil, photo: String? = nil) {
        self.name = name
        self.handle = handle
        self.photo = photo
    }
}

/// Phiên đăng nhập Google (THỬ NGHIỆM, KHÔNG KHUYẾN KHÍCH).
///
/// Giữ cookie của youtube.com lấy được sau khi người dùng đăng nhập trên trang của Google
/// (trong WKWebView). Chỉ các yêu cầu API trang web (www.youtube.com) mới mang cookie;
/// link phát video (visionOS/iOS) vẫn ẩn danh.
///
/// Google xoay vòng cookie định kỳ: cookie mới trong phản hồi được cập nhật lại qua `onChange`
/// để app lưu, nhờ vậy phiên đăng nhập không tự hết hạn.
public final class AccountSession {
    public static let origin = "https://www.youtube.com"

    /// Trang đăng nhập Google, xong thì quay về YouTube (bản di động).
    public static let loginURL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&hl=vi"
        + "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26hl%3Dvi%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"

    private let lock = NSLock()
    private var store: [String: String]
    /// Gọi khi cookie thay đổi (để app lưu lại).
    public var onChange: (([String: String]) -> Void)?

    public init(cookies: [String: String]) {
        store = cookies
    }

    public var cookies: [String: String] {
        lock.lock(); defer { lock.unlock() }
        return store
    }

    /// Có cookie đăng nhập (SAPISID) hay chưa.
    public var isValid: Bool { Self.sapisid(in: cookies) != nil }

    public static func isYoutube(_ host: String) -> Bool {
        let h = host.lowercased()
        return h == "youtube.com" || h.hasSuffix(".youtube.com")
    }

    static func sapisid(in cookies: [String: String]) -> String? {
        cookies["SAPISID"] ?? cookies["__Secure-3PAPISID"]
    }

    /// Tiêu đề giống trang web YouTube: `Authorization: SAPISIDHASH <thời gian>_<sha1>` + Cookie.
    /// Trả về nil nếu thiếu cookie đăng nhập.
    public func authHeaders(origin: String = AccountSession.origin, now: Date = Date()) -> [String: String]? {
        let c = cookies
        guard let sapisid = Self.sapisid(in: c) else { return nil }
        let ts = Int64(now.timeIntervalSince1970)
        let hash = SHA1Digest.hex("\(ts) \(sapisid) \(origin)")
        let cookieHeader = c.keys.sorted().map { "\($0)=\(c[$0] ?? "")" }.joined(separator: "; ")
        return [
            "Authorization": "SAPISIDHASH \(ts)_\(hash)",
            "X-Origin": origin,
            "X-Goog-AuthUser": "0",
            "Cookie": cookieHeader,
        ]
    }

    /// Cập nhật cookie từ tiêu đề Set-Cookie của một phản hồi youtube.com.
    func absorb(_ response: HTTPURLResponse?) {
        guard let response, let url = response.url, let host = url.host, Self.isYoutube(host) else { return }
        var fields: [String: String] = [:]
        for (k, v) in response.allHeaderFields {
            if let k = k as? String, let v = v as? String { fields[k] = v }
        }
        let fresh = HTTPCookie.cookies(withResponseHeaderFields: fields, for: url)
        guard !fresh.isEmpty else { return }
        var changed = false
        lock.lock()
        for c in fresh where Self.isYoutube(c.domain.hasPrefix(".") ? String(c.domain.dropFirst()) : c.domain) {
            let expired = c.expiresDate.map { $0 < Date() } ?? false
            if expired {
                if store.removeValue(forKey: c.name) != nil { changed = true }
            } else if store[c.name] != c.value {
                store[c.name] = c.value
                changed = true
            }
        }
        let snapshot = store
        lock.unlock()
        if changed { onChange?(snapshot) }
    }
}

/// SHA-1 viết bằng Swift thuần (dùng cho SAPISIDHASH).
enum SHA1Digest {
    static func hex(_ s: String) -> String {
        hash(Array(s.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func hash(_ input: [UInt8]) -> [UInt8] {
        var h0: UInt32 = 0x67452301, h1: UInt32 = 0xEFCDAB89, h2: UInt32 = 0x98BADCFE, h3: UInt32 = 0x10325476, h4: UInt32 = 0xC3D2E1F0
        var msg = input
        let bitLen = UInt64(input.count) * 8
        msg.append(0x80)
        while msg.count % 64 != 56 { msg.append(0) }
        for i in (0..<8).reversed() { msg.append(UInt8((bitLen >> (UInt64(i) * 8)) & 0xff)) }

        func rotl(_ x: UInt32, _ n: UInt32) -> UInt32 { (x << n) | (x >> (32 - n)) }

        var w = [UInt32](repeating: 0, count: 80)
        for chunk in stride(from: 0, to: msg.count, by: 64) {
            for i in 0..<16 {
                let j = chunk + i * 4
                w[i] = UInt32(msg[j]) << 24 | UInt32(msg[j + 1]) << 16 | UInt32(msg[j + 2]) << 8 | UInt32(msg[j + 3])
            }
            for i in 16..<80 { w[i] = rotl(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1) }
            var a = h0, b = h1, c = h2, d = h3, e = h4
            for i in 0..<80 {
                let f: UInt32, k: UInt32
                switch i {
                case 0..<20: f = (b & c) | (~b & d); k = 0x5A827999
                case 20..<40: f = b ^ c ^ d; k = 0x6ED9EBA1
                case 40..<60: f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC
                default: f = b ^ c ^ d; k = 0xCA62C1D6
                }
                let t = rotl(a, 5) &+ f &+ e &+ k &+ w[i]
                e = d; d = c; c = rotl(b, 30); b = a; a = t
            }
            h0 = h0 &+ a; h1 = h1 &+ b; h2 = h2 &+ c; h3 = h3 &+ d; h4 = h4 &+ e
        }
        var out: [UInt8] = []
        for v in [h0, h1, h2, h3, h4] {
            out.append(UInt8(v >> 24)); out.append(UInt8((v >> 16) & 0xff))
            out.append(UInt8((v >> 8) & 0xff)); out.append(UInt8(v & 0xff))
        }
        return out
    }
}
