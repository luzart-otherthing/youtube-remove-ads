package com.luzart.tubesach.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Subscriptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.data.ChannelRef
import com.luzart.tubesach.player.CaptionChoice
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.tv.PlayerPanel
import com.luzart.tubesach.tv.TvViewModel
import com.luzart.tubesach.tv.ui.Avatar
import com.luzart.tubesach.tv.ui.PanelOption
import com.luzart.tubesach.tv.ui.SidePanel
import com.luzart.tubesach.tv.ui.TvButton
import com.luzart.tubesach.tv.ui.TvColors
import com.luzart.tubesach.tv.ui.tvClickable

/** Bảng chọn mở từ thanh điều khiển của trình phát. Bấm Quay lại để đóng. */
@Composable
fun PlayerPanelContent(vm: TvViewModel, panel: PlayerPanel) {
    val now by PlaybackController.nowPlaying.collectAsState()
    val settings by vm.store.settings.collectAsState()
    val caption by PlaybackController.caption.collectAsState()
    val speed by PlaybackController.speed.collectAsState()
    val sleep by PlaybackController.sleepTimer.collectAsState()
    val current = remember(panel) { FocusRequester() }
    fun close() {
        vm.panel = null
    }

    when (panel) {
        PlayerPanel.QUALITY -> {
            val selected = QualityOption.current(settings)
            val playing = PlaybackController.currentHeight()
            SidePanel(panel.title, initialFocus = current, subtitle = playing?.let { "Đang phát: ${it}p" }) {
                QualityOption.all.forEach { opt ->
                    item {
                        PanelOption(
                            opt.label,
                            selected = opt == selected,
                            subtitle = opt.description,
                            modifier = if (opt == selected) Modifier.focusRequester(current) else Modifier,
                            onClick = {
                                PlaybackController.setQuality(opt.mode, opt.height)
                                close()
                            },
                        )
                    }
                }
            }
        }

        PlayerPanel.CAPTIONS -> {
            val tracks = now?.captions.orEmpty()
            SidePanel(panel.title, initialFocus = current, subtitle = if (tracks.isEmpty()) "Video này không có phụ đề." else null) {
                item {
                    PanelOption("Tắt", selected = caption == null, modifier = if (caption == null) Modifier.focusRequester(current) else Modifier, onClick = {
                        PlaybackController.setCaption(null)
                        close()
                    })
                }
                tracks.forEach { t ->
                    val isSel = caption?.track == t && caption?.translateTo == null
                    item {
                        PanelOption(t.name, selected = isSel, modifier = if (isSel) Modifier.focusRequester(current) else Modifier, onClick = {
                            PlaybackController.setCaption(CaptionChoice(t))
                            close()
                        })
                    }
                }
                if (tracks.isNotEmpty() && tracks.none { it.languageCode.startsWith("vi") }) {
                    val base = tracks.firstOrNull { !it.isAuto } ?: tracks.first()
                    val isSel = caption?.translateTo == "vi"
                    item {
                        PanelOption(
                            "Tự động dịch: Tiếng Việt",
                            selected = isSel,
                            subtitle = "Từ ${base.name}",
                            modifier = if (isSel) Modifier.focusRequester(current) else Modifier,
                            onClick = {
                                PlaybackController.setCaption(CaptionChoice(base, "vi"))
                                close()
                            },
                        )
                    }
                }
            }
        }

        PlayerPanel.SPEED -> {
            val nearest = SPEEDS.minBy { kotlin.math.abs(it - speed) }
            SidePanel(panel.title, initialFocus = current) {
                SPEEDS.forEach { sp ->
                    item {
                        PanelOption(speedLabel(sp), selected = sp == nearest, modifier = if (sp == nearest) Modifier.focusRequester(current) else Modifier, onClick = {
                            PlaybackController.setSpeed(sp)
                            close()
                        })
                    }
                }
            }
        }

        PlayerPanel.SLEEP -> {
            val active = when {
                sleep == null -> -1
                sleep?.endAt == null -> 0
                else -> Int.MIN_VALUE // đang đếm giờ: không đánh dấu mục nào
            }
            SidePanel(
                panel.title,
                initialFocus = current,
                subtitle = when {
                    sleep == null -> "Tự tạm dừng video sau một khoảng thời gian"
                    sleep?.endAt == null -> "Sẽ dừng khi hết video này"
                    else -> "Sẽ dừng sau khoảng ${((sleep!!.endAt!! - System.currentTimeMillis()) / 60_000).coerceAtLeast(0) + 1} phút"
                },
            ) {
                SLEEP_OPTIONS.forEachIndexed { i, m ->
                    val isSel = m == active
                    item {
                        PanelOption(
                            sleepLabel(m),
                            selected = isSel,
                            modifier = if (isSel || (active == Int.MIN_VALUE && i == 0)) Modifier.focusRequester(current) else Modifier,
                            onClick = {
                                if (m < 0) PlaybackController.cancelSleepTimer() else PlaybackController.setSleepTimer(m)
                                close()
                            },
                        )
                    }
                }
            }
        }

        PlayerPanel.INFO -> InfoPanel(vm, current)
    }
}

@Composable
private fun InfoPanel(vm: TvViewModel, first: FocusRequester) {
    val now by PlaybackController.nowPlaying.collectAsState()
    val subs by vm.store.subscriptions.collectAsState()
    val np = now ?: return
    val watch = np.watch
    val channel = watch?.channel ?: np.video.channelId?.let { ChannelRef(it, np.video.uploader, np.video.channelAvatar) }
    val subscribed = channel != null && subs.any { it.id == channel.id }
    SidePanel(PlayerPanel.INFO.title, initialFocus = first) {
        if (channel != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Avatar(channel.avatar, 56.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        watch?.subscriberText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TvColors.textSecondary) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    TvButton(
                        if (subscribed) "Đã đăng ký" else "Đăng ký",
                        onClick = { vm.store.toggleSubscription(channel) },
                        icon = if (subscribed) Icons.Filled.NotificationsActive else Icons.Filled.Subscriptions,
                        primary = !subscribed,
                        modifier = Modifier.focusRequester(first),
                    )
                    TvButton("Xem kênh", onClick = { vm.openChannel(channel.id) }, icon = Icons.Filled.AccountCircle)
                }
            }
        }
        item {
            Text(np.video.title, style = MaterialTheme.typography.titleMedium)
            val line = listOfNotNull(watch?.viewText ?: np.video.viewText, watch?.dateText ?: np.video.uploadedText, watch?.likeText?.let { "$it lượt thích" })
                .joinToString(" • ")
            if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodyMedium, color = TvColors.textSecondary)
            Spacer(Modifier.height(8.dp))
        }
        // Mô tả chia thành từng đoạn chọn được → cuộn bằng ▲ ▼ trên remote.
        val paragraphs = watch?.description.orEmpty().split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        paragraphs.forEachIndexed { i, para ->
            item(key = "desc$i") {
                DescriptionBlock(para, modifier = if (channel == null && i == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
        if (paragraphs.isEmpty() && channel == null) {
            item { DescriptionBlock("Không có mô tả.", modifier = Modifier.focusRequester(first)) }
        }
    }
}

@Composable
private fun DescriptionBlock(text: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = TvColors.text,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = {})
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .padding(8.dp),
    )
}
