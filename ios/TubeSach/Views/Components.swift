import SwiftUI
import UIKit
import TubeSachCore

// MARK: - Ảnh từ mạng (có bộ nhớ đệm)

/// Tải ảnh và giữ trong bộ nhớ đệm để cuộn danh sách mượt.
final class ImageLoader {
    static let shared = ImageLoader()
    private let cache = NSCache<NSURL, UIImage>()

    init() {
        cache.countLimit = 400
    }

    func cached(_ url: URL) -> UIImage? { cache.object(forKey: url as NSURL) }

    func image(for url: URL) async -> UIImage? {
        if let img = cached(url) { return img }
        guard let result = try? await HTTP.data(URLRequest(url: url)), result.1 == 200,
              let img = UIImage(data: result.0) else { return nil }
        cache.setObject(img, forKey: url as NSURL)
        return img
    }
}

struct RemoteImage: View {
    let url: String?
    var contentMode: ContentMode = .fill

    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color(.secondarySystemBackground)
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            }
        }
        .clipped()
        .task(id: url) {
            guard let s = url, let u = URL(string: s) else { image = nil; return }
            if let c = ImageLoader.shared.cached(u) { image = c; return }
            image = nil
            image = await ImageLoader.shared.image(for: u)
        }
    }
}

struct Avatar: View {
    let url: String?
    var size: CGFloat = 36

    var body: some View {
        RemoteImage(url: url)
            .frame(width: size, height: size)
            .clipShape(Circle())
    }
}

// MARK: - Video

/// Ảnh thu nhỏ 16:9 có nhãn thời lượng / TRỰC TIẾP và vạch đỏ "đã xem tới đâu".
struct Thumbnail: View {
    let video: VideoItem
    var progress: Double = 0
    var cornerRadius: CGFloat = 12

    var body: some View {
        Color.clear
            .aspectRatio(16 / 9, contentMode: .fit)
            .overlay { RemoteImage(url: video.thumbnail) }
            .overlay(alignment: .bottomTrailing) {
                if video.isLive {
                    Badge(text: "TRỰC TIẾP", color: .red)
                } else if let d = video.durationLabel {
                    Badge(text: d, color: .black.opacity(0.8))
                } else if let b = video.badge {
                    Badge(text: b, color: .black.opacity(0.8))
                }
            }
            .overlay(alignment: .bottom) {
                if progress > 0.02 {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(Color.white.opacity(0.4))
                            Rectangle().fill(Color.red).frame(width: geo.size.width * min(1, progress))
                        }
                    }
                    .frame(height: 4)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

struct Badge: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(color, in: RoundedRectangle(cornerRadius: 4))
            .padding(6)
    }
}

/// Dòng phụ dưới tiêu đề: "Kênh · 1,2 Tr lượt xem · 3 ngày trước".
func videoSubtitle(_ v: VideoItem, includeChannel: Bool = true) -> String {
    var parts: [String] = []
    if includeChannel, !v.uploader.isEmpty { parts.append(v.uploader) }
    if let t = v.viewText, !t.isEmpty { parts.append(t) }
    if let t = v.uploadedText, !t.isEmpty { parts.append(t) }
    return parts.joined(separator: " · ")
}

/// Thẻ video lớn (ảnh to, giống trang chủ YouTube).
struct VideoCard: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    let video: VideoItem
    var showChannel = true

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Button { model.play(video) } label: {
                Thumbnail(video: video, progress: model.progress(of: video))
            }
            .buttonStyle(.plain)
            HStack(alignment: .top, spacing: 12) {
                if showChannel {
                    Button {
                        if let id = video.channelId { model.openChannel(id) }
                    } label: {
                        Avatar(url: video.channelAvatar, size: 40)
                    }
                    .buttonStyle(.plain)
                }
                Button { model.play(video) } label: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(video.title)
                            .font(.body.weight(.semibold))
                            .foregroundColor(.primary)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                        Text(videoSubtitle(video))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                VideoMenu(video: video)
            }
        }
    }
}

/// Dòng video nhỏ (ảnh bên trái) – dùng cho video liên quan trên iPad, lịch sử…
struct VideoRow: View {
    @EnvironmentObject var model: AppModel
    let video: VideoItem
    var progress: Double = 0

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Button { model.play(video) } label: {
                HStack(alignment: .top, spacing: 10) {
                    Thumbnail(video: video, progress: progress, cornerRadius: 8)
                        .frame(width: 168)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(video.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.primary)
                            .lineLimit(3)
                            .multilineTextAlignment(.leading)
                        Text(videoSubtitle(video))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            VideoMenu(video: video)
        }
    }
}

/// Nút ⋮ : Xem sau, Chia sẻ, Không quan tâm, Mở kênh.
struct VideoMenu: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    let video: VideoItem

    var body: some View {
        Menu {
            Button {
                store.toggleWatchLater(video)
            } label: {
                Label(store.isWatchLater(video.id) ? "Xoá khỏi Xem sau" : "Lưu vào Xem sau", systemImage: "clock")
            }
            if let url = URL(string: video.url) {
                ShareLink(item: url) { Label("Chia sẻ", systemImage: "square.and.arrow.up") }
            }
            if let id = video.channelId {
                Button { model.openChannel(id) } label: { Label("Mở kênh", systemImage: "person.crop.square") }
            }
            Button(role: .destructive) {
                store.hide(video.id)
            } label: {
                Label("Không quan tâm", systemImage: "eye.slash")
            }
        } label: {
            Image(systemName: "ellipsis")
                .rotationEffect(.degrees(90))
                .foregroundColor(.secondary)
                .frame(width: 32, height: 32)
                .contentShape(Rectangle())
        }
    }
}

