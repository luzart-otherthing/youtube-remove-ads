package com.luzart.tubesach

import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.innertube.Parser
import com.luzart.tubesach.innertube.PlayerData
import com.luzart.tubesach.sponsor.SponsorBlock
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

/** Kiểm tra bộ đọc dữ liệu với phản hồi thật của YouTube (đã lưu sẵn). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ParserTest {

    private fun fixture(name: String): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name.json.gz")
        return JSONObject(GZIPInputStream(stream).bufferedReader().readText())
    }

    @Test
    fun homeFeedHasVietnameseVideosChipsAndShorts() {
        val page = Parser.browseGrid(fixture("home"))
        val videos = page.entries.filterIsInstance<FeedEntry.Video>().map { it.video }
        println("Trang chủ: ${videos.size} video, chip=${page.chips.map { it.title }}, kệ=${page.entries.filter { it !is FeedEntry.Video }.map { it.javaClass.simpleName }}")
        assertTrue(videos.size >= 10)
        val v = videos.first()
        println("Mẫu: ${v.title} | ${v.uploader} | ${v.viewText} | ${v.uploadedText} | ${v.durationText} | avatar=${v.channelAvatar != null}")
        assertTrue(v.title.isNotBlank())
        assertTrue(v.uploader.isNotBlank())
        assertNotNull(v.thumbnail)
        assertTrue(videos.count { it.channelAvatar != null } > videos.size / 2)
        assertTrue(videos.count { it.viewText?.contains("lượt xem") == true } > videos.size / 2)
        assertTrue(page.chips.any { it.title == "Tất cả" })
        assertTrue(page.chips.drop(1).all { it.token != null })
        assertTrue(page.entries.any { it is FeedEntry.ShortsShelf && it.shorts.isNotEmpty() })
        assertNotNull(page.continuation)
    }

    @Test
    fun searchResults() {
        val page = Parser.search(fixture("search"))
        val videos = page.entries.filterIsInstance<FeedEntry.Video>().map { it.video }
        assertTrue(videos.size >= 10)
        val v = videos.first()
        println("Tìm kiếm: ${v.title} | ${v.viewText} | ${v.uploadedText} | ${v.durationSec}s | ${v.channelId}")
        assertTrue(v.durationSec > 0)
        assertTrue(v.channelId!!.startsWith("UC"))
        assertTrue(page.entries.any { it is FeedEntry.ShortsShelf })
        assertNotNull(page.continuation)
    }

    @Test
    fun watchPage() {
        val w = Parser.watch(fixture("next"), "fbiZrIlPNx8")
        println("Xem: ${w.title} | ${w.viewText} | ${w.relativeDateText} | thích=${w.likeText} | kênh=${w.channel} | ${w.subscriberText} | bình luận=${w.commentCountText} | liên quan=${w.related.size}")
        assertTrue(w.title.startsWith("CHỜ NGÀY ANH NHẬN RA EM"))
        assertEquals("64.667 lượt xem", w.viewText)
        assertNotNull(w.likeText)
        assertNotNull(w.channel)
        assertTrue(w.description.isNotBlank())
        assertTrue(w.related.size >= 10)
    }

    @Test
    fun playerBuildsDashManifestWithCaptions() {
        val p = PlayerData.parse(fixture("player"), "dQw4w9WgXcQ")
        val mpd = p.dashManifest()
        assertNotNull(mpd)
        println("Độ phân giải: ${p.availableHeights}, phụ đề: ${p.captions.map { it.name }}")
        assertTrue(720 in p.availableHeights)
        assertTrue(mpd!!.contains("<SegmentBase indexRange="))
        assertTrue(mpd.contains("mp4a.40.2"))
        assertTrue(p.captions.isNotEmpty())
        assertTrue(p.captions.first().vttUrl("vi").endsWith("&fmt=vtt&tlang=vi"))
        assertNotNull(p.playbackTrackingUrl)
    }

    @Test
    fun sponsorBlockParsing() {
        val body = """[{"videoID":"abc","segments":[{"category":"sponsor","segment":[10.5,20.0],"UUID":"u1","actionType":"skip"}]},
            {"videoID":"other","segments":[{"category":"intro","segment":[1,2],"UUID":"u2"}]}]"""
        val segs = SponsorBlock.parse(body, "abc")
        assertEquals(1, segs.size)
        assertEquals(10_500, segs[0].startMs)
        assertEquals(20_000, segs[0].endMs)
        assertEquals("5f6b", SponsorBlock.sha256("dQw4w9WgXcQ").take(4))
    }
}
