import SwiftUI
import TubeSachCore

/// Trang xem video: trình phát, tiêu đề, kênh, nút thích/không thích, bình luận, video liên quan.
/// iPad (màn hình ngang rộng): video liên quan nằm bên phải.
/// iPhone xoay ngang: chỉ còn video, toàn màn hình.
struct WatchView: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    @EnvironmentObject var player: PlayerController
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    @State private var showDescription = false
    @State private var showComments = false
    @State private var showOptions = false

    var body: some View {
        GeometryReader { geo in
            let wide = geo.size.width >= 900 && geo.size.width > geo.size.height
            if verticalSizeClass == .compact && !wide {
                playerArea
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black)
                    .ignoresSafeArea()
            } else if wide {
                HStack(alignment: .top, spacing: 0) {
                    VStack(spacing: 0) {
                        topBar
                        playerArea
                        ScrollView { info }
                    }
                    .frame(width: geo.size.width * 0.64)
                    Divider()
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 12) {
                            ForEach(relatedVideos) { v in
                                VideoRow(video: v, progress: model.progress(of: v))
                            }
                        }
                        .padding()
                    }
                }
            } else {
                VStack(spacing: 0) {
                    topBar
                    playerArea
                    ScrollView {
                        info
                        // iPhone: 1 cột; iPad dọc: 2 cột.
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 320), spacing: 16, alignment: .top)], spacing: 24) {
                            ForEach(relatedVideos) { v in
                                VideoCard(video: v)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 8)
                    }
                }
            }
        }
        .environment(\.openURL, OpenURLAction { url in
            if model.handle(url) {
                if url.host != "seek" { showDescription = false }
                return .handled
            }
            return .systemAction
        })
        .sheet(isPresented: $showDescription) {
            DescriptionSheet()
                .environmentObject(model)
                .environmentObject(store)
                .environmentObject(player)
        }
        .sheet(isPresented: $showComments) {
            CommentsSheet()
                .environmentObject(model)
                .environmentObject(store)
                .environmentObject(player)
        }
        .sheet(isPresented: $showOptions) {
            PlayerOptionsSheet()
                .environmentObject(model)
                .environmentObject(store)
                .environmentObject(player)
        }
    }

    private var relatedVideos: [VideoItem] {
        let hidden = store.hidden
        return (model.watch?.related ?? []).filter { !hidden.contains($0.id) && !(store.settings.hideShorts && $0.isShort) }
    }

    // MARK: Thanh trên

    private var topBar: some View {
        HStack(spacing: 4) {
            Button { model.watchExpanded = false } label: {
                Image(systemName: "chevron.down")
                    .font(.title2.weight(.semibold))
                    .frame(width: 48, height: 44)
            }
            .accessibilityLabel("Thu nhỏ")
            Spacer()
            if store.settings.sponsorBlock {
                Image(systemName: "checkmark.shield.fill")
                    .foregroundColor(.green)
                    .accessibilityLabel("SponsorBlock đang bật")
            }
            Button { showOptions = true } label: {
                Image(systemName: "gearshape")
                    .font(.title3)
                    .frame(width: 48, height: 44)
            }
            .accessibilityLabel("Cài đặt trình phát")
            Button { model.closePlayer() } label: {
                Image(systemName: "xmark")
                    .font(.title3)
                    .frame(width: 48, height: 44)
            }
            .accessibilityLabel("Đóng")
        }
        .foregroundColor(.primary)
        .padding(.horizontal, 4)
    }

    // MARK: Trình phát

    private var playerArea: some View {
        ZStack {
            Color.black
            VideoPlayerView(player: player, autoPip: store.settings.autoPip)
            if player.loading {
                ProgressView()
                    .tint(.white)
                    .scaleEffect(1.6)
            }
            if let err = player.error {
                VStack(spacing: 12) {
                    Text(err)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    Button("Thử lại") { player.retry() }
                        .buttonStyle(.borderedProminent)
                }
                .padding()
            }
        }
        .aspectRatio(16 / 9, contentMode: .fit)
        .overlay(alignment: .bottomLeading) {
            if let t = player.toast {
                HStack(spacing: 12) {
                    Text("Đã bỏ qua: \(t.label)")
                    Button("Hoàn tác") { player.undoSkip() }
                        .font(.body.weight(.bold))
                        .foregroundColor(.yellow)
                }
                .font(.subheadline)
                .foregroundColor(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Color.black.opacity(0.75), in: Capsule())
                .padding(12)
                .padding(.bottom, 36)
            }
        }
        .overlay(alignment: .bottom) {
            if !player.segments.isEmpty && player.duration > 0 {
                SegmentBar(segments: player.segments, duration: player.duration, position: player.position)
            }
        }
    }

    // MARK: Thông tin video

    @ViewBuilder
    private var info: some View {
        let current = player.current
        let w = model.watch
        VStack(alignment: .leading, spacing: 14) {
            Button { showDescription = true } label: {
                VStack(alignment: .leading, spacing: 6) {
                    Text(w?.title ?? current?.title ?? "")
                        .font(.title3.weight(.bold))
                        .foregroundColor(.primary)
                        .multilineTextAlignment(.leading)
                    let meta = [w?.shortViewText ?? w?.viewText, w?.relativeDateText].compactMap { $0 }.joined(separator: " · ")
                    Text(meta.isEmpty ? "…thêm" : meta + "  …thêm")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if let ch = w?.channel {
                HStack(spacing: 12) {
                    Button { model.openChannel(ch.id) } label: {
                        HStack(spacing: 12) {
                            Avatar(url: ch.avatar, size: 44)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(ch.name).font(.headline).foregroundColor(.primary).lineLimit(1)
                                if let s = w?.subscriberText {
                                    Text(s).font(.caption).foregroundColor(.secondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    SubscribeButton(channel: ch)
                }
            }

            if let v = current {
                actionRow(v)
            }

            if model.watchLoading && w == nil {
                ProgressView().frame(maxWidth: .infinity)
            } else if let err = model.watchError {
                Button("\(err) – Thử lại") { model.retryWatch() }
            }

            commentsCard
        }
        .padding()
    }

    private func actionRow(_ v: VideoItem) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                HStack(spacing: 0) {
                    Button { store.toggleLike(v) } label: {
                        Label(model.watch?.likeText ?? "Thích", systemImage: store.isLiked(v.id) ? "hand.thumbsup.fill" : "hand.thumbsup")
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                    }
                    Divider().frame(height: 20)
                    Button { store.toggleDislike(v) } label: {
                        Label(model.dislikes.map { Format.count($0) } ?? "", systemImage: store.isDisliked(v.id) ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                    }
                    .accessibilityLabel("Không thích")
                }
                .background(Color(.secondarySystemFill), in: Capsule())

                if let url = URL(string: v.url) {
                    ShareLink(item: url) {
                        Label("Chia sẻ", systemImage: "arrowshape.turn.up.right")
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(Color(.secondarySystemFill), in: Capsule())
                    }
                }
                Button { store.toggleWatchLater(v) } label: {
                    Label(store.isWatchLater(v.id) ? "Đã lưu" : "Lưu", systemImage: store.isWatchLater(v.id) ? "checkmark" : "plus.square.on.square")
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(Color(.secondarySystemFill), in: Capsule())
                }
                Button { player.loop.toggle() } label: {
                    Label("Lặp lại", systemImage: player.loop ? "repeat.1" : "repeat")
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(player.loop ? Color.accentColor.opacity(0.25) : Color(.secondarySystemFill), in: Capsule())
                }
            }
            .font(.subheadline.weight(.semibold))
            .foregroundColor(.primary)
            .buttonStyle(.plain)
        }
    }

    private var commentsCard: some View {
        Button { showComments = true } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Bình luận").font(.headline)
                    if let c = model.watch?.commentCountText {
                        Text(c).foregroundColor(.secondary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").foregroundColor(.secondary)
                }
                if let first = model.comments.items.first {
                    HStack(alignment: .top, spacing: 8) {
                        Avatar(url: first.avatar, size: 26)
                        Text(first.text)
                            .font(.subheadline)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                } else if model.comments.loading {
                    ProgressView()
                } else if let e = model.comments.error {
                    Text(e).font(.subheadline).foregroundColor(.secondary)
                }
            }
            .foregroundColor(.primary)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }
}

/// Vạch màu các đoạn SponsorBlock dưới video.
struct SegmentBar: View {
    let segments: [Segment]
    let duration: Double
    let position: Double

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                ForEach(segments, id: \.uuid) { s in
                    Rectangle()
                        .fill(color(s.category))
                        .frame(width: max(2, geo.size.width * (s.end - s.start) / duration))
                        .offset(x: geo.size.width * s.start / duration)
                }
            }
        }
        .frame(height: 3)
        .allowsHitTesting(false)
    }

    private func color(_ category: String) -> Color {
        let rgb = SegmentCategory(rawValue: category)?.rgb ?? 0x00D400
        return Color(
            red: Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >> 8) & 0xFF) / 255,
            blue: Double(rgb & 0xFF) / 255
        )
    }
}

