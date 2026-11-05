import Foundation
import XCTest
@testable import TubeSachCore
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Đăng nhập Google (thử nghiệm): SAPISIDHASH, cookie xoay vòng, đọc thông tin tài khoản.
final class AccountTests: XCTestCase {

    func testSha1MatchesKnownVectors() {
        XCTAssertEqual(SHA1Digest.hex("abc"), "a9993e364706816aba3e25717850c26c9cd0d89d")
        XCTAssertEqual(SHA1Digest.hex(""), "da39a3ee5e6b4b0d3255bfef95601890afd80709")
        XCTAssertEqual(SHA1Digest.hex(String(repeating: "a", count: 1000)), "291e9a6c66994949b57ba5e650361e98fc36b1ba")
    }

    func testAuthHeadersLikeYouTubeWeb() throws {
        let session = AccountSession(cookies: ["SAPISID": "abc123", "SID": "s1"])
        XCTAssertTrue(session.isValid)
        let h = try XCTUnwrap(session.authHeaders(now: Date(timeIntervalSince1970: 1_700_000_000)))
        XCTAssertEqual(h["Authorization"], "SAPISIDHASH 1700000000_" + SHA1Digest.hex("1700000000 abc123 https://www.youtube.com"))
        XCTAssertEqual(h["X-Origin"], "https://www.youtube.com")
        XCTAssertEqual(h["X-Goog-AuthUser"], "0")
        XCTAssertEqual(h["Cookie"], "SAPISID=abc123; SID=s1")
        XCTAssertNil(AccountSession(cookies: ["SID": "x"]).authHeaders())
        XCTAssertTrue(AccountSession(cookies: ["__Secure-3PAPISID": "z"]).isValid)
    }

    func testRotatedCookiesAreKept() throws {
        let session = AccountSession(cookies: ["SAPISID": "a", "__Secure-1PSIDTS": "old"])
        var saved: [String: String]?
        session.onChange = { saved = $0 }
        let url = URL(string: "https://www.youtube.com/youtubei/v1/browse")!
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: [
            "Set-Cookie": "__Secure-1PSIDTS=new; Domain=.youtube.com; Path=/; Secure; HttpOnly",
        ])
        session.absorb(response)
        XCTAssertEqual(session.cookies["__Secure-1PSIDTS"], "new")
        XCTAssertEqual(saved?["__Secure-1PSIDTS"], "new")
        XCTAssertEqual(saved?["SAPISID"], "a")
        // Phản hồi của tên miền khác thì bỏ qua.
        let other = HTTPURLResponse(url: URL(string: "https://example.com/")!, statusCode: 200, httpVersion: nil,
                                    headerFields: ["Set-Cookie": "SAPISID=evil; Path=/"])
        session.absorb(other)
        XCTAssertEqual(session.cookies["SAPISID"], "a")
    }

    func testAccountInfoAndChannels() throws {
        let menu = try JSON.parse(#"""
        {"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"header":{"activeAccountHeaderRenderer":
        {"accountName":{"simpleText":"Bà Nội"},"channelHandle":{"simpleText":"@banoi"},
        "accountPhoto":{"thumbnails":[{"url":"https://yt3.ggpht.com/a=s88","width":88,"height":88}]}}}}}}}]}
        """#)
        let info = try XCTUnwrap(Parser.accountInfo(menu))
        XCTAssertEqual(info.name, "Bà Nội")
        XCTAssertEqual(info.handle, "@banoi")
        XCTAssertEqual(info.photo, "https://yt3.ggpht.com/a=s88")

        let channels = try JSON.parse(#"""
        {"contents":{"sectionListRenderer":{"contents":[{"itemSectionRenderer":{"contents":[{"shelfRenderer":{"content":{"expandedShelfContentsRenderer":{"items":[
          {"channelRenderer":{"channelId":"UC1","title":{"simpleText":"Kênh 1"},"thumbnail":{"thumbnails":[{"url":"//yt3/a","width":88}]}}},
          {"channelRenderer":{"channelId":"UC2","title":{"simpleText":"Kênh 2"}}}]}}}}]}},
          {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"MORE"}}}}]}}}
        """#)
        let r = Parser.accountChannels(channels)
        XCTAssertEqual(r.channels.map(\.id), ["UC1", "UC2"])
        XCTAssertEqual(r.channels[0].avatar, "https://yt3/a")
        XCTAssertEqual(r.continuation, "MORE")
    }
}
