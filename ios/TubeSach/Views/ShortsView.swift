import AVFoundation
import SwiftUI
import TubeSachCore

/// Trình phát riêng cho Shorts (video dọc, tự lặp lại).
@MainActor
final class ShortsPlayer: ObservableObject {
    let avPlayer = AVPlayer()
    @Published private(set) var currentId: String?
    @Published private(set) var loading = false
    @Published private(set) var error: String?
    @Published private(set) var info: PlayerData?
    @Published private(set) var paused = false
    private var task: Task<Void, Never>?
    private var endToken: NSObjectProtocol?

    init() {
        avPlayer.actionAtItemEnd = .none
        endToken = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] note in
            let item = note.object as? AVPlayerItem
            DispatchQueue.main.async { self?.ended(item) }
        }
    }

    private func ended(_ item: AVPlayerItem?) {
        guard let item, item === avPlayer.currentItem else { return }
        avPlayer.seek(to: .zero)
        avPlayer.play()
    }

    func show(_ id: String) {
        if id == currentId {
            if !paused { avPlayer.play() }
            return
        }
        task?.cancel()
        currentId = id
        info = nil
        error = nil
        loading = true
        paused = false
        avPlayer.replaceCurrentItem(with: nil)
        task = Task { [weak self] in
            do {
                let pd = try await Services.youtube.player(id)
                guard let self, !Task.isCancelled, self.currentId == id else { return }
                guard let s = pd.hlsURL, let url = URL(string: s) else { throw InnerTubeError("Không phát được video này") }
                self.info = pd
                self.avPlayer.replaceCurrentItem(with: AVPlayerItem(url: url))
                self.avPlayer.play()
                self.loading = false
            } catch {
                guard let self, !Task.isCancelled, self.currentId == id else { return }
                self.loading = false
                self.error = friendlyError(error)
            }
        }
    }

    func retry() {
        guard let id = currentId else { return }
        currentId = nil
        show(id)
    }

    func togglePause() {
        paused.toggle()
        if paused { avPlayer.pause() } else { avPlayer.play() }
    }

    func stop() {
        avPlayer.pause()
    }
}

/// Tab Shorts: vuốt lên/xuống để xem video dọc tiếp theo.
struct ShortsView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var shortsPlayer = ShortsPlayer()

    var body: some View {
        ZStack {
            Color.black
            if model.shorts.isEmpty {
                if model.shortsLoading {
                    ProgressView().tint(.white).scaleEffect(1.5)
                } else {
                    MessageView(icon: "play.square.stack", title: "Chưa có Shorts", actionTitle: "Tải lại") {
                        model.ensureShorts()
                    }
                    .environment(\.colorScheme, .dark)
                }
            } else {
                GeometryReader { proxy in
                    TabView(selection: $model.shortsIndex) {
                        ForEach(Array(model.shorts.enumerated()), id: \.element.id) { index, short in
                            ShortPage(short: short, isCurrent: index == model.shortsIndex, player: shortsPlayer)
                                .rotationEffect(.degrees(-90))
                                .frame(width: proxy.size.width, height: proxy.size.height)
                                .tag(index)
                        }
                    }
                    .frame(width: proxy.size.height, height: proxy.size.width)
                    .rotationEffect(.degrees(90), anchor: .topLeading)
                    .offset(x: proxy.size.width)
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
            }
        }
        .onAppear { playCurrent() }
        .onChange(of: model.shortsIndex) { _ in playCurrent() }
        .onChange(of: model.shorts.first?.id) { _ in playCurrent() }
        .onChange(of: model.shorts.count) { _ in playCurrent() }
        .onChange(of: model.tab) { t in
            if t == .shorts { playCurrent() } else { shortsPlayer.stop() }
        }
        .onChange(of: model.watchExpanded) { expanded in
            if expanded { shortsPlayer.stop() }
        }
    }

    private func playCurrent() {
        guard model.tab == .shorts, !model.watchExpanded else { return }
        let i = model.shortsIndex
        guard i < model.shorts.count else { return }
        shortsPlayer.show(model.shorts[i].id)
        if i >= model.shorts.count - 3 { model.loadMoreShorts() }
    }
}

