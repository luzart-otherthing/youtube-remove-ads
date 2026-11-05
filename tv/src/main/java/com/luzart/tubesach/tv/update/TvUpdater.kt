package com.luzart.tubesach.tv.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.luzart.tubesach.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Ready(val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Tải bản cập nhật về ngay trên TV rồi mở trình cài đặt của hệ thống.
 * (TV thường không có trình duyệt nên không thể mở link tải như trên điện thoại.)
 */
object TvUpdater {

    private const val APK_MIME = "application/vnd.android.package-archive"

    fun updatesDir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    fun download(context: Context, http: OkHttpClient, info: UpdateInfo): Flow<UpdateState> = flow {
        emit(UpdateState.Downloading(0))
        val dir = updatesDir(context)
        dir.listFiles()?.forEach { it.delete() } // xoá bản cũ cho đỡ tốn bộ nhớ TV
        val part = File(dir, "TubeSachTV-${info.version}.apk.part")
        val target = File(dir, "TubeSachTV-${info.version}.apk")
        val result = runCatching {
            http.newCall(Request.Builder().url(info.downloadUrl).build()).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Không có dữ liệu")
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                var read = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val p = (read * 100 / total).toInt().coerceIn(0, 100)
                                if (p != lastPercent) {
                                    lastPercent = p
                                    emit(UpdateState.Downloading(p))
                                }
                            }
                        }
                    }
                }
                if (total > 0 && read != total) error("Tải chưa xong")
                if (!part.renameTo(target)) error("Không lưu được file")
            }
        }
        // Bị huỷ (thoát app giữa chừng): không được phát thêm trạng thái nào nữa.
        (result.exceptionOrNull() as? CancellationException)?.let { part.delete(); throw it }
        if (result.isSuccess) {
            emit(UpdateState.Ready(target))
        } else {
            part.delete()
            emit(UpdateState.Failed("Không tải được bản cập nhật. Kiểm tra mạng rồi thử lại."))
        }
    }.flowOn(Dispatchers.IO)

    /** Mở trình cài đặt. Trả về false nếu TV chưa cho phép app này cài ứng dụng. */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            // Mở thẳng trang cấp quyền (nếu TV có), lần sau bấm "Cài đặt" lại là được.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
