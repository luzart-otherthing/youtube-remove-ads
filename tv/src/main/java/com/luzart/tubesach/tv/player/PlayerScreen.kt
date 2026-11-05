package com.luzart.tubesach.tv.player

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.sponsor.SegmentCategory
import com.luzart.tubesach.tv.PlayerPanel
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.ui.TvButton
import com.luzart.tubesach.tv.ui.TvColors
import com.luzart.tubesach.tv.ui.TvDimens
import com.luzart.tubesach.tv.ui.TvIconButton
import com.luzart.tubesach.tv.ui.VideoCard
import com.luzart.tubesach.tv.ui.formatMs
import com.luzart.tubesach.tv.ui.isSelectKey
import com.luzart.tubesach.tv.ui.tryFocus
import kotlinx.coroutines.delay

/** Thời gian (ms) không bấm phím thì tự ẩn thanh điều khiển khi đang phát. */
const val CONTROLS_AUTO_HIDE_MS = 5_000L

/**
 * Trình phát toàn màn hình cho TV.
 *
 * Thanh điều khiển ẩn: OK = tạm dừng/phát, ◀ ▶ = lùi/tua 10 giây, ▲ ▼ = hiện thanh điều khiển.
 * Thanh điều khiển hiện: chọn nút bằng ◀ ▶, ▼ để xem "Video tiếp theo", Quay lại để ẩn.
 */
