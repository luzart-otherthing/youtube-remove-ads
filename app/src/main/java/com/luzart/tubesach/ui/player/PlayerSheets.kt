package com.luzart.tubesach.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luzart.tubesach.data.LocalStore
import com.luzart.tubesach.data.QualityMode
import com.luzart.tubesach.player.CaptionChoice
import com.luzart.tubesach.player.NowPlaying
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.theme.LocalYt

private enum class Page { MAIN, QUALITY, ADVANCED, CAPTIONS, SPEED, SLEEP }

fun speedLabel(s: Float): String = if (s == 1f) "Chuẩn" else (if (s % 1f == 0f) "${s.toInt()}" else "$s").replace('.', ',') + "x"

/** Bảng cài đặt khi bấm biểu tượng ⚙ trên trình phát (giống app YouTube). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(
    now: NowPlaying,
    store: LocalStore,
    currentHeight: Int,
    onDismiss: () -> Unit,
    onOpenAppSettings: () -> Unit,
    startOnCaptions: Boolean = false,
) {
    val yt = LocalYt.current
    val settings by store.settings.collectAsState()
    val speed by PlaybackController.speed.collectAsState()
    val loop by PlaybackController.loop.collectAsState()
    val caption by PlaybackController.caption.collectAsState()
    val sleep by PlaybackController.sleepTimer.collectAsState()
    var page by remember { mutableStateOf(if (startOnCaptions) Page.CAPTIONS else Page.MAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yt.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            when (page) {
                Page.MAIN -> {
                    val qualityValue = when (settings.quality) {
                        QualityMode.AUTO -> "Tự động" + if (currentHeight > 0) " (${currentHeight}p)" else ""
                        QualityMode.HIGHER -> "Cao hơn" + if (currentHeight > 0) " (${currentHeight}p)" else ""
                        QualityMode.DATA_SAVER -> "Tiết kiệm dữ liệu"
                        QualityMode.ADVANCED -> "${settings.fixedHeight}p"
                    }
                    MenuRow(Icons.Outlined.Tune, "Chất lượng", qualityValue) { page = Page.QUALITY }
                    MenuRow(Icons.Outlined.ClosedCaption, "Phụ đề", caption?.label ?: "Tắt") { page = Page.CAPTIONS }
                    MenuRow(Icons.Outlined.SlowMotionVideo, "Tốc độ phát", speedLabel(speed)) { page = Page.SPEED }
                    MenuRow(
                        Icons.Outlined.Bedtime,
                        "Hẹn giờ ngủ",
                        when {
                            sleep == null -> "Tắt"
                            sleep?.endAt == null -> "Cuối video"
                            else -> "${(((sleep?.endAt ?: 0) - System.currentTimeMillis()) / 60_000 + 1)} phút"
                        },
                    ) { page = Page.SLEEP }
                    SwitchRow(Icons.Outlined.Repeat, "Lặp lại video", loop) { PlaybackController.setLoop(it) }
                    SwitchRow(Icons.Outlined.MoneyOff, "SponsorBlock (bỏ qua đoạn tài trợ)", settings.sponsorBlock) {
                        PlaybackController.setSponsorBlock(it)
                    }
                    MenuRow(Icons.Outlined.Settings, "Cài đặt bổ sung", null) { onDismiss(); onOpenAppSettings() }
                }

                Page.QUALITY -> {
                    SubHeader("Chất lượng của video hiện tại") { page = Page.MAIN }
                    QualityMode.entries.forEach { mode ->
                        CheckRow(
                            mode.label,
                            mode.description,
                            checked = settings.quality == mode,
                        ) {
                            if (mode == QualityMode.ADVANCED) {
                                page = Page.ADVANCED
                            } else {
                                PlaybackController.setQuality(mode)
                                onDismiss()
                            }
                        }
                    }
                    Text(
                        "Trong trường hợp này, chế độ cài đặt chất lượng sẽ được áp dụng cho các video tiếp theo.",
                        color = yt.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                Page.ADVANCED -> {
                    SubHeader("Chất lượng") { page = Page.QUALITY }
                    val heights = now.availableHeights.ifEmpty { listOf(1080, 720, 480, 360, 240, 144) }
                    heights.forEach { h ->
                        CheckRow(
                            "${h}p" + if (h >= 720) " HD" else "",
                            null,
                            checked = settings.quality == QualityMode.ADVANCED && settings.fixedHeight == h,
                        ) {
                            PlaybackController.setQuality(QualityMode.ADVANCED, h)
                            onDismiss()
                        }
                    }
                }

                Page.CAPTIONS -> {
                    SubHeader("Phụ đề") { page = Page.MAIN }
                    CheckRow("Tắt", null, checked = caption == null) { PlaybackController.setCaption(null); onDismiss() }
                    now.captions.forEach { t ->
                        CheckRow(t.name, null, checked = caption?.track == t && caption?.translateTo == null) {
                            PlaybackController.setCaption(CaptionChoice(t)); onDismiss()
                        }
                    }
                    if (now.captions.isNotEmpty() && now.captions.none { it.languageCode.startsWith("vi") }) {
                        val base = now.captions.firstOrNull { !it.isAuto } ?: now.captions.first()
                        CheckRow(
                            "Tự động dịch: Tiếng Việt",
                            "Từ ${base.name}",
                            checked = caption?.translateTo == "vi",
                        ) { PlaybackController.setCaption(CaptionChoice(base, "vi")); onDismiss() }
                    }
                    if (now.captions.isEmpty()) {
                        Text(
                            "Video này không có phụ đề.",
                            color = yt.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }

                Page.SPEED -> {
                    SubHeader("Tốc độ phát") { page = Page.MAIN }
                    Text(
                        String.format(java.util.Locale.US, "%.2fx", speed).replace('.', ','),
                        color = yt.text,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Slider(
                        value = speed,
                        onValueChange = { PlaybackController.setSpeed((it * 20).toInt() / 20f) },
                        valueRange = 0.25f..2f,
                        colors = SliderDefaults.colors(thumbColor = yt.text, activeTrackColor = yt.text, inactiveTrackColor = yt.chip),
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(1f, 1.25f, 1.5f, 2f).forEach { s ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (speed == s) yt.chipSelected else yt.chip)
                                    .clickable { PlaybackController.setSpeed(s) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(speedLabel(s).removeSuffix("x").let { if (s == 1f) "1,0" else it }, color = if (speed == s) yt.chipSelectedText else yt.text, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        CheckRow(speedLabel(s), null, checked = speed == s) { PlaybackController.setSpeed(s); onDismiss() }
                    }
                }

                Page.SLEEP -> {
                    SubHeader("Hẹn giờ ngủ") { page = Page.MAIN }
                    CheckRow("Tắt", null, checked = sleep == null) { PlaybackController.cancelSleepTimer(); onDismiss() }
                    listOf(10, 15, 20, 30, 45, 60).forEach { m ->
                        CheckRow("$m phút", null, checked = false) { PlaybackController.setSleepTimer(m); onDismiss() }
                    }
                    CheckRow("Cuối video", null, checked = sleep != null && sleep?.endAt == null) {
                        PlaybackController.setSleepTimer(0); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, value: String?, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = yt.icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text("•  $value", color = yt.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = yt.icon, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF3EA6FF)),
        )
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    val yt = LocalYt.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = yt.icon) }
        Text(title, color = yt.text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun CheckRow(title: String, description: String?, checked: Boolean, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp)) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = yt.icon)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = yt.text, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
