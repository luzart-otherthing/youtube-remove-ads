package com.luzart.tubesach.data

import android.content.Context
import android.content.SharedPreferences
import com.luzart.tubesach.sponsor.SegmentCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Một mục trong lịch sử xem: video + vị trí đã xem tới. */
data class HistoryEntry(val video: VideoItem, val positionMs: Long, val updatedAt: Long)

enum class ThemeMode(val label: String) { SYSTEM("Dùng giao diện của thiết bị"), LIGHT("Giao diện sáng"), DARK("Giao diện tối") }

/** Chế độ chất lượng giống menu "Chất lượng" của YouTube. */
enum class QualityMode(val label: String, val description: String) {
    AUTO("Tự động (khuyên dùng)", "Điều chỉnh để mang lại trải nghiệm tốt nhất cho điều kiện của bạn"),
    HIGHER("Chất lượng hình ảnh cao hơn", "Sử dụng nhiều dữ liệu hơn"),
    DATA_SAVER("Tiết kiệm dữ liệu", "Chất lượng hình ảnh thấp hơn"),
    ADVANCED("Nâng cao", "Chọn một độ phân giải cụ thể"),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1.0f,
    val autoplay: Boolean = true,
    val autoPip: Boolean = true,
    val quality: QualityMode = QualityMode.AUTO,
    /** Độ phân giải khi chọn "Nâng cao". */
    val fixedHeight: Int = 720,
    val sponsorBlock: Boolean = true,
    val sponsorCategories: Set<String> = SegmentCategory.entries.filter { it.defaultOn }.map { it.key }.toSet(),
    val returnDislike: Boolean = true,
    val hideShorts: Boolean = false,
    /** Cho YouTube biết đã xem gì (ẩn danh) để trang chủ có đề xuất. */
    val personalize: Boolean = true,
    /** Ngôn ngữ phụ đề tự bật (rỗng = tắt). */
    val captionLanguage: String = "",
    val doubleTapSeekSec: Int = 10,
    /** Dùng VP9 (tốn ít dữ liệu, có 2K/4K) khi máy giải mã phần cứng được. */
    val preferVp9: Boolean = true,
)

