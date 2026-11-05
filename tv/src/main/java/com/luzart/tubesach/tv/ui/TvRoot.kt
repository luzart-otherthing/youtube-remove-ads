package com.luzart.tubesach.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.tv.BackResult
import com.luzart.tubesach.tv.Section
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.player.PlayerScreen
import com.luzart.tubesach.tv.screens.ChannelScreen
import com.luzart.tubesach.tv.screens.HomeScreen
import com.luzart.tubesach.tv.screens.LibraryScreen
import com.luzart.tubesach.tv.screens.SearchScreen
import com.luzart.tubesach.tv.screens.SettingsScreen
import com.luzart.tubesach.tv.screens.SubscriptionsScreen
import com.luzart.tubesach.tv.screens.VideoMenuPanel
import kotlinx.coroutines.delay

/** Gọi FocusRequester an toàn (bỏ qua nếu ô đó chưa hiện trên màn hình). */
fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus() }.getOrDefault(false)

/**
 * "Mục chính" của trang đang mở: video đầu tiên, ô tìm kiếm, dòng cài đặt đầu tiên…
 * Ô sáng tự nhảy vào đây khi mở app, khi chuyển trang, khi mở / đóng trang kênh.
 */
val LocalPrimaryFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** Nhớ thẻ video / kênh được chọn gần nhất để quay lại đúng chỗ sau khi xem video hoặc đóng menu. */
class FocusMemory {
    var last: FocusRequester? = null
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/** Thẻ này tự ghi nhớ khi được chọn. Dùng cùng [Modifier.onFocusChanged] của thẻ. */
@Composable
fun rememberCardFocus(): Pair<FocusRequester, (Boolean) -> Unit> {
    val memory = LocalFocusMemory.current
    val requester = remember { FocusRequester() }
    return requester to { focused: Boolean -> if (focused) memory?.last = requester }
}

/** Gắn "mục chính" của trang vào ô này. */
@Composable
fun Modifier.primaryFocus(enabled: Boolean = true): Modifier {
    val r = LocalPrimaryFocus.current
    return if (enabled && r != null) this.focusRequester(r) else this
}

/**
 * Khung chính của app TV: thanh bên trái + nội dung; trình phát toàn màn hình phủ lên trên.
 *
 * Nút **Quay lại** trên remote:
 * 1. Đang xem video: đóng bảng chọn → ẩn thanh điều khiển → thoát video (về đúng ô đã chọn).
 * 2. Đang ở trang kênh: quay lại trang trước.
 * 3. Ô sáng đang ở nội dung: đưa ô sáng về thanh bên trái.
 * 4. Đang ở thanh bên trái của mục khác: về Trang chủ.
 * 5. Ở Trang chủ: bấm thêm lần nữa để thoát.
 */
@Composable
fun TvRoot(
    vm: TvViewModel,
    onVoiceSearch: () -> Unit,
    onExit: () -> Unit,
) {
    val contentFocus = remember { FocusRequester() }
    val primary = remember { FocusRequester() }
    val memory = remember { FocusMemory() }
    val sidebarItems = remember { Section.entries.associateWith { FocusRequester() } }
    fun focusSidebar() = sidebarItems.getValue(vm.section).tryFocus()
    fun focusContent() = primary.tryFocus() || contentFocus.tryFocus()

    var sidebarHasFocus by remember { mutableStateOf(false) }
    var exitHintAt by remember { mutableLongStateOf(0L) }
    var menuVideo by remember { mutableStateOf<VideoItem?>(null) }
    /** Về Trang chủ bằng nút Quay lại: ô sáng ở lại thanh bên trái. */
    var stayInSidebar by remember { mutableStateOf(false) }

    BackHandler {
        if (menuVideo != null) {
            menuVideo = null
            return@BackHandler
        }
        if (vm.onBack() == BackResult.HANDLED) return@BackHandler
        val now = System.currentTimeMillis()
        when {
            !sidebarHasFocus -> focusSidebar()
            vm.section != Section.HOME -> {
                stayInSidebar = true
                vm.selectSection(Section.HOME)
            }
            now - exitHintAt < EXIT_WINDOW_MS -> onExit()
            else -> exitHintAt = now
        }
    }

    Box(Modifier.fillMaxSize().background(TvColors.background)) {
        // ---- Phần duyệt video (vẫn giữ nguyên bên dưới khi xem video để quay lại đúng chỗ)
        Box(
            Modifier
                .fillMaxSize()
                .focusProperties { onEnter = { if (vm.playerOpen || menuVideo != null) cancelFocusChange() } }
                .focusGroup(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = TvDimens.sidebarCollapsed)
                    .focusRequester(contentFocus)
                    // Từ thanh bên trái bấm ▶: quay lại đúng ô vừa rời đi.
                    .focusRestorer()
                    .focusGroup()
                    .testTag("content"),
            ) {
                CompositionLocalProvider(LocalPrimaryFocus provides primary, LocalFocusMemory provides memory) {
                    val channelId = vm.channelStack.lastOrNull()
                    val openMenu: (VideoItem) -> Unit = { menuVideo = it }
                    if (channelId != null) {
                        ChannelScreen(vm, channelId, onVideoMenu = openMenu)
                    } else {
                        when (vm.section) {
                            Section.SEARCH -> SearchScreen(vm, onVoiceSearch = onVoiceSearch, onVideoMenu = openMenu)
                            Section.HOME -> HomeScreen(vm, onVideoMenu = openMenu)
                            Section.SUBSCRIPTIONS -> SubscriptionsScreen(vm, onVideoMenu = openMenu)
                            Section.LIBRARY -> LibraryScreen(vm, onVideoMenu = openMenu)
                            Section.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
            Sidebar(
                selected = vm.section,
                onSelect = vm::selectSection,
                itemRequesters = sidebarItems,
                onFocusChange = { sidebarHasFocus = it },
            )
        }

        // ---- Trình phát toàn màn hình
        if (vm.playerOpen) {
            PlayerScreen(vm)
        }

        menuVideo?.let { v ->
            VideoMenuPanel(vm, v, onDismiss = { menuVideo = null })
        }

        if (exitHintAt != 0L) {
            Text(
                "Bấm Quay lại lần nữa để thoát",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .background(TvColors.surfaceHigh, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
            LaunchedEffect(exitHintAt) {
                delay(EXIT_WINDOW_MS)
                exitHintAt = 0L
            }
        }
    }

    // ---- Đặt ô sáng đúng chỗ

    // Mở app: ô sáng ở thanh bên trái, rồi vào video đầu tiên khi trang chủ tải xong.
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (!vm.playerOpen) focusSidebar()
    }
    val homeReady = vm.home.entries.isNotEmpty() || vm.homeTrending.isNotEmpty()
    LaunchedEffect(homeReady) {
        if (homeReady && !initialFocusDone && vm.section == Section.HOME && !vm.playerOpen) {
            withFrameNanos { }
            withFrameNanos { }
            if (focusContent()) initialFocusDone = true
        }
    }

    // Chuyển trang, mở / đóng trang kênh → ô sáng vào mục chính của trang mới (khi trang đã tải xong).
    val topChannel = vm.channelStack.lastOrNull()
    val topChannelReady = topChannel?.let { vm.channels[it]?.page != null } ?: true
    var lastSection by remember { mutableStateOf(vm.section) }
    var lastStackSize by remember { mutableIntStateOf(0) }
    LaunchedEffect(vm.section, vm.channelStack.size, topChannelReady) {
        val changed = vm.section != lastSection || vm.channelStack.size != lastStackSize
        lastSection = vm.section
        lastStackSize = vm.channelStack.size
        if (!changed && topChannel == null) return@LaunchedEffect
        if (!topChannelReady || vm.playerOpen || menuVideo != null) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (stayInSidebar) {
            stayInSidebar = false
            focusSidebar()
        } else {
            focusContent()
        }
    }

    // Thoát video / đóng menu → ô sáng quay lại đúng video vừa chọn.
    var wasCovered by remember { mutableStateOf(false) }
    val covered = vm.playerOpen || menuVideo != null
    LaunchedEffect(covered) {
        if (covered) {
            wasCovered = true
        } else if (wasCovered) {
            wasCovered = false
            withFrameNanos { }
            if (memory.last?.tryFocus() != true && !contentFocus.tryFocus()) focusSidebar()
        }
    }
}

private const val EXIT_WINDOW_MS = 2_500L
