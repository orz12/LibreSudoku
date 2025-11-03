package com.kaajjo.libresudoku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode

/**
 * 三段式开关组件
 * 
 * 用于在三种状态之间切换：DISABLED（关闭）、ENABLED（启用）、AUTO（自动）
 * 
 * 图标说明：
 * - 🚫 Block: 关闭（DISABLED）
 * - ✨ AutoAwesome: 启用（ENABLED）- 提供提示
 * - ⏩ FastForward: 自动（AUTO）- 自动执行
 * 
 * 交互方式：
 * - 直接点击芯片：精确选择某个状态
 * - 点击外层 Row（标题/空白区域）：由外层控制（通常是循环切换）
 * 
 * @param mode 当前模式
 * @param onModeChange 模式变化回调
 * @param modifier 修饰符
 */
@Composable
fun TriStateSwitch(
    mode: HintMode,
    onModeChange: (HintMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val iconSize = 16.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactChip(
            selected = mode == HintMode.DISABLED,
            onClick = { onModeChange(HintMode.DISABLED) },
            icon = Icons.Rounded.Block,
            contentDescription = "关闭",
            iconSize = iconSize
        )
        
        CompactChip(
            selected = mode == HintMode.ENABLED,
            onClick = { onModeChange(HintMode.ENABLED) },
            icon = Icons.Rounded.AutoAwesome,
            contentDescription = "启用",
            iconSize = iconSize
        )

        CompactChip(
            selected = mode == HintMode.AUTO,
            onClick = { onModeChange(HintMode.AUTO) },
            icon = Icons.Rounded.FastForward,
            contentDescription = "自动",
            iconSize = iconSize
        )
    }
}

/**
 * 紧凑型芯片组件
 * 
 * 完全自定义实现，最小化 padding
 */
@Composable
private fun CompactChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) 
            MaterialTheme.colorScheme.secondaryContainer
        else 
            Color.Transparent, // 未选中时透明背景
        animationSpec = tween(200),
        label = "containerColor"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.onSecondaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant, // 未选中时使用次要颜色
        animationSpec = tween(200),
        label = "contentColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (selected)
            Color.Transparent
        else
            MaterialTheme.colorScheme.outline, // 未选中时显示边框
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    Surface(
        modifier = modifier
            .height(32.dp)
            .clip(MaterialTheme.shapes.small) // 先裁剪，防止涟漪超出圆角
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.small
            ),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

