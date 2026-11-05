import Foundation
import TubeSachCore

/// Các dịch vụ dùng chung trong app.
enum Services {
    /// Mã khách ẩn danh của YouTube được lưu trong UserDefaults (an toàn khi gọi từ luồng nền).
    static let youtube = YouTube(innerTube: InnerTube(
        loadVisitor: { UserDefaults.standard.string(forKey: "visitorData") },
        saveVisitor: { UserDefaults.standard.set($0, forKey: "visitorData") }
    ))

    /// Kho GitHub chứa các bản phát hành (thẻ ios-v…).
    static let updateRepo = "luzart-otherthing/youtube-remove-ads"

    static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    /// Bộ nhớ đệm ảnh lớn hơn mặc định để cuộn mượt.
    static func configureCaches() {
        URLCache.shared = URLCache(memoryCapacity: 64 * 1024 * 1024, diskCapacity: 300 * 1024 * 1024)
    }
}

/// Thông báo lỗi dễ hiểu cho người dùng.
func friendlyError(_ error: Error) -> String {
    if let e = error as? InnerTubeError {
        if e.message.hasPrefix("Không có kết nối mạng") { return "Không có kết nối mạng" }
        return e.message
    }
    if (error as NSError).domain == NSURLErrorDomain { return "Không có kết nối mạng" }
    return "Đã xảy ra lỗi"
}
