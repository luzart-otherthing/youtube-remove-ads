package com.luzart.tubesach.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient

/**
 * Theo dõi mạng: khi có mạng trở lại (bật màn hình, đổi Wi-Fi ↔ 4G…), bỏ các kết nối cũ
 * và báo cho app tải lại những gì đang lỗi.
 *
 * Vì sao cần: khi tắt màn hình lâu, nhà mạng/router đã cắt các kết nối đang mở nhưng điện thoại
 * không biết. Yêu cầu tiếp theo đi vào kết nối "chết" đó và treo tới khi hết thời gian chờ (≈30 giây)
 * → app quay vòng tải rất lâu rồi báo lỗi. Bỏ hết kết nối cũ là cách sửa.
 */
object NetworkMonitor {

    private val _reconnected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Phát ra mỗi khi có mạng (mới) sau khi mất mạng hoặc đổi mạng. */
    val reconnected: SharedFlow<Unit> = _reconnected.asSharedFlow()

    private lateinit var http: OkHttpClient
    private val main = Handler(Looper.getMainLooper())
    private var current: Network? = null
    private var blocked = false

    fun init(context: Context, client: OkHttpClient) {
        http = client
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        current = runCatching { cm.activeNetwork }.getOrNull()
        blocked = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.post {
                    val previous = current
                    current = network
                    // Lần gọi đầu tiên (đúng mạng đang dùng) không tính; chỉ báo khi có mạng mới / có lại mạng.
                    if (previous != network) onReconnected()
                }
            }

            override fun onLost(network: Network) {
                main.post { if (current == network) current = null }
            }

            /** Android 10+: hết bị chặn mạng (ví dụ máy vừa ra khỏi chế độ ngủ sâu Doze khi bật màn hình). */
            override fun onBlockedStatusChanged(network: Network, isBlocked: Boolean) {
                main.post {
                    val was = blocked
                    blocked = isBlocked
                    if (was && !isBlocked) onReconnected()
                }
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback) // chỉ theo dõi mạng đang dùng (Wi-Fi hoặc 4G)
            } else {
                val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
                cm.registerNetworkCallback(request, callback)
            }
        }
    }

    /** Bỏ mọi kết nối đang rảnh (có thể đã "chết") để yêu cầu sau mở kết nối mới. */
    fun dropIdleConnections() {
        if (::http.isInitialized) runCatching { http.connectionPool.evictAll() }
    }

    private fun onReconnected() {
        dropIdleConnections()
        _reconnected.tryEmit(Unit)
    }
}
