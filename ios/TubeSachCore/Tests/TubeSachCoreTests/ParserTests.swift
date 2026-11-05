import Foundation
import XCTest
@testable import TubeSachCore

/// Kiểm tra bộ đọc dữ liệu với phản hồi thật của YouTube (dùng chung bộ dữ liệu với bản Android).
final class ParserTests: XCTestCase {

    private func fixture(_ name: String) throws -> JSON {
        // ios/TubeSachCore/Tests/TubeSachCoreTests/ParserTests.swift → gốc repo
        let root = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent()
        let path = root.appendingPathComponent("app/src/test/resources/fixtures/\(name).json.gz").path
        let p = Process()
        p.executableURL = URL(fileURLWithPath: "/usr/bin/env")
        p.arguments = ["gzip", "-dc", path]
        let pipe = Pipe()
        p.standardOutput = pipe
        try p.run()
        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        p.waitUntilExit()
        XCTAssertEqual(p.terminationStatus, 0, "Không đọc được \(path)")
        return try JSON.parse(data)
    }

    func testHomeFeedHasVietnameseVideosChipsAndShorts() throws {
        let page = Parser.browseGrid(try fixture("home"))
        let videos = page.videos
        print("Trang chủ: \(videos.count) video, chip=\(page.chips.map(\.title))")
        XCTAssertGreaterThanOrEqual(videos.count, 10)
        let v = videos[0]
        print("Mẫu: \(v.title) | \(v.uploader) | \(v.viewText ?? "-") | \(v.uploadedText ?? "-") | \(v.durationText ?? "-")")
        XCTAssertFalse(v.title.isEmpty)
        XCTAssertFalse(v.uploader.isEmpty)
        XCTAssertNotNil(v.thumbnail)
        XCTAssertGreaterThan(videos.filter { $0.channelAvatar != nil }.count, videos.count / 2)
        XCTAssertGreaterThan(videos.filter { $0.viewText?.contains("lượt xem") == true }.count, videos.count / 2)
        XCTAssertTrue(page.chips.contains { $0.title == "Tất cả" })
        XCTAssertTrue(page.chips.dropFirst().allSatisfy { $0.token != nil })
        XCTAssertTrue(page.entries.contains { if case .shortsShelf(_, let s) = $0 { return !s.isEmpty }; return false })
        XCTAssertNotNil(page.continuation)
    }

    func testSearchResults() throws {
        let page = Parser.search(try fixture("search"))
        let videos = page.videos
        XCTAssertGreaterThanOrEqual(videos.count, 10)
        let v = videos[0]
        print("Tìm kiếm: \(v.title) | \(v.viewText ?? "-") | \(v.uploadedText ?? "-") | \(v.durationSec)s | \(v.channelId ?? "-")")
        XCTAssertGreaterThan(v.durationSec, 0)
        XCTAssertTrue(v.channelId?.hasPrefix("UC") == true)
        XCTAssertTrue(page.entries.contains { if case .shortsShelf = $0 { return true }; return false })
        XCTAssertNotNil(page.continuation)
    }

    func testWatchPage() throws {
        let w = Parser.watch(try fixture("next"), videoId: "fbiZrIlPNx8")
        print("Xem: \(w.title) | \(w.viewText ?? "-") | thích=\(w.likeText ?? "-") | kênh=\(w.channel?.name ?? "-") | \(w.subscriberText ?? "-") | bình luận=\(w.commentCountText ?? "-") | liên quan=\(w.related.count)")
        XCTAssertTrue(w.title.hasPrefix("CHỜ NGÀY ANH NHẬN RA EM"))
        XCTAssertEqual(w.viewText, "64.667 lượt xem")
        XCTAssertNotNil(w.likeText)
        XCTAssertNotNil(w.channel)
        XCTAssertFalse(w.description.isEmpty)
        XCTAssertGreaterThanOrEqual(w.related.count, 10)
        XCTAssertNotNil(w.commentsToken)
    }

    func testRelatedVideosContinuation() throws {
        // Trang xem chỉ trả ~20 video liên quan; phần còn lại nằm sau token (onResponseReceivedEndpoints).
        let watch = Parser.watch(try fixture("next"), videoId: "x")
        XCTAssertNotNil(watch.relatedContinuation, "Phải có token để tải thêm video liên quan")
        let page = Parser.continuation(try fixture("next_cont"))
        print("Tải thêm video liên quan: \(page.videos.count) video, còn tiếp=\(page.continuation != nil)")
        XCTAssertGreaterThanOrEqual(page.videos.count, 10)
        XCTAssertNotNil(page.continuation)
        // Khi chưa đăng nhập YouTube không trả mã đăng bình luận.
        XCTAssertNil(Parser.createCommentParams(try fixture("next")))
    }

