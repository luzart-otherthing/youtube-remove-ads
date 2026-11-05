package com.luzart.tubesach.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.luzart.tubesach.data.Settings
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.SegmentCategory
import com.luzart.tubesach.tv.BuildConfig
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.player.CaptionLanguageOption
import com.luzart.tubesach.tv.player.QualityOption
import com.luzart.tubesach.tv.ui.PanelOption
import com.luzart.tubesach.tv.ui.SidePanel
import com.luzart.tubesach.tv.ui.TvColors
import com.luzart.tubesach.tv.ui.TvDimens
import com.luzart.tubesach.tv.ui.tvClickable
import com.luzart.tubesach.tv.ui.primaryFocus
import com.luzart.tubesach.tv.update.UpdateState

private enum class SettingsPanel { QUALITY, CAPTIONS, SPONSOR_CATEGORIES, CLEAR_HISTORY }

private fun onOff(b: Boolean) = if (b) "Bật" else "Tắt"

@Composable
fun SettingsScreen(vm: TvViewModel) {
    val s by vm.store.settings.collectAsState()
    var panel by rememberSaveable { mutableStateOf<SettingsPanel?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val update = vm.update

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 24.dp, end = TvDimens.safeHorizontal, top = TvDimens.safeVertical, bottom = TvDimens.safeVertical),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.widthIn(max = 760.dp).focusRestorer().testTag("settings"),
        ) {
            item { Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 12.dp)) }

            item { Group("Xem video") }
            item {
                SettingRow("Chất lượng video", QualityOption.current(s).label, "Tự động là tốt nhất cho mạng chậm/nhanh", Modifier.primaryFocus()) {
                    panel = SettingsPanel.QUALITY
                }
            }
            item {
                SettingRow("Tự động phát video tiếp theo", onOff(s.autoplay)) {
                    vm.store.updateSettings { it.copy(autoplay = !it.autoplay) }
                }
            }
            item {
                SettingRow("Phụ đề tự bật", CaptionLanguageOption.of(s.captionLanguage).label) { panel = SettingsPanel.CAPTIONS }
            }

            item { Group("Bỏ qua quảng cáo trong video (SponsorBlock)") }
            item {
                SettingRow(
                    "SponsorBlock",
                    onOff(s.sponsorBlock),
                    "Tự bỏ qua đoạn tài trợ mà người làm video tự chèn vào",
                ) { PlaybackController.setSponsorBlock(!s.sponsorBlock) }
            }
            item {
                SettingRow(
                    "Các đoạn sẽ bỏ qua",
                    SegmentCategory.entries.filter { it.key in s.sponsorCategories }.joinToString(", ") { it.label }.ifEmpty { "Không có" },
                ) { panel = SettingsPanel.SPONSOR_CATEGORIES }
            }
            item {
                SettingRow("Hiện số lượt không thích", onOff(s.returnDislike), "Return YouTube Dislike") {
                    vm.store.updateSettings { it.copy(returnDislike = !it.returnDislike) }
                }
            }

            item { Group("Trang chủ") }
            item {
                SettingRow("Ẩn Shorts", onOff(s.hideShorts)) { vm.store.updateSettings { it.copy(hideShorts = !it.hideShorts) } }
            }
            item {
                SettingRow(
                    "Đề xuất theo video đã xem",
                    onOff(s.personalize),
                    "Ẩn danh, không cần tài khoản Google",
                ) { vm.store.updateSettings { it.copy(personalize = !it.personalize) } }
            }
            item {
                SettingRow("Đặt lại đề xuất trang chủ", null, "Xoá mã khách ẩn danh, trang chủ bắt đầu lại từ đầu") {
                    vm.resetRecommendations()
                    message = "Đã đặt lại đề xuất."
                }
            }

            item { Group("Dữ liệu trên TV") }
            item { SettingRow("Xoá lịch sử xem", null) { panel = SettingsPanel.CLEAR_HISTORY } }
            item {
                SettingRow("Xoá các tìm kiếm gần đây", null) {
                    vm.store.clearRecentSearches()
                    message = "Đã xoá các tìm kiếm gần đây."
                }
            }

            item { Group("Ứng dụng") }
            item {
                val value = when {
                    vm.checkingUpdate -> "Đang kiểm tra…"
                    update != null -> when (val st = vm.updateState) {
                        is UpdateState.Downloading -> "Đang tải ${st.percent}%"
                        is UpdateState.Ready -> "Bấm để cài bản ${update.version}"
                        else -> "Có bản ${update.version} – bấm để tải"
                    }
                    else -> null
                }
                SettingRow("Kiểm tra cập nhật", value, "Phiên bản đang dùng: ${BuildConfig.VERSION_NAME}") {
                    when {
                        vm.updateState is UpdateState.Ready -> vm.installUpdate()
                        update != null -> vm.downloadUpdate()
                        else -> vm.checkUpdate(manual = true)
                    }
                }
            }
            (vm.updateMessage ?: message ?: (vm.updateState as? UpdateState.Failed)?.message)?.let { msg ->
                item {
                    Text(msg, style = MaterialTheme.typography.bodyLarge, color = TvColors.accent, modifier = Modifier.padding(12.dp))
                }
            }
            item {
                Text(
                    "Tube Sạch TV ${BuildConfig.VERSION_NAME} – xem YouTube không quảng cáo, không cần đăng nhập.\n" +
                        "Không liên kết với YouTube hay Google.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColors.textSecondary,
                    modifier = Modifier.padding(top = 16.dp, start = 12.dp),
                )
            }
        }

        when (panel) {
            SettingsPanel.QUALITY -> QualityPanel(s, onDone = { panel = null })
            SettingsPanel.CAPTIONS -> {
                val first = remember { FocusRequester() }
                SidePanel("Phụ đề tự bật", initialFocus = first, subtitle = "Áp dụng cho các video mở sau") {
                    CaptionLanguageOption.entries.forEach { opt ->
                        item {
                            PanelOption(
                                opt.label,
                                selected = s.captionLanguage == opt.code,
                                modifier = if (s.captionLanguage == opt.code) Modifier.focusRequester(first) else Modifier,
                                onClick = {
                                    vm.store.updateSettings { it.copy(captionLanguage = opt.code) }
                                    panel = null
                                },
                            )
                        }
                    }
                }
            }
            SettingsPanel.SPONSOR_CATEGORIES -> SponsorCategoriesPanel(s, vm)
            SettingsPanel.CLEAR_HISTORY -> {
                val cancel = remember { FocusRequester() }
                SidePanel("Xoá toàn bộ lịch sử xem?", initialFocus = cancel, subtitle = "Không thể hoàn tác.") {
                    item { PanelOption("Huỷ", selected = false, showCheck = false, modifier = Modifier.focusRequester(cancel), onClick = { panel = null }) }
                    item {
                        PanelOption("Xoá lịch sử", selected = false, showCheck = false, onClick = {
                            vm.store.clearHistory()
                            message = "Đã xoá lịch sử xem."
                            panel = null
                        })
                    }
                }
            }
            null -> Unit
        }
    }
    // Bảng chọn đang mở: Quay lại → đóng bảng (ưu tiên hơn nút Quay lại của khung chính).
    BackHandler(enabled = panel != null) { panel = null }
}

