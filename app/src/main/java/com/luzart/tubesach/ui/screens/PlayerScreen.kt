package com.luzart.tubesach.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.components.Divider
import com.luzart.tubesach.ui.components.TopBar
import com.luzart.tubesach.ui.components.VideoRow
import com.luzart.tubesach.ui.components.formatMs
import com.luzart.tubesach.ui.components.subtitleOf
import com.luzart.tubesach.ui.theme.LiveRed
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    vm: AppViewModel,
    inPip: Boolean,
    pipSupported: Boolean,
    onEnterPip: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
) {
    val now by PlaybackController.nowPlaying.collectAsState()
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val loading by PlaybackController.loading.collectAsState()
    val error by PlaybackController.error.collectAsState()
    val sleep by PlaybackController.sleepTimer.collectAsState()
    val favorites by vm.store.favorites.collectAsState()
    val settings by vm.store.settings.collectAsState()

    val current = now
    if (current == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val video = current.video
    val compact = inPip || vm.fullscreen

    // Cập nhật vị trí phát mỗi nửa giây để vẽ thanh tua.
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        val p = PlaybackController.player
        while (true) {
            position = p.currentPosition
            duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (video.durationSec * 1000)
            clock = System.currentTimeMillis()
            delay(500)
        }
    }

    var showSleepDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(video.id) { listState.scrollToItem(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (compact) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        if (!compact) TopBar("Đang xem", onBack = { vm.back() })

        VideoArea(
            modifier = if (compact) Modifier.fillMaxSize() else Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            fullscreen = vm.fullscreen && !inPip,
            inPip = inPip,
            isPlaying = isPlaying,
            loading = loading,
            error = error,
            onExitFullscreen = onExitFullscreen,
        )

        if (!compact) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                // ---- Thanh tua + thời gian
                item {
                    if (video.isLive) {
                        Text(
                            "🔴 Đang phát trực tiếp",
                            color = LiveRed,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        SeekBar(position, duration)
                    }
                }

                // ---- Nút điều khiển chính, thật to
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeekButton(Icons.Filled.Replay10, "Lùi 10 giây", enabled = !video.isLive) {
                            PlaybackController.seekBy(-PlaybackController.SEEK_MS)
                        }
                        FilledIconButton(
                            onClick = { PlaybackController.togglePlayPause() },
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                                modifier = Modifier.size(68.dp),
                            )
                        }
                        SeekButton(Icons.Filled.Forward10, "Tới 10 giây", enabled = !video.isLive) {
                            PlaybackController.seekBy(PlaybackController.SEEK_MS)
                        }
                    }
                }

                // ---- Các nút tiện ích
                item {
                    val isFav = favorites.any { it.id == video.id }
                    val sleepLabel = when {
                        sleep == null -> "Hẹn giờ tắt"
                        sleep?.endAt == null -> "Tắt khi hết video"
                        else -> {
                            val mins = (((sleep?.endAt ?: 0L) - clock) / 60_000L + 1).coerceAtLeast(1)
                            "Tắt sau $mins phút"
                        }
                    }
                    Column(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (pipSupported) {
                                ActionButton(Icons.Filled.PictureInPictureAlt, "Thu nhỏ màn hình", onClick = onEnterPip)
                            }
                            ActionButton(Icons.Filled.Fullscreen, "Toàn màn hình", onClick = onEnterFullscreen)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ActionButton(
                                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (isFav) "Đã yêu thích" else "Yêu thích",
                                tint = if (isFav) Color(0xFFE53935) else null,
                            ) { vm.store.toggleFavorite(video) }
                            ActionButton(
                                Icons.Filled.Bedtime,
                                sleepLabel,
                                highlighted = sleep != null,
                            ) { showSleepDialog = true }
                        }
                    }
                }

                // ---- Tiêu đề
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(video.title, style = MaterialTheme.typography.titleLarge)
                        val sub = subtitleOf(video)
                        if (sub.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Divider()
                }

                // ---- Tự phát tiếp
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.store.updateSettings { it.copy(autoplay = !it.autoplay) } }
                            .heightIn(min = 68.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Tự động phát video tiếp theo",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = settings.autoplay,
                            onCheckedChange = { v -> vm.store.updateSettings { it.copy(autoplay = v) } },
                        )
                    }
                    Divider()
                }

                if (current.related.isNotEmpty()) {
                    item {
                        Text(
                            "Video liên quan",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(current.related, key = { "r" + it.id }) { item ->
                        VideoRow(item, onClick = { vm.openVideo(item) })
                    }
                }
            }
        }
    }

    if (showSleepDialog) {
        SleepTimerDialog(
            active = sleep != null,
            onPick = { minutes ->
                PlaybackController.setSleepTimer(minutes)
                showSleepDialog = false
            },
            onCancelTimer = {
                PlaybackController.cancelSleepTimer()
                showSleepDialog = false
            },
            onDismiss = { showSleepDialog = false },
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoArea(
    modifier: Modifier,
    fullscreen: Boolean,
    inPip: Boolean,
    isPlaying: Boolean,
    loading: Boolean,
    error: String?,
    onExitFullscreen: () -> Unit,
) {
    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = PlaybackController.player
                }
            },
            update = { view ->
                if (view.player !== PlaybackController.player) view.player = PlaybackController.player
                view.useController = fullscreen
                if (!fullscreen) view.hideController()
                view.keepScreenOn = isPlaying
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (loading && error == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 5.dp, modifier = Modifier.size(56.dp))
                if (!inPip) {
                    Spacer(Modifier.height(12.dp))
                    Text("Đang tải video…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (error != null && !inPip) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    error,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { PlaybackController.retry() },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text("Thử lại", style = MaterialTheme.typography.titleMedium) }
            }
        }

        // Nút phát to ở giữa khi đang tạm dừng
        if (!loading && error == null && !isPlaying && !fullscreen && !inPip) {
            FilledIconButton(
                onClick = { PlaybackController.togglePlayPause() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(84.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xB3000000)),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Phát", tint = Color.White, modifier = Modifier.size(60.dp))
            }
        }

        if (fullscreen) {
            Button(
                onClick = onExitFullscreen,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x99000000), contentColor = Color.White),
                shape = RoundedCornerShape(30.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .heightIn(min = 56.dp),
            ) {
                Icon(Icons.Filled.FullscreenExit, contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(6.dp))
                Text("Thu về", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(position: Long, duration: Long) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = dragging ?: fraction
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { PlaybackController.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            // Nút kéo tròn to + thanh dày để người lớn tuổi dễ chạm.
            thumb = {
                Box(
                    Modifier
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(10.dp),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatMs((shown * duration).toLong()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(formatMs(duration), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SeekButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(76.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RowScope.ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = if (highlighted) {
            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 84.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint ?: MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SleepTimerDialog(
    active: Boolean,
    onPick: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tự tắt sau bao lâu?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to "15 phút", 30 to "30 phút", 60 to "1 tiếng", 90 to "1 tiếng rưỡi", 0 to "Khi hết video này")
                    .forEach { (minutes, label) ->
                        OutlinedButton(
                            onClick = { onPick(minutes) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
                    }
                if (active) {
                    Button(
                        onClick = onCancelTimer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) { Text("Huỷ hẹn giờ", style = MaterialTheme.typography.titleMedium) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 52.dp)) {
                Text("Đóng", style = MaterialTheme.typography.titleMedium)
            }
        },
    )
}
