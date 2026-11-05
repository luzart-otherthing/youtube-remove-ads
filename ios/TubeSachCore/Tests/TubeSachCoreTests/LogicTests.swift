import Foundation
import XCTest
@testable import TubeSachCore

final class LogicTests: XCTestCase {

    func testFindsYoutubeLinksInSharedText() {
        let id = "dQw4w9WgXcQ"
        let expected = "https://www.youtube.com/watch?v=\(id)"
        XCTAssertEqual(Format.findYoutubeURL("Xem cái này hay lắm https://youtu.be/\(id)?si=abc"), expected)
        XCTAssertEqual(Format.findYoutubeURL("https://m.youtube.com/watch?v=\(id)&t=30"), expected)
        XCTAssertEqual(Format.findYoutubeURL("https://www.youtube.com/shorts/\(id)"), expected)
        XCTAssertEqual(Format.findYoutubeURL("https://youtube.com/live/\(id)?feature=share"), expected)
        XCTAssertNil(Format.findYoutubeURL("https://zalo.me/abc"))
        XCTAssertNil(Format.findYoutubeURL(nil))
        XCTAssertEqual(Format.youtubeVideoId(inText: "https://youtu.be/\(id)"), id)
        XCTAssertNil(Format.youtubeVideoId(inText: "nhạc v=\(id)"))
    }

    func testComparesVersions() {
        XCTAssertTrue(Format.isNewer("1.0.12", than: "1.0.9"))
        XCTAssertTrue(Format.isNewer("1.1.0", than: "1.0.99"))
        XCTAssertFalse(Format.isNewer("1.0.5", than: "1.0.5"))
        XCTAssertFalse(Format.isNewer("1.0.4", than: "1.0.5-debug"))
    }

    func testFormatsLikeYouTubeVietnam() {
        XCTAssertEqual(Format.parseDuration("1:02:03"), 3723)
        XCTAssertEqual(Format.parseDuration("3:05"), 185)
        XCTAssertEqual(Format.parseDuration("abc"), 0)
        XCTAssertEqual(Format.duration(3723), "1:02:03")
        XCTAssertEqual(Format.duration(185), "3:05")
        XCTAssertEqual(Format.views(1_234_567), "1,2 Tr lượt xem")
        XCTAssertEqual(Format.views(15_000), "15 N lượt xem")
        XCTAssertEqual(Format.views(999), "999 lượt xem")
        XCTAssertEqual(Format.count(2_000_000_000), "2 T")
        let now = Date(timeIntervalSince1970: 1_000_000)
        XCTAssertEqual(Format.relativeTime(now.addingTimeInterval(-30), now: now), "Vừa xong")
        XCTAssertEqual(Format.relativeTime(now.addingTimeInterval(-3 * 3600), now: now), "3 giờ trước")
        XCTAssertEqual(Format.relativeTime(now.addingTimeInterval(-15 * 86400), now: now), "2 tuần trước")
        XCTAssertEqual(Format.approxAge("3 ngày trước"), 3 * 86400)
        XCTAssertEqual(Format.approxAge("Đã phát trực tiếp 2 tuần trước"), 14 * 86400)
        XCTAssertEqual(Format.approxAge("1 năm trước"), 365 * 86400)
        XCTAssertNil(Format.approxAge("Hôm qua"))
    }

    func testJSONKeepsKeyOrderAndUnicode() throws {
        let json = try JSON.parse(#"{"b":{"x":{"id":1}},"a":{"x":{"id":2}},"s":"Việt Nam 😀","n":-1.5e2,"t":true,"z":null}"#)
        XCTAssertEqual(json.objectValue?.keys, ["b", "a", "s", "n", "t", "z"])
        XCTAssertEqual(findFirst(json, "x")?["id"]?.intValue, 1)
        XCTAssertEqual(findAll(json, "x").count, 2)
        XCTAssertEqual(json.s("s"), "Việt Nam 😀")
        XCTAssertEqual(json["n"]?.doubleValue, -150)
        XCTAssertEqual(json["t"]?.boolValue, true)
        XCTAssertThrowsError(try JSON.parse("{\"a\":}"))
        XCTAssertEqual(textOf(try JSON.parse(#"{"runs":[{"text":"1 Tr"},{"text":" lượt xem"}]}"#)), "1 Tr lượt xem")
    }

    func testWatchTrackerKeepsExistingParams() {
        let url = WatchTracker.withParams("https://s.youtube.com/api/stats/playback?ns=yt&cmt=0&docid=abc", ["cmt": "12", "ver": "2"])
        XCTAssertTrue(url.contains("ns=yt"))
        XCTAssertTrue(url.contains("docid=abc"))
        XCTAssertTrue(url.contains("cmt=12"))
        XCTAssertFalse(url.contains("cmt=0"))
        XCTAssertEqual(WatchTracker.randomCpn().count, 16)
    }

    func testMergedVideoKeepsRicherInfo() {
        let old = VideoItem(id: "abc", title: "Cũ", uploader: "Kênh", channelId: "UC1", channelAvatar: "a.jpg", viewText: "1 N lượt xem")
        var new = VideoItem(id: "abc", title: "", uploader: "")
        new.channelAvatar = nil
        let m = new.merged(with: old)
        XCTAssertEqual(m.title, "Cũ")
        XCTAssertEqual(m.channelAvatar, "a.jpg")
        XCTAssertEqual(m.channelId, "UC1")
        XCTAssertEqual(m.viewText, "1 N lượt xem")
    }
}