/** Lưu dữ liệu trên máy (không gửi đi đâu): cài đặt, lịch sử, kênh đăng ký, video đã lưu. */
class LocalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tubesach", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    /** "Xem sau" (trước đây là "Yêu thích"). */
    private val _favorites = MutableStateFlow(loadVideos("favorites"))
    val favorites: StateFlow<List<VideoItem>> = _favorites.asStateFlow()

    private val _liked = MutableStateFlow(loadVideos("liked"))
    val liked: StateFlow<List<VideoItem>> = _liked.asStateFlow()

    private val _disliked = MutableStateFlow(loadStrings("disliked").toSet())
    val disliked: StateFlow<Set<String>> = _disliked.asStateFlow()

    private val _subscriptions = MutableStateFlow(loadChannels())
    val subscriptions: StateFlow<List<ChannelRef>> = _subscriptions.asStateFlow()

    private val _hidden = MutableStateFlow(loadStrings("hidden").toSet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadStrings("recentSearches"))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // ---------- Cài đặt ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putString("theme", s.theme.name)
            .putFloat("textScale", s.textScale)
            .putBoolean("autoplay", s.autoplay)
            .putBoolean("autoPip", s.autoPip)
            .putString("quality", s.quality.name)
            .putInt("fixedHeight", s.fixedHeight)
            .putBoolean("sponsorBlock", s.sponsorBlock)
            .putString("sponsorCategories", s.sponsorCategories.joinToString(","))
            .putBoolean("returnDislike", s.returnDislike)
            .putBoolean("hideShorts", s.hideShorts)
            .putBoolean("personalize", s.personalize)
            .putString("captionLanguage", s.captionLanguage)
            .putInt("doubleTapSeekSec", s.doubleTapSeekSec)
            .putBoolean("preferVp9", s.preferVp9)
            .apply()
    }

    private fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", d.theme.name)!!) }.getOrDefault(d.theme),
            textScale = prefs.getFloat("textScale", d.textScale),
            autoplay = prefs.getBoolean("autoplay", d.autoplay),
            autoPip = prefs.getBoolean("autoPip", d.autoPip),
            quality = runCatching { QualityMode.valueOf(prefs.getString("quality", d.quality.name)!!) }.getOrDefault(d.quality),
            fixedHeight = prefs.getInt("fixedHeight", d.fixedHeight),
            sponsorBlock = prefs.getBoolean("sponsorBlock", d.sponsorBlock),
            sponsorCategories = prefs.getString("sponsorCategories", null)
                ?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: d.sponsorCategories,
            returnDislike = prefs.getBoolean("returnDislike", d.returnDislike),
            hideShorts = prefs.getBoolean("hideShorts", d.hideShorts),
            personalize = prefs.getBoolean("personalize", d.personalize),
            captionLanguage = prefs.getString("captionLanguage", d.captionLanguage).orEmpty(),
            doubleTapSeekSec = prefs.getInt("doubleTapSeekSec", d.doubleTapSeekSec),
            preferVp9 = prefs.getBoolean("preferVp9", d.preferVp9),
        )
    }

    private val _vp9Broken = MutableStateFlow(prefs.getBoolean("vp9Broken", false))

    /**
     * Máy này phát VP9 không ổn (bộ giải mã báo lỗi hoặc giật) → app đã tự chuyển về H.264 và nhớ lại.
     * Bật lại "Tiết kiệm dữ liệu (VP9)" trong Cài đặt sẽ xoá dấu này để thử lại.
     */
    val vp9Broken: StateFlow<Boolean> = _vp9Broken.asStateFlow()

    fun setVp9Broken(broken: Boolean) {
        _vp9Broken.value = broken
        prefs.edit().putBoolean("vp9Broken", broken).apply()
    }

    // ---------- Lịch sử ----------

    fun positionOf(videoId: String): Long =
        _history.value.firstOrNull { it.video.id == videoId }?.positionMs ?: 0L

    fun addToHistory(video: VideoItem, positionMs: Long) {
        val now = System.currentTimeMillis()
        val old = _history.value.firstOrNull { it.video.id == video.id }?.video
        val merged = if (old != null) mergeVideo(old, video) else video
        val list = _history.value.filterNot { it.video.id == video.id }.toMutableList()
        list.add(0, HistoryEntry(merged, positionMs, now))
        _history.value = list.take(MAX_HISTORY)
        saveHistory()
    }

    /** Giữ lại thông tin đầy đủ hơn khi cập nhật (ví dụ ảnh đại diện kênh). */
    private fun mergeVideo(old: VideoItem, new: VideoItem) = new.copy(
        thumbnail = new.thumbnail ?: old.thumbnail,
        channelAvatar = new.channelAvatar ?: old.channelAvatar,
        channelId = new.channelId ?: old.channelId,
        viewText = new.viewText ?: old.viewText,
        uploadedText = new.uploadedText ?: old.uploadedText,
        durationSec = if (new.durationSec > 0) new.durationSec else old.durationSec,
        durationText = new.durationText ?: old.durationText,
        title = new.title.ifBlank { old.title },
        uploader = new.uploader.ifBlank { old.uploader },
    )

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
            arr.put(JSONObject().put("video", it.video.toJson()).put("pos", it.positionMs).put("at", it.updatedAt))
        }
        prefs.edit().putString("history", arr.toString()).apply()
    }

    private fun loadHistory(): List<HistoryEntry> = runCatching {
        val arr = JSONArray(prefs.getString("history", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryEntry(VideoItem.fromJson(o.getJSONObject("video")), o.optLong("pos"), o.optLong("at"))
        }
    }.getOrDefault(emptyList())

    // ---------- Xem sau / Đã thích / Không thích ----------

    fun isFavorite(videoId: String) = _favorites.value.any { it.id == videoId }

    fun toggleFavorite(video: VideoItem) {
        _favorites.value = toggle(_favorites.value, video, MAX_FAVORITES)
        saveVideos("favorites", _favorites.value)
    }

    fun toggleLike(video: VideoItem) {
        _liked.value = toggle(_liked.value, video, MAX_FAVORITES)
        saveVideos("liked", _liked.value)
        if (_disliked.value.contains(video.id)) toggleDislike(video.id)
    }

    fun toggleDislike(videoId: String) {
        _disliked.value = if (videoId in _disliked.value) _disliked.value - videoId else _disliked.value + videoId
        saveStrings("disliked", _disliked.value.toList())
        if (videoId in _disliked.value && _liked.value.any { it.id == videoId }) {
            _liked.value = _liked.value.filterNot { it.id == videoId }
            saveVideos("liked", _liked.value)
        }
    }

    private fun toggle(list: List<VideoItem>, video: VideoItem, max: Int) =
        if (list.any { it.id == video.id }) list.filterNot { it.id == video.id } else (listOf(video) + list).take(max)

    private fun saveVideos(key: String, list: List<VideoItem>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadVideos(key: String): List<VideoItem> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { VideoItem.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    // ---------- Kênh đăng ký ----------

    fun isSubscribed(channelId: String) = _subscriptions.value.any { it.id == channelId }

    fun toggleSubscription(channel: ChannelRef) {
        _subscriptions.value = if (isSubscribed(channel.id)) {
            _subscriptions.value.filterNot { it.id == channel.id }
        } else {
            listOf(channel) + _subscriptions.value
        }
        val arr = JSONArray()
        _subscriptions.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("subscriptions", arr.toString()).apply()
    }

    /** Thêm các kênh (ví dụ kênh đã đăng ký trong tài khoản Google) – kênh đã có thì giữ nguyên. */
    fun addSubscriptions(channels: List<ChannelRef>) {
        val known = _subscriptions.value.mapTo(HashSet()) { it.id }
        val added = channels.filter { known.add(it.id) }
        if (added.isEmpty()) return
        _subscriptions.value = _subscriptions.value + added
        val arr = JSONArray()
        _subscriptions.value.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("subscriptions", arr.toString()).apply()
    }

    private fun loadChannels(): List<ChannelRef> = runCatching {
        val arr = JSONArray(prefs.getString("subscriptions", "[]"))
        (0 until arr.length()).map { ChannelRef.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    // ---------- "Không quan tâm" ----------

    fun hide(videoId: String) {
        _hidden.value = _hidden.value + videoId
        saveStrings("hidden", _hidden.value.toList().takeLast(500))
    }

    // ---------- Tìm kiếm gần đây ----------

    fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _recentSearches.value = (listOf(q) + _recentSearches.value.filterNot { it.equals(q, true) }).take(20)
        saveStrings("recentSearches", _recentSearches.value)
    }

    fun removeRecentSearch(query: String) {
        _recentSearches.value = _recentSearches.value.filterNot { it == query }
        saveStrings("recentSearches", _recentSearches.value)
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        saveStrings("recentSearches", emptyList())
    }

    private fun saveStrings(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadStrings(key: String): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    // ---------- Khác ----------

    var visitorData: String?
        get() = prefs.getString("visitorData", null)?.ifBlank { null }
        set(value) = prefs.edit().putString("visitorData", value ?: "").apply()

    var lastUpdateCheck: Long
        get() = prefs.getLong("lastUpdateCheck", 0)
        set(value) = prefs.edit().putLong("lastUpdateCheck", value).apply()

    companion object {
        private const val MAX_HISTORY = 300
        private const val MAX_FAVORITES = 500
    }
}
