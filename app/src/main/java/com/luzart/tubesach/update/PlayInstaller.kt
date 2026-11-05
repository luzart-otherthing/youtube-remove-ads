package com.luzart.tubesach.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/**
 * Cài (lại) Tube Sạch sao cho Android Auto chịu hiện nó trên màn hình xe.
 *
 * Android Auto chỉ hiện các ứng dụng "cài từ CH Play". Với phần xem video kiểu ứng dụng bản đồ,
 * nút "Nguồn không xác định" trong cài đặt Android Auto không có tác dụng. Cách mà AAAD
 * (công cụ cài CarStream) dùng: mở trình cài đặt của Android kèm thông tin "nguồn cài: CH Play".
 * Trên Android 13 trở xuống, trình cài đặt hệ thống chấp nhận thông tin này nên Android Auto coi
 * Tube Sạch như app tải từ CH Play. Từ Android 14, Android bỏ qua thông tin này.
 *
 * App không tải gì từ CH Play; chỉ cài lại đúng file APK đang có trên máy (hoặc bản cập nhật từ GitHub).
 */
object PlayInstaller {

    const val PLAY_STORE = "com.android.vending"

    /** Android 13 trở xuống mới làm được (Android 14 bỏ qua "nguồn cài"). */
    val supported: Boolean get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU

    /** Ứng dụng nào đã cài Tube Sạch (null nếu không rõ). */
    fun installerOf(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    /** Android Auto đã coi Tube Sạch là app từ CH Play chưa. */
    fun isTrusted(context: Context): Boolean = installerOf(context) == PLAY_STORE

    /** Cài lại chính bản đang dùng (giữ nguyên lịch sử, cài đặt). */
    fun reinstallSelf(context: Context): Result<Unit> = runCatching {
        val src = File(context.applicationInfo.sourceDir)
        val dir = File(context.cacheDir, "install").apply { mkdirs() }
        val apk = File(dir, "TubeSach.apk")
        src.copyTo(apk, overwrite = true)
        install(context, apk)
    }

    /** Mở trình cài đặt cho file APK, kèm "nguồn cài: CH Play" khi máy cho phép. */
    fun install(context: Context, apk: File) {
        val uri: Uri = FileProvider.getUriForFile(context, authority(context), apk)
        val intent = installIntent(uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    @Suppress("DEPRECATION") // ACTION_INSTALL_PACKAGE vẫn là cách duy nhất truyền "nguồn cài" qua trình cài đặt hệ thống
    fun installIntent(uri: Uri): Intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        .setDataAndType(uri, APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        .putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY_STORE)

    fun authority(context: Context) = context.packageName + ".files"

    private const val APK_MIME = "application/vnd.android.package-archive"
}
