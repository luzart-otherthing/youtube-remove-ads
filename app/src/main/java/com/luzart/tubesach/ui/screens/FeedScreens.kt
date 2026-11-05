package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzart.tubesach.R
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.FeedState
import com.luzart.tubesach.ui.ListKind
import com.luzart.tubesach.ui.Screen
import com.luzart.tubesach.ui.components.Avatar
import com.luzart.tubesach.ui.components.ChipRow
import com.luzart.tubesach.ui.components.EmptyState
import com.luzart.tubesach.ui.components.ErrorState
import com.luzart.tubesach.ui.components.LoadingBox
import com.luzart.tubesach.ui.components.MenuState
import com.luzart.tubesach.ui.components.ShelfHeader
import com.luzart.tubesach.ui.components.ShortsShelf
import com.luzart.tubesach.ui.components.SubscribeButton
import com.luzart.tubesach.ui.components.VideoCard
import com.luzart.tubesach.ui.components.VideoMenuSheet
import com.luzart.tubesach.ui.components.VideoRow
import com.luzart.tubesach.ui.components.VideoTile
import com.luzart.tubesach.ui.components.YtDivider
import com.luzart.tubesach.ui.components.rememberMenuState
import com.luzart.tubesach.ui.theme.LocalYt

// ---------------------------------------------------------------- Thanh trên

@Composable
fun HomeTopBar(vm: AppViewModel) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(yt.background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF1554B3)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_pip_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text("Tube Sạch", color = yt.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.openSearchInput() }) {
            Icon(Icons.Outlined.Search, contentDescription = "Tìm kiếm", tint = yt.icon)
        }
        IconButton(onClick = { vm.push(Screen.Settings) }) {
            Icon(Icons.Outlined.Settings, contentDescription = "Cài đặt", tint = yt.icon)
        }
    }
}

@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(yt.background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = yt.icon) }
        Text(title, color = yt.text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

// ---------------------------------------------------------------- Danh sách chung

/** Vẽ các mục của trang chủ / kết quả tìm kiếm. */
fun LazyListScope.feedItems(
    vm: AppViewModel,
    entries: List<FeedEntry>,
    menu: MenuState,
    keyPrefix: String,
) {
    items(entries.size, key = { i -> keyPrefix + i + entries[i].key }) { i ->
        when (val e = entries[i]) {
            is FeedEntry.Video -> VideoCard(
                e.video,
                onClick = { vm.openVideo(e.video) },
                onChannelClick = { vm.openChannel(it) },
                onMore = { menu.video = e.video },
            )
            is FeedEntry.ShortsShelf -> ShortsShelf(e.title, e.shorts) { vm.openShort(it) }
            is FeedEntry.Shelf -> Column(Modifier.padding(bottom = 16.dp)) {
                if (e.title.isNotBlank()) ShelfHeader(e.title)
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(e.videos, key = { it.id }) { v ->
                        VideoTile(v, 300.dp, onClick = { vm.openVideo(v) }, onMore = { menu.video = v })
                    }
                }
            }
            is FeedEntry.Channel -> ChannelResultRow(vm, e.channel)
        }
    }
}

