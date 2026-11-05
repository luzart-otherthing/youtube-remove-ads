import Foundation
import SwiftUI
import TubeSachCore

/// Một mục trong lịch sử xem: video + vị trí đã xem tới (giây).
struct HistoryEntry: Codable, Hashable, Identifiable {
    var video: VideoItem
    var position: Double
    var updatedAt: Date

    var id: String { video.id }
}

enum ThemeMode: String, Codable, CaseIterable, Identifiable {
    case system, light, dark

    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "Dùng giao diện của thiết bị"
        case .light: return "Giao diện sáng"
        case .dark: return "Giao diện tối"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

/// Cỡ chữ cho người lớn tuổi.
enum TextSize: String, Codable, CaseIterable, Identifiable {
    case normal, large, larger, largest

    var id: String { rawValue }

    var label: String {
        switch self {
        case .normal: return "Bình thường"
        case .large: return "Lớn"
        case .larger: return "Rất lớn"
        case .largest: return "Cực lớn"
        }
    }

    var dynamicType: DynamicTypeSize {
        switch self {
        case .normal: return .large
        case .large: return .xLarge
        case .larger: return .xxLarge
        case .largest: return .xxxLarge
        }
    }
}

/// Chế độ chất lượng giống menu "Chất lượng" của YouTube.
enum QualityMode: String, Codable, CaseIterable, Identifiable {
    case auto, dataSaver, fixed

    var id: String { rawValue }

    var label: String {
        switch self {
        case .auto: return "Tự động (khuyên dùng)"
        case .dataSaver: return "Tiết kiệm dữ liệu"
        case .fixed: return "Giới hạn độ phân giải"
        }
    }

    var detail: String {
        switch self {
        case .auto: return "Tự chọn chất lượng theo tốc độ mạng"
        case .dataSaver: return "Tối đa 480p, tốn ít 4G hơn"
        case .fixed: return "Không vượt quá độ phân giải bạn chọn"
        }
    }
}

struct AppSettings: Codable, Equatable {
    var theme: ThemeMode = .system
    var textSize: TextSize = .normal
    var autoplay = true
    var autoPip = true
    var backgroundPlay = true
    var quality: QualityMode = .auto
    /// Độ phân giải tối đa khi chọn "Nâng cao".
    var fixedHeight = 720
    var sponsorBlock = true
    var sponsorCategories: Set<String> = SegmentCategory.defaults
    var returnDislike = true
    var hideShorts = false
    /// Cho YouTube biết đã xem gì (ẩn danh) để trang chủ có đề xuất.
    var personalize = true
    /// Ngôn ngữ phụ đề tự bật (rỗng = tắt).
    var captionLanguage = ""
    var playbackSpeed: Float = 1.0

    init() {}

    // Đọc từng khoá một để bản cập nhật thêm cài đặt mới vẫn giữ được cài đặt cũ.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = AppSettings()
        theme = (try? c.decode(ThemeMode.self, forKey: .theme)) ?? d.theme
        textSize = (try? c.decode(TextSize.self, forKey: .textSize)) ?? d.textSize
        autoplay = (try? c.decode(Bool.self, forKey: .autoplay)) ?? d.autoplay
        autoPip = (try? c.decode(Bool.self, forKey: .autoPip)) ?? d.autoPip
        backgroundPlay = (try? c.decode(Bool.self, forKey: .backgroundPlay)) ?? d.backgroundPlay
        quality = (try? c.decode(QualityMode.self, forKey: .quality)) ?? d.quality
        fixedHeight = (try? c.decode(Int.self, forKey: .fixedHeight)) ?? d.fixedHeight
        sponsorBlock = (try? c.decode(Bool.self, forKey: .sponsorBlock)) ?? d.sponsorBlock
        sponsorCategories = (try? c.decode(Set<String>.self, forKey: .sponsorCategories)) ?? d.sponsorCategories
        returnDislike = (try? c.decode(Bool.self, forKey: .returnDislike)) ?? d.returnDislike
        hideShorts = (try? c.decode(Bool.self, forKey: .hideShorts)) ?? d.hideShorts
        personalize = (try? c.decode(Bool.self, forKey: .personalize)) ?? d.personalize
        captionLanguage = (try? c.decode(String.self, forKey: .captionLanguage)) ?? d.captionLanguage
        playbackSpeed = (try? c.decode(Float.self, forKey: .playbackSpeed)) ?? d.playbackSpeed
    }
}

/// Lưu dữ liệu trên máy (không gửi đi đâu): cài đặt, lịch sử, kênh đăng ký, video đã lưu.
@MainActor
final class LocalStore: ObservableObject {
    static let maxHistory = 300
    static let maxSaved = 500

    private let defaults: UserDefaults

