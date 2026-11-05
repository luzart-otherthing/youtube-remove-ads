package com.luzart.tubesach.tv.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Màu giao diện TV (nền tối như YouTube trên TV). */
object TvColors {
    val background = Color(0xFF0F0F0F)
    val surface = Color(0xFF1E1E1E)
    val surfaceHigh = Color(0xFF2C2C2C)
    val text = Color(0xFFF1F1F1)
    val textSecondary = Color(0xFFAAAAAA)

    /** Ô đang được chọn bằng remote: nền trắng, chữ đen – nhìn rõ từ xa. */
    val focus = Color(0xFFFFFFFF)
    val onFocus = Color(0xFF0F0F0F)
    val red = Color(0xFFFF0033)
    val brand = Color(0xFF1554B3)
    val accent = Color(0xFFFFB300)
    val scrim = Color(0xCC000000)
}

/**
 * Kích thước cho TV: màn hình 1920×1080 (hoặc 4K) được Android vẽ thành khoảng 960×540 dp.
 * Chữ to hơn điện thoại vì ngồi xa 2–3 mét.
 */
object TvDimens {
    /** Lề an toàn: một số TV cắt mất mép hình (overscan). */
    val safeHorizontal = 48.dp
    val safeVertical = 27.dp
    val sidebarCollapsed = 72.dp
    val sidebarExpanded = 220.dp
}

private val TvTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvColors.brand,
            onPrimary = Color.White,
            background = TvColors.background,
            onBackground = TvColors.text,
            surface = TvColors.surface,
            onSurface = TvColors.text,
        ),
        typography = TvTypography,
    ) {
        CompositionLocalProvider(LocalContentColor provides TvColors.text, content = content)
    }
}
