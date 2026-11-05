import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Gửi yêu cầu HTTP (bọc URLSession để chạy được cả trên iOS lẫn Linux).
public enum HTTP {
    public static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 20
        config.httpAdditionalHeaders = ["Accept-Language": "vi-VN,vi;q=0.9"]
        return URLSession(configuration: config)
    }()

    public static func data(_ request: URLRequest) async throws -> (Data, Int) {
        try await withCheckedThrowingContinuation { cont in
            let task = session.dataTask(with: request) { data, response, error in
                if let error {
                    cont.resume(throwing: InnerTubeError("Không có kết nối mạng (\(error.localizedDescription))"))
                    return
                }
                let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                cont.resume(returning: (data ?? Data(), code))
            }
            task.resume()
        }
    }

    public static func get(_ url: String, headers: [String: String] = [:]) async throws -> (Data, Int) {
        guard let u = URL(string: url) else { throw InnerTubeError("Link không hợp lệ") }
        var req = URLRequest(url: u)
        for (k, v) in headers { req.setValue(v, forHTTPHeaderField: k) }
        return try await data(req)
    }
}

/// Gọi thẳng API nội bộ của YouTube (InnerTube) – cùng API mà app YouTube và trang web dùng.
///
/// - Ngôn ngữ tiếng Việt (hl=vi), khu vực Việt Nam (gl=VN) → đề xuất, thịnh hành đều tiếng Việt.
/// - Không đăng nhập. YouTube nhận diện "khách ẩn danh" qua `visitorData` (giống trình duyệt
///   chưa đăng nhập), nhờ vậy trang chủ có đề xuất theo những video đã xem trên máy này.
public final class InnerTube {
    public static let hl = "vi"
    public static let gl = "VN"
    public static let webVersionFallback = "2.20261030.02.00"
    public static let visionOSVersion = "1.02"
    public static let iosVersion = "20.10.4"
    public static let webUA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    public static let visionUA =
        "Mozilla/5.0 (Apple Vision Pro; U; CPU visionOS 25_6_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/605.1.15"
    public static let iosUA = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"

    private let loadVisitor: () -> String?
    private let saveVisitor: (String) -> Void
    private let lock = NSLock()
    private var visitor: String?
    private var webVersion: String?

    public init(loadVisitor: @escaping () -> String?, saveVisitor: @escaping (String) -> Void) {
        self.loadVisitor = loadVisitor
        self.saveVisitor = saveVisitor
    }

    /// Chạy `body` trong khoá (hàm đồng bộ nên gọi được từ cả mã async).
    private func locked<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    public var visitorData: String? {
        locked {
            if visitor == nil { visitor = loadVisitor().flatMap { $0.isEmpty ? nil : $0 } }
            return visitor
        }
    }

    /// Xoá mã khách ẩn danh → YouTube coi như máy mới (đề xuất làm lại từ đầu).
    public func resetVisitor() {
        locked { visitor = nil }
        saveVisitor("")
    }

    private func rememberVisitor(_ v: String) {
        let isNew: Bool = locked {
            if !(visitor ?? "").isEmpty { return false }
            visitor = v
            return true
        }
        if isNew { saveVisitor(v) }
    }

    /// Phiên bản máy khách WEB hiện tại (lấy từ youtube.com/sw.js_data, có dự phòng).
    private func currentWebVersion() async -> String {
        if let cached = locked({ webVersion }) { return cached }
        var found = Self.webVersionFallback
        if let result = try? await HTTP.get("https://www.youtube.com/sw.js_data", headers: ["User-Agent": Self.webUA]),
           result.1 == 200 {
            let text = String(decoding: result.0, as: UTF8.self)
            if let r = text.range(of: "\"2\\.\\d{8}\\.\\d{2}\\.\\d{2}\"", options: .regularExpression) {
                found = String(text[r]).replacingOccurrences(of: "\"", with: "")
            }
        }
        locked { webVersion = found }
        return found
    }

    private func post(_ url: String, body: [String: Any], headers: [String: String]) async throws -> JSON {
        guard let u = URL(string: url) else { throw InnerTubeError("Link không hợp lệ") }
        var req = URLRequest(url: u)
        req.httpMethod = "POST"
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        for (k, v) in headers { req.setValue(v, forHTTPHeaderField: k) }
        if let v = visitorData { req.setValue(v, forHTTPHeaderField: "X-Goog-Visitor-Id") }
        let (data, code) = try await HTTP.data(req)
        guard (200..<300).contains(code) else { throw InnerTubeError("YouTube trả lỗi HTTP \(code)", code: code) }
        let json: JSON
        do { json = try JSON.parse(data) } catch { throw InnerTubeError("YouTube trả dữ liệu lạ") }
        if let v = json.s("responseContext", "visitorData") { rememberVisitor(v) }
        return json
    }

    private func clientContext(_ client: [String: Any]) -> [String: Any] {
        var c = client
        c["hl"] = Self.hl
        c["gl"] = Self.gl
        if let v = visitorData { c["visitorData"] = v }
        return ["client": c]
    }

