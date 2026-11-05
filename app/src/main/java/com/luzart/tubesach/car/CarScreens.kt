package com.luzart.tubesach.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.ui.components.Thumbnail
import com.luzart.tubesach.ui.components.formatMs
import com.luzart.tubesach.ui.components.metaLine
import com.luzart.tubesach.ui.player.PlayerSurface
import com.luzart.tubesach.ui.player.SkipToast
import com.luzart.tubesach.ui.player.YtSeekBar
import com.luzart.tubesach.ui.player.rememberPlayerUiState
import com.luzart.tubesach.ui.theme.LocalYt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Giao diện Tube Sạch cho màn hình ô tô: màn hình ngang, chữ to, nút to (dễ bấm khi ngồi trên xe).
 * Bên trái là các mục (giống thanh dưới của YouTube), bên phải là lưới video.
 */
@Composable
fun CarApp(vm: CarViewModel) {
    val yt = LocalYt.current
    val now by PlaybackController.nowPlaying.collectAsState()
    Box(
        Modifier
            .fillMaxSize()
            .background(yt.background)
            .safeDrawingPadding(),
    ) {
        Row(Modifier.fillMaxSize()) {
            CarNavRail(vm, now)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (vm.section) {
                    CarSection.HOME -> CarFeedGrid(vm, CarViewModel.KEY_HOME, emptyTitle = "Chưa có video", emptyMessage = "Kiểm tra kết nối mạng rồi bấm Tải lại.")
                    CarSection.SEARCH -> CarSearch(vm)
                    CarSection.SUBSCRIPTIONS -> CarSubscriptions(vm)
                    CarSection.HISTORY -> CarHistory(vm)
                    CarSection.LIBRARY -> CarLibraryScreen(vm)
                }
            }
        }
        val current = now
        if (vm.playerOpen && current != null) {
            CarPlayer(current, onClose = { vm.playerOpen = false }, onPlay = vm::openVideo)
        }
    }
}

// ---------------------------------------------------------------- Thanh bên

@Composable
private fun CarNavRail(vm: CarViewModel, now: NowPlaying?) {
    val yt = LocalYt.current
    Column(
        Modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(yt.surface)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailItem(Icons.Filled.Home, CarSection.HOME.label, vm.section == CarSection.HOME && !vm.playerOpen) { vm.select(CarSection.HOME) }
        RailItem(Icons.Filled.Search, CarSection.SEARCH.label, vm.section == CarSection.SEARCH && !vm.playerOpen) { vm.select(CarSection.SEARCH) }
        RailItem(Icons.Filled.Subscriptions, "Kênh\nđăng ký", vm.section == CarSection.SUBSCRIPTIONS && !vm.playerOpen) { vm.select(CarSection.SUBSCRIPTIONS) }
        RailItem(Icons.Filled.History, CarSection.HISTORY.label, vm.section == CarSection.HISTORY && !vm.playerOpen) { vm.select(CarSection.HISTORY) }
        RailItem(Icons.Filled.VideoLibrary, CarSection.LIBRARY.label, vm.section == CarSection.LIBRARY && !vm.playerOpen) { vm.select(CarSection.LIBRARY) }
        Spacer(Modifier.weight(1f))
        if (now != null && !now.isShort) {
            NowPlayingButton(now) { vm.playerOpen = true }
        }
    }
}

@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val yt = LocalYt.current
    Column(
        Modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) yt.chip else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) yt.text else yt.textSecondary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (selected) yt.text else yt.textSecondary,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** Ô "Đang phát" ở góc dưới thanh bên: bấm để mở lại video, nút tròn để tạm dừng / phát. */
