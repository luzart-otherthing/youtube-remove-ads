package com.luzart.tubesach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.components.ShortsFilled
import com.luzart.tubesach.ui.components.ShortsOutlined
import com.luzart.tubesach.ui.player.MiniPlayerBar
import com.luzart.tubesach.ui.player.rememberPlayerUiState
import com.luzart.tubesach.ui.screens.ChannelScreen
import com.luzart.tubesach.ui.screens.HomeScreen
import com.luzart.tubesach.ui.screens.ResultsScreen
import com.luzart.tubesach.ui.screens.SearchInputScreen
import com.luzart.tubesach.ui.screens.SettingsScreen
import com.luzart.tubesach.ui.screens.ShortsScreen
import com.luzart.tubesach.ui.screens.SubscriptionsScreen
import com.luzart.tubesach.ui.screens.VideoListScreen
import com.luzart.tubesach.ui.screens.WatchScreen
import com.luzart.tubesach.ui.screens.YouScreen
import com.luzart.tubesach.ui.theme.LocalYt

/** Khung chính của ứng dụng: các tab, màn hình chồng, trình phát mini, thanh điều hướng dưới, trang xem. */
@Composable
fun TubeApp(vm: AppViewModel, inPip: Boolean, pipSupported: Boolean, onVoice: () -> Unit, onOpenUrl: (String) -> Unit) {
    val yt = LocalYt.current
    val now by PlaybackController.nowPlaying.collectAsState()
    val isPlaying by PlaybackController.isPlaying.collectAsState()
    val settings by vm.store.settings.collectAsState()

    BackHandler(enabled = vm.fullscreen || vm.watchExpanded || vm.stack.isNotEmpty() || vm.tab != Tab.HOME) { vm.back() }

    if (inPip) {
        WatchScreen(vm, inPip = true, onToggleFullscreen = {})
        return
    }

    Box(Modifier.fillMaxSize().background(yt.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (vm.tab) {
                    Tab.HOME -> HomeScreen(vm, onOpenUrl)
                    Tab.SHORTS -> ShortsScreen(vm)
                    Tab.SUBSCRIPTIONS -> SubscriptionsScreen(vm)
                    Tab.YOU -> YouScreen(vm)
                }
                vm.top?.let { screen ->
                    Box(Modifier.fillMaxSize().background(yt.background)) {
                        when (screen) {
                            Screen.SearchInput -> SearchInputScreen(vm, onVoice)
                            is Screen.Results -> ResultsScreen(vm, screen.query, onVoice)
                            is Screen.Channel -> ChannelScreen(vm, screen.id)
                            Screen.Settings -> SettingsScreen(vm, pipSupported)
                            is Screen.VideoList -> VideoListScreen(vm, screen.kind)
                        }
                    }
                }
            }
            val current = now
            if (current != null && !vm.watchExpanded && !current.isShort && vm.tab != Tab.SHORTS) {
                MiniPlayerBar(
                    now = current,
                    isPlaying = isPlaying,
                    ui = rememberPlayerUiState(),
                    onExpand = { vm.expandPlayer() },
                    onClose = { PlaybackController.close() },
                )
            }
            BottomBar(vm, settings.hideShorts)
        }
        if (vm.watchExpanded && now != null && now?.isShort == false) {
            Box(Modifier.fillMaxSize().background(yt.background)) {
                WatchScreen(vm, inPip = false, onToggleFullscreen = { vm.fullscreen = !vm.fullscreen })
            }
        }
    }
}

@Composable
private fun BottomBar(vm: AppViewModel, hideShorts: Boolean) {
    val yt = LocalYt.current
    val shorts = vm.tab == Tab.SHORTS && vm.stack.isEmpty()
    val bg = if (shorts) Color.Black else yt.background
    val fg = if (shorts) Color.White else yt.text
    Column(Modifier.background(bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (shorts) Color(0x33FFFFFF) else yt.divider))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(vm, Tab.HOME, Icons.Filled.Home, Icons.Outlined.Home, fg)
            if (!hideShorts) NavItem(vm, Tab.SHORTS, ShortsFilled, ShortsOutlined, fg)
            NavItem(vm, Tab.SUBSCRIPTIONS, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, fg)
            NavItem(vm, Tab.YOU, Icons.Filled.AccountCircle, Icons.Outlined.AccountCircle, fg)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(vm: AppViewModel, tab: Tab, selected: ImageVector, normal: ImageVector, color: Color) {
    val isSel = vm.tab == tab
    Column(
        Modifier
            .weight(1f)
            .clickable { vm.selectTab(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (isSel) selected else normal, contentDescription = tab.label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(tab.label, color = color, fontSize = 10.sp, maxLines = 1)
    }
}

