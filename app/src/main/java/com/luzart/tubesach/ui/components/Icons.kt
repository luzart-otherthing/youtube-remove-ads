package com.luzart.tubesach.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Biểu tượng Shorts: khung điện thoại dọc có nút phát (tự vẽ). */
val ShortsOutlined: ImageVector by lazy {
    ImageVector.Builder("ShortsOutlined", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(8f, 2.5f)
            lineTo(16f, 2.5f)
            curveTo(17.4f, 2.5f, 18.5f, 3.6f, 18.5f, 5f)
            lineTo(18.5f, 19f)
            curveTo(18.5f, 20.4f, 17.4f, 21.5f, 16f, 21.5f)
            lineTo(8f, 21.5f)
            curveTo(6.6f, 21.5f, 5.5f, 20.4f, 5.5f, 19f)
            lineTo(5.5f, 5f)
            curveTo(5.5f, 3.6f, 6.6f, 2.5f, 8f, 2.5f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 9f)
            lineTo(15f, 12f)
            lineTo(10f, 15f)
            close()
        }
    }.build()
}

val ShortsFilled: ImageVector by lazy {
    ImageVector.Builder("ShortsFilled", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(8f, 2f)
            lineTo(16f, 2f)
            curveTo(17.7f, 2f, 19f, 3.3f, 19f, 5f)
            lineTo(19f, 19f)
            curveTo(19f, 20.7f, 17.7f, 22f, 16f, 22f)
            lineTo(8f, 22f)
            curveTo(6.3f, 22f, 5f, 20.7f, 5f, 19f)
            lineTo(5f, 5f)
            curveTo(5f, 3.3f, 6.3f, 2f, 8f, 2f)
            close()
            // lỗ hình tam giác (vẽ ngược chiều để tạo khoảng trống)
            moveTo(10f, 9f)
            lineTo(10f, 15f)
            lineTo(15f, 12f)
            close()
        }
    }.build()
}
