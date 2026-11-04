package com.luzart.tubesach.ui.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.sponsor.SegmentCategory
import com.luzart.tubesach.ui.components.formatMs
import com.luzart.tubesach.ui.theme.LocalYt
import com.luzart.tubesach.ui.theme.YtRed
import kotlinx.coroutines.delay

/** Vị trí phát, thời lượng, đã tải… cập nhật 4 lần/giây. */
class PlayerUiState {
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var buffered by mutableLongStateOf(0L)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var height by mutableIntStateOf(0)
}

@Composable
fun rememberPlayerUiState(): PlayerUiState {
    val state = remember { PlayerUiState() }
    LaunchedEffect(Unit) {
        val p = PlaybackController.player
        while (true) {
            state.position = p.currentPosition
            state.duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            state.buffered = p.bufferedPosition
            state.buffering = p.playbackState == Player.STATE_BUFFERING
            state.ended = p.playbackState == Player.STATE_ENDED
            state.height = p.videoFormat?.height ?: 0
            delay(250)
        }
    }
    return state
}

/** Khung hình video (PlayerView của Media3, tắt bộ điều khiển mặc định). */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(modifier: Modifier = Modifier, zoom: Boolean = false, isPlaying: Boolean = false) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                subtitleView?.setStyle(
                    CaptionStyleCompat(
                        android.graphics.Color.WHITE,
                        0xBF000000.toInt(),
                        android.graphics.Color.TRANSPARENT,
                        CaptionStyleCompat.EDGE_TYPE_NONE,
                        android.graphics.Color.WHITE,
                        null,
                    ),
                )
                player = PlaybackController.player
            }
        },
        update = { view ->
            if (view.player !== PlaybackController.player) view.player = PlaybackController.player
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.keepScreenOn = isPlaying
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- Thanh tua

@Composable
fun YtSeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    segments: List<Segment>,
    showThumb: Boolean,
    modifier: Modifier = Modifier,
    trackHeight: androidx.compose.ui.unit.Dp = 3.dp,
    onSeekPreview: (Long?) -> Unit = {},
    onSeek: (Long) -> Unit,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val red = YtRed
    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) onSeek((o.x / size.width * duration).toLong().coerceIn(0, duration))
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { if (duration > 0) onSeek((it * duration).toLong()) }
                        dragFraction = null
                        onSeekPreview(null)
                    },
                    onDragCancel = { dragFraction = null; onSeekPreview(null) },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeekPreview(((dragFraction ?: 0f) * duration).toLong())
                    },
                )
            },
    ) {
        widthPx = size.width
        val h = (if (dragFraction != null) trackHeight * 1.6f else trackHeight).toPx()
        val y = size.height / 2
        val frac = dragFraction ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bufFrac = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
        drawRect(Color(0x4DFFFFFF), Offset(0f, y - h / 2), Size(size.width, h))
        drawRect(Color(0x80FFFFFF), Offset(0f, y - h / 2), Size(size.width * bufFrac, h))
        if (duration > 0) {
            for (s in segments) {
                val c = SegmentCategory.of(s.category)?.color ?: 0xFF00D400
                val x0 = s.startMs.toFloat() / duration * size.width
                val x1 = s.endMs.toFloat() / duration * size.width
                drawRect(Color(c), Offset(x0, y - h / 2), Size((x1 - x0).coerceAtLeast(2f), h))
            }
        }
        drawRect(red, Offset(0f, y - h / 2), Size(size.width * frac, h))
        if (showThumb || dragFraction != null) {
            drawCircle(red, radius = (if (dragFraction != null) 9.dp else 6.dp).toPx(), center = Offset(size.width * frac, y))
        }
    }
}

// ---------------------------------------------------------------- Lớp điều khiển

/**
 * Lớp điều khiển phủ trên video, bố cục theo app YouTube:
 * trên: thu nhỏ ⌄ … SponsorBlock, phụ đề, cài đặt; giữa: trước / phát / tiếp; dưới: thời gian, toàn màn hình, thanh tua đỏ.
 */
