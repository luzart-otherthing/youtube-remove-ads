package com.luzart.tubesach.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class UpdateInfo(val version: String, val downloadUrl: String, val sizeBytes: Long = -1)

/**
 * Kiểm tra bản mới trên GitHub Releases.
 * YouTube hay thay đổi, nên khi có bản sửa lỗi, ứng dụng sẽ hiện nút "Cập nhật" thật to.
 * (Chỉ hoạt động khi kho GitHub để công khai; nếu không thì bỏ qua, không báo lỗi.)
 *
 * Một bản phát hành có cả APK điện thoại (`TubeSach-x.y.z.apk`) và APK TV (`TubeSachTV-x.y.z.apk`):
 * mỗi app chỉ lấy file có tên bắt đầu bằng [assetPrefix] của mình.
 */
object UpdateChecker {

    const val PHONE_PREFIX = "TubeSach-"
    const val TV_PREFIX = "TubeSachTV-"

    suspend fun check(
        client: OkHttpClient,
        repo: String,
        currentVersion: String,
        assetPrefix: String,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseRelease(JSONObject(response.body?.string() ?: return@use null), currentVersion, assetPrefix)
            }
        }.getOrNull()
    }

    /** Đọc JSON của GitHub Releases; trả về null nếu không có bản mới hơn cho app này. */
    fun parseRelease(json: JSONObject, currentVersion: String, assetPrefix: String): UpdateInfo? {
        val tag = json.optString("tag_name").removePrefix("v")
        val apk = pickAsset(json.optJSONArray("assets") ?: return null, assetPrefix) ?: return null
        if (!isNewer(tag, currentVersion)) return null
        return UpdateInfo(tag, apk.getString("browser_download_url"), apk.optLong("size", -1))
    }

    fun pickAsset(assets: JSONArray, assetPrefix: String): JSONObject? =
        (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { val n = it.optString("name"); n.startsWith(assetPrefix) && n.endsWith(".apk") }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
        val l = local.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
