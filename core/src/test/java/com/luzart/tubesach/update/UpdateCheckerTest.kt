package com.luzart.tubesach.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Một bản phát hành có cả APK điện thoại lẫn APK TV: mỗi app phải lấy đúng file của mình. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckerTest {

    private fun release(tag: String, vararg names: String) = JSONObject(
        """{"tag_name":"$tag","assets":[${names.joinToString(",") {
            """{"name":"$it","size":123,"browser_download_url":"https://example.com/$it"}"""
        }}]}""",
    )

    @Test
    fun phoneAndTvPickTheirOwnApk() {
        // Thứ tự trong danh sách không được ảnh hưởng tới kết quả.
        for (r in listOf(
            release("v2.0.9", "TubeSachTV-2.0.9.apk", "TubeSach-2.0.9.apk"),
            release("v2.0.9", "TubeSach-2.0.9.apk", "TubeSachTV-2.0.9.apk"),
        )) {
            val phone = UpdateChecker.parseRelease(r, "2.0.3", UpdateChecker.PHONE_PREFIX)
            val tv = UpdateChecker.parseRelease(r, "2.0.3", UpdateChecker.TV_PREFIX)
            assertEquals("https://example.com/TubeSach-2.0.9.apk", phone?.downloadUrl)
            assertEquals("https://example.com/TubeSachTV-2.0.9.apk", tv?.downloadUrl)
            assertEquals("2.0.9", tv?.version)
            assertEquals(123L, tv?.sizeBytes)
        }
    }

    @Test
    fun noUpdateWhenSameVersionOrNoMatchingApk() {
        assertNull(UpdateChecker.parseRelease(release("v2.0.9", "TubeSachTV-2.0.9.apk"), "2.0.9", UpdateChecker.TV_PREFIX))
        // Bản phát hành cũ chỉ có APK điện thoại → app TV không được tải nhầm.
        assertNull(UpdateChecker.parseRelease(release("v2.0.9", "TubeSach-2.0.9.apk"), "2.0.3", UpdateChecker.TV_PREFIX))
        assertNull(UpdateChecker.parseRelease(JSONObject("{}"), "2.0.3", UpdateChecker.TV_PREFIX))
    }

    @Test
    fun comparesVersions() {
        assertTrue(UpdateChecker.isNewer("2.0.12", "2.0.9"))
        assertFalse(UpdateChecker.isNewer("2.0.9", "2.0.9-debug"))
    }
}