struct ShortPage: View {
    @EnvironmentObject var model: AppModel
    @EnvironmentObject var store: LocalStore
    let short: ShortItem
    let isCurrent: Bool
    @ObservedObject var player: ShortsPlayer

    private var isPlayingThis: Bool { isCurrent && player.currentId == short.id }

    private var video: VideoItem {
        let info = isPlayingThis ? player.info : nil
        return VideoItem(
            id: short.id,
            title: short.title.isEmpty ? (info?.title ?? "") : short.title,
            uploader: info?.author ?? "",
            durationSec: info?.lengthSec ?? 0,
            channelId: info?.channelId,
            isShort: true
        )
    }

    @State private var showComments = false

    var body: some View {
        GeometryReader { geo in
            content(boxAspect: Double(geo.size.width / max(geo.size.height, 1)))
        }
        .sheet(isPresented: $showComments) {
            CommentsSheet(shorts: true)
                .environmentObject(model)
                .environmentObject(store)
                .presentationDetents([.medium, .large])
        }
    }

    private func content(boxAspect: Double) -> some View {
        ZStack {
            Color.black
            if isPlayingThis, let info = player.info {
                // Shorts dọc: phủ kín màn hình như YouTube; video ngang/vuông: giữ nguyên khung.
                PlayerLayerView(player: player.avPlayer, fill: Format.shouldFill(videoAspect: info.aspectRatio, boxAspect: boxAspect))
            } else {
                RemoteImage(url: short.thumbnail, contentMode: .fit, placeholder: .black)
            }
            if isPlayingThis && player.loading {
                ProgressView().tint(.white).scaleEffect(1.5)
            }
            if isPlayingThis, let e = player.error {
                VStack(spacing: 12) {
                    Text(e)
                        .multilineTextAlignment(.center)
                    Button("Thử lại") { player.retry() }
                        .buttonStyle(.borderedProminent)
                }
                .foregroundColor(.white)
                .padding(20)
                .background(Color.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 16))
                .padding(32)
            }
            if isPlayingThis && player.paused {
                Image(systemName: "play.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.white.opacity(0.85))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { if isPlayingThis { player.togglePause() } }
        .overlay(alignment: .bottom) { bottomBar }
    }

    private var bottomBar: some View {
        let v = video
        return HStack(alignment: .bottom, spacing: 12) {
            VStack(alignment: .leading, spacing: 6) {
                if !v.uploader.isEmpty {
                    Button {
                        if let id = v.channelId { model.openChannel(id) }
                    } label: {
                        Text("@" + v.uploader).font(.headline)
                    }
                    .buttonStyle(.plain)
                }
                Text(v.title)
                    .font(.subheadline)
                    .lineLimit(3)
                if let views = short.viewText {
                    Text(views).font(.caption)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(spacing: 22) {
                Button { model.toggleLike(v) } label: {
                    VStack(spacing: 4) {
                        Image(systemName: store.isLiked(v.id) ? "hand.thumbsup.fill" : "hand.thumbsup").font(.title2)
                        Text("Thích").font(.caption)
                    }
                }
                Button {
                    model.loadShortComments(short.id)
                    showComments = true
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: "text.bubble").font(.title2)
                        Text("Bình luận").font(.caption)
                    }
                }
                if let url = URL(string: "https://www.youtube.com/shorts/\(short.id)") {
                    ShareLink(item: url) {
                        VStack(spacing: 4) {
                            Image(systemName: "arrowshape.turn.up.right").font(.title2)
                            Text("Chia sẻ").font(.caption)
                        }
                    }
                }
                Button {
                    player.stop()
                    model.play(v)
                } label: {
                    VStack(spacing: 4) {
                        Image(systemName: "rectangle.on.rectangle").font(.title2)
                        Text("Xem ngang").font(.caption)
                    }
                }
            }
            .buttonStyle(.plain)
        }
        .foregroundColor(.white)
        .shadow(color: .black.opacity(0.6), radius: 4)
        .padding()
        .padding(.bottom, 8)
    }
}
