package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.Screen
import com.luzart.tubesach.ui.components.SectionTitle
import com.luzart.tubesach.ui.components.VideoRow

private data class Topic(val emoji: String, val label: String, val query: String, val color: Color)

private val TOPICS = listOf(
    Topic("📰", "Tin tức", "thời sự hôm nay", Color(0xFFE3F2FD)),
    Topic("🎵", "Ca nhạc", "nhạc trữ tình hay nhất", Color(0xFFFCE4EC)),
    Topic("🎤", "Bolero", "nhạc bolero", Color(0xFFF3E5F5)),
    Topic("🎭", "Cải lương", "cải lương xưa", Color(0xFFFFF3E0)),
    Topic("🎬", "Phim", "phim việt nam", Color(0xFFE8F5E9)),
    Topic("😂", "Hài kịch", "hài kịch hay", Color(0xFFFFFDE7)),
    Topic("🍲", "Nấu ăn", "nấu ăn ngon mỗi ngày", Color(0xFFFBE9E7)),
    Topic("❤️", "Sức khỏe", "sức khỏe người cao tuổi", Color(0xFFFFEBEE)),
    Topic("🏃", "Thể dục", "bài tập thể dục cho người già", Color(0xFFE0F7FA)),
    Topic("🙏", "Phật pháp", "phật pháp", Color(0xFFFFF8E1)),
    Topic("👶", "Thiếu nhi", "phim hoạt hình thiếu nhi", Color(0xFFEDE7F6)),
    Topic("✈️", "Du lịch", "du lịch việt nam", Color(0xFFE8EAF6)),
)

@Composable
fun HomeScreen(vm: AppViewModel, onVoiceSearch: () -> Unit, onOpenUpdate: (String) -> Unit, bottomPadding: PaddingValues) {
    val history by vm.store.history.collectAsState()
    val favorites by vm.store.favorites.collectAsState()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomPadding) {
        item {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tube Sạch", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "Xem video không quảng cáo",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                        IconButton(onClick = { vm.push(Screen.Settings) }, modifier = Modifier.size(64.dp)) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Cài đặt",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Ô tìm kiếm thật to
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .weight(1f)
                                .heightIn(min = 68.dp)
                                .background(Color.White, RoundedCornerShape(34.dp))
                                .clickable { vm.openSearchInput() }
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Bấm vào đây để tìm…",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF5A6270),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onVoiceSearch,
                            shape = RoundedCornerShape(34.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB300),
                                contentColor = Color(0xFF231A00),
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            modifier = Modifier.heightIn(min = 68.dp),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(32.dp))
                                Text("Nói", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        vm.update?.let { info ->
            item {
                Card(
                    onClick = { onOpenUpdate(info.downloadUrl) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("🔔 Có phiên bản mới (${info.version})", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Bấm vào đây để tải về rồi bấm \"Cài đặt\". Bản mới giúp xem video ổn định hơn.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item { SectionTitle("Chọn chủ đề") }
        item {
            Column(
                Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TOPICS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { topic ->
                            TopicButton(topic, Modifier.weight(1f)) { vm.search(topic.query) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                SectionTitle("Xem tiếp", action = "Xem tất cả") { vm.push(Screen.History) }
            }
            items(history.take(5), key = { "h" + it.video.id }) { entry ->
                val dur = entry.video.durationSec * 1000f
                VideoRow(
                    entry.video,
                    onClick = { vm.openVideo(entry.video) },
                    progress = if (dur > 0) entry.positionMs / dur else null,
                )
            }
        }

        if (favorites.isNotEmpty()) {
            item {
                SectionTitle("Yêu thích", action = "Xem tất cả") { vm.push(Screen.Favorites) }
            }
            items(favorites.take(5), key = { "f" + it.id }) { video ->
                VideoRow(video, onClick = { vm.openVideo(video) })
            }
        }

        if (history.isEmpty() && favorites.isEmpty()) {
            item {
                Text(
                    "👆 Bấm vào ô tìm kiếm, nút \"Nói\" hoặc một chủ đề ở trên để bắt đầu xem.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TopicButton(topic: Topic, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = topic.color, contentColor = Color(0xFF15181D)),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.heightIn(min = 84.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(topic.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                Text(topic.label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
