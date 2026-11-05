package com.luzart.tubesach.tv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luzart.tubesach.tv.Section

fun Section.icon(): ImageVector = when (this) {
    Section.SEARCH -> Icons.Filled.Search
    Section.HOME -> Icons.Filled.Home
    Section.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    Section.LIBRARY -> Icons.Filled.VideoLibrary
    Section.SETTINGS -> Icons.Filled.Settings
}

/**
 * Thanh bên trái kiểu YouTube trên TV: thu gọn còn biểu tượng, mở rộng hiện chữ khi được chọn.
 * Bấm OK vào một mục để chuyển trang.
 */
@Composable
fun Sidebar(
    selected: Section,
    onSelect: (Section) -> Unit,
    itemRequesters: Map<Section, FocusRequester>,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) TvDimens.sidebarExpanded else TvDimens.sidebarCollapsed, label = "sidebar")
    Column(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (expanded) {
                    Brush.horizontalGradient(0f to Color(0xFA0A0A0A), 0.75f to Color(0xF20A0A0A), 1f to Color(0x000A0A0A))
                } else {
                    Brush.horizontalGradient(listOf(TvColors.background, TvColors.background))
                },
            )
            .onFocusChanged {
                expanded = it.hasFocus
                onFocusChange(it.hasFocus)
            }
            // Bấm ◀ từ nội dung: ô sáng luôn vào đúng mục đang mở (giống YouTube trên TV).
            .focusProperties {
                onEnter = {
                    if (requestedFocusDirection != FocusDirection.Enter) itemRequesters.getValue(selected).tryFocus()
                }
            }
            .focusGroup()
            .padding(vertical = TvDimens.safeVertical)
            .testTag("sidebar"),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier.padding(start = 20.dp, bottom = 24.dp).height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(TvColors.brand), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text("Tube Sạch", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Section.entries.forEach { s ->
            SidebarItem(
                section = s,
                selected = s == selected,
                expanded = expanded,
                onClick = { onSelect(s) },
                modifier = Modifier.focusRequester(itemRequesters.getValue(s)),
            )
        }
    }
}

@Composable
private fun SidebarItem(section: Section, selected: Boolean, expanded: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fg = when {
        focused -> TvColors.onFocus
        selected -> Color.White
        else -> TvColors.textSecondary
    }
    Row(
        modifier
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .clip(RoundedCornerShape(26.dp))
            .background(
                when {
                    focused -> TvColors.focus
                    selected && !expanded -> Color(0x33FFFFFF)
                    else -> Color.Transparent
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("nav:${section.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon(), section.label, tint = fg, modifier = Modifier.size(24.dp))
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            Text(
                section.label,
                color = fg,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}