@Composable
fun BoxScope.PlayerOverlay(
    now: NowPlaying,
    ui: PlayerUiState,
    isPlaying: Boolean,
    loading: Boolean,
    error: String?,
    fullscreen: Boolean,
    segments: List<Segment>,
    captionsOn: Boolean,
    sponsorOn: Boolean,
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleCaptions: () -> Unit,
    onToggleSponsor: () -> Unit,
    fullscreenActions: (@Composable () -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var doubleTapSide by remember { mutableIntStateOf(0) } // -1 trái, 1 phải
    var doubleTapCount by remember { mutableIntStateOf(0) }
    var dragY by remember { mutableFloatStateOf(0f) }

    fun poke() {
        lastInteraction = System.currentTimeMillis()
    }

    LaunchedEffect(visible, isPlaying, lastInteraction) {
        if (visible && isPlaying) {
            delay(3_000)
            if (System.currentTimeMillis() - lastInteraction >= 2_900) visible = false
        }
    }
    LaunchedEffect(doubleTapCount) {
        if (doubleTapCount > 0) {
            delay(700)
            doubleTapSide = 0
            doubleTapCount = 0
        }
    }

    val seekStep = 10_000L
    Box(
        Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { visible = !visible; poke() },
                    onDoubleTap = { o ->
                        val side = if (o.x < size.width / 2) -1 else 1
                        PlaybackController.seekBy(side * seekStep)
                        if (doubleTapSide == side) doubleTapCount++ else { doubleTapSide = side; doubleTapCount = 1 }
                    },
                )
            }
            .pointerInput(fullscreen) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        if (dragY > threshold) {
                            if (fullscreen) onToggleFullscreen() else onMinimize()
                        } else if (dragY < -threshold && !fullscreen) {
                            onToggleFullscreen()
                        }
                        dragY = 0f
                    },
                    onVerticalDrag = { _, d -> dragY += d },
                )
            },
    )

    // Hiệu ứng chạm 2 lần để tua
    if (doubleTapSide != 0) {
        Box(
            Modifier
                .align(if (doubleTapSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.4f)
                .clip(
                    if (doubleTapSide < 0) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                    else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
                )
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (doubleTapSide < 0) Icons.Filled.FastRewind else Icons.Filled.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Text("${doubleTapCount * 10} giây", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    // Vòng xoay khi đang tải
    if ((loading || ui.buffering) && error == null) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .size(if (fullscreen) 56.dp else 44.dp),
        )
    }

    AnimatedVisibility(
        visible = visible || error != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000)),
        ) {
            // ---- hàng trên
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp) else Modifier.padding(4.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (fullscreen) {
                    Column(Modifier.weight(1f)) {
                        Text(now.video.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (now.video.uploader.isNotBlank()) {
                            Text(now.video.uploader, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                } else {
                    OverlayIcon(Icons.Filled.KeyboardArrowDown, "Thu nhỏ", onMinimize)
                    Spacer(Modifier.weight(1f))
                }
                OverlayIcon(
                    Icons.Outlined.MoneyOff,
                    if (sponsorOn) "Tắt SponsorBlock" else "Bật SponsorBlock",
                    { onToggleSponsor(); poke() },
                    tint = if (sponsorOn) Color(0xFF00D400) else Color.White,
                )
                OverlayIcon(
                    if (captionsOn) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                    "Phụ đề",
                    { onToggleCaptions(); poke() },
                )
                OverlayIcon(Icons.Outlined.Settings, "Cài đặt", { onOpenSettings(); poke() })
            }

            // ---- hàng giữa
            if (error == null) {
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 64.dp else 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircleIcon(Icons.Filled.SkipPrevious, "Video trước", 44.dp, enabled = true) { PlaybackController.previous(); poke() }
                    if (!loading && !ui.buffering) {
                        CircleIcon(
                            when {
                                ui.ended -> Icons.Filled.Replay
                                isPlaying -> Icons.Filled.Pause
                                else -> Icons.Filled.PlayArrow
                            },
                            if (isPlaying) "Tạm dừng" else "Phát",
                            if (fullscreen) 72.dp else 60.dp,
                            enabled = true,
                        ) { PlaybackController.togglePlayPause(); poke() }
                    } else {
                        Spacer(Modifier.size(if (fullscreen) 72.dp else 60.dp))
                    }
                    CircleIcon(Icons.Filled.SkipNext, "Video tiếp theo", 44.dp, enabled = PlaybackController.hasNext()) {
                        PlaybackController.next(); poke()
                    }
                }
            } else {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .clickable { PlaybackController.retry() }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Icon(Icons.Filled.Replay, contentDescription = "Thử lại", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            // ---- hàng dưới
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.navigationBarsPadding().padding(horizontal = 16.dp) else Modifier),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (now.video.isLive) {
                        Box(Modifier.size(8.dp).background(Color(0xFFFF0000), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("TRỰC TIẾP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Text(formatMs(seekPreview ?: ui.position), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(" / " + formatMs(ui.duration), color = Color(0xB3FFFFFF), fontSize = 12.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    OverlayIcon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Toàn màn hình", onToggleFullscreen)
                }
                if (!now.video.isLive) {
                    YtSeekBar(
                        position = ui.position,
                        duration = ui.duration,
                        buffered = ui.buffered,
                        segments = segments,
                        showThumb = true,
                        onSeekPreview = { seekPreview = it; poke() },
                        onSeek = { PlaybackController.seekTo(it); poke() },
                        modifier = Modifier.padding(horizontal = if (fullscreen) 0.dp else 0.dp),
                    )
                }
                if (fullscreen && fullscreenActions != null) {
                    fullscreenActions()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Khi ẩn điều khiển: chỉ còn vạch đỏ mảnh ở đáy (giống YouTube, ở chế độ dọc)
    if (!visible && error == null && !fullscreen && !now.video.isLive) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0x4DFFFFFF)),
        ) {
            val frac = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(YtRed))
        }
    }
}

@Composable
private fun OverlayIcon(icon: ImageVector, desc: String, onClick: () -> Unit, tint: Color = Color.White) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, desc: String, size: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0x40000000))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = if (enabled) Color.White else Color(0x66FFFFFF), modifier = Modifier.size(size * 0.62f))
    }
}

