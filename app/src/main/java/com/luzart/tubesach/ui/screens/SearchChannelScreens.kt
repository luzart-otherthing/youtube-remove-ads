package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.YouTubeRepository
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.ChannelState
import com.luzart.tubesach.ui.FeedState
import com.luzart.tubesach.ui.components.Avatar
import com.luzart.tubesach.ui.components.EmptyState
import com.luzart.tubesach.ui.components.ErrorState
import com.luzart.tubesach.ui.components.LoadingBox
import com.luzart.tubesach.ui.components.SubscribeButton
import com.luzart.tubesach.ui.components.VideoRow
import com.luzart.tubesach.ui.components.YtChip
import com.luzart.tubesach.ui.components.rememberMenuState
import com.luzart.tubesach.ui.theme.LocalYt

// ---------------------------------------------------------------- Ô tìm kiếm

@Composable
private fun SearchField(
    text: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focus: FocusRequester? = null,
    onClick: (() -> Unit)? = null,
) {
    val yt = LocalYt.current
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(yt.chip)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("Tìm kiếm", color = yt.textSecondary, fontSize = 16.sp)
            if (readOnly) {
                Text(text, color = yt.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = yt.text, fontSize = 16.sp),
                    cursorBrush = SolidColor(yt.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
                )
            }
        }
        if (text.isNotEmpty() && !readOnly) {
            Icon(
                Icons.Filled.Clear,
                contentDescription = "Xoá",
                tint = yt.icon,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun MicButton(onVoice: () -> Unit) {
    val yt = LocalYt.current
    Box(
        Modifier
            .padding(start = 8.dp, end = 8.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(yt.chip)
            .clickable(onClick = onVoice),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Tìm bằng giọng nói", tint = yt.icon)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchInputScreen(vm: AppViewModel, onVoice: () -> Unit) {
    val yt = LocalYt.current
    val recent by vm.store.recentSearches.collectAsState()
    val focus = remember { FocusRequester() }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(yt.background)
            .imePadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = yt.icon) }
            SearchField(vm.searchText, vm::onSearchTextChange, { vm.search(vm.searchText) }, Modifier.weight(1f), focus = focus)
            MicButton(onVoice)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            val rows: List<Pair<String, Boolean>> = if (vm.searchText.isBlank()) {
                recent.map { it to true }
            } else {
                recent.filter { it.contains(vm.searchText, true) }.take(3).map { it to true } +
                    vm.suggestions.filterNot { s -> recent.any { it.equals(s, true) } }.map { it to false }
            }
            items(rows, key = { (if (it.second) "r:" else "s:") + it.first }) { (text, isRecent) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { vm.search(text) },
                            onLongClick = { if (isRecent) removeTarget = text },
                        )
                        .heightIn(min = 48.dp)
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (isRecent) Icons.Outlined.History else Icons.Outlined.Search, contentDescription = null, tint = yt.icon, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(20.dp))
                    Text(text, color = yt.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { vm.onSearchTextChange("$text ") }) {
                        Icon(Icons.Outlined.NorthWest, contentDescription = "Điền", tint = yt.icon, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    removeTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(t) },
            text = { Text("Xoá khỏi nhật ký tìm kiếm?") },
            confirmButton = { TextButton(onClick = { vm.store.removeRecentSearch(t); removeTarget = null }) { Text("XOÁ") } },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("HUỶ") } },
        )
    }
}

