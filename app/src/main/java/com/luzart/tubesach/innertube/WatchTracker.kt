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
    private var playbackUrl: String? = null
    private var watchtimeUrl: String? = null
    private var authed = false

    fun begin(player: PlayerData, startPositionMs: Long) {
        data = player
        playbackUrl = player.playbackTrackingUrl
        watchtimeUrl = player.watchtimeTrackingUrl
        authed = false
        cpn = randomCpn()
        startedAtSec = startPositionMs / 1000
        reportedPlayback = false
        lastWatchtimeSec = -1
    }

    fun clear() {
        data = null
    }

    /**
     * Đã đăng nhập: dùng link thống kê của /player WEB (gắn với tài khoản) để video được ghi vào
     * lịch sử xem của tài khoản Google.
     */
    fun useAccountUrls(videoId: String, playback: String?, watchtime: String?) {
        if (data?.videoId != videoId || playback == null) return
        playbackUrl = playback
        watchtimeUrl = watchtime
        authed = true
        reportedPlayback = false // báo lại lượt xem cho tài khoản (lần trước là ẩn danh)
    }

    /** Gọi định kỳ khi đang phát. */
    suspend fun onProgress(positionMs: Long, enabled: Boolean) {
        val d = data ?: return
        if (!enabled) return
        val pos = positionMs / 1000
        if (!reportedPlayback) {
            reportedPlayback = true
            playbackUrl?.let {
                innerTube.ping(withParams(it, mapOf("ver" to "2", "cpn" to cpn, "cmt" to pos.toString())), authed)
            }
        }
        // Báo thời lượng đã xem sau 30 giây, rồi mỗi 2 phút một lần.
        val watched = pos - startedAtSec
        val due = (lastWatchtimeSec < 0 && watched >= 30) || (lastWatchtimeSec >= 0 && pos - lastWatchtimeSec >= 120)
        if (due) {
            lastWatchtimeSec = pos
            watchtimeUrl?.let {
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
                    authed,
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