/** Thông báo nhỏ "Đã bỏ qua đoạn Tài trợ · Hoàn tác" (SponsorBlock). */
@Composable
fun BoxScope.SkipToast(segment: Segment?, onUndo: (Segment) -> Unit) {
    if (segment == null) return
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 12.dp, bottom = 36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xE6202020))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Đã bỏ qua: " + PlaybackController.categoryLabel(segment), color = Color.White, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            "Hoàn tác",
            color = Color(0xFF3EA6FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { onUndo(segment) },
        )
    }
}

// ---------------------------------------------------------------- Trình phát mini

/** Thanh phát mini nằm trên thanh điều hướng dưới (giống YouTube). */
@Composable
fun MiniPlayerBar(
    now: NowPlaying,
    isPlaying: Boolean,
    ui: PlayerUiState,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    val yt = LocalYt.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (yt.isDark) Color(0xFF212121) else Color(0xFFFFFFFF))
            .clickable(onClick = onExpand),
    ) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
            ) {
                PlayerSurface(Modifier.fillMaxSize(), isPlaying = isPlaying)
                if (now.video.thumbnail != null && ui.height == 0) {
                    AsyncImage(now.video.thumbnail, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(now.video.title, color = yt.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(now.video.uploader, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { PlaybackController.togglePlayPause() }) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Tạm dừng" else "Phát", tint = yt.icon)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Đóng", tint = yt.icon)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(yt.divider)) {
            val frac = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(frac).height(2.dp).background(YtRed))
        }
    }
}
