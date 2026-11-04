package com.luzart.tubesach.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Một mục trong lịch sử xem: video + vị trí đã xem tới. */
data class HistoryEntry(val video: VideoItem, val positionMs: Long, val updatedAt: Long)

data class Settings(
    /** Độ phân giải tối đa (chiều cao, ví dụ 720). */
    val maxQuality: Int = 720,
    /** Tự phát video liên quan khi hết video. */
    val autoplay: Boolean = true,
    /** Tự thu nhỏ thành cửa sổ nổi khi bấm nút Home lúc đang xem. */
    val autoPip: Boolean = true,
    /** Hệ số cỡ chữ (1.0 = lớn, 1.15 = rất lớn, 1.3 = cực lớn). */
    val textScale: Float = 1.0f,
)

/** Lưu dữ liệu trên máy (không gửi đi đâu): cài đặt, lịch sử, danh sách yêu thích. */
class LocalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tubesach", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<VideoItem>> = _favorites.asStateFlow()

    // ---------- Cài đặt ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt("maxQuality", s.maxQuality)
            .putBoolean("autoplay", s.autoplay)
            .putBoolean("autoPip", s.autoPip)
            .putFloat("textScale", s.textScale)
            .apply()
    }

    private fun loadSettings() = Settings(
        maxQuality = prefs.getInt("maxQuality", 720),
        autoplay = prefs.getBoolean("autoplay", true),
        autoPip = prefs.getBoolean("autoPip", true),
        textScale = prefs.getFloat("textScale", 1.0f),
    )

    // ---------- Lịch sử ----------

    fun positionOf(videoId: String): Long =
        _history.value.firstOrNull { it.video.id == videoId }?.positionMs ?: 0L

    fun addToHistory(video: VideoItem, positionMs: Long) {
        val now = System.currentTimeMillis()
        val list = _history.value.filterNot { it.video.id == video.id }.toMutableList()
        list.add(0, HistoryEntry(video, positionMs, now))
        _history.value = list.take(MAX_HISTORY)
        saveHistory()
    }

    fun savePosition(videoId: String, positionMs: Long) {
        val list = _history.value
        val idx = list.indexOfFirst { it.video.id == videoId }
        if (idx < 0) return
        val updated = list.toMutableList()
        updated[idx] = updated[idx].copy(positionMs = positionMs)
        _history.value = updated
        saveHistory()
    }

    fun removeFromHistory(videoId: String) {
        _history.value = _history.value.filterNot { it.video.id == videoId }
        saveHistory()
    }

    fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        _history.value.forEach {
            arr.put(
                JSONObject()
                    .put("video", it.video.toJson())
                    .put("pos", it.positionMs)
                    .put("at", it.updatedAt),
            )
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryEntry(
                VideoItem.fromJson(o.getJSONObject("video")),
                o.optLong("pos"),
                o.optLong("at"),
            )
        }
    }.getOrDefault(emptyList())

    // ---------- Yêu thích ----------

    fun isFavorite(videoId: String) = _favorites.value.any { it.id == videoId }

    fun toggleFavorite(video: VideoItem) {
        _favorites.value = if (isFavorite(video.id)) {
            _favorites.value.filterNot { it.id == video.id }
        } else {
            (listOf(video) + _favorites.value).take(MAX_FAVORITES)
        }
        val arr = JSONArray()
        _favorites.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("favorites", arr.toString()).apply()
    }

    private fun loadFavorites(): List<VideoItem> = runCatching {
        val arr = JSONArray(prefs.getString("favorites", "[]"))
        (0 until arr.length()).map { VideoItem.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    // ---------- Tìm kiếm gần đây ----------

    private val _recentSearches = MutableStateFlow(loadRecentSearches())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _recentSearches.value = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, true) }).take(10)
        val arr = JSONArray()
        _recentSearches.value.forEach { arr.put(it) }
        prefs.edit().putString("recentSearches", arr.toString()).apply()
    }

    private fun loadRecentSearches(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("recentSearches", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    // ---------- Khác ----------

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()

    companion object {
        private const val MAX_HISTORY = 200
        private const val MAX_FAVORITES = 300
    }
}
