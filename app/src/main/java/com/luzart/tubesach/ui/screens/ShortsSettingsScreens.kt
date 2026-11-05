package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.luzart.tubesach.BuildConfig
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.data.ThemeMode
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.sponsor.SegmentCategory
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.components.Avatar
import com.luzart.tubesach.ui.components.EmptyState
import com.luzart.tubesach.ui.components.ShortsOutlined
import com.luzart.tubesach.ui.components.YtDivider
import com.luzart.tubesach.ui.components.shareVideo
import com.luzart.tubesach.ui.player.PlayerSurface
import com.luzart.tubesach.ui.player.rememberPlayerUiState
import com.luzart.tubesach.ui.theme.LocalYt
import com.luzart.tubesach.ui.theme.YtRed
import kotlinx.coroutines.flow.distinctUntilChanged

// ---------------------------------------------------------------- Shorts

@Composable
fun ShortsScreen(vm: AppViewModel) {
    val ids = vm.shorts
    if (ids.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (vm.shortsLoading) CircularProgressIndicator(color = Color.White)
            else EmptyState(ShortsOutlined, "Chưa có Shorts", "Kiểm tra kết nối mạng rồi thử lại.")
        }
        return
    }
    val pager = rememberPagerState(initialPage = vm.shortsStart.coerceIn(0, ids.lastIndex)) { ids.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            ids.getOrNull(page)?.let { PlaybackController.playShort(it) }
            vm.shortsStart = page
            if (page >= ids.size - 3) vm.loadMoreShorts()
        }
    }
    VerticalPager(state = pager, modifier = Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 0) { page ->
        ShortPage(vm, ids[page], current = page == pager.currentPage)
    }
}

@Composable
private fun ShortPage(vm: AppViewModel, id: String, current: Boolean) {
    val context = LocalContext.current
    val now by PlaybackController.nowPlaying.collectAsState()
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val liked by vm.store.liked.collectAsState()
    val disliked by vm.store.disliked.collectAsState()
    val subs by vm.store.subscriptions.collectAsState()
    val info = now?.takeIf { it.video.id == id }
    val ui = rememberPlayerUiState()
    val video = info?.video ?: VideoItem.ofId(id)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = current) { PlaybackController.togglePlayPause() },
    ) {
        AsyncImage("https://i.ytimg.com/vi/$id/oardefault.jpg", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (current) PlayerSurface(Modifier.fillMaxSize(), zoom = true, isPlaying = isPlaying)
        if (current && !isPlaying && info != null && ui.duration > 0) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Phát",
                tint = Color(0xCCFFFFFF),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp),
            )
        }
        Text(
            "Shorts",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp),
        )
        // Cột nút bên phải
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ShortAction(if (liked.any { it.id == id }) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, info?.watch?.likeText ?: "Thích") { vm.store.toggleLike(video) }
            ShortAction(if (id in disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, "Không thích") { vm.store.toggleDislike(id) }
            ShortAction(Icons.AutoMirrored.Outlined.Comment, info?.watch?.commentCountText ?: "Bình luận") { }
            ShortAction(Icons.Outlined.Share, "Chia sẻ") { shareVideo(context, video) }
        }
        // Thông tin kênh + tiêu đề
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, end = 80.dp, bottom = 24.dp),
        ) {
            val channel = info?.watch?.channel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(channel?.avatar, 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    channel?.name?.let { "@$it" } ?: video.uploader,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (channel != null) {
                    Spacer(Modifier.width(8.dp))
                    val subscribed = subs.any { it.id == channel.id }
                    Box(
                        Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (subscribed) Color(0x4DFFFFFF) else Color.White)
                            .clickable { vm.store.toggleSubscription(channel) }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (subscribed) "Đã đăng ký" else "Đăng ký", color = if (subscribed) Color.White else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(info?.watch?.title ?: video.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (current && ui.duration > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0x4DFFFFFF)),
            ) {
                Box(Modifier.fillMaxWidth((ui.position.toFloat() / ui.duration).coerceIn(0f, 1f)).height(2.dp).background(YtRed))
            }
        }
    }
}

@Composable
private fun ShortAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Cài đặt

