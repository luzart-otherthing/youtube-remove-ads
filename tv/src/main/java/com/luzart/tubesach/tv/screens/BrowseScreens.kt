package com.luzart.tubesach.tv.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.tv.LibraryKind
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.ui.Avatar
import com.luzart.tubesach.tv.ui.ChannelCard
import com.luzart.tubesach.tv.ui.FeedGrid
import com.luzart.tubesach.tv.ui.LoadingBox
import com.luzart.tubesach.tv.ui.MessageBox
import com.luzart.tubesach.tv.ui.PanelOption
import com.luzart.tubesach.tv.ui.SectionTitle
import com.luzart.tubesach.tv.ui.SidePanel
import com.luzart.tubesach.tv.ui.TvButton
import com.luzart.tubesach.tv.ui.TvChip
import com.luzart.tubesach.tv.ui.TvColors
import com.luzart.tubesach.tv.ui.fullRow
import com.luzart.tubesach.tv.ui.primaryFocus

// ---------------------------------------------------------------- Trang kênh

@Composable
fun ChannelScreen(vm: TvViewModel, channelId: String, onVideoMenu: (VideoItem) -> Unit) {
    val st = vm.channels[channelId]
    val subs by vm.store.subscriptions.collectAsState()
    when {
        st == null || st.loading -> LoadingBox()
        st.error != null || st.page == null -> MessageBox(
            st.error ?: "Không tải được kênh",
            action = "Thử lại",
            onAction = { vm.loadChannel(channelId, force = true) },
        )
        else -> {
            val page = st.page
            val subscribed = subs.any { it.id == channelId }
            val videos = st.videos
            FeedGrid(
                entries = vm.visible(videos.entries),
                onVideo = { vm.openVideo(it) },
                onShort = vm::openShort,
                onChannel = vm::openChannel,
                onVideoMenu = onVideoMenu,
                loadingMore = videos.loadingMore,
                onLoadMore = { vm.loadMoreChannel(channelId) },
                primaryOnFirstItem = false,
            ) {
                fullRow("header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(page.channel.avatar, 96.dp)
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(page.channel.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val info = listOfNotNull(page.handle, page.subscriberText, page.videoCountText).joinToString(" • ")
                            if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.bodyLarge, color = TvColors.textSecondary)
                        }
                        Spacer(Modifier.width(16.dp))
                        TvButton(
                            if (subscribed) "Đã đăng ký" else "Đăng ký",
                            onClick = { vm.store.toggleSubscription(page.channel) },
                            icon = if (subscribed) Icons.Filled.NotificationsActive else Icons.Filled.Subscriptions,
                            primary = !subscribed,
                            modifier = Modifier.primaryFocus(),
                        )
                    }
                }
                fullRow("tabs") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                        TvChip("Video", selected = st.tab == 0, onClick = { vm.loadChannelTab(channelId, 0) })
                        if (page.liveParams != null) {
                            TvChip("Trực tiếp", selected = st.tab == 1, onClick = { vm.loadChannelTab(channelId, 1) })
                        }
                    }
                }
                when {
                    videos.loading -> fullRow("loading") { LoadingBox(Modifier.height(200.dp)) }
                    videos.error != null -> fullRow("error") { MessageBox(videos.error) }
                    videos.entries.isEmpty() -> fullRow("empty") { MessageBox("Kênh này chưa có video") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Kênh đăng ký

@Composable
fun SubscriptionsScreen(vm: TvViewModel, onVideoMenu: (VideoItem) -> Unit) {
    val subs by vm.store.subscriptions.collectAsState()
    val feed = vm.subscriptionFeed
    LaunchedEffect(subs.size) { vm.loadSubscriptions() }
    if (subs.isEmpty()) {
        MessageBox(
            title = "Chưa đăng ký kênh nào",
            message = "Khi xem video, bấm ▲ để hiện nút điều khiển rồi chọn \"Đăng ký\". Kênh sẽ hiện ở đây.",
            icon = Icons.Filled.Subscriptions,
        )
        return
    }
    FeedGrid(
        entries = vm.visible(feed.entries),
        onVideo = { vm.openVideo(it) },
        onShort = vm::openShort,
        onChannel = vm::openChannel,
        onVideoMenu = onVideoMenu,
    ) {
        fullRow("channels") {
            Column {
                SectionTitle("Kênh đã đăng ký")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.focusGroup()) {
                    items(subs, key = { it.id }) { c ->
                        ChannelCard(c, onClick = { vm.openChannel(c.id) }, avatarSize = 72.dp, modifier = Modifier.width(120.dp))
                    }
                }
            }
        }
        fullRow("latest") { SectionTitle("Video mới nhất") }
        when {
            feed.loading && feed.entries.isEmpty() -> fullRow("loading") { LoadingBox(Modifier.height(200.dp)) }
            feed.error != null -> fullRow("error") {
                MessageBox(feed.error, action = "Thử lại", onAction = { vm.loadSubscriptions(force = true) })
            }
            feed.loaded && feed.entries.isEmpty() -> fullRow("empty") {
                MessageBox("Chưa có video mới", message = "Các kênh bạn đăng ký chưa đăng video nào gần đây.")
            }
        }
    }
}

// ---------------------------------------------------------------- Thư viện

@Composable
fun LibraryScreen(vm: TvViewModel, onVideoMenu: (VideoItem) -> Unit) {
    val history by vm.store.history.collectAsState()
    val later by vm.store.favorites.collectAsState()
    val liked by vm.store.liked.collectAsState()
    val kind = vm.libraryKind
    val videos: List<VideoItem> = when (kind) {
        LibraryKind.HISTORY -> history.map { it.video }
        LibraryKind.WATCH_LATER -> later
        LibraryKind.LIKED -> liked
    }
    val progress = remember(history) {
        history.associate { h ->
            val dur = h.video.durationSec * 1000
            h.video.id to if (dur > 0 && h.positionMs > 0) h.positionMs.toFloat() / dur else null
        }
    }
    FeedGrid(
        entries = videos.map { FeedEntry.Video(it) },
        onVideo = { vm.openVideo(it) },
        onShort = vm::openShort,
        onChannel = vm::openChannel,
        onVideoMenu = onVideoMenu,
        progressOf = { progress[it.id] },
    ) {
        fullRow("tabs") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                LibraryKind.entries.forEach { k ->
                    TvChip(
                        k.title,
                        selected = k == kind,
                        onClick = { vm.libraryKind = k },
                        modifier = Modifier.primaryFocus(videos.isEmpty() && k == kind),
                    )
                }
            }
        }
        if (videos.isEmpty()) {
            fullRow("empty") {
                MessageBox(
                    title = when (kind) {
                        LibraryKind.HISTORY -> "Chưa xem video nào"
                        LibraryKind.WATCH_LATER -> "Chưa lưu video nào để xem sau"
                        LibraryKind.LIKED -> "Chưa thích video nào"
                    },
                    message = "Giữ nút OK trên một video để lưu vào \"Xem sau\".",
                    icon = if (kind == LibraryKind.HISTORY) Icons.Filled.History else Icons.Filled.VideoLibrary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Menu video (giữ nút OK)

@Composable
fun VideoMenuPanel(vm: TvViewModel, video: VideoItem, onDismiss: () -> Unit) {
    val later by vm.store.favorites.collectAsState()
    val history by vm.store.history.collectAsState()
    val saved = later.any { it.id == video.id }
    val inHistory = history.any { it.video.id == video.id }
    val first = remember { FocusRequester() }
    SidePanel(title = video.title, subtitle = video.uploader, initialFocus = first) {
        item {
            PanelOption("Phát video", selected = false, showCheck = false, modifier = Modifier.focusRequester(first), onClick = {
                onDismiss()
                vm.openVideo(video)
            })
        }
        if (inHistory) {
            item {
                PanelOption("Phát từ đầu", selected = false, showCheck = false, onClick = {
                    onDismiss()
                    vm.openVideo(video, resume = false)
                })
            }
        }
        item {
            PanelOption(
                if (saved) "Xoá khỏi Xem sau" else "Lưu vào Xem sau",
                selected = false,
                showCheck = false,
                onClick = {
                    vm.store.toggleFavorite(video)
                    onDismiss()
                },
            )
        }
        video.channelId?.let { id ->
            item {
                PanelOption("Đi đến kênh ${video.uploader}".trim(), selected = false, showCheck = false, onClick = {
                    onDismiss()
                    vm.openChannel(id)
                })
            }
        }
        if (inHistory) {
            item {
                PanelOption("Xoá khỏi video đã xem", selected = false, showCheck = false, onClick = {
                    vm.store.removeFromHistory(video.id)
                    onDismiss()
                })
            }
        }
        item {
            PanelOption("Không quan tâm (ẩn video này)", selected = false, showCheck = false, onClick = {
                vm.store.hide(video.id)
                onDismiss()
            })
        }
    }
}
