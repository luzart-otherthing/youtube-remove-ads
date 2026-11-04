package com.luzart.tubesach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.luzart.tubesach.data.ThemeMode

/** Bảng màu theo kiểu app YouTube (sáng / tối). */
@Immutable
data class YtColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val chip: Color,
    val chipSelected: Color,
    val chipSelectedText: Color,
    val divider: Color,
    val red: Color,
    val subscribeBg: Color,
    val subscribeText: Color,
    val icon: Color,
)

val YtRed = Color(0xFFFF0033)

val LightYt = YtColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    text = Color(0xFF0F0F0F),
    textSecondary = Color(0xFF606060),
    chip = Color(0x0D000000),
    chipSelected = Color(0xFF0F0F0F),
    chipSelectedText = Color(0xFFFFFFFF),
    divider = Color(0x1A000000),
    red = YtRed,
    subscribeBg = Color(0xFF0F0F0F),
    subscribeText = Color(0xFFFFFFFF),
    icon = Color(0xFF0F0F0F),
)

val DarkYt = YtColors(
    isDark = true,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF212121),
    text = Color(0xFFF1F1F1),
    textSecondary = Color(0xFFAAAAAA),
    chip = Color(0x1AFFFFFF),
    chipSelected = Color(0xFFF1F1F1),
    chipSelectedText = Color(0xFF0F0F0F),
    divider = Color(0x33FFFFFF),
    red = YtRed,
    subscribeBg = Color(0xFFF1F1F1),
    subscribeText = Color(0xFF0F0F0F),
    icon = Color(0xFFF1F1F1),
)

val LocalYt = staticCompositionLocalOf { LightYt }

/** Cỡ chữ gần giống app YouTube trên Android. */
private val ytTypography = Typography(
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
)

@Composable
fun TubeTheme(mode: ThemeMode, textScale: Float, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val yt = if (dark) DarkYt else LightYt
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF3EA6FF),
            onPrimary = Color(0xFF0F0F0F),
            background = yt.background,
            onBackground = yt.text,
            surface = yt.background,
            onSurface = yt.text,
            surfaceVariant = Color(0xFF272727),
            onSurfaceVariant = yt.textSecondary,
            surfaceContainerLow = yt.surface,
            surfaceContainer = yt.surface,
            surfaceContainerHigh = yt.surface,
            surfaceContainerHighest = Color(0xFF303030),
            secondaryContainer = Color(0xFF3F3F3F),
            outline = Color(0xFF717171),
            outlineVariant = yt.divider,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF065FD4),
            onPrimary = Color.White,
            background = yt.background,
            onBackground = yt.text,
            surface = yt.background,
            onSurface = yt.text,
            surfaceVariant = Color(0xFFF2F2F2),
            onSurfaceVariant = yt.textSecondary,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color(0xFFF2F2F2),
            secondaryContainer = Color(0xFFE5E5E5),
            outline = Color(0xFF909090),
            outlineVariant = yt.divider,
        )
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalYt provides yt,
        LocalDensity provides Density(density.density, density.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = ytTypography, content = content)
    }
}