@Composable
fun PlayerScreen(vm: TvViewModel) {
    val now by PlaybackController.nowPlaying.collectAsState()
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val loading by PlaybackController.loading.collectAsState()
    val error by PlaybackController.error.collectAsState()
    val segments by PlaybackController.segments.collectAsState()

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val seek = remember { SeekAccumulator() }
    var seekTarget by remember { mutableStateOf<Long?>(null) }
    var seekTick by remember { mutableIntStateOf(0) }
    var lastKeyAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var skippedText by remember { mutableStateOf<String?>(null) }

    // Vị trí phát, cập nhật 4 lần mỗi giây.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var wasEnded = false
        while (true) {
            val p = PlaybackController.player
            // Chỉ cập nhật khi thanh tua đang hiện → TV yếu không phải vẽ lại liên tục khi đang xem.
            if (vm.controlsVisible || seekTarget != null) {
                position = p.currentPosition
                duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: ((now?.video?.durationSec ?: 0) * 1000)
                buffered = p.bufferedPosition
            }
            // Hết video (không tự phát tiếp) → hiện thanh điều khiển để chọn xem lại / video khác.
            val ended = p.playbackState == Player.STATE_ENDED
            if (ended && !wasEnded && !vm.controlsVisible && vm.panel == null) vm.controlsVisible = true
            wasEnded = ended
            delay(250)
        }
    }

    val isLive = now?.video?.isLive == true

    fun showControls() {
        vm.controlsVisible = true
    }

    fun pressSeek(forward: Boolean, repeatCount: Int) {
        if (isLive) return
        val p = PlaybackController.player
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: duration
        seekTarget = seek.press(forward, p.currentPosition, dur, repeatCount)
        seekTick++
    }

    fun perform(action: PlayerKeyAction, repeatCount: Int) {
        when (action) {
            PlayerKeyAction.TOGGLE_PLAY -> {
                PlaybackController.togglePlayPause()
                showControls()
            }
            PlayerKeyAction.PLAY -> PlaybackController.player.play()
            PlayerKeyAction.PAUSE -> PlaybackController.pause()
            PlayerKeyAction.SEEK_BACK -> pressSeek(false, repeatCount)
            PlayerKeyAction.SEEK_FORWARD -> pressSeek(true, repeatCount)
            PlayerKeyAction.NEXT -> if (PlaybackController.hasNext()) PlaybackController.next()
            PlayerKeyAction.PREVIOUS -> PlaybackController.previous()
            PlayerKeyAction.SHOW_CONTROLS -> showControls()
            PlayerKeyAction.CLOSE -> vm.closePlayer()
            PlayerKeyAction.NONE -> Unit
        }
    }

    // Ngừng bấm ◀ ▶ một lúc → mới thật sự tua (đỡ tải lại video nhiều lần).
    LaunchedEffect(seekTick) {
        if (seekTarget == null) return@LaunchedEffect
        delay(SeekAccumulator.COMMIT_DELAY_MS)
        seek.commit()?.let { PlaybackController.seekTo(it) }
        position = seekTarget ?: position
        seekTarget = null
    }

    // Tự ẩn thanh điều khiển sau vài giây không bấm gì (chỉ khi đang phát).
    LaunchedEffect(vm.controlsVisible, isPlaying, vm.panel, lastKeyAt) {
        if (vm.controlsVisible && isPlaying && vm.panel == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            vm.controlsVisible = false
        }
    }

    // Đưa ô sáng đúng chỗ: nút Phát khi hiện điều khiển, nút Thử lại khi lỗi, cả màn hình khi ẩn.
    LaunchedEffect(vm.controlsVisible, error, vm.panel) {
        withFrameNanos { }
        when {
            vm.panel != null -> Unit // bảng chọn tự đặt ô sáng
            error != null -> retryFocus.tryFocus()
            vm.controlsVisible -> if (!controlsFocus.tryFocus()) playFocus.tryFocus()
            else -> rootFocus.tryFocus()
        }
    }
    // Mỗi lần hiện thanh điều khiển: bắt đầu từ nút Phát/Tạm dừng.
    LaunchedEffect(vm.controlsVisible) {
        if (vm.controlsVisible) {
            withFrameNanos { }
            playFocus.tryFocus()
        }
    }

    // Lỗi phát: ẩn thanh điều khiển / bảng chọn → nút Quay lại thoát video ngay ở lần bấm đầu.
    LaunchedEffect(error) {
        if (error != null) {
            vm.controlsVisible = false
            vm.panel = null
        }
    }

    // Đổi sang video khác (⏭, hết video tự phát tiếp) khi đang hiện điều khiển → ô sáng về nút Phát.
    var lastVideoId by remember { mutableStateOf(now?.video?.id) }
    LaunchedEffect(now?.video?.id) {
        val id = now?.video?.id
        if (id != null && id != lastVideoId && vm.controlsVisible && vm.panel == null) {
            withFrameNanos { }
            playFocus.tryFocus()
        }
        lastVideoId = id
    }

    // Thông báo "Đã bỏ qua đoạn tài trợ".
    LaunchedEffect(Unit) {
        PlaybackController.skipped.collect { seg ->
            skippedText = "Đã bỏ qua: ${PlaybackController.categoryLabel(seg)}"
        }
    }
    LaunchedEffect(skippedText) {
        if (skippedText != null) {
            delay(3_000)
            skippedText = null
        }
    }

    // Video bị đóng từ nơi khác (ví dụ phím Stop) → thoát trình phát.
    LaunchedEffect(now == null) {
        if (now == null) {
            delay(300)
            if (PlaybackController.nowPlaying.value == null && vm.playerOpen) vm.closePlayer()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                val native = e.nativeKeyEvent
                val code = native.keyCode
                if (e.type == KeyEventType.KeyDown) lastKeyAt = SystemClock.uptimeMillis()
                if (isMediaKey(code)) {
                    val action = mapPlayerKey(code, controlsVisible = true)
                    val isSeek = action == PlayerKeyAction.SEEK_BACK || action == PlayerKeyAction.SEEK_FORWARD
                    // Giữ phím ⏯ / ⏭: chỉ tính một lần (riêng tua thì giữ để tua nhanh).
                    if (e.type == KeyEventType.KeyDown && (native.repeatCount == 0 || isSeek)) perform(action, native.repeatCount)
                    return@onPreviewKeyEvent true
                }
                if (vm.panel != null || error != null) return@onPreviewKeyEvent false
                val action = mapPlayerKey(code, vm.controlsVisible)
                if (action == PlayerKeyAction.NONE) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) perform(action, native.repeatCount)
                true
            }
            // Ô sáng không được lọt ra phần duyệt video phía sau.
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusRequester(rootFocus)
            .focusable()
            .testTag("player"),
    ) {
        TvPlayerSurface(isPlaying = isPlaying, modifier = Modifier.fillMaxSize())

        if (loading && error == null) {
            CircularProgressIndicator(
                color = TvColors.red,
                strokeWidth = 5.dp,
                modifier = Modifier.align(Alignment.Center).size(64.dp),
            )
        }

        // Đang tua khi thanh điều khiển ẩn: chỉ hiện thanh tua ở dưới.
        if (!vm.controlsVisible && seekTarget != null && error == null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(horizontal = TvDimens.safeHorizontal, vertical = TvDimens.safeVertical),
            ) {
                SeekBar({ position }, { duration }, { buffered }, segments, seekTarget, focused = true, isLive = isLive)
            }
        }

        AnimatedVisibility(
            visible = vm.controlsVisible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                vm = vm,
                isPlaying = isPlaying,
                position = { position },
                duration = { duration },
                buffered = { buffered },
                segments = segments,
                seekTarget = seekTarget,
                isLive = isLive,
                playFocus = playFocus,
                controlsFocus = controlsFocus,
                onSeek = ::pressSeek,
            )
        }

        error?.let { msg ->
            ErrorOverlay(
                message = msg,
                retryFocus = retryFocus,
                onRetry = { PlaybackController.retry() },
                onClose = { vm.closePlayer() },
                onNext = if (PlaybackController.hasNext()) ({ PlaybackController.next() }) else null,
            )
        }

        skippedText?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = TvDimens.safeVertical, end = TvDimens.safeHorizontal)
                    .background(Color(0xE6202020), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("skipToast"),
            )
        }

        vm.panel?.let { p -> PlayerPanelContent(vm, p) }
    }
}

