package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 绘画工具栏
 */
@Composable
fun DrawingToolbar(
    modifier: Modifier = Modifier,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    canUndo: Boolean = true
) {
    // Material3 主题颜色
    val colorPalette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.onErrorContainer,
        MaterialTheme.colorScheme.error,
    )

    Row(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 颜色选择器
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colorPalette.forEach { color ->
                ColorButton(
                    modifier = Modifier.weight(1f),
                    color = color,
                    selected = color == selectedColor,
                    onClick = { onColorSelected(color) }
                )
            }
        }

        // 撤销按钮
        IconButton(
            onClick = onUndo,
            enabled = canUndo
        ) {
            Icon(
                imageVector = Icons.Rounded.Undo,
                contentDescription = "撤销",
                tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }

        // 清空按钮
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Rounded.Clear,
                contentDescription = "清空"
            )
        }
    }
}

/**
 * 颜色选择按钮
 */
@Composable
private fun ColorButton(
    modifier: Modifier = Modifier,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    )
}