// MARK: - Mô tả

struct DescriptionSheet: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var player: PlayerController
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    let w = model.watch
                    Text(w?.title ?? player.current?.title ?? "")
                        .font(.title3.bold())
                    HStack(spacing: 24) {
                        stat(w?.likeText, "Lượt thích")
                        stat(w?.viewText, "Lượt xem")
                        stat(w?.dateText, "Ngày đăng")
                    }
                    Divider()
                    Text(linkified(w?.description ?? ""))
                        .font(.body)
                        .textSelection(.enabled)
                }
                .padding()
            }
            .navigationTitle("Nội dung mô tả")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Xong") { dismiss() }
                }
            }
        }
    }

    private func stat(_ value: String?, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value ?? "–").font(.headline)
            Text(label).font(.caption).foregroundColor(.secondary)
        }
    }
}

/// Biến link và mốc thời gian (1:23) trong mô tả/bình luận thành chữ bấm được.
func linkified(_ text: String) -> AttributedString {
    var result = AttributedString()
    let ns = text as NSString
    guard let re = try? NSRegularExpression(pattern: "(https?://[^\\s]+)|(\\b(?:\\d{1,2}:)?\\d{1,2}:\\d{2}\\b)") else {
        return AttributedString(text)
    }
    var last = 0
    for m in re.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
        if m.range.location > last {
            result.append(AttributedString(ns.substring(with: NSRange(location: last, length: m.range.location - last))))
        }
        let s = ns.substring(with: m.range)
        var part = AttributedString(s)
        if m.range(at: 1).location != NSNotFound {
            part.link = URL(string: s)
        } else {
            part.link = URL(string: "tubesach://seek?t=\(Format.parseDuration(s))")
        }
        result.append(part)
        last = m.range.location + m.range.length
    }
    if last < ns.length {
        result.append(AttributedString(ns.substring(from: last)))
    }
    return result
}

