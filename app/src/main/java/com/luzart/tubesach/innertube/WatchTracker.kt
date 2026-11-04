package com.luzart.tubesach.innertube

import android.net.Uri

/**
 * Báo cho YouTube biết "khách ẩn danh" này đã xem video nào (giống trình duyệt chưa đăng nhập).
 * Nhờ đó trang chủ có đề xuất tiếng Việt hợp với sở thích. Có thể tắt trong Cài đặt.
 */
class WatchTracker(private val innerTube: InnerTube) {

    private var data: PlayerData? = null
    private var cpn: String = ""
    private var startedAtSec: Long = 0
    private var reportedPlayback = false
    private var lastWatchtimeSec: Long = -1

    fun begin(player: PlayerData, startPositionMs: Long) {
        data = player
        cpn = randomCpn()
        startedAtSec = startPositionMs / 1000
        reportedPlayback = false
        lastWatchtimeSec = -1
    }

    fun clear() {
        data = null
    }

    /** Gọi định kỳ khi đang phát. */
    suspend fun onProgress(positionMs: Long, enabled: Boolean) {
        val d = data ?: return
        if (!enabled) return
        val pos = positionMs / 1000
        if (!reportedPlayback) {
            reportedPlayback = true
            d.playbackTrackingUrl?.let {
                innerTube.ping(withParams(it, mapOf("ver" to "2", "cpn" to cpn, "cmt" to pos.toString())))
            }
        }
        // Báo thời lượng đã xem sau 30 giây, rồi mỗi 2 phút một lần.
        val watched = pos - startedAtSec
        val due = (lastWatchtimeSec < 0 && watched >= 30) || (lastWatchtimeSec >= 0 && pos - lastWatchtimeSec >= 120)
        if (due) {
            lastWatchtimeSec = pos
            d.watchtimeTrackingUrl?.let {
                innerTube.ping(
                    withParams(
                        it,
                        mapOf(
                            "ver" to "2",
                            "cpn" to cpn,
                            "cmt" to pos.toString(),
                            "st" to startedAtSec.toString(),
                            "et" to pos.toString(),
                            "state" to "playing",
                        ),
                    ),
                )
            }
        }
    }

    private fun withParams(url: String, params: Map<String, String>): String {
        val uri = Uri.parse(url)
        val b = uri.buildUpon().clearQuery()
        val existing = uri.queryParameterNames.filterNot { it in params.keys }
        existing.forEach { k -> uri.getQueryParameters(k).forEach { b.appendQueryParameter(k, it) } }
        params.forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }

    private fun randomCpn(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