@Composable
fun ChannelResultRow(vm: AppViewModel, channel: com.luzart.tubesach.data.ChannelRef) {
    val yt = LocalYt.current
    val subs by vm.store.subscriptions.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openChannel(channel.id) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp), contentAlignment = Alignment.Center) { Avatar(channel.avatar, 88.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = yt.text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            channel.subtitle?.let { Text(it, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            Spacer(Modifier.height(8.dp))
            SubscribeButton(subs.any { it.id == channel.id }, onClick = { vm.store.toggleSubscription(channel) })
        }
    }
}

@Composable
fun MenuHost(vm: AppViewModel, menu: MenuState) {
    val v = menu.video ?: return
    val saved by vm.store.favorites.collectAsState()
    VideoMenuSheet(
        video = v,
        saved = saved.any { it.id == v.id },
        onDismiss = { menu.video = null },
        onToggleSave = { vm.store.toggleFavorite(v) },
        onNotInterested = { vm.store.hide(v.id) },
        onOpenChannel = { vm.openChannel(it) },
    )
}

@Composable
fun InfiniteScroll(state: LazyListState, onLoadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(nearEnd) { if (nearEnd) onLoadMore() }
}

// ---------------------------------------------------------------- Trang chủ

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onOpenUpdate: (String) -> Unit) {
    val yt = LocalYt.current
    LaunchedEffect(Unit) { vm.loadHome() }
    val state = vm.home
    val menu = rememberMenuState()
    val list = rememberLazyListState()
    InfiniteScroll(list) { vm.loadMoreHome() }
    vm.store.hidden.collectAsState().value
    vm.store.settings.collectAsState().value

    Column(Modifier.fillMaxSize().background(yt.background)) {
        HomeTopBar(vm)
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refreshHome() }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                if (state.chips.isNotEmpty()) {
                    item(key = "chips") { ChipRow(state.chips, state.selectedChip, onSelect = { vm.selectChip(it) }) }
                }
                vm.update?.let { info ->
                    item(key = "update") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(yt.chip)
                                .clickable { vm.installUpdate(onOpenUpdate) }
                                .padding(16.dp),
                        ) {
                            Column {
                                Text("Có phiên bản mới ${info.version}", color = yt.text, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (vm.downloadingUpdate) "Đang tải về…" else "Nhấn để tải về và cài đặt",
                                    color = yt.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                when {
                    state.loading -> item(key = "loading") { LoadingBox() }
                    state.error != null && state.entries.isEmpty() -> item(key = "error") { ErrorState(state.error) { vm.refreshHome() } }
                    state.loaded && state.entries.isEmpty() -> {
                        item(key = "empty") {
                            EmptyState(
                                Icons.Outlined.Search,
                                "Thử tìm kiếm để bắt đầu",
                                "Bắt đầu xem video để chúng tôi tạo nguồn cấp dữ liệu gồm những video mà bạn sẽ thích.",
                                action = "Tìm kiếm",
                                onAction = { vm.openSearchInput() },
                            )
                        }
                        if (vm.homeTrending.isNotEmpty()) {
                            item(key = "trend-h") { ShelfHeader("Thịnh hành tại Việt Nam", icon = Icons.Outlined.Whatshot) }
                            items(vm.homeTrending, key = { "t" + it.id }) { v ->
                                VideoCard(v, onClick = { vm.openVideo(v) }, onChannelClick = { vm.openChannel(it) }, onMore = { menu.video = v })
                            }
                        }
                    }
                    else -> feedItems(vm, vm.visible(state.entries), menu, "home")
                }
                if (state.loadingMore) item(key = "more") { LoadingBox() }
            }
        }
    }
    MenuHost(vm, menu)
}

// ---------------------------------------------------------------- Kênh đăng ký