    @Published var settings: AppSettings { didSet { if settings != oldValue { save(settings, "settings") } } }
    @Published private(set) var history: [HistoryEntry]
    /// "Xem sau".
    @Published private(set) var watchLater: [VideoItem]
    @Published private(set) var liked: [VideoItem]
    @Published private(set) var disliked: Set<String>
    @Published private(set) var subscriptions: [ChannelRef]
    @Published private(set) var hidden: Set<String>
    @Published private(set) var recentSearches: [String]

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        settings = Self.load(AppSettings.self, "settings", defaults) ?? AppSettings()
        history = Self.load([HistoryEntry].self, "history", defaults) ?? []
        watchLater = Self.load([VideoItem].self, "watchLater", defaults) ?? []
        liked = Self.load([VideoItem].self, "liked", defaults) ?? []
        disliked = Self.load(Set<String>.self, "disliked", defaults) ?? []
        subscriptions = Self.load([ChannelRef].self, "subscriptions", defaults) ?? []
        hidden = Self.load(Set<String>.self, "hidden", defaults) ?? []
        recentSearches = Self.load([String].self, "recentSearches", defaults) ?? []
    }

    private static func load<T: Decodable>(_ type: T.Type, _ key: String, _ defaults: UserDefaults) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(type, from: data)
    }

    private func save<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? JSONEncoder().encode(value) { defaults.set(data, forKey: key) }
    }

    // MARK: Lịch sử

    func position(of videoId: String) -> Double {
        history.first { $0.video.id == videoId }?.position ?? 0
    }

    func addToHistory(_ video: VideoItem, position: Double) {
        let old = history.first { $0.video.id == video.id }?.video
        let merged = old.map { video.merged(with: $0) } ?? video
        var list = history.filter { $0.video.id != video.id }
        list.insert(HistoryEntry(video: merged, position: position, updatedAt: Date()), at: 0)
        history = Array(list.prefix(Self.maxHistory))
        save(history, "history")
    }

    func savePosition(_ videoId: String, position: Double) {
        guard let i = history.firstIndex(where: { $0.video.id == videoId }) else { return }
        history[i].position = position
        save(history, "history")
    }

    func removeFromHistory(_ videoId: String) {
        history.removeAll { $0.video.id == videoId }
        save(history, "history")
    }

    func clearHistory() {
        history = []
        save(history, "history")
    }

    // MARK: Xem sau / Đã thích / Không thích

    func isWatchLater(_ id: String) -> Bool { watchLater.contains { $0.id == id } }
    func isLiked(_ id: String) -> Bool { liked.contains { $0.id == id } }
    func isDisliked(_ id: String) -> Bool { disliked.contains(id) }

    func toggleWatchLater(_ video: VideoItem) {
        watchLater = toggled(watchLater, video)
        save(watchLater, "watchLater")
    }

    func toggleLike(_ video: VideoItem) {
        liked = toggled(liked, video)
        save(liked, "liked")
        if disliked.remove(video.id) != nil { save(disliked, "disliked") }
    }

    func toggleDislike(_ video: VideoItem) {
        if disliked.contains(video.id) {
            disliked.remove(video.id)
        } else {
            disliked.insert(video.id)
            if liked.contains(where: { $0.id == video.id }) {
                liked.removeAll { $0.id == video.id }
                save(liked, "liked")
            }
        }
        save(disliked, "disliked")
    }

    private func toggled(_ list: [VideoItem], _ video: VideoItem) -> [VideoItem] {
        if list.contains(where: { $0.id == video.id }) { return list.filter { $0.id != video.id } }
        return Array(([video] + list).prefix(Self.maxSaved))
    }

    // MARK: Kênh đăng ký

    func isSubscribed(_ channelId: String) -> Bool { subscriptions.contains { $0.id == channelId } }

    func toggleSubscription(_ channel: ChannelRef) {
        if isSubscribed(channel.id) {
            subscriptions.removeAll { $0.id == channel.id }
        } else {
            var ch = channel
            ch.subtitle = nil
            subscriptions.insert(ch, at: 0)
        }
        save(subscriptions, "subscriptions")
    }

    // MARK: "Không quan tâm"

    func hide(_ videoId: String) {
        hidden.insert(videoId)
        save(hidden, "hidden")
    }

    // MARK: Tìm kiếm gần đây

    func addRecentSearch(_ query: String) {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return }
        recentSearches = Array(([q] + recentSearches.filter { $0.lowercased() != q.lowercased() }).prefix(20))
        save(recentSearches, "recentSearches")
    }

    func removeRecentSearch(_ query: String) {
        recentSearches.removeAll { $0 == query }
        save(recentSearches, "recentSearches")
    }

    func clearRecentSearches() {
        recentSearches = []
        save(recentSearches, "recentSearches")
    }
}