    func testPortraitShortsKeepHighQuality() throws {
        let json = try JSON.parse(#"""
        {"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"s","lengthSeconds":"30"},
         "streamingData":{"hlsManifestUrl":"https://manifest.googlevideo.com/x","adaptiveFormats":[
          {"itag":137,"mimeType":"video/mp4; codecs=\"avc1.640028\"","width":1080,"height":1920},
          {"itag":136,"mimeType":"video/mp4; codecs=\"avc1.4d401f\"","width":720,"height":1280},
          {"itag":135,"mimeType":"video/mp4; codecs=\"avc1.4d401e\"","width":480,"height":854},
          {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\""}]}}
        """#)
        let p = try PlayerData.parse(json, videoId: "s")
        XCTAssertTrue(p.isPortrait)
        XCTAssertEqual(p.availableHeights, [1080, 720, 480])
        XCTAssertEqual(p.aspectRatio, 1080.0 / 1920.0, accuracy: 0.001)
        let landscape = try PlayerData.parse(try fixture("player"), videoId: "dQw4w9WgXcQ")
        XCTAssertFalse(landscape.isPortrait)
    }

    func testShortsFillOnlyWhenAspectIsClose() {
        let phone = 1179.0 / 2556.0
        XCTAssertTrue(Format.shouldFill(videoAspect: 9.0 / 16.0, boxAspect: phone)) // Shorts dọc: phủ kín
        XCTAssertFalse(Format.shouldFill(videoAspect: 16.0 / 9.0, boxAspect: phone)) // video ngang: giữ khung
        XCTAssertFalse(Format.shouldFill(videoAspect: 1, boxAspect: phone)) // video vuông
        XCTAssertFalse(Format.shouldFill(videoAspect: 0, boxAspect: phone)) // chưa biết kích thước
    }

    func testPlayerHasHlsAndCaptions() throws {
        let p = try PlayerData.parse(try fixture("player"), videoId: "dQw4w9WgXcQ")
        print("Độ phân giải: \(p.availableHeights), phụ đề: \(p.captions.map(\.name))")
        XCTAssertTrue(p.hlsURL?.hasPrefix("https://manifest.googlevideo.com/") == true)
        XCTAssertTrue(p.availableHeights.contains(720))
        XCTAssertFalse(p.captions.isEmpty)
        XCTAssertTrue(p.captions[0].vttURL(translateTo: "vi").hasSuffix("&fmt=vtt&tlang=vi"))
        XCTAssertNotNil(p.playbackTrackingURL)
        XCTAssertGreaterThan(p.lengthSec, 0)
    }

    func testPlayerErrorShowsReason() throws {
        let json = try JSON.parse(#"{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Video này giới hạn độ tuổi"}}"#)
        XCTAssertThrowsError(try PlayerData.parse(json, videoId: "x")) { e in
            XCTAssertEqual(e.localizedDescription, "Video này giới hạn độ tuổi")
        }
    }

    func testCommentsParsing() throws {
        let body = #"""
        {"onResponseReceivedEndpoints":[
          {"reloadContinuationItemsCommand":{"slot":"RELOAD_CONTINUATION_SLOT_HEADER","continuationItems":[{"commentsHeaderRenderer":{}}]}},
          {"reloadContinuationItemsCommand":{"slot":"RELOAD_CONTINUATION_SLOT_BODY","continuationItems":[
            {"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"K1","pinnedText":"Đã ghim"}}}},
            {"commentThreadRenderer":{"commentViewModel":{"commentViewModel":{"commentKey":"K2"}}}},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}
          ]}}],
         "frameworkUpdates":{"entityBatchUpdate":{"mutations":[
           {"entityKey":"K1","payload":{"commentEntityPayload":{"properties":{"commentId":"c1","content":{"content":"Hay quá"},"publishedTime":"2 ngày trước"},"author":{"displayName":"@an","avatarThumbnailUrl":"//yt3.ggpht.com/a"},"toolbar":{"likeCountNotliked":"1,2 N","replyCount":"5"}}}},
           {"entityKey":"K2","payload":{"commentEntityPayload":{"properties":{"commentId":"c2","content":{"content":"Cảm ơn"}},"author":{"displayName":"@binh"},"toolbar":{"likeCountNotliked":" ","replyCount":""}}}}
         ]}}}
        """#
        let page = Parser.comments(try JSON.parse(body))
        XCTAssertEqual(page.items.count, 2)
        XCTAssertEqual(page.items[0].text, "Hay quá")
        XCTAssertEqual(page.items[0].author, "@an")
        XCTAssertEqual(page.items[0].avatar, "https://yt3.ggpht.com/a")
        XCTAssertEqual(page.items[0].likes, "1,2 N")
        XCTAssertEqual(page.items[0].replies, 5)
        XCTAssertTrue(page.items[0].pinned)
        XCTAssertNil(page.items[1].likes)
        XCTAssertFalse(page.items[1].pinned)
        XCTAssertEqual(page.continuation, "NEXT")
    }

    func testSponsorBlockParsing() {
        let body = #"""
        [{"videoID":"abc","segments":[{"category":"sponsor","segment":[10.5,20.0],"UUID":"u1","actionType":"skip"}]},
         {"videoID":"other","segments":[{"category":"intro","segment":[1,2],"UUID":"u2"}]}]
        """#
        let segs = SponsorBlock.parse(Data(body.utf8), videoId: "abc")
        XCTAssertEqual(segs.count, 1)
        XCTAssertEqual(segs[0].start, 10.5)
        XCTAssertEqual(segs[0].end, 20.0)
        XCTAssertEqual(String(SponsorBlock.sha256Hex("dQw4w9WgXcQ").prefix(4)), "5f6b")
        XCTAssertEqual(SponsorBlock.sha256Hex(""), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        XCTAssertEqual(SponsorBlock.sha256Hex(String(repeating: "a", count: 200)),
                       "c2a908d98f5df987ade41b5fce213067efbcc21ef2240212a41e54b5e7c28ae5")
    }

    func testSubscriptionRSS() {
        let xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns:media="http://search.yahoo.com/mrss/" xmlns="http://www.w3.org/2005/Atom">
         <title>Kênh A</title>
         <entry>
          <yt:videoId>dQw4w9WgXcQ</yt:videoId>
          <title>Bài hát &amp; lời</title>
          <link rel="alternate" href="https://www.youtube.com/watch?v=dQw4w9WgXcQ"/>
          <published>2026-11-01T10:00:00+00:00</published>
          <media:group><media:thumbnail url="https://i1.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg" width="480" height="360"/>
          <media:community><media:statistics views="1234567"/></media:community></media:group>
         </entry>
         <entry>
          <yt:videoId>abcdefghijk</yt:videoId>
          <title>Short</title>
          <link rel="alternate" href="https://www.youtube.com/shorts/abcdefghijk"/>
          <published>2026-11-04T10:00:00+00:00</published>
         </entry>
        </feed>
        """
        let now = ISO8601DateFormatter().date(from: "2026-11-05T10:00:00Z")!
        let items = YouTube.parseRSS(Data(xml.utf8), channel: ChannelRef(id: "UC1", name: "Kênh A"), now: now)
        XCTAssertEqual(items.count, 2)
        XCTAssertEqual(items[0].0.title, "Bài hát & lời")
        XCTAssertEqual(items[0].0.viewText, "1,2 Tr lượt xem")
        XCTAssertEqual(items[0].0.uploadedText, "4 ngày trước")
        XCTAssertEqual(items[0].0.thumbnail, "https://i1.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
        XCTAssertFalse(items[0].0.isShort)
        XCTAssertTrue(items[1].0.isShort)
    }

    func testChannelTabLockupHasViewsAndDateButNoChannelName() throws {
        let body = #"""
        {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"richGridRenderer":{"contents":[
          {"richItemRenderer":{"content":{"lockupViewModel":{"contentId":"PXC_PYeB6F8","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
            "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i.ytimg.com/vi/PXC_PYeB6F8/hq.jpg","width":480}]},
              "overlays":[{"thumbnailOverlayBadgeViewModel":{"thumbnailBadges":[{"thumbnailBadgeViewModel":{"text":"4:12"}}]}}]}},
            "metadata":{"lockupMetadataViewModel":{"title":{"content":"Angels On My Side"},
              "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[{"text":{"content":"116 N lượt xem"}},{"text":{"content":"4 tháng trước"}}]}]}}}}}}}}
        ]}}}}]}}}
        """#
        let page = Parser.browseGrid(try JSON.parse(body))
        let v = try XCTUnwrap(page.videos.first)
        XCTAssertEqual(v.uploader, "")
        XCTAssertEqual(v.viewText, "116 N lượt xem")
        XCTAssertEqual(v.uploadedText, "4 tháng trước")
        XCTAssertEqual(v.durationSec, 252)
    }

    func testSuggestionsParsing() {
        let body = #"["sơn tùng",["sơn tùng m-tp","sơn tùng karaoke"],[],{}]"#
        XCTAssertEqual(YouTube.parseSuggestions(Data(body.utf8)), ["sơn tùng m-tp", "sơn tùng karaoke"])
    }
}