@Composable
fun SettingsScreen(vm: AppViewModel, pipSupported: Boolean) {
    val yt = LocalYt.current
    val s by vm.store.settings.collectAsState()
    var dialog by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(yt.background)) {
        BackTopBar("Cài đặt", onBack = { vm.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Chung")
            ValueRow("Giao diện", s.theme.label) { dialog = "theme" }
            ValueRow("Cỡ chữ", when (s.textScale) { 1.15f -> "Lớn"; 1.3f -> "Rất lớn"; else -> "Mặc định" }) { dialog = "text" }
            ToggleRow("Tự động phát", "Phát video tiếp theo khi video kết thúc", s.autoplay) { v -> vm.store.updateSettings { it.copy(autoplay = v) } }
            if (pipSupported) {
                ToggleRow("Hình trong hình", "Tự thu nhỏ video thành cửa sổ nổi khi rời ứng dụng", s.autoPip) { v -> vm.store.updateSettings { it.copy(autoPip = v) } }
            }
            ValueRow("Chất lượng video ưu tiên", s.quality.label) { dialog = "quality" }
            ToggleRow("Ẩn Shorts", "Ẩn kệ Shorts ở trang chủ và kết quả tìm kiếm", s.hideShorts) { v -> vm.store.updateSettings { it.copy(hideShorts = v) } }
            YtDivider()

            Section("SponsorBlock")
            ToggleRow(
                "Bỏ qua đoạn tài trợ",
                "Tự bỏ qua các đoạn quảng cáo do người làm video chèn vào (dữ liệu cộng đồng sponsor.ajay.app). Có thể bật/tắt nhanh bằng nút \$ trên trình phát.",
                s.sponsorBlock,
            ) { PlaybackController.setSponsorBlock(it) }
            if (s.sponsorBlock) {
                SegmentCategory.entries.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.store.updateSettings { st ->
                                    st.copy(sponsorCategories = if (c.key in st.sponsorCategories) st.sponsorCategories - c.key else st.sponsorCategories + c.key)
                                }
                            }
                            .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.label, color = yt.text, style = MaterialTheme.typography.bodyLarge)
                            Text(c.description, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = c.key in s.sponsorCategories,
                            onCheckedChange = { on ->
                                vm.store.updateSettings { st -> st.copy(sponsorCategories = if (on) st.sponsorCategories + c.key else st.sponsorCategories - c.key) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3EA6FF)),
                        )
                    }
                }
            }
            ToggleRow("Hiện số lượt không thích", "Dùng Return YouTube Dislike để hiện lại số lượt không thích", s.returnDislike) { v ->
                vm.store.updateSettings { it.copy(returnDislike = v) }
            }
            YtDivider()

            Section("Quyền riêng tư")
            ToggleRow(
                "Đề xuất theo video đã xem",
                "Cho YouTube biết bạn đã xem gì (ẩn danh, không cần tài khoản) để trang chủ có đề xuất tiếng Việt hợp với bạn.",
                s.personalize,
            ) { v -> vm.store.updateSettings { it.copy(personalize = v) } }
            ActionRow("Xoá dữ liệu đề xuất", "Tạo mã khách ẩn danh mới, trang chủ sẽ trống lại như lúc đầu") { dialog = "reset" }
            ActionRow("Xoá nhật ký xem", null) { dialog = "history" }
            ActionRow("Xoá nhật ký tìm kiếm", null) { vm.store.clearRecentSearches() }
            YtDivider()

            AndroidAutoSection()
            if (com.luzart.tubesach.update.PlayInstaller.supported) {
                ToggleRow(
                    "Cho hành khách xem hình khi xe đang chạy",
                    if (s.carVideoWhileDriving) "Đang bật: màn hình ô tô luôn hiện hình. Người lái đừng nhìn video."
                    else "Đang tắt: xe chạy thì màn hình ô tô chỉ phát tiếng, xe dừng thì hiện hình.",
                    s.carVideoWhileDriving,
                ) { on ->
                    if (on) dialog = "carDriving" else vm.store.updateSettings { it.copy(carVideoWhileDriving = false) }
                }
            }
            YtDivider()

            Section("Giới thiệu")
            ActionRow("Kiểm tra bản cập nhật", vm.updateMessage ?: vm.update?.let { "Có bản mới ${it.version}" }) { vm.checkUpdate(manual = true) }
            Text(
                "Tube Sạch ${BuildConfig.VERSION_NAME}\n" +
                    "Không quảng cáo · Không cần đăng nhập · Phát nền · Hình trong hình · SponsorBlock.\n" +
                    "Ứng dụng không liên kết với YouTube hay Google.",
                color = yt.textSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog("Giao diện", ThemeMode.entries.map { it.label }, ThemeMode.entries.indexOf(s.theme), { dialog = null }) { i ->
            vm.store.updateSettings { it.copy(theme = ThemeMode.entries[i]) }
        }
        "text" -> ChoiceDialog("Cỡ chữ", listOf("Mặc định", "Lớn", "Rất lớn"), listOf(1f, 1.15f, 1.3f).indexOf(s.textScale).coerceAtLeast(0), { dialog = null }) { i ->
            vm.store.updateSettings { it.copy(textScale = listOf(1f, 1.15f, 1.3f)[i]) }
        }
        "quality" -> {
            val modes = QualityMode.entries.filter { it != QualityMode.ADVANCED }
            ChoiceDialog("Chất lượng video ưu tiên", modes.map { it.label }, modes.indexOf(s.quality).coerceAtLeast(0), { dialog = null }) { i ->
                PlaybackController.setQuality(modes[i])
            }
        }
        "reset" -> ConfirmDialog("Xoá dữ liệu đề xuất?", "Trang chủ sẽ trống cho đến khi bạn xem thêm video.", { dialog = null }) { vm.resetRecommendations() }
        "carDriving" -> ConfirmDialog(
            "Hiện hình khi xe đang chạy?",
            "Màn hình ô tô sẽ hiện video cả khi xe đang chạy để hành khách xem. Màn hình nằm trong tầm mắt người lái: người lái tuyệt đối không nhìn video khi đang lái. Có thể tắt lại bất cứ lúc nào ở đây.",
            { dialog = null },
            confirmLabel = "BẬT",
        ) { vm.store.updateSettings { it.copy(carVideoWhileDriving = true) } }
        "history" -> ConfirmDialog("Xoá nhật ký xem?", "Danh sách video đã xem trên máy này sẽ bị xoá.", { dialog = null }) { vm.store.clearHistory() }
    }
}

