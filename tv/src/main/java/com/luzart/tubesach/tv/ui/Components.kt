package com.luzart.tubesach.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.luzart.tubesach.data.ChannelRef
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.ShortItem
import com.luzart.tubesach.data.VideoItem
import kotlinx.coroutines.flow.distinctUntilChanged

// ---------------------------------------------------------------- Định dạng

fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMs(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

/** "Tên kênh • 1,2 Tr lượt xem • 3 ngày trước" (chữ giữ nguyên như YouTube). */
fun metaLine(video: VideoItem, includeChannel: Boolean = true): String = listOfNotNull(
    video.uploader.takeIf { includeChannel && it.isNotBlank() },
    video.viewText ?: video.viewCount.takeIf { it >= 0 }?.let { "%,d lượt xem".format(it).replace(',', '.') },
    video.uploadedText,
).joinToString(" • ")

// ---------------------------------------------------------------- Thẻ video

private val CardShape = RoundedCornerShape(10.dp)

/** Hiệu ứng khi ô được chọn: phóng to nhẹ + viền trắng – nhìn rõ từ xa. */
@Composable
private fun focusScale(focused: Boolean) = animateFloatAsState(if (focused) 1.06f else 1f, label = "focusScale").value

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    progress: Float? = null,
    showChannel: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    val (memoryFocus, onCardFocus) = rememberCardFocus()
    Column(
        modifier
            .focusRequester(memoryFocus)
            .onFocusChanged { focused = it.isFocused; onCardFocus(it.isFocused) }
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
            .testTag("video:${video.id}"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CardShape)
                .background(TvColors.surfaceHigh)
                .then(if (focused) Modifier.border(3.dp, TvColors.focus, CardShape) else Modifier),
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            val label = when {
                video.isLive -> "TRỰC TIẾP"
                video.durationText != null -> video.durationText.orEmpty()
                else -> formatDuration(video.durationSec)
            }
            if (label.isNotEmpty()) {
                Text(
                    label,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = if (progress != null && progress > 0f) 9.dp else 6.dp)
                        .background(if (video.isLive) Color(0xFFCC0000) else Color(0xCC000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (progress != null && progress > 0f) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(TvColors.red))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) TvColors.focus else TvColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = metaLine(video, showChannel)
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ShortCard(short: ShortItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    val (memoryFocus, onCardFocus) = rememberCardFocus()
    Column(
        modifier
            .focusRequester(memoryFocus)
            .onFocusChanged { focused = it.isFocused; onCardFocus(it.isFocused) }
            .tvClickable(onClick = onClick)
            .testTag("short:${short.id}"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CardShape)
                .background(TvColors.surfaceHigh)
                .then(if (focused) Modifier.border(3.dp, TvColors.focus, CardShape) else Modifier),
        ) {
            AsyncImage(short.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(short.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        short.viewText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TvColors.textSecondary, maxLines = 1) }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(TvColors.surfaceHigh)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ChannelCard(channel: ChannelRef, onClick: () -> Unit, modifier: Modifier = Modifier, avatarSize: Dp = 96.dp) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    val (memoryFocus, onCardFocus) = rememberCardFocus()
    Column(
        modifier
            .focusRequester(memoryFocus)
            .onFocusChanged { focused = it.isFocused; onCardFocus(it.isFocused) }
            .tvClickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("channel:${channel.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(
            channel.avatar,
            avatarSize,
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(if (focused) Modifier.border(3.dp, TvColors.focus, CircleShape) else Modifier),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            channel.name,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) TvColors.focus else TvColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        channel.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = TvColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- Nút

private val PillShape = RoundedCornerShape(50)

/** Nút hình viên thuốc: khi được chọn → nền trắng chữ đen. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    val bg = when {
        focused -> TvColors.focus
        primary -> TvColors.red
        selected -> Color(0xFF4A4A4A)
        else -> TvColors.surfaceHigh
    }
    val fg = if (focused) TvColors.onFocus else if (enabled) TvColors.text else TvColors.textSecondary
    Row(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Nút tròn chỉ có biểu tượng (dùng trong trình phát). */
@Composable
fun TvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    active: Boolean = false,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale = focusScale(focused)
    val onFocusedState by rememberUpdatedState(onFocused)
    Box(
        modifier
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocusedState()
            }
            .tvClickable(onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(size)
            .clip(CircleShape)
            .background(if (focused) TvColors.focus else Color(0x33FFFFFF))
            .testTag("btn:$contentDescription"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription,
            tint = when {
                focused -> TvColors.onFocus
                active -> TvColors.accent
                else -> Color.White
            },
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
fun TvChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> TvColors.focus
        selected -> Color(0xFFDDDDDD)
        else -> TvColors.surfaceHigh
    }
    val fg = if (focused || selected) TvColors.onFocus else TvColors.text
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("chip:$text"),
    )
}

// ---------------------------------------------------------------- Trạng thái

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = TvColors.red, strokeWidth = 4.dp, modifier = Modifier.size(48.dp))
    }
}

@Composable
fun MessageBox(
    title: String,
    message: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = TvColors.textSecondary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = TvColors.textSecondary)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            TvButton(action, onAction)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 8.dp, bottom = 12.dp))
}

