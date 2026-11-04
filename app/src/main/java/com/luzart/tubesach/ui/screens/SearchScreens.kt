package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.ResultsState
import com.luzart.tubesach.ui.components.Divider
import com.luzart.tubesach.ui.components.ErrorBox
import com.luzart.tubesach.ui.components.LoadingBox
import com.luzart.tubesach.ui.components.TopBar
import com.luzart.tubesach.ui.components.VideoCard

/** Màn hình gõ chữ để tìm, có gợi ý và lịch sử tìm kiếm. */
@Composable
fun SearchInputScreen(vm: AppViewModel, onVoiceSearch: () -> Unit) {
    val recent by vm.store.recentSearches.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopBar("Tìm video", onBack = { vm.back() })
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = vm.searchText,
                onValueChange = vm::onSearchTextChange,
                placeholder = { Text("Gõ tên video…", style = MaterialTheme.typography.titleMedium) },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(vm.searchText) }),
                trailingIcon = {
                    if (vm.searchText.isNotEmpty()) {
                        IconButton(onClick = { vm.onSearchTextChange("") }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.Clear, contentDescription = "Xoá chữ", modifier = Modifier.size(30.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 68.dp)
                    .focusRequester(focus),
            )
            Spacer(Modifier.width(10.dp))
            IconButton(onClick = onVoiceSearch, modifier = Modifier.size(68.dp)) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Tìm bằng giọng nói",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Button(
            onClick = { vm.search(vm.searchText) },
            enabled = vm.searchText.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(min = 64.dp),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tìm", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (vm.searchText.isBlank()) {
                if (recent.isNotEmpty()) {
                    item {
                        Text(
                            "Đã tìm gần đây",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(recent) { q -> SuggestionRow(Icons.Filled.History, q) { vm.search(q) } }
            } else {
                items(vm.suggestions) { s -> SuggestionRow(Icons.Filled.Search, s) { vm.search(s) } }
            }
        }
    }
}

@Composable
private fun SuggestionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
    Divider()
}

/** Danh sách kết quả tìm kiếm, tự tải thêm khi kéo xuống cuối. */
@Composable
fun ResultsScreen(vm: AppViewModel, query: String, bottomPadding: PaddingValues) {
    val state = vm.results[query] ?: ResultsState(loading = false, error = "Hãy tìm lại.")
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(nearEnd, state.items.size) {
        if (nearEnd && state.items.isNotEmpty()) vm.loadMore(query)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(query, onBack = { vm.back() })
        when {
            state.loading -> LoadingBox("Đang tìm video…")
            state.error != null -> ErrorBox(state.error, onRetry = { vm.runSearch(query) })
            state.items.isEmpty() -> ErrorBox("Không tìm thấy video nào.", onRetry = { vm.runSearch(query) })
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = bottomPadding) {
                itemsIndexed(state.items, key = { i, v -> "$i-${v.id}" }) { _, video ->
                    VideoCard(video, onClick = { vm.openVideo(video) })
                    Divider()
                }
                if (state.loadingMore) item { LoadingBox("Đang tải thêm…") }
            }
        }
    }
}

/** Lịch sử xem: tiếp tục xem từ chỗ đã dừng. */
@Composable
fun HistoryScreen(vm: AppViewModel, bottomPadding: PaddingValues) {
    val history by vm.store.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Đã xem", onBack = { vm.back() }) {
            if (history.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Xoá hết", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        if (history.isEmpty()) {
            EmptyText("Bạn chưa xem video nào.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding) {
                items(history, key = { it.video.id }) { entry ->
                    val dur = entry.video.durationSec * 1000f
                    VideoCard(
                        entry.video,
                        onClick = { vm.openVideo(entry.video) },
                        progress = if (dur > 0) entry.positionMs / dur else null,
                    )
                    Divider()
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Xoá lịch sử xem?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Danh sách video đã xem sẽ bị xoá.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = { vm.store.clearHistory(); confirmClear = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Xoá", style = MaterialTheme.typography.titleMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("Không", style = MaterialTheme.typography.titleMedium)
                }
            },
        )
    }
}

@Composable
fun FavoritesScreen(vm: AppViewModel, bottomPadding: PaddingValues) {
    val favorites by vm.store.favorites.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar("Yêu thích", onBack = { vm.back() })
        if (favorites.isEmpty()) {
            EmptyText("Chưa có video yêu thích.\nKhi xem video, bấm nút ❤ \"Yêu thích\" để lưu lại đây.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding) {
                items(favorites, key = { it.id }) { video ->
                    VideoCard(video, onClick = { vm.openVideo(video) })
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}
