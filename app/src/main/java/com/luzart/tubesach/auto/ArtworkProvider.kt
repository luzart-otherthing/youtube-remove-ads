package com.luzart.tubesach.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.luzart.tubesach.App
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Đưa ảnh thu nhỏ của YouTube cho Android Auto.
 *
 * Android Auto không tự tải ảnh từ link https mà chỉ đọc ảnh qua `content://` của chính app
 * (xem "Display media artwork" trong tài liệu Android for Cars). Provider này tải ảnh về bộ nhớ đệm
 * rồi trả file cho xe. Chỉ nhận ảnh từ máy chủ ảnh của YouTube/Google.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read-only")
        val ctx = context ?: throw FileNotFoundException("no context")
        val url = decode(uri) ?: throw FileNotFoundException("bad uri")
        val file = cached(ctx, url)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun cached(ctx: Context, url: String): File {
        val dir = File(ctx.cacheDir, "car_artwork").apply { mkdirs() }
        val file = File(dir, sha1(url) + ".img")
        if (file.length() > 0) return file
        val http = (ctx.applicationContext as App).http
        val tmp = File(dir, file.name + ".tmp" + Thread.currentThread().id)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw FileNotFoundException("HTTP ${r.code}")
                tmp.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true)
        } catch (e: FileNotFoundException) {
            throw e
        } catch (e: Exception) {
            throw FileNotFoundException(e.message ?: "download failed")
        } finally {
            tmp.delete()
        }
        trim(dir)
        return file
    }

    /** Giữ bộ nhớ đệm nhỏ gọn (~ vài chục MB). */
    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { it.name.endsWith(".img") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        private const val MAX_FILES = 400

        private val ALLOWED_HOSTS = listOf("ytimg.com", "ggpht.com", "googleusercontent.com", "youtube.com")

        fun authority(context: Context) = context.packageName + ".artwork"

        /** Link ảnh https → `content://<gói>.artwork/i/<link mã hoá>` (null nếu không phải ảnh của YouTube). */
        fun uriFor(context: Context, rawUrl: String?): Uri? {
            val imageUrl = when {
                rawUrl.isNullOrBlank() -> return null
                rawUrl.startsWith("//") -> "https:$rawUrl"
                rawUrl.startsWith("http://") -> "https://" + rawUrl.removePrefix("http://")
                else -> rawUrl
            }
            if (!isAllowed(imageUrl)) return null
            val encoded = Base64.encodeToString(imageUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return Uri.Builder().scheme("content").authority(authority(context)).appendPath("i").appendPath(encoded).build()
        }

        fun decode(uri: Uri): String? {
            val seg = uri.pathSegments
            if (seg.size != 2 || seg[0] != "i") return null
            val url = runCatching { String(Base64.decode(seg[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)) }.getOrNull()
            return url?.takeIf(::isAllowed)
        }

        fun isAllowed(url: String): Boolean {
            val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            if (u.scheme != "https") return false
            val host = u.host?.lowercase() ?: return false
            return ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        private fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
