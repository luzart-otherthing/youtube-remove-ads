import AVFoundation
import AVKit
import MediaPlayer
import SwiftUI
import UIKit
import TubeSachCore

/// Thông báo "Đã bỏ qua … · Hoàn tác".
struct SkipToast: Equatable {
    let segment: Segment
    let label: String
}

enum SleepTimer: Equatable {
    case off
    case at(Date)
    case endOfVideo
}

/// Điều khiển trình phát chính (AVPlayer + luồng HLS của YouTube).
///
/// - Tự đổi chất lượng theo mạng (HLS), phụ đề, AirPlay, hình trong hình có sẵn của iOS.
/// - Phát tiếp khi tắt màn hình: tách AVPlayer khỏi khung hình khi app vào nền.
/// - SponsorBlock: tự bỏ qua đoạn tài trợ.
/// - Báo lượt xem ẩn danh để trang chủ có đề xuất (tắt được trong Cài đặt).
@MainActor
final class PlayerController: NSObject, ObservableObject {
    let avPlayer = AVPlayer()
    private let store: LocalStore
    private let tracker = WatchTracker(innerTube: Services.youtube.innerTube)

    @Published private(set) var current: VideoItem?
    @Published private(set) var data: PlayerData?
    @Published private(set) var loading = false
    @Published private(set) var error: String?
    @Published private(set) var isPlaying = false
    @Published private(set) var position: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var segments: [Segment] = []
    @Published private(set) var pipActive = false
    @Published var toast: SkipToast?
    @Published var loop = false
    @Published var sleepTimer: SleepTimer = .off

    /// Gọi khi video phát hết (để tự phát video tiếp theo).
    var onEnded: (() -> Void)?
    /// Gọi khi bấm "tiếp theo" trên màn hình khoá.
    var onNext: (() -> Void)?
    /// Gọi khi thoát hình trong hình và cần mở lại trang xem.
    var onRestoreUI: (() -> Void)?

    weak var viewController: AVPlayerViewController?
    private var detached = false
    private var timeObserver: Any?
    private var statusObservation: NSKeyValueObservation?
    private var itemObservation: NSKeyValueObservation?
    private var notificationTokens: [NSObjectProtocol] = []
    private var loadTask: Task<Void, Never>?
    private var toastTask: Task<Void, Never>?
    private var skipped: Set<String> = []
    private var lastSaved: Double = 0
    private var pendingSeek: Double = 0
    private var retried = false
    private var artwork: MPMediaItemArtwork?

