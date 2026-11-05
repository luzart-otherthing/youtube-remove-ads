import Foundation
import Network

/// Theo dõi mạng: khi có mạng trở lại thì báo để tải lại những gì đang lỗi.
final class NetworkMonitor {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "tubesach.network")
    private var wasOnline: Bool?
    /// Gọi trên luồng chính khi mạng chuyển từ mất sang có.
    var onReconnect: (() -> Void)?

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            DispatchQueue.main.async {
                guard let self else { return }
                defer { self.wasOnline = online }
                // Bỏ qua lần báo đầu tiên (chỉ là trạng thái lúc mở app).
                if self.wasOnline == false && online { self.onReconnect?() }
            }
        }
        monitor.start(queue: queue)
    }
}
