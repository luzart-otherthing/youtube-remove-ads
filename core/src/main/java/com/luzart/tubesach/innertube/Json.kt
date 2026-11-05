package com.luzart.tubesach.innertube

import org.json.JSONArray
import org.json.JSONObject

// Các hàm nhỏ giúp đọc JSON của YouTube (cấu trúc lồng rất sâu).

internal fun JSONObject.o(vararg path: String): JSONObject? {
    var cur: JSONObject? = this
    for (p in path) cur = cur?.optJSONObject(p) ?: return null
    return cur
}

internal fun JSONObject.a(vararg path: String): JSONArray? {
    if (path.isEmpty()) return null
    val parent = if (path.size == 1) this else o(*path.copyOfRange(0, path.size - 1))
    return parent?.optJSONArray(path.last())
}

internal fun JSONObject.s(vararg path: String): String? {
    if (path.isEmpty()) return null
    val parent = if (path.size == 1) this else o(*path.copyOfRange(0, path.size - 1))
    val v = parent?.opt(path.last())
    return (v as? String)?.takeIf { it.isNotEmpty() }
}

internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Đọc chữ ở các dạng YouTube hay dùng: simpleText, runs[], content. */
internal fun textOf(v: Any?): String? = when (v) {
    is String -> v.takeIf { it.isNotEmpty() }
    is JSONObject -> v.optString("simpleText").ifEmpty { null }
        ?: v.optJSONArray("runs")?.objects()?.joinToString("") { it.optString("text") }?.ifEmpty { null }
        ?: v.optString("content").ifEmpty { null }
    else -> null
}?.replace(' ', ' ')

/** Tìm (theo chiều sâu, đúng thứ tự) đối tượng đầu tiên có khoá [key]. */
internal fun findFirst(root: Any?, key: String): JSONObject? {
    when (root) {
        is JSONObject -> {
            root.optJSONObject(key)?.let { return it }
            val keys = root.keys()
            while (keys.hasNext()) {
                findFirst(root.opt(keys.next()), key)?.let { return it }
            }
        }
        is JSONArray -> for (i in 0 until root.length()) findFirst(root.opt(i), key)?.let { return it }
    }
    return null
}

/** Tìm tất cả đối tượng có khoá [key] (không đi sâu vào bên trong chính chúng). */
internal fun findAll(root: Any?, key: String, out: MutableList<JSONObject> = mutableListOf()): MutableList<JSONObject> {
    when (root) {
        is JSONObject -> {
            val hit = root.optJSONObject(key)
            if (hit != null) {
                out.add(hit)
            }
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == key) continue
                findAll(root.opt(k), key, out)
            }
        }
        is JSONArray -> for (i in 0 until root.length()) findAll(root.opt(i), key, out)
    }
    return out
}

/** Chọn ảnh có chiều rộng gần [target] nhất trong mảng {url,width,height}. */
internal fun bestImage(arr: JSONArray?, target: Int = 720): String? {
    val list = arr.objects().filter { it.optString("url").isNotEmpty() }
    if (list.isEmpty()) return null
    val pick = list.minByOrNull { val w = it.optInt("width"); if (w <= 0) Int.MAX_VALUE / 2 else kotlin.math.abs(w - target) }
    return fixUrl(pick?.optString("url"))
}

internal fun fixUrl(url: String?): String? = when {
    url.isNullOrEmpty() -> null
    url.startsWith("//") -> "https:$url"
    else -> url
}