/**
 * Mục "Ô tô (Android Auto)": cho biết Android Auto đã nhận Tube Sạch chưa, và nút cài lại một lần
 * để xem được video trên màn hình xe (giống cách AAAD cài CarStream).
 */
@Composable
private fun AndroidAutoSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val trusted = remember(refresh) { com.luzart.tubesach.update.PlayInstaller.isTrusted(context) }
    var error by remember { mutableStateOf<String?>(null) }
    Section("Ô tô (Android Auto)")
    when {
        trusted -> ActionRow(
            "Đã sẵn sàng cho Android Auto",
            "Cắm điện thoại vào xe, mở danh sách ứng dụng trên màn hình xe rồi chọn “Tube Sạch Video” (xem hình khi đỗ xe) hoặc “Tube Sạch” (nghe).",
        ) { }
        com.luzart.tubesach.update.PlayInstaller.supported -> ActionRow(
            "Cài để hiện trên Android Auto",
            error ?: "Bấm vào đây rồi bấm “Cài đặt”. Tube Sạch được cài lại (giữ nguyên lịch sử, kênh đăng ký) để màn hình ô tô hiện được video. Chỉ cần làm một lần. Nếu điện thoại hỏi, hãy cho phép Tube Sạch cài ứng dụng.",
        ) {
            error = com.luzart.tubesach.update.PlayInstaller.reinstallSelf(context).exceptionOrNull()?.let { "Không mở được trình cài đặt. Thử lại sau." }
        }
        android.os.Build.VERSION.SDK_INT >= 35 -> ActionRow(
            "Dùng trên Android Auto",
            "Bật “Nguồn không xác định” trong cài đặt dành cho nhà phát triển của Android Auto. Trên xe: “Tube Sạch Video” để xem khi đỗ xe, “Tube Sạch” để nghe khi lái.",
        ) { }
        else -> ActionRow(
            "Dùng trên Android Auto",
            "Bật “Nguồn không xác định” trong cài đặt dành cho nhà phát triển của Android Auto. Trên Android 14, xe chỉ cho mở “Tube Sạch” để chọn video và nghe tiếng.",
        ) { }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = LocalYt.current.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val yt = LocalYt.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val yt = LocalYt.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3EA6FF)))
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(i); onDismiss() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("HUỶ") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, onDismiss: () -> Unit, confirmLabel: String = "XOÁ", onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("HUỶ") } },
    )
}
