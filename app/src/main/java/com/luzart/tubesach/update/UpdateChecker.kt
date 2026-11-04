package com.luzart.tubesach.update

import com.luzart.tubesach.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(val version: String, val downloadUrl: String)

/**
 * Kiểm tra bản mới trên GitHub Releases.
 * YouTube hay thay đổi, nên khi có bản sửa lỗi, ứng dụng sẽ hiện nút "Cập nhật" thật to.
 * (Chỉ hoạt động khi kho GitHub để công khai; nếu không thì bỏ qua, không báo lỗi.)
 */
object UpdateChecker {

    suspend fun check(client: OkHttpClient): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string() ?: return@use null)
                val tag = json.optString("tag_name").removePrefix("v")
                val assets = json.optJSONArray("assets") ?: return@use null
                val apk = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?: return@use null
                if (isNewer(tag, BuildConfig.VERSION_NAME)) {
                    UpdateInfo(tag, apk.getString("browser_download_url"))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

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
