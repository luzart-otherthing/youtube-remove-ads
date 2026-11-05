package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.Segment
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.Screen
import com.luzart.tubesach.ui.components.Avatar
import com.luzart.tubesach.ui.components.LoadingBox
import com.luzart.tubesach.ui.components.PillButton
import com.luzart.tubesach.ui.components.SubscribeButton
import com.luzart.tubesach.ui.components.VideoCard
import com.luzart.tubesach.ui.components.rememberMenuState
import com.luzart.tubesach.ui.components.shareVideo
import com.luzart.tubesach.ui.player.PlayerOverlay
import com.luzart.tubesach.ui.player.PlayerSettingsSheet
import com.luzart.tubesach.ui.player.PlayerSurface
import com.luzart.tubesach.ui.player.SkipToast
import com.luzart.tubesach.ui.player.rememberPlayerUiState
import com.luzart.tubesach.ui.theme.LocalYt
import kotlinx.coroutines.delay

@Composable
fun WatchScreen(vm: AppViewModel, inPip: Boolean, onToggleFullscreen: () -> Unit) {
    val yt = LocalYt.current
    val nowState by PlaybackController.nowPlaying.collectAsState()
    val now = nowState ?: return
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val loading by PlaybackController.loading.collectAsState()
    val error by PlaybackController.error.collectAsState()
    val segments by PlaybackController.segments.collectAsState()
    val caption by PlaybackController.caption.collectAsState()
    val settings by vm.store.settings.collectAsState()
    val ui = rememberPlayerUiState()

    var showSettings by remember { mutableStateOf(false) }
    var settingsOnCaptions by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Segment?>(null) }

    LaunchedEffect(Unit) {
        PlaybackController.skipped.collect { seg ->
            toast = seg
            delay(4_000)
            if (toast == seg) toast = null
        }
    }
    LaunchedEffect(now.video.id) { vm.loadCommentsPreview(now.video) }

    val compact = inPip || vm.fullscreen
    Column(
        Modifier
            .fillMaxSize()
            .background(if (compact) Color.Black else yt.background),
    ) {
        Box(
            (if (compact) Modifier.fillMaxSize() else Modifier.background(Color.Black).statusBarsPadding().fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black),
        ) {
            PlayerSurface(Modifier.fillMaxSize(), isPlaying = isPlaying)
            if (!inPip) {
                PlayerOverlay(
                    now = now,
                    ui = ui,
                    isPlaying = isPlaying,
                    loading = loading,
                    error = error,
                    fullscreen = vm.fullscreen,
                    segments = segments,
                    captionsOn = caption != null,
                    sponsorOn = settings.sponsorBlock,
                    onMinimize = { vm.watchExpanded = false },
                    onToggleFullscreen = onToggleFullscreen,
                    onOpenSettings = { settingsOnCaptions = false; showSettings = true },
                    onToggleCaptions = {
                        if (caption != null) PlaybackController.setCaption(null)
                        else { settingsOnCaptions = true; showSettings = true }
                    },
                    onToggleSponsor = { PlaybackController.setSponsorBlock(!settings.sponsorBlock) },
                    fullscreenActions = { FullscreenActions(vm, now, onComments = { showComments = true }) },
                )
                SkipToast(toast) { PlaybackController.undoSkip(it); toast = null }
            }
        }
        if (!compact) {
            WatchDetails(vm, now, onDescription = { showDescription = true }, onComments = { showComments = true })
        }
    }

    if (showSettings) {
        PlayerSettingsSheet(
            now = now,
            store = vm.store,
            currentHeight = ui.height,
            onDismiss = { showSettings = false },
            onOpenAppSettings = { vm.watchExpanded = false; vm.push(Screen.Settings) },
            startOnCaptions = settingsOnCaptions,
        )
    }
    if (showDescription) DescriptionSheet(now, onDismiss = { showDescription = false })
    if (showComments) CommentsSheet(vm, onDismiss = { showComments = false })
}