@Composable
fun ResultsScreen(vm: AppViewModel, query: String, onVoice: () -> Unit) {
    val yt = LocalYt.current
    val state = vm.results[query] ?: FeedState(loading = true)
    val filter = vm.resultFilter[query] ?: YouTubeRepository.SearchFilter.ALL
    val menu = rememberMenuState()
    val list = rememberLazyListState()
    InfiniteScroll(list) { vm.loadMoreResults(query) }
    LaunchedEffect(query) { if (vm.results[query] == null) vm.runSearch(query, filter) }
    vm.store.hidden.collectAsState().value

    Column(Modifier.fillMaxSize().background(yt.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = yt.icon) }
            SearchField(query, {}, {}, Modifier.weight(1f), readOnly = true, onClick = { vm.back(); vm.openSearchInput(query) })
            MicButton(onVoice)
        }
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item(key = "filters") {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    items(YouTubeRepository.SearchFilter.entries.toList()) { f ->
                        YtChip(f.label, f == filter) { vm.runSearch(query, f) }
                    }
                }
            }
            when {
                state.loading -> item(key = "loading") { LoadingBox() }
                state.error != null -> item(key = "error") { ErrorState(state.error) { vm.runSearch(query, filter) } }
                state.entries.isEmpty() -> item(key = "empty") { EmptyState(Icons.Outlined.Search, "Không tìm thấy kết quả nào", "Hãy thử từ khoá khác") }
                else -> feedItems(vm, vm.visible(state.entries), menu, "res")
            }
            if (state.loadingMore) item(key = "more") { LoadingBox() }
        }
    }
    MenuHost(vm, menu)
}

// ---------------------------------------------------------------- Kênh

@Composable
fun ChannelScreen(vm: AppViewModel, id: String) {
    val yt = LocalYt.current
    LaunchedEffect(id) { vm.loadChannel(id) }
    val st = vm.channels[id] ?: ChannelState()
    val subs by vm.store.subscriptions.collectAsState()
    val menu = rememberMenuState()
    val list = rememberLazyListState()
    InfiniteScroll(list) { vm.loadMoreChannel(id) }

    Column(Modifier.fillMaxSize().background(yt.background)) {
        BackTopBar(st.page?.channel?.name.orEmpty(), onBack = { vm.back() }) {
            IconButton(onClick = { vm.openSearchInput() }) { Icon(Icons.Outlined.Search, contentDescription = "Tìm kiếm", tint = yt.icon) }
        }
        when {
            st.loading -> LoadingBox()
            st.error != null || st.page == null -> ErrorState(st.error ?: "Không tải được kênh") { vm.channels.remove(id); vm.loadChannel(id) }
            else -> {
                val page = st.page
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    item(key = "header") {
                        Column {
                            if (page.banner != null) {
                                AsyncImage(
                                    page.banner,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(6.2f)
                                        .clip(RoundedCornerShape(12.dp)),
                                )
                            }
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(page.channel.avatar, 72.dp)
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(page.channel.name, color = yt.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                    Text(
                                        listOfNotNull(page.handle, page.subscriberText, page.videoCountText).joinToString(" • "),
                                        color = yt.textSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            page.description?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    color = yt.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            SubscribeButton(
                                subs.any { it.id == id },
                                onClick = { vm.toggleSubscription(page.channel) },
                                wide = true,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                            )
                            val tabs = listOfNotNull("Video", if (page.liveParams != null) "Trực tiếp" else null)
                            TabRow(
                                selectedTabIndex = st.tab.coerceAtMost(tabs.lastIndex),
                                containerColor = yt.background,
                                contentColor = yt.text,
                                indicator = { pos ->
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(pos[st.tab.coerceAtMost(tabs.lastIndex)]),
                                        color = yt.text,
                                    )
                                },
                            ) {
                                tabs.forEachIndexed { i, t ->
                                    Tab(selected = st.tab == i, onClick = { vm.loadChannelTab(id, i) }, text = { Text(t, fontWeight = FontWeight.Medium) })
                                }
                            }
                        }
                    }
                    val videos = st.videos
                    when {
                        videos.loading -> item(key = "vl") { LoadingBox() }
                        videos.entries.isEmpty() -> item(key = "ve") { EmptyState(Icons.Outlined.VideoLibrary, "Kênh này chưa có video", null) }
                        else -> {
                            val vids = videos.entries.filterIsInstance<FeedEntry.Video>().map { it.video }
                            items(vids.size, key = { "cv$it" + vids[it].id }) { i ->
                                VideoRow(vids[i], onClick = { vm.openVideo(vids[i]) }, onMore = { menu.video = vids[i] }, showChannel = false)
                            }
                        }
                    }
                    if (videos.loadingMore) item(key = "vm") { LoadingBox() }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
    MenuHost(vm, menu)
}