// ---------------------------------------------------------------- Khung hình video

@OptIn(UnstableApi::class)
@Composable
private fun TvPlayerSurface(isPlaying: Boolean, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                // Không cho khung video "giành" phím của remote.
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                subtitleView?.apply {
                    setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            0xBF000000.toInt(),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            android.graphics.Color.WHITE,
                            null,
                        ),
                    )
                    // Chữ phụ đề to hơn một chút cho dễ đọc từ xa.
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.15f)
                }
                player = PlaybackController.player
            }
        },
        update = { view ->
            if (view.player !== PlaybackController.player) view.player = PlaybackController.player
            view.keepScreenOn = isPlaying
        },
        onRelease = { it.player = null },
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- Thanh điều khiển

@Composable
private fun Controls(
    vm: TvViewModel,
    isPlaying: Boolean,
    position: () -> Long,
    duration: () -> Long,
    buffered: () -> Long,
    segments: List<Segment>,
    seekTarget: Long?,
    isLive: Boolean,
    playFocus: FocusRequester,
    controlsFocus: FocusRequester,
    onSeek: (forward: Boolean, repeatCount: Int) -> Unit,
) {
    val now by PlaybackController.nowPlaying.collectAsState()
    val settings by vm.store.settings.collectAsState()
    val caption by PlaybackController.caption.collectAsState()
    val speed by PlaybackController.speed.collectAsState()
    val sleep by PlaybackController.sleepTimer.collectAsState()
    val liked by vm.store.liked.collectAsState()
    val later by vm.store.favorites.collectAsState()
    val video = now?.video ?: return
    var label by remember { mutableStateOf("") }
    var relatedFocused by remember { mutableStateOf(false) }
    // Sang video khác: hàng "Video tiếp theo" được dựng lại, ô sáng không còn ở đó.
    LaunchedEffect(video.id) { relatedFocused = false }
    var relatedHeightPx by remember { mutableIntStateOf(0) }
    val related = now?.related.orEmpty().filter { !it.isShort }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        // ---- Trên: tiêu đề, kênh (ẩn khi đang xem "Video tiếp theo" cho khỏi chồng lên nhau)
        if (!relatedFocused) Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xE6000000), Color.Transparent)))
                .padding(horizontal = TvDimens.safeHorizontal, vertical = TvDimens.safeVertical),
        ) {
            Text(
                video.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("playerTitle"),
            )
            Spacer(Modifier.height(6.dp))
            val votes = now?.votes
            val meta = listOfNotNull(
                video.uploader.takeIf { it.isNotBlank() },
                video.viewText,
                video.uploadedText,
                votes?.let { "👍 ${YouTubeRepository.formatCount(it.likes)}" },
                votes?.takeIf { settings.returnDislike }?.let { "👎 ${YouTubeRepository.formatCount(it.dislikes)}" },
            ).joinToString("  •  ")
            Text(meta, style = MaterialTheme.typography.bodyLarge, color = Color(0xFFDDDDDD), maxLines = 1)
        }

        // ---- Dưới: thanh tua, nút, video tiếp theo
        val slide by animateDpAsState(
            if (relatedFocused || related.isEmpty()) 0.dp else with(density) { relatedHeightPx.toDp() },
            label = "relatedSlide",
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .offset { IntOffset(0, slide.roundToPx()) }
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000), Color(0xF2000000))))
                .padding(horizontal = TvDimens.safeHorizontal)
                .padding(top = 48.dp, bottom = TvDimens.safeVertical),
        ) {
            SeekBar(
                position, duration, buffered, segments, seekTarget,
                focused = false,
                isLive = isLive,
                focusable = true,
                onSeek = onSeek,
                onFocused = { label = "◀ ▶ để tua" },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .focusRequester(controlsFocus)
                    .focusRestorer(playFocus)
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvIconButton(Icons.Filled.SkipPrevious, "Video trước", { PlaybackController.previous() }, onFocused = { label = "Video trước" })
                TvIconButton(Icons.Filled.Replay10, "Lùi 10 giây", { onSeek(false, 0) }, onFocused = { label = "Lùi 10 giây" })
                TvIconButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    "Phát / Tạm dừng",
                    { PlaybackController.togglePlayPause() },
                    size = 60.dp,
                    modifier = Modifier.focusRequester(playFocus),
                    onFocused = { label = if (isPlaying) "Tạm dừng" else "Phát" },
                )
                TvIconButton(Icons.Filled.Forward10, "Tua 10 giây", { onSeek(true, 0) }, onFocused = { label = "Tua tới 10 giây" })
                TvIconButton(
                    Icons.Filled.SkipNext,
                    "Video tiếp theo",
                    { if (PlaybackController.hasNext()) PlaybackController.next() },
                    onFocused = { label = related.firstOrNull()?.let { "Tiếp theo: ${it.title}" } ?: "Video tiếp theo" },
                )
                Spacer(Modifier.width(16.dp))
                TvIconButton(
                    if (caption != null) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionDisabled,
                    "Phụ đề",
                    { vm.panel = PlayerPanel.CAPTIONS },
                    active = caption != null,
                    onFocused = { label = "Phụ đề: " + (caption?.label ?: "Tắt") },
                )
                TvIconButton(
                    Icons.Filled.HighQuality,
                    "Chất lượng",
                    { vm.panel = PlayerPanel.QUALITY },
                    onFocused = {
                        val h = PlaybackController.currentHeight()
                        label = "Chất lượng: " + QualityOption.current(settings).label + (h?.let { " – đang phát ${it}p" } ?: "")
                    },
                )
                TvIconButton(
                    Icons.Filled.Speed,
                    "Tốc độ phát",
                    { vm.panel = PlayerPanel.SPEED },
                    active = speed != 1f,
                    onFocused = { label = "Tốc độ phát: " + speedLabel(speed) },
                )
                TvIconButton(
                    Icons.Filled.MoneyOff,
                    "SponsorBlock",
                    { PlaybackController.setSponsorBlock(!settings.sponsorBlock) },
                    active = settings.sponsorBlock,
                    onFocused = { label = "Bỏ qua đoạn tài trợ (SponsorBlock): " + if (settings.sponsorBlock) "Bật" else "Tắt" },
                )
                val isLiked = liked.any { it.id == video.id }
                TvIconButton(
                    if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    "Thích",
                    { vm.store.toggleLike(video) },
                    active = isLiked,
                    onFocused = { label = if (isLiked) "Đã thích" else "Thích video này" },
                )
                val isSaved = later.any { it.id == video.id }
                TvIconButton(
                    if (isSaved) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                    "Xem sau",
                    { vm.store.toggleFavorite(video) },
                    active = isSaved,
                    onFocused = { label = if (isSaved) "Đã lưu vào Xem sau" else "Lưu vào Xem sau" },
                )
                TvIconButton(
                    Icons.Filled.Bedtime,
                    "Hẹn giờ tắt",
                    { vm.panel = PlayerPanel.SLEEP },
                    active = sleep != null,
                    onFocused = { label = "Hẹn giờ tắt" + sleepRemaining(sleep?.endAt, sleep != null) },
                )
                TvIconButton(
                    Icons.Filled.Info,
                    "Thông tin",
                    { vm.panel = PlayerPanel.INFO },
                    onFocused = { label = "Thông tin video, kênh, đăng ký" },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("controlLabel"))

            if (related.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("Video tiếp theo  ▾", style = MaterialTheme.typography.titleMedium, color = Color(0xFFDDDDDD))
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { relatedHeightPx = it.height }
                        .onFocusChanged { relatedFocused = it.hasFocus }
                        .focusRestorer()
                        .focusGroup()
                        .testTag("related"),
                ) {
                    items(related, key = { it.id }) { v ->
                        VideoCard(v, onClick = { vm.openVideo(v, resume = false) }, modifier = Modifier.width(210.dp))
                    }
                }
            }
        }
    }
}

