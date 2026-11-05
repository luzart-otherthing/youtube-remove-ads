import Foundation
import XCTest
@testable import TubeSachCore

/// Gọi YouTube thật. Chỉ chạy khi đặt biến môi trường ONLINE=1:
///   ONLINE=1 swift test --filter OnlineTests
final class OnlineTests: XCTestCase {

    private var store: [String: String] = [:]

    private func makeYouTube() -> YouTube {
        YouTube(innerTube: InnerTube(loadVisitor: { [weak self] in self?.store["v"] }, saveVisitor: { [weak self] in self?.store["v"] = $0 }))
    }

    override func setUpWithError() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["ONLINE"] == "1", "Đặt ONLINE=1 để gọi YouTube thật")
    }

    func testHomeSearchWatchPlayerCommentsChannel() async throws {
        let yt = makeYouTube()
        let home = try await yt.home()
        print("Trang chủ: \(home.entries.count) mục, chip=\(home.chips.map(\.title))")
        XCTAssertNotNil(store["v"], "Phải nhận được mã khách ẩn danh")

        let trending = try await yt.trendingVietnam()
        print("Thịnh hành: \(trending.videos.count) video")
        XCTAssertFalse(trending.videos.isEmpty)

        let results = try await yt.search("sơn tùng m-tp")
        print("Tìm kiếm: \(results.videos.count) video")
        XCTAssertGreaterThan(results.videos.count, 5)
        if let more = results.continuation {
            let page2 = try await yt.searchMore(more)
            XCTAssertFalse(page2.entries.isEmpty)
        }

        let id = ProcessInfo.processInfo.environment["VIDEO_ID"] ?? "dQw4w9WgXcQ"
        let watch = try await yt.watch(id)
        print("Xem: \(watch.title) | \(watch.likeText ?? "-") | liên quan \(watch.related.count)")
        XCTAssertFalse(watch.title.isEmpty)

        let player = try await yt.player(id)
        print("HLS: \(player.hlsURL?.prefix(80) ?? "không có")")
        let hls = try XCTUnwrap(player.hlsURL)
        let (manifest, code) = try await HTTP.get(hls)
        XCTAssertEqual(code, 200)
        XCTAssertTrue(String(decoding: manifest, as: UTF8.self).contains("#EXT-X-STREAM-INF"))

        let token = try XCTUnwrap(watch.commentsToken)
        let comments = try await yt.comments(token: token)
        print("Bình luận: \(comments.items.prefix(3).map { "\($0.author): \($0.text.prefix(40))" })")
        XCTAssertFalse(comments.items.isEmpty)
        if let next = comments.continuation {
            let more = try await yt.comments(token: next)
            XCTAssertFalse(more.items.isEmpty)
        }

        if let ch = watch.channel {
            let page = try await yt.channel(ch.id)
            let videos = try await yt.channelVideos(ch.id, params: page.videosParams)
            print("Kênh: \(page.channel.name) \(page.subscriberText ?? "-") – \(videos.videos.count) video")
            XCTAssertFalse(videos.videos.isEmpty)
            let rss = await yt.subscriptionFeed([ch])
            print("Kênh đăng ký: \(rss.count) video, mới nhất: \(rss.first?.title ?? "-") (\(rss.first?.uploadedText ?? "-"))")
            XCTAssertFalse(rss.isEmpty)
        }

        let suggestions = await yt.suggestions("sơn tùng")
        print("Gợi ý: \(suggestions)")
        XCTAssertFalse(suggestions.isEmpty)

        let segs = await SponsorBlock.segments(videoId: "dQw4w9WgXcQ", categories: Set(SegmentCategory.allCases.map(\.rawValue)))
        print("SponsorBlock: \(segs.count) đoạn")
        let votes = await Dislikes.votes(videoId: "dQw4w9WgXcQ")
        print("Dislike: \(votes.map { "\($0.dislikes)" } ?? "-")")
    }

    func testShorts() async throws {
        let yt = makeYouTube()
        let seed = await yt.shortsSeed()
        print("Shorts mồi: \(seed.count)")
        XCTAssertFalse(seed.isEmpty)
        if let params = seed.first(where: { $0.sequenceParams != nil })?.sequenceParams {
            let seq = try await yt.shortsSequence(sequenceParams: params)
            print("Chuỗi Shorts: \(seq.ids.count), tiếp: \(seq.continuation != nil)")
            XCTAssertFalse(seq.ids.isEmpty)
        }
        let p = try await yt.player(seed[0].id)
        XCTAssertNotNil(p.hlsURL)
    }
}