@Composable
private fun WatchDetails(vm: AppViewModel, now: NowPlaying, onDescription: () -> Unit, onComments: () -> Unit) {
    val yt = LocalYt.current
    val subs by vm.store.subscriptions.collectAsState()
    val menu = rememberMenuState()
    val list = rememberLazyListState()
    LaunchedEffect(now.video.id) { list.scrollToItem(0) }
    // Cuộn gần hết → tải thêm video liên quan (trước đây chỉ có ~20 video rồi hết).
    InfiniteScroll(list) { PlaybackController.loadMoreRelated() }
    val relatedLoading by PlaybackController.relatedLoading.collectAsState()
    val w = now.watch
    val v = now.video

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), state = list) {
        item(key = "title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDescription)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            ) {
                Text(w?.title?.ifBlank { null } ?: v.title, color = yt.text, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                val meta = listOfNotNull(
                    w?.shortViewText ?: v.viewText,
                    w?.relativeDateText ?: v.uploadedText,
                ).joinToString("  ")
                Row {
                    Text(meta, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Text("  ...thêm", color = yt.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
        item(key = "channel") {
            val channel = w?.channel
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = channel != null) { channel?.let { vm.watchExpanded = false; vm.openChannel(it.id) } }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(channel?.avatar ?: v.channelAvatar, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(channel?.name ?: v.uploader, color = yt.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                w?.subscriberText?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it.substringBefore(" người"), color = yt.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Spacer(Modifier.weight(1f))
                if (channel != null) {
                    SubscribeButton(subs.any { it.id == channel.id }, onClick = { vm.store.toggleSubscription(channel) })
                }
            }
        }
        item(key = "actions") { ActionRow(vm, now) }
        item(key = "comments") { CommentsCard(vm, now, onComments) }
        if (w == null) {
            if (now.watchFailed) {
                item(key = "relError") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { PlaybackController.reloadWatch() }
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Không tải được video đề xuất", color = yt.text, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Nhấn để thử lại", color = Color(0xFF3EA6FF), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                item(key = "loadingRel") { LoadingBox() }
            }
        }
        items(now.related.size, key = { "rel" + it + now.related[it].id }) { i ->
            val r = now.related[i]
            VideoCard(r, onClick = { vm.openVideo(r, resume = false) }, onChannelClick = { vm.watchExpanded = false; vm.openChannel(it) }, onMore = { menu.video = r })
        }
        if (relatedLoading) item(key = "relMore") { LoadingBox() }
    }
    MenuHost(vm, menu)
}

@Composable
private fun ActionRow(vm: AppViewModel, now: NowPlaying) {
    val yt = LocalYt.current
    val context = LocalContext.current
    val liked by vm.store.liked.collectAsState()
    val disliked by vm.store.disliked.collectAsState()
    val saved by vm.store.favorites.collectAsState()
    val v = now.video
    val isLiked = liked.any { it.id == v.id }
    val isDisliked = v.id in disliked
    val likeText = now.watch?.likeText ?: now.votes?.likes?.let { YouTubeRepository.formatCount(it) } ?: "Thích"
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        item {
            Row(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(yt.chip),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .fillMaxHeight()
                        .clickable { vm.store.toggleLike(v) }
                        .padding(start = 12.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, contentDescription = "Thích", tint = yt.icon, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(likeText, color = yt.text, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.width(1.dp).height(24.dp).background(yt.divider))
                Row(
                    Modifier
                        .fillMaxHeight()
                        .clickable { vm.store.toggleDislike(v.id) }
                        .padding(start = 10.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, contentDescription = "Không thích", tint = yt.icon, modifier = Modifier.size(20.dp))
                    now.votes?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(YouTubeRepository.formatCount(it.dislikes), color = yt.text, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        item { PillButton(Icons.Outlined.Share, "Chia sẻ", onClick = { shareVideo(context, v) }) }
        item {
            val isSaved = saved.any { it.id == v.id }
            PillButton(if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, if (isSaved) "Đã lưu" else "Lưu", onClick = { vm.store.toggleFavorite(v) })
        }
    }
}

@Composable
private fun FullscreenActions(vm: AppViewModel, now: NowPlaying, onComments: () -> Unit) {
    val context = LocalContext.current
    val liked by vm.store.liked.collectAsState()
    val disliked by vm.store.disliked.collectAsState()
    val saved by vm.store.favorites.collectAsState()
    val v = now.video
    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        FsAction(if (liked.any { it.id == v.id }) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, now.watch?.likeText ?: "Thích") { vm.store.toggleLike(v) }
        FsAction(if (v.id in disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, now.votes?.let { YouTubeRepository.formatCount(it.dislikes) } ?: "Không thích") { vm.store.toggleDislike(v.id) }
        FsAction(Icons.AutoMirrored.Outlined.Comment, now.watch?.commentCountText ?: "Bình luận", onComments)
        FsAction(if (saved.any { it.id == v.id }) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Lưu") { vm.store.toggleFavorite(v) }
        FsAction(Icons.Outlined.Share, "Chia sẻ") { shareVideo(context, v) }
    }
}

@Composable
private fun FsAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CommentsCard(vm: AppViewModel, now: NowPlaying, onClick: () -> Unit) {
    val yt = LocalYt.current
    val c = vm.comments
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(yt.chip)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bình luận", color = yt.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            now.watch?.commentCountText?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, color = yt.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        val first = c.items.firstOrNull()
        when {
            c.loading -> Text("Đang tải…", color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
            first != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(first.avatar, 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(first.text, color = yt.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            else -> Text(c.error ?: "Chưa có bình luận nào", color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- Mô tả

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DescriptionSheet(now: NowPlaying, onDismiss: () -> Unit) {
    val yt = LocalYt.current
    val w = now.watch
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = yt.surface) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Nội dung mô tả", color = yt.text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(w?.title ?: now.video.title, color = yt.text, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(w?.likeText ?: "–", "Lượt thích")
                Stat(w?.viewText?.removeSuffix(" lượt xem") ?: now.video.viewText?.removeSuffix(" lượt xem") ?: "–", "Lượt xem")
                Stat(w?.dateText ?: now.video.uploadedText ?: "–", "Ngày đăng")
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(yt.chip)
                    .padding(12.dp),
            ) {
                Text(linkify(w?.description.orEmpty(), onSeek = { PlaybackController.seekTo(it); onDismiss() }), color = yt.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val yt = LocalYt.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = yt.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

private val LINK = Regex("(https?://[^\\s]+)|(\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b)")

/** Biến link và mốc thời gian (12:34) trong mô tả thành chữ bấm được. */
private fun linkify(text: String, onSeek: (Long) -> Unit): AnnotatedString = buildAnnotatedString {
    var last = 0
    val style = TextLinkStyles(SpanStyle(color = Color(0xFF3EA6FF)))
    for (m in LINK.findAll(text)) {
        append(text.substring(last, m.range.first))
        val value = m.value
        if (m.groups[1] != null) {
            withLink(LinkAnnotation.Url(value, style)) { append(value) }
        } else {
            val secs = value.split(':').fold(0L) { a, p -> a * 60 + (p.toLongOrNull() ?: 0) }
            withLink(LinkAnnotation.Clickable("t$secs", style) { onSeek(secs * 1000) }) { append(value) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

// ---------------------------------------------------------------- Bình luận

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val yt = LocalYt.current
    val c = vm.comments
    val list = rememberLazyListState()
    InfiniteScroll(list) { vm.loadMoreComments() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = yt.surface) {
        Text("Bình luận", color = yt.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().height(560.dp), state = list) {
            if (c.loading) item { LoadingBox() }
            items(c.items.size, key = { it }) { i ->
                val it = c.items[i]
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Avatar(it.avatar, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (it.pinned) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PushPin, contentDescription = null, tint = yt.textSecondary, modifier = Modifier.size(12.dp))
                                Text(" Đã ghim", color = yt.textSecondary, fontSize = 11.sp)
                            }
                        }
                        Text(listOfNotNull(it.author, it.time).joinToString(" • "), color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(2.dp))
                        Text(it.text, color = yt.text, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ThumbUp, contentDescription = null, tint = yt.icon, modifier = Modifier.size(16.dp))
                            it.likes?.let { l -> Text("  $l", color = yt.textSecondary, fontSize = 12.sp) }
                            Spacer(Modifier.width(20.dp))
                            Icon(Icons.Outlined.ThumbDown, contentDescription = null, tint = yt.icon, modifier = Modifier.size(16.dp))
                            if (it.replies > 0) {
                                Spacer(Modifier.width(20.dp))
                                Text("${it.replies} phản hồi", color = Color(0xFF3EA6FF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            if (c.loadingMore) item { LoadingBox() }
            if (!c.loading && c.items.isEmpty()) {
                item { Text(c.error ?: "Chưa có bình luận nào", color = yt.textSecondary, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
