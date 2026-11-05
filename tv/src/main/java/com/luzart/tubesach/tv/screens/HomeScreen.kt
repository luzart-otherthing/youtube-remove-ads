package com.luzart.tubesach.tv.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.data.FeedEntry
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.ui.FeedGrid
import com.luzart.tubesach.tv.ui.LoadingBox
import com.luzart.tubesach.tv.ui.MessageBox
import com.luzart.tubesach.tv.ui.TvButton
import com.luzart.tubesach.tv.ui.TvChip
import com.luzart.tubesach.tv.ui.TvColors
import com.luzart.tubesach.tv.ui.fullRow
import com.luzart.tubesach.tv.update.UpdateState

@Composable
fun HomeScreen(vm: TvViewModel, onVideoMenu: (VideoItem) -> Unit) {
    val home = vm.home
    // Đọc để giao diện cập nhật khi đổi cài đặt "Ẩn Shorts" / bấm "Không quan tâm".
    vm.store.hidden.collectAsState().value
    vm.store.settings.collectAsState().value
    val entries = vm.visible(home.entries)

    when {
        // Đổi chip: giữ nguyên hàng chip (ô sáng không bị mất), chỉ phần video hiện vòng tải.
        home.loading && entries.isEmpty() && home.chips.isEmpty() -> LoadingBox()
        home.error != null && entries.isEmpty() -> MessageBox(
            title = home.error,
            message = "Kiểm tra Wi‑Fi của TV rồi thử lại.",
            action = "Thử lại",
            onAction = { vm.loadHome(force = true) },
        )
        else -> {
            // Lần đầu dùng (chưa xem gì): YouTube để trống trang chủ → hiện tạm "Thịnh hành tại Việt Nam".
            val showTrending = entries.isEmpty() && home.loaded && !home.loading && home.selectedChip == 0
            val shown = if (showTrending) vm.homeTrending.map { FeedEntry.Video(it) } else entries
            FeedGrid(
                entries = shown,
                onVideo = { vm.openVideo(it) },
                onShort = vm::openShort,
                onChannel = vm::openChannel,
                onVideoMenu = onVideoMenu,
                loadingMore = home.loadingMore,
                onLoadMore = if (showTrending) null else vm::loadMoreHome,
            ) {
                fullRow("update") { UpdateBanner(vm) }
                if (home.chips.size > 1 && !showTrending) {
                    fullRow("chips") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier.focusGroup(),
                        ) {
                            itemsIndexed(home.chips) { i, chip ->
                                TvChip(chip.title, selected = i == home.selectedChip, onClick = { vm.selectChip(i) })
                            }
                        }
                    }
                }
                if (showTrending) {
                    fullRow("welcome") {
                        Column {
                            Text("Thịnh hành tại Việt Nam", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Xem vài video hoặc tìm kiếm để trang chủ có đề xuất dành riêng cho bạn.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = TvColors.textSecondary,
                            )
                        }
                    }
                }
                if (home.loading) fullRow("loading") { LoadingBox(Modifier.height(120.dp)) }
            }
        }
    }
}

/** Hiện khi có bản mới (chỉ khi kho GitHub để công khai). Tải và cài ngay trên TV. */
@Composable
fun UpdateBanner(vm: TvViewModel) {
    val info = vm.update ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvColors.brand)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Có phiên bản mới ${info.version}", style = MaterialTheme.typography.titleMedium)
            val sub = when (val st = vm.updateState) {
                is UpdateState.Downloading -> "Đang tải… ${st.percent}%"
                is UpdateState.Ready -> "Đã tải xong. Bấm Cài đặt để cập nhật."
                is UpdateState.Failed -> st.message
                UpdateState.Idle -> "Sửa lỗi khi YouTube thay đổi. Nên cập nhật."
            }
            Text(sub, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(16.dp))
        when (vm.updateState) {
            is UpdateState.Ready -> TvButton("Cài đặt", vm::installUpdate, icon = Icons.Filled.SystemUpdate)
            is UpdateState.Downloading -> Unit
            else -> TvButton("Cập nhật", vm::downloadUpdate, icon = Icons.Filled.SystemUpdate)
        }
    }
}