    init(store: LocalStore) {
        self.store = store
        super.init()
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        avPlayer.allowsExternalPlayback = true

        statusObservation = avPlayer.observe(\.timeControlStatus, options: [.new]) { [weak self] p, _ in
            let playing = p.timeControlStatus == .playing
            DispatchQueue.main.async { self?.playingChanged(playing) }
        }
        timeObserver = avPlayer.addPeriodicTimeObserver(
            forInterval: CMTime(value: 1, timescale: 2),
            queue: .main
        ) { [weak self] time in
            let seconds = time.seconds
            DispatchQueue.main.async { self?.tick(seconds) }
        }
        let center = NotificationCenter.default
        notificationTokens.append(center.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] note in
            let item = note.object as? AVPlayerItem
            DispatchQueue.main.async { self?.itemEnded(item) }
        })
        notificationTokens.append(center.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            DispatchQueue.main.async { self?.didEnterBackground() }
        })
        notificationTokens.append(center.addObserver(forName: UIApplication.willEnterForegroundNotification, object: nil, queue: .main) { [weak self] _ in
            DispatchQueue.main.async { self?.willEnterForeground() }
        })
        notificationTokens.append(center.addObserver(forName: AVAudioSession.interruptionNotification, object: nil, queue: .main) { [weak self] note in
            let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            let optionsRaw = note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt ?? 0
            DispatchQueue.main.async { self?.interruption(type: raw, options: optionsRaw) }
        })
        setupRemoteCommands()
    }

    // MARK: Phát / dừng

    /// Phát một video. `start` = vị trí bắt đầu (giây).
    func play(_ video: VideoItem, start: Double = 0, isRetry: Bool = false) {
        loadTask?.cancel()
        saveCurrentPosition()
        current = video
        data = nil
        error = nil
        loading = true
        segments = []
        skipped = []
        toast = nil
        position = start
        duration = Double(video.durationSec)
        lastSaved = start
        retried = isRetry
        artwork = nil
        avPlayer.replaceCurrentItem(with: nil)
        tracker.clear()
        try? AVAudioSession.sharedInstance().setActive(true)
        updateNowPlaying()
        loadArtwork(for: video)
        loadTask = Task { [weak self] in
            await self?.load(video, start: start)
        }
    }

    private func load(_ video: VideoItem, start: Double) async {
        do {
            let pd = try await Services.youtube.player(video.id)
            guard !Task.isCancelled, current?.id == video.id else { return }
            data = pd
            guard let hls = pd.hlsURL, let url = URL(string: hls) else {
                throw InnerTubeError("Video này chưa phát được. Hãy thử lại sau.")
            }
            if duration <= 0 { duration = Double(pd.lengthSec) }
            if var v = current {
                if v.title.isEmpty { v.title = pd.title }
                if v.uploader.isEmpty { v.uploader = pd.author }
                if v.channelId == nil { v.channelId = pd.channelId }
                if v.durationSec <= 0 { v.durationSec = pd.lengthSec }
                v.isLive = v.isLive || pd.isLive
                current = v
            }
            let item = AVPlayerItem(url: url)
            item.preferredForwardBufferDuration = 20
            applyQuality(to: item)
            observe(item)
            let length = Double(pd.lengthSec)
            pendingSeek = (!pd.isLive && start > 5 && (length <= 0 || start < length - 10)) ? start : 0
            avPlayer.replaceCurrentItem(with: item)
            avPlayer.defaultRate = store.settings.playbackSpeed
            avPlayer.playImmediately(atRate: store.settings.playbackSpeed)
            tracker.begin(pd, startPosition: pendingSeek)
            loading = false
            updateNowPlaying()

            if store.settings.sponsorBlock && !pd.isLive {
                let segs = await SponsorBlock.segments(videoId: video.id, categories: store.settings.sponsorCategories)
                if current?.id == video.id { segments = segs }
            }
        } catch {
            guard !Task.isCancelled, current?.id == video.id else { return }
            loading = false
            self.error = friendlyError(error)
        }
    }

    private func observe(_ item: AVPlayerItem) {
        itemObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            let status = item.status
            DispatchQueue.main.async { self?.itemStatusChanged(item, status) }
        }
    }

    private func itemStatusChanged(_ item: AVPlayerItem, _ status: AVPlayerItem.Status) {
        guard item === avPlayer.currentItem else { return }
        switch status {
        case .readyToPlay:
            if pendingSeek > 0 {
                let t = pendingSeek
                pendingSeek = 0
                seek(to: t)
            }
            applyCaptions(to: item)
        case .failed:
            // Link phát hết hạn hoặc mạng chập chờn: tự lấy lại link một lần.
            if let v = current, !retried {
                play(v, start: position, isRetry: true)
            } else {
                error = "Không phát được video. Bấm “Thử lại”."
                loading = false
            }
        default:
            break
        }
    }

    func togglePlay() {
        if isPlaying {
            avPlayer.pause()
        } else {
            if error != nil { retry(); return }
            avPlayer.play()
        }
    }

    func pause() { avPlayer.pause() }

    func retry() {
        guard let v = current else { return }
        play(v, start: position, isRetry: false)
    }

    func seek(to seconds: Double) {
        let t = max(0, seconds)
        position = t
        avPlayer.seek(to: CMTime(seconds: t, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        updateNowPlaying()
    }

    func skip(by delta: Double) { seek(to: position + delta) }

    /// Đóng hẳn trình phát (nút X trên trình phát mini).
    func close() {
        saveCurrentPosition()
        loadTask?.cancel()
        avPlayer.pause()
        avPlayer.replaceCurrentItem(with: nil)
        current = nil
        data = nil
        error = nil
        loading = false
        segments = []
        toast = nil
        sleepTimer = .off
        tracker.clear()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func saveCurrentPosition() {
        guard let v = current, position > 0 else { return }
        store.savePosition(v.id, position: position)
    }

    // MARK: Chất lượng, phụ đề

    func applyQuality(to item: AVPlayerItem) {
        switch store.settings.quality {
        case .auto:
            item.preferredPeakBitRate = 0
            item.preferredMaximumResolution = .zero
        case .dataSaver:
            item.preferredPeakBitRate = 1_000_000
            item.preferredMaximumResolution = CGSize(width: 854, height: 480)
        case .fixed:
            let h = Double(store.settings.fixedHeight)
            item.preferredPeakBitRate = 0
            item.preferredMaximumResolution = CGSize(width: (h * 16 / 9).rounded(), height: h)
        }
    }

    func settingsChanged() {
        if let item = avPlayer.currentItem, !detached { applyQuality(to: item) }
    }

    private func applyCaptions(to item: AVPlayerItem) {
        let lang = store.settings.captionLanguage
        guard !lang.isEmpty else { return }
        let asset = item.asset
        Task { @MainActor in
            guard let group = try? await asset.loadMediaSelectionGroup(for: .legible) else { return }
            let option = group.options.first { opt in
                let tag = opt.extendedLanguageTag ?? opt.locale?.identifier ?? ""
                return tag.lowercased().hasPrefix(lang.lowercased())
            }
            if let option { item.select(option, in: group) }
        }
    }

    // MARK: Mỗi nửa giây

    private func playingChanged(_ playing: Bool) {
        isPlaying = playing
        if !playing { saveCurrentPosition() }
        updateNowPlaying()
    }

    private func tick(_ seconds: Double) {
        guard let v = current, seconds.isFinite, avPlayer.currentItem != nil else { return }
        position = seconds
        if let d = avPlayer.currentItem?.duration.seconds, d.isFinite, d > 0 { duration = d }

        // SponsorBlock: bỏ qua đoạn tài trợ.
        let settings = store.settings
        if settings.sponsorBlock, isPlaying {
            for s in segments where !skipped.contains(s.uuid)
                && settings.sponsorCategories.contains(s.category)
                && seconds >= s.start && seconds < s.end - 0.5 {
                skipped.insert(s.uuid)
                seek(to: s.end)
                showToast(SkipToast(segment: s, label: SegmentCategory(rawValue: s.category)?.label ?? "Tài trợ"))
                break
            }
        }

        tracker.onProgress(position: seconds, enabled: settings.personalize)

        if abs(seconds - lastSaved) >= 10 {
            lastSaved = seconds
            store.savePosition(v.id, position: seconds)
        }

        if case .at(let date) = sleepTimer, Date() >= date {
            sleepTimer = .off
            avPlayer.pause()
        }
    }

    private func showToast(_ t: SkipToast) {
        toast = t
        toastTask?.cancel()
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            if !Task.isCancelled, self?.toast == t { self?.toast = nil }
        }
    }

    /// "Hoàn tác": quay lại đầu đoạn vừa bỏ qua (không bỏ qua lại nữa).
    func undoSkip() {
        guard let t = toast else { return }
        toast = nil
        seek(to: t.segment.start)
    }

    private func itemEnded(_ item: AVPlayerItem?) {
        guard let item, item === avPlayer.currentItem else { return }
        if let v = current { store.savePosition(v.id, position: 0) }
        if loop {
            seek(to: 0)
            avPlayer.play()
            return
        }
        if sleepTimer == .endOfVideo {
            sleepTimer = .off
            return
        }
        onEnded?()
    }

    // MARK: Hình trong hình, phát nền

    func attach(_ vc: AVPlayerViewController) {
        viewController = vc
        if !detached { vc.player = avPlayer }
    }

    func setPip(_ active: Bool) {
        pipActive = active
    }

    func restoreFromPip() {
        onRestoreUI?()
    }

    private func didEnterBackground() {
        guard current != nil, !pipActive else { return }
        if store.settings.backgroundPlay {
            // Tách khung hình để iOS không tự dừng; chỉ cần tiếng nên hạ chất lượng cho đỡ tốn 4G.
            viewController?.player = nil
            detached = true
            avPlayer.currentItem?.preferredPeakBitRate = 300_000
        } else {
            avPlayer.pause()
        }
    }

    private func willEnterForeground() {
        guard detached else { return }
        detached = false
        viewController?.player = avPlayer
        if let item = avPlayer.currentItem { applyQuality(to: item) }
    }

    private func interruption(type: UInt?, options: UInt) {
        guard let type, let t = AVAudioSession.InterruptionType(rawValue: type) else { return }
        if t == .ended, AVAudioSession.InterruptionOptions(rawValue: options).contains(.shouldResume) {
            avPlayer.play()
        }
    }

    // MARK: Màn hình khoá, tai nghe

    private func setupRemoteCommands() {
        let cc = MPRemoteCommandCenter.shared()
        cc.playCommand.addTarget { [weak self] _ in
            DispatchQueue.main.async { self?.avPlayer.play() }
            return .success
        }
        cc.pauseCommand.addTarget { [weak self] _ in
            DispatchQueue.main.async { self?.avPlayer.pause() }
            return .success
        }
        cc.togglePlayPauseCommand.addTarget { [weak self] _ in
            DispatchQueue.main.async { self?.togglePlay() }
            return .success
        }
        cc.skipForwardCommand.preferredIntervals = [10]
        cc.skipForwardCommand.addTarget { [weak self] _ in
            DispatchQueue.main.async { self?.skip(by: 10) }
            return .success
        }
        cc.skipBackwardCommand.preferredIntervals = [10]
        cc.skipBackwardCommand.addTarget { [weak self] _ in
            DispatchQueue.main.async { self?.skip(by: -10) }
            return .success
        }
        cc.nextTrackCommand.addTarget { [weak self] _ in
            DispatchQueue.main.async { self?.onNext?() }
            return .success
        }
        cc.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let t = e.positionTime
            DispatchQueue.main.async { self?.seek(to: t) }
            return .success
        }
    }

    private func updateNowPlaying() {
        guard let v = current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: v.title.isEmpty ? (data?.title ?? "") : v.title,
            MPMediaItemPropertyArtist: v.uploader.isEmpty ? (data?.author ?? "") : v.uploader,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? Double(avPlayer.rate) : 0.0,
        ]
        if duration > 0 { info[MPMediaItemPropertyPlaybackDuration] = duration }
        if data?.isLive == true || v.isLive { info[MPNowPlayingInfoPropertyIsLiveStream] = true }
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func loadArtwork(for video: VideoItem) {
        guard let s = video.thumbnail, let url = URL(string: s) else { return }
        Task { [weak self] in
            guard let image = await ImageLoader.shared.image(for: url) else { return }
            guard let self, self.current?.id == video.id else { return }
            self.artwork = makeArtwork(image)
            self.updateNowPlaying()
        }
    }
}

/// Tạo ảnh bìa cho màn hình khoá (hàm ngoài lớp để không bị gắn vào luồng chính).
private func makeArtwork(_ image: UIImage) -> MPMediaItemArtwork {
    MPMediaItemArtwork(boundsSize: image.size) { _ in image }
}
