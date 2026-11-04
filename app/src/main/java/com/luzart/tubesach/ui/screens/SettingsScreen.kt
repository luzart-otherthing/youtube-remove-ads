package com.luzart.tubesach.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.BuildConfig
import com.luzart.tubesach.ui.AppViewModel
import com.luzart.tubesach.ui.components.Divider
import com.luzart.tubesach.ui.components.TopBar

@Composable
fun SettingsScreen(vm: AppViewModel, pipSupported: Boolean, bottomPadding: PaddingValues) {
    val settings by vm.store.settings.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar("Cài đặt", onBack = { vm.back() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottomPadding),
        ) {
            Header("Chất lượng video")
            listOf(
                360 to "Tiết kiệm 3G/4G (360p)",
                480 to "Vừa (480p)",
                720 to "Nét – nên dùng (720p)",
                1080 to "Rất nét (1080p)",
            ).forEach { (q, label) ->
                Choice(label, selected = settings.maxQuality == q) {
                    vm.store.updateSettings { it.copy(maxQuality = q) }
                }
            }
            Note("Áp dụng cho video mở lần sau.")
            Divider()

            Header("Cỡ chữ")
            listOf(1.0f to "Lớn", 1.15f to "Rất lớn", 1.3f to "Cực lớn").forEach { (scale, label) ->
                Choice(label, selected = kotlin.math.abs(settings.textScale - scale) < 0.01f) {
                    vm.store.updateSettings { it.copy(textScale = scale) }
                }
            }
            Divider()

            Header("Khi xem video")
            Toggle(
                "Tự động phát video tiếp theo",
                "Hết video sẽ tự phát video liên quan (hợp khi nghe nhạc).",
                settings.autoplay,
            ) { v -> vm.store.updateSettings { it.copy(autoplay = v) } }
            if (pipSupported) {
                Toggle(
                    "Tự thu nhỏ khi ra màn hình chính",
                    "Đang xem mà bấm nút Home, video thu thành cửa sổ nhỏ nổi trên màn hình.",
                    settings.autoPip,
                ) { v -> vm.store.updateSettings { it.copy(autoPip = v) } }
            }
            Divider()

            Header("Cập nhật")
            OutlinedButton(
                onClick = { vm.checkUpdate(manual = true) },
                enabled = !vm.checkingUpdate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 60.dp),
            ) {
                Text(
                    if (vm.checkingUpdate) "Đang kiểm tra…" else "Kiểm tra bản cập nhật",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            vm.updateMessage?.let { Note(it) }
            vm.update?.let { Note("Có bản mới ${it.version}. Quay lại Trang chủ và bấm vào thông báo màu vàng để tải.") }
            Divider()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Giới thiệu", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Không có quảng cáo: ứng dụng lấy thẳng video gốc, không dùng trình phát của YouTube nên quảng cáo không bao giờ được tải.\n" +
                            "• Không cần đăng nhập tài khoản.\n" +
                            "• Tắt màn hình vẫn nghe được tiếng; có nút điều khiển ở màn hình khoá.\n" +
                            "• Lịch sử và video yêu thích chỉ lưu trên máy này.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Phiên bản ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