private fun sleepRemaining(endAt: Long?, active: Boolean): String {
    if (!active) return ""
    if (endAt == null) return ": khi hết video này"
    val min = ((endAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1
    return ": còn khoảng $min phút"
}

// ---------------------------------------------------------------- Thanh tua

/**
 * Thanh tua màu đỏ như YouTube. Đoạn tài trợ (SponsorBlock) được tô màu.
 * Khi được chọn: ◀ ▶ để tua (giữ phím để tua nhanh), OK để tạm dừng/phát.
 */
@Composable
fun SeekBar(
    positionOf: () -> Long,
    durationOf: () -> Long,
    bufferedOf: () -> Long,
    segments: List<Segment>,
    seekTarget: Long?,
    focused: Boolean,
    isLive: Boolean,
    focusable: Boolean = false,
    onSeek: (forward: Boolean, repeatCount: Int) -> Unit = { _, _ -> },
    onFocused: () -> Unit = {},
) {
    var hasFocus by remember { mutableStateOf(false) }
    val active = focused || hasFocus
    // Đọc vị trí ở đây → chỉ thanh tua vẽ lại 4 lần/giây, không phải cả thanh điều khiển.
    val position = positionOf()
    val duration = durationOf()
    val buffered = bufferedOf()
    val shown = seekTarget ?: position
    val fraction = if (duration > 0) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .then(
                    if (focusable) {
                        Modifier
                            .onFocusChanged {
                                hasFocus = it.isFocused
                                if (it.isFocused) onFocused()
                            }
                            .onKeyEvent { e ->
                                val n = e.nativeKeyEvent
                                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when {
                                    n.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { onSeek(false, n.repeatCount); true }
                                    n.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { onSeek(true, n.repeatCount); true }
                                    isSelectKey(n.keyCode) && n.repeatCount == 0 -> { PlaybackController.togglePlayPause(); true }
                                    else -> false
                                }
                            }
                            .focusable()
                            .testTag("seekBar")
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackHeight = if (active) 8.dp else 5.dp
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(trackHeight).clip(RoundedCornerShape(4.dp)).background(Color(0x55FFFFFF))) {
                if (!isLive) {
                    Box(Modifier.fillMaxWidth(bufferedFraction).fillMaxHeight().background(Color(0x88FFFFFF)))
                    if (duration > 0) {
                        segments.forEach { s ->
                            val start = (s.startMs.toFloat() / duration).coerceIn(0f, 1f)
                            val end = (s.endMs.toFloat() / duration).coerceIn(0f, 1f)
                            if (end > start) {
                                Box(
                                    Modifier
                                        .offset(x = w * start)
                                        .width(w * (end - start))
                                        .fillMaxHeight()
                                        .background(Color(SegmentCategory.of(s.category)?.color ?: 0xFF00D400)),
                                )
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(if (isLive) 1f else fraction).fillMaxHeight().background(TvColors.red))
            }
            if (active && !isLive) {
                val knob = 18.dp
                Box(
                    Modifier
                        .offset(x = (w * fraction - knob / 2).coerceAtLeast(0.dp))
                        .size(knob)
                        .clip(CircleShape)
                        .background(if (hasFocus) Color.White else TvColors.red),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLive) {
                Text(
                    "TRỰC TIẾP",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xFFCC0000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            } else {
                Text(
                    "${formatMs(shown)} / ${formatMs(duration)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (seekTarget != null) TvColors.accent else Color.White,
                    modifier = Modifier.testTag("time"),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Lỗi

@Composable
private fun ErrorOverlay(
    message: String,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onNext: (() -> Unit)?,
) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(48.dp)) {
            Text(message, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("playerError"))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.focusGroup()) {
                TvButton("Thử lại", onRetry, modifier = Modifier.focusRequester(retryFocus))
                if (onNext != null) TvButton("Video tiếp theo", onNext)
                TvButton("Quay lại", onClose)
            }
        }
    }
}
