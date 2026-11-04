package com.luzart.tubesach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.luzart.tubesach.data.VideoItem
import com.luzart.tubesach.ui.theme.LiveRed
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

fun formatViews(views: Long): String? {
    if (views < 0) return null
    fun one(v: Double) = String.format(Locale.forLanguageTag("vi"), "%.1f", v).removeSuffix(",0")
    return when {
        views >= 1_000_000_000 -> "${one(views / 1e9)} tỷ lượt xem"
        views >= 1_000_000 -> "${one(views / 1e6)} triệu lượt xem"
        views >= 1_000 -> "${one(views / 1e3)} nghìn lượt xem"
        else -> "$views lượt xem"
    }
}

fun subtitleOf(video: VideoItem): String =
    listOfNotNull(
        video.uploader.ifBlank { null },
        formatViews(video.viewCount),
        video.uploadedText?.ifBlank { null },
    ).joinToString(" • ")

// ---------------------------------------------------------------- Thanh tiêu đề

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 68.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Quay lại", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            actions()
        }
    }
}

// ---------------------------------------------------------------- Ảnh thu nhỏ

@Composable
fun Thumbnail(video: VideoItem, modifier: Modifier = Modifier, progress: Float? = null, big: Boolean = true) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF222222)),
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val label = if (video.isLive) "TRỰC TIẾP" else formatDuration(video.durationSec)
        if (label.isNotEmpty()) {
            Text(
                label,
                color = Color.White,
                style = if (big) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(
                        if (video.isLive) LiveRed else Color(0xD9000000),
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        if (progress != null && progress > 0f) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                color = Color(0xFFE53935),
                trackColor = Color(0x66FFFFFF),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(6.dp),
            )
        }
    }
}

/** Thẻ video lớn: ảnh to toàn chiều ngang + tiêu đề chữ to. */
@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, progress: Float? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Thumbnail(video, Modifier.fillMaxWidth(), progress)
        Spacer(Modifier.height(10.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val sub = subtitleOf(video)
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Dòng video gọn: ảnh bên trái, chữ bên phải. */
@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit, progress: Float? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Thumbnail(video, Modifier.width(150.dp), progress, big = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (video.uploader.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    video.uploader,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Trạng thái

@Composable
fun LoadingBox(text: String = "Đang tải…", modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("😟", style = MaterialTheme.typography.displaySmall)
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        BigButton("Thử lại", onClick = onRetry)
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 60.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 52.dp)) {
                Text(action, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun Divider() = HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 2.dp)
