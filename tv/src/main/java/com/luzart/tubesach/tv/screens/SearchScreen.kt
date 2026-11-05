package com.luzart.tubesach.tv.screens

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.ui.FeedGrid
import com.luzart.tubesach.tv.ui.LoadingBox
import com.luzart.tubesach.tv.ui.MessageBox
import com.luzart.tubesach.tv.ui.TvButton
import com.luzart.tubesach.tv.ui.TvColors
import com.luzart.tubesach.tv.ui.TvIconButton
import com.luzart.tubesach.tv.ui.fullRow
import com.luzart.tubesach.tv.ui.isSelectKey
import com.luzart.tubesach.tv.ui.primaryFocus
import com.luzart.tubesach.tv.ui.tryFocus

@Composable
fun SearchScreen(vm: TvViewModel, onVoiceSearch: () -> Unit, onVideoMenu: (VideoItem) -> Unit) {
    val recent by vm.store.recentSearches.collectAsState()
    val results = vm.results
    val query = vm.resultsQuery
    val typing = vm.searchText.isNotBlank() && vm.searchText != query
    val chipsTitle: String?
    val chips: List<String>
    when {
        typing && vm.suggestions.isNotEmpty() -> {
            chipsTitle = "Gợi ý"
            chips = vm.suggestions
        }
        query == null && recent.isNotEmpty() -> {
            chipsTitle = "Tìm kiếm gần đây"
            chips = recent.take(12)
        }
        else -> {
            chipsTitle = null
            chips = emptyList()
        }
    }

    val firstResult = remember { FocusRequester() }
    FeedGrid(
        entries = vm.visible(results.entries),
        firstItemFocus = firstResult,
        onVideo = { vm.openVideo(it) },
        onShort = vm::openShort,
        onChannel = vm::openChannel,
        onVideoMenu = onVideoMenu,
        loadingMore = results.loadingMore,
        onLoadMore = vm::loadMoreResults,
        primaryOnFirstItem = false,
    ) {
        fullRow("searchBar") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.focusGroup()) {
                SearchField(
                    text = vm.searchText,
                    onTextChange = vm::onSearchTextChange,
                    onSearch = { vm.search(vm.searchText) },
                    // ▼: tới kết quả đầu tiên (nếu không có hàng gợi ý ở giữa).
                    onDown = { chipsTitle == null && firstResult.tryFocus() },
                    modifier = Modifier.weight(1f).primaryFocus(),
                )
                Spacer(Modifier.width(16.dp))
                TvIconButton(Icons.Filled.Mic, "Tìm bằng giọng nói", onVoiceSearch)
                Spacer(Modifier.width(12.dp))
                TvIconButton(Icons.Filled.Search, "Tìm", { vm.search(vm.searchText) })
            }
        }
        if (chipsTitle != null) {
            fullRow("chips:$chipsTitle") {
                Column {
                    Text(chipsTitle, style = MaterialTheme.typography.titleMedium, color = TvColors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.focusGroup(),
                    ) {
                        items(chips, key = { it }) { c ->
                            TvButton(
                                c,
                                onClick = { vm.search(c) },
                                icon = if (chipsTitle == "Gợi ý") Icons.Filled.Search else Icons.Filled.History,
                            )
                        }
                    }
                }
            }
        }
        when {
            query == null -> fullRow("hint") {
                MessageBox(
                    title = "Tìm video, ca sĩ, chương trình…",
                    message = "Bấm OK vào ô tìm kiếm để hiện bàn phím, hoặc bấm nút micro để nói.",
                    icon = Icons.Filled.Search,
                )
            }
            results.loading -> fullRow("loading") { LoadingBox(Modifier.height(200.dp)) }
            results.error != null -> fullRow("error") {
                MessageBox(results.error, action = "Thử lại", onAction = { vm.search(query) })
            }
            results.loaded && results.entries.isEmpty() -> fullRow("empty") {
                MessageBox("Không tìm thấy kết quả nào cho \"$query\"")
            }
            else -> fullRow("resultsTitle") {
                Text("Kết quả cho \"$query\"", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/**
 * Ô nhập chữ cho TV: bấm OK để hiện bàn phím của TV; ◀ ▶ ▲ ▼ để rời ô (không bị kẹt).
 */
@Composable
private fun SearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = TextStyle(color = TvColors.text, fontSize = 20.sp),
        cursorBrush = SolidColor(TvColors.red),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = false),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onSearch()
        }),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                val code = e.nativeKeyEvent.keyCode
                when {
                    isSelectKey(code) -> {
                        if (e.type == KeyEventType.KeyUp) keyboard?.show()
                        true
                    }
                    e.type != KeyEventType.KeyDown -> false
                    code == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> onDown() || focusManager.moveFocus(FocusDirection.Down)
                    code == AndroidKeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(FocusDirection.Up)
                    code == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> focusManager.moveFocus(FocusDirection.Left)
                    code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> focusManager.moveFocus(FocusDirection.Right)
                    else -> false
                }
            }
            .testTag("searchField"),
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (focused) TvColors.surfaceHigh else TvColors.surface)
                    .border(2.dp, if (focused) TvColors.focus else TvColors.surfaceHigh, RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, null, tint = TvColors.textSecondary, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text("Tìm kiếm trên YouTube", color = TvColors.textSecondary, fontSize = 20.sp)
                    }
                    inner()
                }
            }
        },
    )
}
