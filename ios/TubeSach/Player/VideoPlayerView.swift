import AVKit
import SwiftUI

/// Khung phát video dùng trình phát gốc của iOS (AVPlayerViewController):
/// nút phát/tua, toàn màn hình, tốc độ, phụ đề, AirPlay, hình trong hình.
struct VideoPlayerView: UIViewControllerRepresentable {
    let player: PlayerController
    let autoPip: Bool

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let vc = AVPlayerViewController()
        vc.allowsPictureInPicturePlayback = true
        vc.canStartPictureInPictureAutomaticallyFromInline = autoPip
        vc.updatesNowPlayingInfoCenter = false
        vc.entersFullScreenWhenPlaybackBegins = false
        vc.exitsFullScreenWhenPlaybackEnds = false
        vc.videoGravity = .resizeAspect
        vc.delegate = context.coordinator
        player.attach(vc)
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {
        vc.canStartPictureInPictureAutomaticallyFromInline = autoPip
        if player.viewController !== vc {
            player.attach(vc)
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(player: player)
    }

    final class Coordinator: NSObject, AVPlayerViewControllerDelegate {
        weak var player: PlayerController?

        init(player: PlayerController) {
            self.player = player
        }

        func playerViewControllerWillStartPictureInPicture(_ playerViewController: AVPlayerViewController) {
            DispatchQueue.main.async { [weak self] in self?.player?.setPip(true) }
        }

        func playerViewControllerDidStopPictureInPicture(_ playerViewController: AVPlayerViewController) {
            DispatchQueue.main.async { [weak self] in self?.player?.setPip(false) }
        }

        func playerViewController(
            _ playerViewController: AVPlayerViewController,
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
        ) {
            DispatchQueue.main.async { [weak self] in
                self?.player?.restoreFromPip()
                completionHandler(true)
            }
        }
    }
}

/// Lớp hiển thị video đơn giản (không có nút), dùng cho Shorts.
struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    var fill = false

    final class LayerView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }

    func makeUIView(context: Context) -> LayerView {
        let v = LayerView()
        v.backgroundColor = .black
        v.playerLayer.videoGravity = fill ? .resizeAspectFill : .resizeAspect
        v.playerLayer.player = player
        return v
    }

    func updateUIView(_ uiView: LayerView, context: Context) {
        if uiView.playerLayer.player !== player { uiView.playerLayer.player = player }
        let gravity: AVLayerVideoGravity = fill ? .resizeAspectFill : .resizeAspect
        if uiView.playerLayer.videoGravity != gravity { uiView.playerLayer.videoGravity = gravity }
    }
}