// ---------------------------------------------------------------- Bảng chọn bên phải

/**
 * Bảng chọn trượt từ bên phải (Chất lượng, Phụ đề, Tốc độ, menu video…).
 * Ô sáng bị giữ trong bảng cho tới khi bấm Quay lại.
 * [initialFocus] là lựa chọn đang dùng – được chọn sẵn khi mở bảng.
 */
@Composable
fun SidePanel(
    title: String,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
    subtitle: String? = null,
    content: LazyListScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Color(0x99000000))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(400.dp)
                .fillMaxHeight()
                .background(TvColors.surface)
                .padding(horizontal = 24.dp, vertical = TvDimens.safeVertical)
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .testTag("panel:$title"),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TvColors.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
    if (initialFocus != null) {
        LaunchedEffect(Unit) {
            // Mục trong LazyColumn chỉ có sau lượt layout → chờ vài khung hình rồi mới đặt ô sáng.
            repeat(5) {
                withFrameNanos { }
                if (initialFocus.tryFocus()) return@LaunchedEffect
            }
        }
    }
}

@Composable
fun PanelOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showCheck: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) TvColors.onFocus else TvColors.text
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) TvColors.focus else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("option:$text"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showCheck) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Filled.Check, null, tint = if (focused) TvColors.onFocus else TvColors.accent)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(text, color = fg, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = if (focused) Color(0xFF444444) else TvColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- Lưới video

/** Số cột video trên TV (giống YouTube trên TV). */
const val GRID_COLUMNS = 4

/**
 * Lưới video cuộn dọc: video, kệ Shorts, kệ ngang, kênh. Tự tải thêm khi gần cuối.
 * [header] thêm các ô chiếm cả hàng ở đầu lưới (tiêu đề, hàng chip…).
 */
@Composable
fun FeedGrid(
    entries: List<FeedEntry>,
    onVideo: (VideoItem) -> Unit,
    onShort: (ShortItem) -> Unit,
    onChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    onVideoMenu: ((VideoItem) -> Unit)? = null,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    progressOf: ((VideoItem) -> Float?)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 24.dp, end = TvDimens.safeHorizontal, top = TvDimens.safeVertical, bottom = TvDimens.safeVertical),
    /** Video / kênh đầu tiên là "mục chính" của trang (ô sáng tự vào đây). */
    primaryOnFirstItem: Boolean = true,
    /** Gắn vào video / kênh đầu tiên (ví dụ để ô tìm kiếm bấm ▼ là tới kết quả đầu). */
    firstItemFocus: FocusRequester? = null,
    header: LazyGridScope.() -> Unit = {},
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    val firstIndex = entries.indexOfFirst { it is FeedEntry.Video || it is FeedEntry.Channel }
    val primaryIndex = if (primaryOnFirstItem) firstIndex else -1
    fun Modifier.first(index: Int) = if (firstItemFocus != null && index == firstIndex) focusRequester(firstItemFocus) else this
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        // Lưới tự nhớ ô cuối cùng được chọn → thoát video / đóng menu là về đúng video đó.
        modifier = modifier.fillMaxSize().focusRestorer().testTag("feedGrid"),
    ) {
        header()
        entries.forEachIndexed { index, entry ->
            when (entry) {
                is FeedEntry.Video -> item(key = entry.key) {
                    VideoCard(
                        entry.video,
                        onClick = { onVideo(entry.video) },
                        onLongClick = onVideoMenu?.let { m -> { m(entry.video) } },
                        progress = progressOf?.invoke(entry.video),
                        modifier = Modifier.primaryFocus(index == primaryIndex).first(index),
                    )
                }
                is FeedEntry.Channel -> item(key = entry.key) {
                    ChannelCard(entry.channel, onClick = { onChannel(entry.channel.id) }, modifier = Modifier.primaryFocus(index == primaryIndex).first(index))
                }
                is FeedEntry.ShortsShelf -> item(key = entry.key, span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle(entry.title.ifBlank { "Shorts" })
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusRestorer().focusGroup()) {
                            items(entry.shorts, key = { it.id }) { s ->
                                ShortCard(s, onClick = { onShort(s) }, modifier = Modifier.width(140.dp))
                            }
                        }
                    }
                }
                is FeedEntry.Shelf -> item(key = entry.key, span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionTitle(entry.title)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.focusRestorer().focusGroup()) {
                            items(entry.videos, key = { it.id }) { v ->
                                VideoCard(
                                    v,
                                    onClick = { onVideo(v) },
                                    onLongClick = onVideoMenu?.let { m -> { m(v) } },
                                    modifier = Modifier.width(200.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (loadingMore) {
            item(key = "loadingMore", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TvColors.red, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
    // Gần tới cuối danh sách → tải thêm (cuộn mãi không hết như YouTube).
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Đổi giá trị mỗi khi danh sách dài thêm → vẫn tải tiếp nếu người dùng vẫn đang ở cuối.
            if (info.totalItemsCount > 0 && last >= info.totalItemsCount - GRID_COLUMNS * 2) info.totalItemsCount else -1
        }.distinctUntilChanged().collect { total -> if (total > 0) loadMore?.invoke() }
    }
}

/** Ô chiếm cả hàng trong lưới. */
fun LazyGridScope.fullRow(key: Any, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}