@Composable
private fun QualityPanel(s: Settings, onDone: () -> Unit) {
    val first = remember { FocusRequester() }
    val current = QualityOption.current(s)
    SidePanel("Chất lượng video", initialFocus = first, subtitle = "Tối đa 1080p") {
        QualityOption.all.forEach { opt ->
            item {
                PanelOption(
                    opt.label,
                    selected = opt == current,
                    subtitle = opt.description,
                    modifier = if (opt == current) Modifier.focusRequester(first) else Modifier,
                    onClick = {
                        PlaybackController.setQuality(opt.mode, opt.height)
                        onDone()
                    },
                )
            }
        }
    }
}

@Composable
private fun SponsorCategoriesPanel(s: Settings, vm: TvViewModel) {
    val first = remember { FocusRequester() }
    SidePanel("Các đoạn sẽ bỏ qua", initialFocus = first, subtitle = "Bấm OK để bật / tắt từng loại") {
        SegmentCategory.entries.forEachIndexed { i, c ->
            item {
                PanelOption(
                    c.label,
                    selected = c.key in s.sponsorCategories,
                    subtitle = c.description,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = {
                        vm.store.updateSettings {
                            it.copy(sponsorCategories = if (c.key in it.sponsorCategories) it.sponsorCategories - c.key else it.sponsorCategories + c.key)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = TvColors.textSecondary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, value: String?, description: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) TvColors.onFocus else TvColors.text
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) TvColors.focus else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("setting:$title"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, color = if (focused) Color(0xFF444444) else TvColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (value != null) {
            Spacer(Modifier.padding(start = 16.dp))
            Text(value, color = if (focused) TvColors.onFocus else TvColors.accent, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}
