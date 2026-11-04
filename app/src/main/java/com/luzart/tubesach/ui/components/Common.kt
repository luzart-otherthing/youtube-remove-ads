package com.luzart.tubesach.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.luzart.tubesach.data.Chip
import com.luzart.tubesach.data.ShortItem
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.ui.theme.LocalYt
import java.util.Locale

// ---------------------------------------------------------------- Định dạng

fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return ""
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatMs(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

/** "Kênh • 1,2 Tr lượt xem • 2 ngày trước" */
fun metaLine(video: VideoItem, includeChannel: Boolean = true): String = listOfNotNull(
    video.uploader.ifBlank { null }?.takeIf { includeChannel },
    video.viewText ?: video.viewCount.takeIf { it >= 0 }?.let { com.luzart.tubesach.data.YouTubeRepository.formatViewsShort(it) },
    video.uploadedText?.ifBlank { null },
).joinToString(" • ")

fun shareVideo(context: Context, video: VideoItem) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "https://youtu.be/${video.id}")
        .putExtra(Intent.EXTRA_SUBJECT, video.title)
    context.startActivity(Intent.createChooser(intent, "Chia sẻ"))
}

// ---------------------------------------------------------------- Ảnh

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    val yt = LocalYt.current
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(yt.chip),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = yt.textSecondary, modifier = Modifier.size(size * 0.6f))
        } else {
            AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Thumbnail(
    video: VideoItem,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
    progress: Float? = null,
) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner))
            .background(Color(0xFF282828)),
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val label = when {
            video.isLive -> "TRỰC TIẾP"
            video.durationText != null -> video.durationText
            else -> formatDuration(video.durationSec)
        }
        if (label.isNotEmpty()) {
            Text(
                label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = if (progress != null && progress > 0f) 10.dp else 8.dp)
                    .background(if (video.isLive) Color(0xFFCC0000) else Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (video.badge != null) {
            Text(
                video.badge,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (progress != null && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color(0x66FFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(LocalYt.current.red),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Thẻ video

/** Thẻ video lớn như trang chủ YouTube: ảnh tràn viền, ảnh đại diện kênh, tiêu đề 2 dòng. */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    progress: Float? = null,
    rounded: Boolean = false,
) {
    val yt = LocalYt.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 16.dp)
            .then(if (rounded) Modifier.padding(horizontal = 12.dp) else Modifier),
    ) {
        Thumbnail(video, Modifier.fillMaxWidth(), corner = if (rounded) 12.dp else 0.dp, progress = progress)
        Row(Modifier.padding(start = if (rounded) 0.dp else 12.dp, end = 0.dp, top = 12.dp)) {
            Avatar(
                video.channelAvatar,
                36.dp,
                Modifier.clickable(enabled = onChannelClick != null && video.channelId != null) {
                    video.channelId?.let { onChannelClick?.invoke(it) }
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = yt.text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    metaLine(video),
                    color = yt.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onMore != null) {
                IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Tác vụ", tint = yt.icon, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/** Dòng video gọn: ảnh bên trái, chữ bên phải (dùng cho kênh, lịch sử, danh sách). */
@Composable
fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    onMore: (() -> Unit)? = null,
    progress: Float? = null,
    showChannel: Boolean = true,
) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Thumbnail(video, Modifier.width(168.dp), corner = 8.dp, progress = progress)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, color = yt.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(metaLine(video, showChannel), color = yt.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Tác vụ", tint = yt.icon, modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Thẻ nhỏ cuộn ngang (kệ "Tin nổi bật", "Video đã xem"). */
@Composable
fun VideoTile(video: VideoItem, width: Dp, onClick: () -> Unit, progress: Float? = null, onMore: (() -> Unit)? = null) {
    val yt = LocalYt.current
    Column(
        Modifier
            .width(width)
            .clickable(onClick = onClick),
    ) {
        Thumbnail(video, Modifier.fillMaxWidth(), corner = 8.dp, progress = progress)
        Row(Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(video.title, color = yt.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.uploader, color = yt.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onMore != null) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Tác vụ",
                    tint = yt.icon,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onMore),
                )
            }
        }
    }
}

@Composable
fun ShelfHeader(title: String, icon: ImageVector? = null, iconTint: Color? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint ?: yt.red, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = yt.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                color = yt.text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, yt.divider, RoundedCornerShape(18.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- Shorts

@Composable
fun ShortsShelf(title: String, shorts: List<ShortItem>, onClick: (ShortItem) -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = (screenWidth - 12.dp * 2 - 6.dp) / 2
    Column(Modifier.padding(bottom = 16.dp)) {
        ShelfHeader(title, icon = ShortsFilled)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(shorts, key = { it.id }) { s -> ShortCard(s, cardWidth) { onClick(s) } }
        }
    }
}

@Composable
fun ShortCard(short: ShortItem, width: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .width(width)
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF282828))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(short.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xB3000000))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
        ) {
            Text(short.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            short.viewText?.let { Text(it, color = Color.White, fontSize = 12.sp) }
        }
    }
}

// ---------------------------------------------------------------- Chip & nút

@Composable
fun ChipRow(chips: List<Chip>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips.size) { i -> YtChip(chips[i].title, i == selected) { onSelect(i) } }
    }
}