@Composable
private fun NowPlayingButton(now: NowPlaying, onOpen: () -> Unit) {
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    Column(
        Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Thumbnail(now.video, Modifier.fillMaxWidth(), corner = 8.dp)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .clickable { PlaybackController.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text("Đang phát", color = LocalYt.current.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// ---------------------------------------------------------------- Lưới video

private val GRID_MIN = 200.dp

@Composable
private fun CarFeedGrid(
    vm: CarViewModel,
    key: String,
    emptyTitle: String,
    emptyMessage: String?,
    header: (@Composable () -> Unit)? = null,
) {
    val feed = vm.feeds[key] ?: CarFeed(loading = true)
    when {
        feed.loading && feed.videos.isEmpty() -> CarMessage(header) { CircularProgressIndicator(color = LocalYt.current.red) }
        feed.error != null && feed.videos.isEmpty() -> CarMessage(header) {
            CarEmpty(feed.error, "Bấm để thử lại", Icons.Filled.Refresh, onAction = vm::refresh)
        }
        feed.videos.isEmpty() -> CarMessage(header) { CarEmpty(emptyTitle, emptyMessage, null, null) }
        else -> VideoGrid(
            videos = feed.videos,
            onClick = vm::openVideo,
            header = if (header != null || feed.note != null) {
                {
                    header?.invoke()
                    if (feed.note != null) SectionTitle(feed.note)
                }
            } else null,
            loadingMore = feed.loadingMore,
            onNearEnd = if (feed.continuation != null) ({ vm.loadMore(key) }) else null,
        )
    }
}

@Composable
private fun VideoGrid(
    videos: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    header: (@Composable () -> Unit)? = null,
    progressOf: ((VideoItem) -> Float?)? = null,
    loadingMore: Boolean = false,
    onNearEnd: (() -> Unit)? = null,
) {
    val state = rememberLazyGridState()
    if (onNearEnd != null) {
        LaunchedEffect(state, videos.size) {
            snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged()
                .collect { last -> if (last >= videos.size - 6) onNearEnd() }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_MIN),
        state = state,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
        }
        items(videos, key = { it.id }) { v ->
            CarVideoCard(v, progressOf?.invoke(v)) { onClick(v) }
        }
        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalYt.current.red, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/** Thẻ video: ảnh to, tiêu đề 2 dòng chữ lớn, tên kênh + lượt xem. */
@Composable
private fun CarVideoCard(video: VideoItem, progress: Float?, onClick: () -> Unit) {
    val yt = LocalYt.current
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Thumbnail(video, Modifier.fillMaxWidth(), corner = 12.dp, progress = progress)
        Text(
            video.title,
            color = yt.text,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            metaLine(video),
            color = yt.textSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = LocalYt.current.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun CarMessage(header: (@Composable () -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        header?.invoke()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun CarEmpty(title: String, message: String?, actionIcon: ImageVector?, onAction: (() -> Unit)?) {
    val yt = LocalYt.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (actionIcon != null) Icon(actionIcon, contentDescription = null, tint = yt.text, modifier = Modifier.size(48.dp))
        Text(title, color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (message != null) {
            Text(message, color = yt.textSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun CarChip(text: String, selected: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) yt.chipSelected else yt.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) yt.chipSelectedText else yt.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (selected) yt.chipSelectedText else yt.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- Các mục

@Composable
private fun CarSubscriptions(vm: CarViewModel) {
    val subs by vm.store.subscriptions.collectAsState()
    LaunchedEffect(subs.size) { vm.loadSubscriptions() }
    if (subs.isEmpty()) {
        CarMessage(null) {
            CarEmpty(
                "Chưa đăng ký kênh nào",
                "Trên điện thoại, mở một video rồi bấm Đăng ký. Video mới của kênh sẽ hiện ở đây.",
                null,
                null,
            )
        }
    } else {
        CarFeedGrid(vm, CarViewModel.KEY_SUBS, emptyTitle = "Chưa có video mới", emptyMessage = null)
    }
}

@Composable
private fun CarHistory(vm: CarViewModel) {
    val history by vm.store.history.collectAsState()
    val items = remember(history) { history.filterNot { it.video.isShort } }
    if (items.isEmpty()) {
        CarMessage(null) { CarEmpty("Chưa xem video nào", "Video đã xem sẽ hiện ở đây để xem tiếp.", null, null) }
        return
    }
    val progress = remember(items) {
        items.associate { h ->
            val dur = h.video.durationSec * 1000
            h.video.id to if (dur > 0 && h.positionMs > 0) (h.positionMs.toFloat() / dur).coerceIn(0f, 1f) else null
        }
    }
    VideoGrid(
        videos = items.map { it.video },
        onClick = vm::openVideo,
        header = { SectionTitle("Xem tiếp") },
        progressOf = { progress[it.id] },
    )
}

@Composable
private fun CarLibraryScreen(vm: CarViewModel) {
    val later by vm.store.favorites.collectAsState()
    val liked by vm.store.liked.collectAsState()
    LaunchedEffect(Unit) { vm.selectShelf(vm.shelf) }
    val chips: @Composable () -> Unit = {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            items(CarShelf.entries) { s -> CarChip(s.label, selected = s == vm.shelf) { vm.selectShelf(s) } }
        }
    }
    when (vm.shelf) {
        CarShelf.WATCH_LATER, CarShelf.LIKED -> {
            val list = if (vm.shelf == CarShelf.WATCH_LATER) later else liked
            if (list.isEmpty()) {
                CarMessage(chips) {
                    CarEmpty(
                        if (vm.shelf == CarShelf.WATCH_LATER) "Chưa có video Xem sau" else "Chưa thích video nào",
                        "Trên điện thoại, bấm Lưu hoặc 👍 ở dưới video.",
                        null,
                        null,
                    )
                }
            } else {
                VideoGrid(list, vm::openVideo, header = chips)
            }
        }
        CarShelf.TRENDING -> CarFeedGrid(vm, CarViewModel.KEY_TRENDING, "Chưa có video", null, header = chips)
        CarShelf.TOP_MUSIC -> CarFeedGrid(vm, CarViewModel.KEY_TOP_MUSIC, "Chưa có video", null, header = chips)
    }
}

// ---------------------------------------------------------------- Tìm kiếm

@Composable
private fun CarSearch(vm: CarViewModel) {
    val yt = LocalYt.current
    val recent by vm.store.recentSearches.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = vm.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                placeholder = { Text("Tìm kiếm trên YouTube", fontSize = 18.sp) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(28.dp)) },
                trailingIcon = if (vm.query.isNotEmpty()) {
                    { CarIconButton(Icons.Filled.Close, "Xoá", 44.dp, onClick = vm::clearSearch) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(vm.query) }),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = yt.chip,
                    unfocusedContainerColor = yt.chip,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            VoiceSearchButton(onResult = vm::search)
        }
        if (vm.submitted.isNotEmpty() && vm.query == vm.submitted) {
            CarFeedGrid(vm, CarViewModel.searchKey(vm.submitted), emptyTitle = "Không tìm thấy video", emptyMessage = "Thử từ khoá khác.")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.suggestions.isNotEmpty()) {
                    items(vm.suggestions) { s -> SuggestionRow(s, Icons.Filled.Search) { vm.search(s) } }
                } else {
                    if (recent.isNotEmpty()) {
                        item { SectionTitle("Tìm kiếm gần đây") }
                        item { ChipFlow(recent.take(8), Icons.Filled.History, vm::search) }
                    }
                    item { SectionTitle("Gợi ý") }
                    item { ChipFlow(CarViewModel.TOPICS, null, vm::search) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = yt.textSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(text, color = yt.text, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(items: List<String>, icon: ImageVector?, onClick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { t -> CarChip(t, icon = icon) { onClick(t) } }
    }
}

/**
 * Nút micro: nói để tìm (dùng bộ nhận dạng giọng nói của Google ngay trong app,
 * vì trên màn hình xe không mở được cửa sổ của app khác).
 */
@Composable
private fun VoiceSearchButton(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val available = remember { runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false) }
    if (!available) return
    var listening by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val recognizer = remember { runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull() }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; hint = "Đang nghe…" }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { hint = "Đang tìm…" }
            override fun onError(error: Int) {
                listening = false
                hint = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Không nghe rõ, thử lại" else null
            }
            override fun onResults(results: Bundle?) {
                listening = false
                hint = null
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onResult)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { hint = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        onDispose { recognizer?.destroy() }
    }
    fun start() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        runCatching { recognizer?.startListening(intent) }.onFailure { hint = "Không dùng được micro" }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start() else hint = "Hãy cho phép micro trên điện thoại"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (listening) LocalYt.current.red else LocalYt.current.chip)
                .clickable {
                    if (listening) {
                        recognizer?.stopListening()
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        start()
                    } else {
                        permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Mic, contentDescription = "Tìm bằng giọng nói", tint = LocalYt.current.text, modifier = Modifier.size(30.dp))
        }
        hint?.let { Text(it, color = LocalYt.current.textSecondary, fontSize = 12.sp, maxLines = 1) }
    }
}

// ---------------------------------------------------------------- Trình phát

/**
 * Xem video trên màn hình xe: video to, nút điều khiển to ở giữa, thanh tua ở dưới.
 * Chạm vào video để hiện / ẩn nút. Nút "Danh sách" mở cột video tiếp theo bên phải.
 */
@Composable
private fun CarPlayer(now: NowPlaying, onClose: () -> Unit, onPlay: (VideoItem) -> Unit) {
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val loading by PlaybackController.loading.collectAsState()
    val error by PlaybackController.error.collectAsState()
    val segments by PlaybackController.segments.collectAsState()
    val ui = rememberPlayerUiState()
    var controls by remember { mutableStateOf(true) }
    var showList by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var toast by remember { mutableStateOf<Segment?>(null) }

    fun poke() { lastTouch = System.currentTimeMillis(); controls = true }

    LaunchedEffect(controls, isPlaying, lastTouch, showList) {
        if (controls && isPlaying && !showList) {
            delay(CONTROLS_TIMEOUT_MS)
            controls = false
        }
    }
    LaunchedEffect(Unit) {
        PlaybackController.skipped.collect { seg ->
            toast = seg
            delay(5_000)
            if (toast == seg) toast = null
        }
    }
    LaunchedEffect(now.video.id) { poke() }

    Row(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (controls) controls = false else poke() },
                        onDoubleTap = { o ->
                            PlaybackController.seekBy(if (o.x < size.width / 2) -PlaybackController.SEEK_MS else PlaybackController.SEEK_MS)
                            poke()
                        },
                    )
                },
        ) {
            PlayerSurface(Modifier.fillMaxSize(), isPlaying = isPlaying, onCar = true, subtitleScale = 1.3f)

            if (loading || ui.buffering) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.align(Alignment.Center).size(64.dp))
            }
            error?.let { msg -> PlayerError(msg) }

            Fading(visible = controls || !isPlaying || error != null, modifier = Modifier.matchParentSize()) {
                PlayerControls(
                    now = now,
                    isPlaying = isPlaying,
                    ended = ui.ended,
                    position = ui.position,
                    duration = ui.duration,
                    buffered = ui.buffered,
                    segments = segments,
                    showList = showList,
                    onClose = onClose,
                    onToggleList = { showList = !showList; poke() },
                    onAction = ::poke,
                )
            }
            SkipToast(toast) { PlaybackController.undoSkip(it); toast = null }
        }
        if (showList) {
            UpNextPanel(now, onPlay = { onPlay(it); poke() })
        }
    }
}

@Composable
private fun BoxScope.PlayerError(msg: String) {
    Column(
        Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC000000))
            .clickable { PlaybackController.retry() }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        Text(msg, color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PlayerControls(
    now: NowPlaying,
    isPlaying: Boolean,
    ended: Boolean,
    position: Long,
    duration: Long,
    buffered: Long,
    segments: List<Segment>,
    showList: Boolean,
    onClose: () -> Unit,
    onToggleList: () -> Unit,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // Lớp tối phía trên/dưới cho chữ dễ đọc
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB3000000), Color(0x33000000), Color(0xB3000000)))))

        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CarIconButton(Icons.Filled.KeyboardArrowDown, "Thu nhỏ", 56.dp, onClick = onClose)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(now.video.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now.video.uploader, color = Color(0xFFCCCCCC), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CarIconButton(Icons.Filled.PlaylistPlay, if (showList) "Ẩn danh sách" else "Video tiếp theo", 56.dp, onClick = onToggleList)
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarIconButton(Icons.Filled.SkipPrevious, "Video trước", 64.dp) { PlaybackController.previous(); onAction() }
            CarIconButton(Icons.Filled.Replay10, "Lùi 10 giây", 64.dp) { PlaybackController.seekBy(-PlaybackController.SEEK_MS); onAction() }
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable { PlaybackController.togglePlayPause(); onAction() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        ended -> Icons.Filled.Replay
                        isPlaying -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = when {
                        ended -> "Xem lại"
                        isPlaying -> "Tạm dừng"
                        else -> "Phát"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            CarIconButton(Icons.Filled.Forward10, "Tới 10 giây", 64.dp) { PlaybackController.seekBy(PlaybackController.SEEK_MS); onAction() }
            CarIconButton(Icons.Filled.SkipNext, "Video tiếp theo", 64.dp) { PlaybackController.next(); onAction() }
        }

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (!now.video.isLive) {
                Text(
                    "${formatMs(position)} / ${formatMs(duration)}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                YtSeekBar(
                    position = position,
                    duration = duration,
                    buffered = buffered,
                    segments = segments,
                    showThumb = true,
                    trackHeight = 5.dp,
                    onSeek = { PlaybackController.seekTo(it); onAction() },
                )
            } else {
                Text("● TRỰC TIẾP", color = LocalYt.current.red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Cột "Video tiếp theo" bên phải trình phát. */
@Composable
private fun UpNextPanel(now: NowPlaying, onPlay: (VideoItem) -> Unit) {
    val yt = LocalYt.current
    val related = remember(now.related) { now.related.filterNot { it.isShort } }
    Column(
        Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(yt.background),
    ) {
        Text("Video tiếp theo", color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
        if (related.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (now.watch == null) {
                    CircularProgressIndicator(color = yt.red, modifier = Modifier.size(32.dp))
                } else {
                    Text("Không có video gợi ý", color = yt.textSecondary, fontSize = 15.sp)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(related, key = { it.id }) { v ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPlay(v) },
                    ) {
                        Thumbnail(v, Modifier.width(132.dp), corner = 8.dp)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(v.title, color = yt.text, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(v.uploader, color = yt.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CarIconButton(icon: ImageVector, desc: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** Hiện/ẩn mờ dần (tách riêng để không bị nhầm với AnimatedVisibility của Row). */
@Composable
private fun Fading(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) { content() }
}

/** Nút điều khiển tự ẩn sau vài giây khi đang phát (giống YouTube). */
private const val CONTROLS_TIMEOUT_MS = 4_000L
