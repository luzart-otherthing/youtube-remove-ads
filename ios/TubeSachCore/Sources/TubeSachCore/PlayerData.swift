import Foundation

/// Lỗi do YouTube trả về (ví dụ video bị chặn) hoặc lỗi mạng.
public struct InnerTubeError: LocalizedError {
    public let message: String
    public let code: Int

    public init(_ message: String, code: Int = 0) {
        self.message = message
        self.code = code
    }

    public var errorDescription: String? { message }
}

/// Một phụ đề có sẵn của video.
public struct CaptionTrack: Hashable {
    public var name: String
    public var languageCode: String
    public var url: String
    public var isAuto: Bool

    /// Link WebVTT; `translateTo` = mã ngôn ngữ để YouTube tự động dịch (ví dụ "vi").
    public func vttURL(translateTo: String? = nil) -> String {
        let base = url.replacingOccurrences(of: "&fmt=[^&]*", with: "", options: .regularExpression) + "&fmt=vtt"
        if let translateTo { return base + "&tlang=" + translateTo }
        return base
    }
}

/// Kết quả gọi /player: thông tin video + link phát HLS + link thống kê lượt xem.
///
/// Trên iOS dùng luồng HLS (`hlsManifestUrl`) mà YouTube trả cho máy khách Apple:
/// AVPlayer tự đổi chất lượng theo mạng, có sẵn phụ đề, hình trong hình, AirPlay.
public struct PlayerData {
    public var videoId: String
    public var title: String
    public var author: String
    public var channelId: String?
    public var lengthSec: Int64
    public var viewCount: Int64
    public var isLive: Bool
    public var thumbnail: String?
    public var hlsURL: String?
    public var playbackTrackingURL: String?
    public var watchtimeTrackingURL: String?
    public var expiresAt: Date
    public var captions: [CaptionTrack]
    /// Các độ phân giải H.264 có sẵn (để hiện menu "Chất lượng").
    /// "Độ phân giải" theo cách YouTube gọi = cạnh ngắn: Shorts 1080×1920 vẫn là 1080p.
    public var availableHeights: [Int]
    /// Kích thước khung hình (của luồng hình lớn nhất), 0 nếu không biết.
    public var videoWidth: Int
    public var videoHeight: Int

    /// Video dọc (Shorts, video quay dọc).
    public var isPortrait: Bool { videoWidth > 0 && videoWidth < videoHeight }

    /// Tỉ lệ rộng/cao, 0 nếu không biết.
    public var aspectRatio: Double { videoHeight > 0 ? Double(videoWidth) / Double(videoHeight) : 0 }

    public static func parse(_ json: JSON, videoId: String, now: Date = Date()) throws -> PlayerData {
        let status = json.s("playabilityStatus", "status")
        if status != "OK" {
            let reason = textOf(json.o("playabilityStatus")?["reason"]) ?? json.s("playabilityStatus", "reason")
            throw InnerTubeError(reason ?? "Không phát được video (\(status ?? "?"))")
        }
        let details = json["videoDetails"]
        let sd = json["streamingData"]
        let expires = sd?["expiresInSeconds"]?.int64Value ?? 21_000

        var heights = Set<Int>()
        var size = (w: 0, h: 0)
        for f in sd?.a("adaptiveFormats") ?? [] {
            let mime = f["mimeType"]?.stringValue ?? ""
            guard mime.hasPrefix("video/") else { continue }
            let w = f["width"]?.intValue ?? 0
            let h = f["height"]?.intValue ?? 0
            let quality = (w > 0 && h > 0) ? min(w, h) : h
            if mime.hasPrefix("video/mp4"), mime.contains("avc1"), quality > 0, quality <= 1080 { heights.insert(quality) }
            if w * h > size.w * size.h { size = (w, h) }
        }

        let captions: [CaptionTrack] = (json.a("captions", "playerCaptionsTracklistRenderer", "captionTracks") ?? []).compactMap { c in
            guard let url = c.s("baseUrl") else { return nil }
            let code = c["languageCode"]?.stringValue ?? ""
            return CaptionTrack(
                name: textOf(c["name"]) ?? code,
                languageCode: code,
                url: url.hasPrefix("/") ? "https://www.youtube.com" + url : url,
                isAuto: c["kind"]?.stringValue == "asr"
            )
        }

        return PlayerData(
            videoId: videoId,
            title: details?["title"]?.stringValue ?? "",
            author: details?["author"]?.stringValue ?? "",
            channelId: details?.s("channelId"),
            lengthSec: details?["lengthSeconds"]?.int64Value ?? 0,
            viewCount: details?["viewCount"]?.int64Value ?? -1,
            isLive: details?["isLive"]?.boolValue == true,
            thumbnail: bestImage(details?.a("thumbnail", "thumbnails")),
            hlsURL: sd?.s("hlsManifestUrl"),
            playbackTrackingURL: json.s("playbackTracking", "videostatsPlaybackUrl", "baseUrl"),
            watchtimeTrackingURL: json.s("playbackTracking", "videostatsWatchtimeUrl", "baseUrl"),
            expiresAt: now.addingTimeInterval(TimeInterval(expires)),
            captions: captions,
            availableHeights: heights.sorted(by: >),
            videoWidth: size.w,
            videoHeight: size.h
        )
    }
}
