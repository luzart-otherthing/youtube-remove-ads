package com.luzart.tubesach.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luzart.tubesach.App
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.update.UpdateChecker
import com.luzart.tubesach.update.UpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface Screen {
    data object Home : Screen
    data object SearchInput : Screen
    data class Results(val query: String) : Screen
    data object History : Screen
    data object Favorites : Screen
    data object Player : Screen
    data object Settings : Screen
}

data class ResultsState(
    val items: List<VideoItem> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val session: YouTubeRepository.SearchSession? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val store = (app as App).store
    private val http = (app as App).http

    // ---------------------------------------------------------------- Điều hướng

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val top: Screen get() = stack.last()

    var fullscreen by mutableStateOf(false)

    fun push(screen: Screen) {
        if (top != screen) stack.add(screen)
    }

    fun back(): Boolean {
        if (fullscreen) {
            fullscreen = false
            return true
        }
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
            return true
        }
        return false
    }

    fun goHome() {
        fullscreen = false
        stack.clear()
        stack.add(Screen.Home)
    }

    fun openVideo(video: VideoItem, resume: Boolean = true) {
        PlaybackController.play(video, resume)
        openPlayerScreen()
    }

    fun openPlayerScreen() {
        if (PlaybackController.nowPlaying.value == null) return
        stack.remove(Screen.Player)
        stack.add(Screen.Player)
    }

    // ---------------------------------------------------------------- Tìm kiếm

    var searchText by mutableStateOf("")
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    private var suggestJob: Job? = null

    fun onSearchTextChange(text: String) {
        searchText = text
        suggestJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            suggestions = YouTubeRepository.suggestions(text)
        }
    }

    fun openSearchInput() {
        searchText = ""
        suggestions = emptyList()
        push(Screen.SearchInput)
    }

    val results = mutableStateMapOf<String, ResultsState>()

    fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        store.addRecentSearch(q)
        stack.remove(Screen.SearchInput)
        stack.removeAll { it is Screen.Results && it.query == q }
        stack.add(Screen.Results(q))
        runSearch(q)
    }

    fun runSearch(q: String) {
        results[q] = ResultsState(loading = true)
        viewModelScope.launch {
            results[q] = try {
                val (items, session) = YouTubeRepository.search(q)
                ResultsState(items = items, loading = false, session = session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ResultsState(loading = false, error = errorText(e))
            }
            // Giữ tối đa 6 kết quả tìm kiếm trong bộ nhớ.
            val keep = stack.filterIsInstance<Screen.Results>().map { it.query }.toSet()
            results.keys.filter { it !in keep }.drop(6).forEach { results.remove(it) }
        }
    }

    fun loadMore(q: String) {
        val state = results[q] ?: return
        val session = state.session ?: return
        if (state.loadingMore || !session.hasMore) return
        results[q] = state.copy(loadingMore = true)
        viewModelScope.launch {
            val more = runCatching { YouTubeRepository.loadMore(session) }.getOrDefault(emptyList())
            val current = results[q] ?: return@launch
            val known = current.items.map { it.id }.toHashSet()
            results[q] = current.copy(
                items = current.items + more.filter { it.id !in known },
                loadingMore = false,
            )
        }
    }

    private fun errorText(e: Throwable) = when (e) {
        is IOException -> "Mất kết nối mạng. Kiểm tra Wi-Fi hoặc 4G rồi thử lại."
        else -> "Không tìm được. Vui lòng thử lại."
    }

    // ---------------------------------------------------------------- Cập nhật

    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    var checkingUpdate by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)

    fun checkUpdate(manual: Boolean = false) {
        if (checkingUpdate) return
        checkingUpdate = true
        viewModelScope.launch {
            val result = UpdateChecker.check(http)
            store.lastUpdateCheck = System.currentTimeMillis()
            update = result
            checkingUpdate = false
            if (manual) {
                updateMessage = if (result == null) "Bạn đang dùng bản mới nhất." else null
            }
        }
    }
}