// MARK: - Shorts

struct ShortsShelf: View {
    @EnvironmentObject var model: AppModel
    let title: String
    let shorts: [ShortItem]

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(title, systemImage: "play.square.stack.fill")
                .font(.title3.bold())
                .foregroundColor(.primary)
                .padding(.horizontal)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 10) {
                    ForEach(shorts) { s in
                        Button { model.openShort(s, in: shorts) } label: {
                            ShortTile(short: s)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

struct ShortTile: View {
    let short: ShortItem

    var body: some View {
        Color.clear
            .frame(width: 160, height: 284)
            .overlay { RemoteImage(url: short.thumbnail) }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(short.title)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(2)
                    if let v = short.viewText {
                        Text(v).font(.caption)
                    }
                }
                .foregroundColor(.white)
                .shadow(radius: 3)
                .padding(8)
            }
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

// MARK: - Kênh

struct ChannelRow: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    let channel: ChannelRef

    var body: some View {
        HStack(spacing: 14) {
            Button { model.openChannel(channel.id) } label: {
                HStack(spacing: 14) {
                    Avatar(url: channel.avatar, size: 64)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(channel.name).font(.headline).foregroundColor(.primary)
                        if let s = channel.subtitle {
                            Text(s).font(.subheadline).foregroundColor(.secondary).lineLimit(2)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            SubscribeButton(channel: channel)
        }
        .padding(.horizontal)
    }
}

struct SubscribeButton: View {
    @EnvironmentObject var store: LocalStore
    let channel: ChannelRef

    var body: some View {
        let subscribed = store.isSubscribed(channel.id)
        Button {
            store.toggleSubscription(channel)
        } label: {
            HStack(spacing: 4) {
                if subscribed { Image(systemName: "bell") }
                Text(subscribed ? "Đã đăng ký" : "Đăng ký")
            }
            .font(.subheadline.weight(.semibold))
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .foregroundColor(subscribed ? .primary : Color(.systemBackground))
            .background(subscribed ? Color(.secondarySystemFill) : Color.primary, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Danh sách (trang chủ, tìm kiếm, kênh…)

/// Hiển thị một danh sách mục: video lớn, kệ Shorts, kệ ngang, kênh.
/// Trên iPad các video được xếp thành lưới nhiều cột.
struct FeedList: View {
    @EnvironmentObject var model: AppModel
    let entries: [FeedEntry]
    var showChannel = true
    var loadingMore = false
    var onLoadMore: (() -> Void)?

    private enum Block: Identifiable {
        case videos([VideoItem])
        case entry(FeedEntry)

        var id: String {
            switch self {
            case .videos(let v): return "g:" + (v.first?.id ?? "")
            case .entry(let e): return e.key
            }
        }
    }

    private var blocks: [Block] {
        var out: [Block] = []
        var run: [VideoItem] = []
        for e in model.visible(entries) {
            if case .video(let v) = e {
                run.append(v)
            } else {
                if !run.isEmpty { out.append(.videos(run)); run = [] }
                out.append(.entry(e))
            }
        }
        if !run.isEmpty { out.append(.videos(run)) }
        return out
    }

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 24) {
            ForEach(blocks) { block in
                switch block {
                case .videos(let videos):
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 320), spacing: 16, alignment: .top)], spacing: 24) {
                        ForEach(videos) { v in
                            VideoCard(video: v, showChannel: showChannel)
                        }
                    }
                    .padding(.horizontal)
                case .entry(let e):
                    entryView(e)
                }
            }
            if onLoadMore != nil {
                HStack {
                    Spacer()
                    if loadingMore { ProgressView() }
                    Spacer()
                }
                .frame(height: 44)
                .onAppear { onLoadMore?() }
            }
        }
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func entryView(_ e: FeedEntry) -> some View {
        switch e {
        case .shortsShelf(let title, let shorts):
            ShortsShelf(title: title, shorts: shorts)
        case .shelf(let title, let videos):
            VStack(alignment: .leading, spacing: 10) {
                if !title.isEmpty {
                    Text(title).font(.title3.bold()).padding(.horizontal)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: 14) {
                        ForEach(videos) { v in
                            VideoCard(video: v, showChannel: false).frame(width: 280)
                        }
                    }
                    .padding(.horizontal)
                }
            }
        case .channel(let c):
            ChannelRow(channel: c)
        case .video(let v):
            VideoCard(video: v, showChannel: showChannel).padding(.horizontal)
        }
    }
}

// MARK: - Trạng thái

struct LoadingView: View {
    var body: some View {
        ProgressView()
            .scaleEffect(1.4)
            .frame(maxWidth: .infinity, minHeight: 240)
    }
}

struct MessageView: View {
    let icon: String
    let title: String
    var message: String?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 48))
                .foregroundColor(.secondary)
            Text(title)
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)
            if let message {
                Text(message)
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            if let actionTitle, let action {
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, minHeight: 280)
    }
}

struct ErrorView: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        MessageView(icon: "wifi.exclamationmark", title: message, actionTitle: "Thử lại", action: retry)
    }
}

/// Logo + tên app trên thanh trên cùng.
struct AppLogo: View {
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "play.fill")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 30, height: 22)
                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 6))
            Text("Tube Sạch")
                .font(.title3.bold())
                .foregroundColor(.primary)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Tube Sạch")
    }
}