// MARK: - Bình luận

struct CommentsSheet: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                let c = model.comments
                if c.loading && c.items.isEmpty {
                    LoadingView().listRowSeparator(.hidden)
                } else if let e = c.error, c.items.isEmpty {
                    Text(e).foregroundColor(.secondary)
                }
                ForEach(c.items) { item in
                    CommentRow(comment: item)
                }
                if c.continuation != nil {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                    .listRowSeparator(.hidden)
                    .onAppear { model.loadMoreComments() }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Bình luận")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Xong") { dismiss() }
                }
            }
        }
    }
}

struct CommentRow: View {
    let comment: CommentItem

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Avatar(url: comment.avatar, size: 36)
            VStack(alignment: .leading, spacing: 6) {
                if comment.pinned {
                    Label("Đã ghim", systemImage: "pin.fill")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Text([comment.author, comment.time].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(linkified(comment.text))
                    .font(.body)
                HStack(spacing: 16) {
                    Label(comment.likes ?? "", systemImage: "hand.thumbsup")
                    if comment.replies > 0 {
                        Label("\(comment.replies) phản hồi", systemImage: "bubble.left")
                    }
                }
                .font(.caption)
                .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Cài đặt trình phát

struct PlayerOptionsSheet: View {
    @EnvironmentObject var store: LocalStore
    @EnvironmentObject var player: PlayerController
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Chất lượng") {
                    Picker("Chất lượng", selection: $store.settings.quality) {
                        ForEach(QualityMode.allCases) { q in
                            Text(q.label).tag(q)
                        }
                    }
                    if store.settings.quality == .fixed {
                        Picker("Tối đa", selection: $store.settings.fixedHeight) {
                            ForEach(qualityHeights, id: \.self) { h in
                                Text("\(h)p").tag(h)
                            }
                        }
                    }
                }
                Section {
                    Toggle("Lặp lại video", isOn: $player.loop)
                    Picker("Hẹn giờ tắt", selection: Binding(get: { sleepChoice }, set: { setSleep($0) })) {
                        Text("Tắt").tag(0)
                        Text("15 phút").tag(15)
                        Text("30 phút").tag(30)
                        Text("45 phút").tag(45)
                        Text("1 giờ").tag(60)
                        Text("Hết video này").tag(-1)
                    }
                    if let date = sleepDate {
                        Text("Tự dừng lúc \(date.formatted(date: .omitted, time: .shortened))")
                            .foregroundColor(.secondary)
                    }
                } footer: {
                    Text("Tốc độ phát, phụ đề và AirPlay có trong nút … của trình phát.")
                }
                Section {
                    Toggle("Bỏ qua đoạn tài trợ (SponsorBlock)", isOn: $store.settings.sponsorBlock)
                } footer: {
                    Text("Tự bỏ qua đoạn quảng cáo do người làm video tự chèn. Chọn loại đoạn trong Cài đặt.")
                }
            }
            .navigationTitle("Cài đặt trình phát")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Xong") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var qualityHeights: [Int] {
        let available = player.data?.availableHeights ?? []
        return available.isEmpty ? [1080, 720, 480, 360, 240, 144] : available
    }

    private var sleepDate: Date? {
        if case .at(let date) = player.sleepTimer { return date }
        return nil
    }

    private var sleepChoice: Int {
        switch player.sleepTimer {
        case .off: return 0
        case .endOfVideo: return -1
        case .at(let date):
            let minutes = Int((date.timeIntervalSinceNow / 60).rounded())
            return [15, 30, 45, 60].min { abs($0 - minutes) < abs($1 - minutes) } ?? 0
        }
    }

    private func setSleep(_ minutes: Int) {
        switch minutes {
        case 0: player.sleepTimer = .off
        case -1: player.sleepTimer = .endOfVideo
        default: player.sleepTimer = .at(Date().addingTimeInterval(Double(minutes) * 60))
        }
    }
}