@Composable
fun YtChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val yt = LocalYt.current
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) yt.chipSelected else yt.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) yt.chipSelectedText else yt.text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** Nút "Đăng ký" kiểu YouTube: viên thuốc đen chữ trắng; khi đã đăng ký: xám, có chuông. */
@Composable
fun SubscribeButton(subscribed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false) {
    val yt = LocalYt.current
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (subscribed) yt.chip else yt.subscribeBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (wide) Arrangement.Center else Arrangement.Start,
    ) {
        if (subscribed) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = yt.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Đã đăng ký", color = yt.text, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = yt.text, modifier = Modifier.size(20.dp))
        } else {
            Text("Đăng ký", color = yt.subscribeText, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Nút viên thuốc xám (Chia sẻ, Lưu…). */
@Composable
fun PillButton(icon: ImageVector?, text: String, onClick: () -> Unit, highlighted: Boolean = false) {
    val yt = LocalYt.current
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlighted) yt.chipSelected else yt.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (highlighted) yt.chipSelectedText else yt.text
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = c, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------- Trạng thái

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = LocalYt.current.red, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String?, action: String? = null, onAction: (() -> Unit)? = null) {
    val yt = LocalYt.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = yt.textSecondary, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = yt.text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = yt.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(yt.subscribeBg)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, color = yt.subscribeText, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    EmptyState(Icons.Outlined.Block, message, null, action = "Thử lại", onAction = onRetry)
}

@Composable
fun YtDivider() = HorizontalDivider(color = LocalYt.current.divider, thickness = 1.dp)

// ---------------------------------------------------------------- Menu ⋮

/** Bảng tác vụ khi bấm ⋮ trên một video (giống YouTube). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuSheet(
    video: VideoItem,
    saved: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onNotInterested: () -> Unit,
    onOpenChannel: ((String) -> Unit)?,
) {
    val context = LocalContext.current
    val yt = LocalYt.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = yt.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetItem(Icons.Outlined.WatchLater, if (saved) "Xoá khỏi danh sách Xem sau" else "Lưu vào danh sách Xem sau") { onToggleSave(); onDismiss() }
            SheetItem(Icons.AutoMirrored.Outlined.PlaylistAdd, "Lưu vào danh sách phát") { onToggleSave(); onDismiss() }
            SheetItem(Icons.Outlined.Share, "Chia sẻ") { shareVideo(context, video); onDismiss() }
            if (onOpenChannel != null && video.channelId != null) {
                SheetItem(Icons.Outlined.AccountBox, "Đi đến kênh") { onOpenChannel(video.channelId); onDismiss() }
            }
            SheetItem(Icons.Outlined.Block, "Không quan tâm") { onNotInterested(); onDismiss() }
        }
    }
}

@Composable
fun SheetItem(icon: ImageVector?, text: String, value: String? = null, onClick: () -> Unit) {
    val yt = LocalYt.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = yt.icon, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(20.dp))
        }
        Text(text, color = yt.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = yt.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun rememberMenuState(): MenuState = remember { MenuState() }

class MenuState {
    var video by mutableStateOf<VideoItem?>(null)
}