    private func web(_ endpoint: String, _ body: [String: Any]) async throws -> JSON {
        let version = await currentWebVersion()
        var b = body
        b["context"] = clientContext([
            "clientName": "WEB",
            "clientVersion": version,
            "timeZone": "Asia/Ho_Chi_Minh",
            "utcOffsetMinutes": 420,
        ])
        return try await post(
            "https://www.youtube.com/youtubei/v1/\(endpoint)?prettyPrint=false",
            body: b,
            headers: [
                "User-Agent": Self.webUA,
                "X-YouTube-Client-Name": "1",
                "X-YouTube-Client-Version": version,
                "Origin": "https://www.youtube.com",
                "Referer": "https://www.youtube.com/",
            ]
        )
    }

    /// Đảm bảo đã có mã khách ẩn danh trước khi gọi các API khác.
    public func ensureVisitor() async {
        if visitorData == nil { _ = try? await browse(browseId: "FEwhat_to_watch") }
    }

    public func browse(browseId: String? = nil, params: String? = nil, continuation: String? = nil) async throws -> JSON {
        var b: [String: Any] = [:]
        if let browseId { b["browseId"] = browseId }
        if let params { b["params"] = params }
        if let continuation { b["continuation"] = continuation }
        return try await web("browse", b)
    }

    public func search(query: String? = nil, params: String? = nil, continuation: String? = nil) async throws -> JSON {
        var b: [String: Any] = [:]
        if let query { b["query"] = query }
        if let params { b["params"] = params.removingPercentEncoding ?? params }
        if let continuation { b["continuation"] = continuation }
        return try await web("search", b)
    }

    public func next(videoId: String? = nil, continuation: String? = nil) async throws -> JSON {
        var b: [String: Any] = ["contentCheckOk": true, "racyCheckOk": true]
        if let videoId { b["videoId"] = videoId }
        if let continuation { b["continuation"] = continuation }
        return try await web("next", b)
    }

    public func reelSequence(sequenceParams: String? = nil, continuation: String? = nil) async throws -> JSON {
        var b: [String: Any] = [:]
        if let sequenceParams { b["sequenceParams"] = sequenceParams }
        if let continuation { b["continuation"] = continuation }
        return try await web("reel/reel_watch_sequence", b)
    }

    /// Lấy link phát. Máy khách visionOS: 1 yêu cầu, trả luồng HLS dùng được ngay.
    /// Nếu lỗi thì thử máy khách iOS (cũng trả HLS).
    public func player(videoId: String) async throws -> JSON {
        await ensureVisitor()
        do {
            let json = try await playerRequest(videoId: videoId, client: [
                "clientName": "VISIONOS",
                "clientVersion": Self.visionOSVersion,
                "deviceMake": "Apple",
                "deviceModel": "RealityDevice14,1",
                "osName": "visionOS",
                "osVersion": "25.6.0.23O471",
            ], headers: [
                "User-Agent": Self.visionUA,
                "X-YouTube-Client-Name": "101",
                "X-YouTube-Client-Version": Self.visionOSVersion,
            ])
            if json.s("playabilityStatus", "status") == "OK", json.s("streamingData", "hlsManifestUrl") != nil {
                return json
            }
            return try await playerIOS(videoId: videoId, otherwise: json)
        } catch {
            return try await playerIOS(videoId: videoId, otherwise: nil, error: error)
        }
    }

    private func playerIOS(videoId: String, otherwise: JSON?, error: Error? = nil) async throws -> JSON {
        do {
            let json = try await playerRequest(videoId: videoId, client: [
                "clientName": "IOS",
                "clientVersion": Self.iosVersion,
                "deviceMake": "Apple",
                "deviceModel": "iPhone16,2",
                "osName": "iPhone",
                "osVersion": "18.3.2.22D82",
            ], headers: [
                "User-Agent": Self.iosUA,
                "X-YouTube-Client-Name": "5",
                "X-YouTube-Client-Version": Self.iosVersion,
            ])
            if json.s("playabilityStatus", "status") == "OK" || otherwise == nil { return json }
            return otherwise ?? json
        } catch let e {
            if let otherwise { return otherwise }
            throw error ?? e
        }
    }

    private func playerRequest(videoId: String, client: [String: Any], headers: [String: String]) async throws -> JSON {
        try await post(
            "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false",
            body: [
                "context": clientContext(client),
                "videoId": videoId,
                "contentCheckOk": true,
                "racyCheckOk": true,
            ],
            headers: headers
        )
    }

    /// Gửi một "ping" thống kê lượt xem (để YouTube biết khách ẩn danh này đã xem video nào).
    public func ping(_ url: String) async {
        var headers = ["User-Agent": Self.visionUA]
        if let v = visitorData { headers["X-Goog-Visitor-Id"] = v }
        _ = try? await HTTP.get(url, headers: headers)
    }
}