@Composable
fun SubscriptionsScreen(vm: AppViewModel) {
    val yt = LocalYt.current
    val subs by vm.store.subscriptions.collectAsState()
    LaunchedEffect(subs.size) { vm.loadSubscriptions(force = true) }
    val menu = rememberMenuState()
    Column(Modifier.fillMaxSize().background(yt.background)) {
        HomeTopBar(vm)
        if (subs.isEmpty()) {
            EmptyState(
                Icons.Outlined.Subscriptions,
                "Đừng bỏ lỡ video mới",
                "Đăng ký kênh để xem video mới nhất của họ tại đây. Bạn không cần đăng nhập.",
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "avatars") {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(subs, key = { it.id }) { c ->
                        Column(
                            Modifier
                                .width(72.dp)
                                .clickable { vm.openChannel(c.id) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(c.avatar, 56.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(c.name, color = yt.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                YtDivider()
            }
            val feed = vm.subscriptionFeed
            when {
                feed.loading -> item(key = "loading") { LoadingBox() }
                feed.entries.isEmpty() -> item(key = "empty") { EmptyState(Icons.Outlined.Subscriptions, "Chưa có video mới", null) }
                else -> feedItems(vm, vm.visible(feed.entries), menu, "subs")
            }
        }
    }
    MenuHost(vm, menu)
}

// ---------------------------------------------------------------- Bạn

@Composable
fun YouScreen(vm: AppViewModel) {
    val yt = LocalYt.current
    val history by vm.store.history.collectAsState()
    val liked by vm.store.liked.collectAsState()
    val later by vm.store.favorites.collectAsState()
    val menu = rememberMenuState()
    Column(Modifier.fillMaxSize().background(yt.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.openSearchInput() }) { Icon(Icons.Outlined.Search, contentDescription = "Tìm kiếm", tint = yt.icon) }
            IconButton(onClick = { vm.push(Screen.Settings) }) { Icon(Icons.Outlined.Settings, contentDescription = "Cài đặt", tint = yt.icon) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(null, 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Khách", color = yt.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Chưa đăng nhập • Dữ liệu chỉ lưu trên máy này", color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                ShelfHeader("Video đã xem", action = "Xem tất cả") { vm.push(Screen.VideoList(ListKind.HISTORY)) }
                if (history.isEmpty()) {
                    Text("Những video bạn xem sẽ xuất hiện ở đây", color = yt.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                } else {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history.take(20), key = { it.video.id }) { h ->
                            val d = h.video.durationSec * 1000f
                            VideoTile(h.video, 160.dp, onClick = { vm.openVideo(h.video) }, progress = if (d > 0) h.positionMs / d else null, onMore = { menu.video = h.video })
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            item { YouRow(Icons.Outlined.History, "Video đã xem", "${history.size} video") { vm.push(Screen.VideoList(ListKind.HISTORY)) } }
            item { YouRow(Icons.Outlined.ThumbUp, "Video đã thích", "${liked.size} video") { vm.push(Screen.VideoList(ListKind.LIKED)) } }
            item { YouRow(Icons.Outlined.WatchLater, "Xem sau", "${later.size} video") { vm.push(Screen.VideoList(ListKind.WATCH_LATER)) } }
            item { YtDivider() }
            item { YouRow(Icons.Outlined.Whatshot, "Thịnh hành tại Việt Nam", null) { vm.push(Screen.VideoList(ListKind.TRENDING)); vm.loadList(ListKind.TRENDING) } }
            item { YouRow(Icons.Outlined.MusicNote, "Nhạc hàng đầu Việt Nam", null) { vm.push(Screen.VideoList(ListKind.TOP_MUSIC)); vm.loadList(ListKind.TOP_MUSIC) } }
            item { YtDivider() }
            item { YouRow(Icons.Outlined.Settings, "Cài đặt", null) { vm.push(Screen.Settings) } }
        }
    }
    MenuHost(vm, menu)
}

@Composable
private fun YouRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = yt.icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ---------------------------------------------------------------- Danh sách video

@Composable
fun VideoListScreen(vm: AppViewModel, kind: ListKind) {
    val yt = LocalYt.current
    val history by vm.store.history.collectAsState()
    val liked by vm.store.liked.collectAsState()
    val later by vm.store.favorites.collectAsState()
    val menu = rememberMenuState()
    LaunchedEffect(kind) { vm.loadList(kind) }
    Column(Modifier.fillMaxSize().background(yt.background)) {
        BackTopBar(kind.title, onBack = { vm.back() }) {
            if (kind == ListKind.HISTORY && history.isNotEmpty()) {
                Text(
                    "Xoá tất cả",
                    color = yt.text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clickable { vm.store.clearHistory() }
                        .padding(12.dp),
                )
            }
        }
        val videos: List<Pair<VideoItem, Float?>> = when (kind) {
            ListKind.HISTORY -> history.map { h -> h.video to (h.video.durationSec * 1000f).let { d -> if (d > 0) h.positionMs / d else null } }
            ListKind.LIKED -> liked.map { it to null }
            ListKind.WATCH_LATER -> later.map { it to null }
            else -> (vm.lists[kind] ?: FeedState()).entries.filterIsInstance<FeedEntry.Video>().map { it.video to null }
        }
        val remote = vm.lists[kind]
        when {
            remote?.loading == true -> LoadingBox()
            remote?.error != null -> ErrorState(remote.error) { vm.lists.remove(kind); vm.loadList(kind) }
            videos.isEmpty() -> EmptyState(Icons.AutoMirrored.Outlined.PlaylistPlay, "Danh sách này chưa có video", null)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (kind == ListKind.TRENDING || kind == ListKind.TOP_MUSIC) {
                    item {
                        Row(
                            Modifier
                                .padding(12.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(yt.subscribeBg)
                                .clickable { vm.openVideo(videos.first().first) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = yt.subscribeText)
                            Text("Phát tất cả", color = yt.subscribeText, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                items(videos.size, key = { videos[it].first.id + it }) { i ->
                    val (v, p) = videos[i]
                    VideoRow(v, onClick = { vm.openVideo(v) }, onMore = { menu.video = v }, progress = p)
                }
            }
        }
    }
    MenuHost(vm, menu)
}
