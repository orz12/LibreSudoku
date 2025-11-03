package com.kaajjo.libresudoku.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode

/**
 * 三段式开关组件
 * 
 * 用于在三种状态之间切换：DISABLED（关闭）、ENABLED（启用）、AUTO（自动）
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
    val disabledColor = MaterialTheme.colorScheme.surfaceVariant
    val enabledColor = MaterialTheme.colorScheme.primary
    val autoColor = MaterialTheme.colorScheme.tertiary
    
    val backgroundColor by animateColorAsState(
        targetValue = when (mode) {
            HintMode.DISABLED -> disabledColor
            HintMode.ENABLED -> enabledColor
            HintMode.AUTO -> autoColor
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor"
    )
    
    // 0.0 = left (DISABLED), 0.5 = center (ENABLED), 1.0 = right (AUTO)
    val thumbPosition by animateFloatAsState(
        targetValue = when (mode) {
            HintMode.DISABLED -> 0.0f
            HintMode.ENABLED -> 0.5f
            HintMode.AUTO -> 1.0f
        },
        animationSpec = tween(durationMillis = 300),
        label = "thumbPosition"
    )
    
    val thumbColor = MaterialTheme.colorScheme.surface
    
    Canvas(
        modifier = modifier
            .size(width = 72.dp, height = 32.dp)
            .pointerInput(mode) {
                detectTapGestures { offset ->
                    // 根据点击位置确定新状态
                    val newMode = when {
                        offset.x < size.width / 3 -> HintMode.DISABLED
                        offset.x < size.width * 2 / 3 -> HintMode.ENABLED
                        else -> HintMode.AUTO
                    }
                    if (newMode != mode) {
                        onModeChange(newMode)
                    }
                }
            }
    ) {
        // 绘制背景轨道
        drawRoundRect(
            color = backgroundColor,
            cornerRadius = CornerRadius(size.height / 2, size.height / 2),
            size = size
        )
        
        // 绘制滑块
        val thumbRadius = size.height * 0.8f
        val thumbCenterX = thumbRadius / 2 + (size.width - thumbRadius) * thumbPosition
        val thumbCenterY = size.height / 2
        
        drawCircle(
            color = thumbColor,
            radius = thumbRadius / 2,
            center = Offset(thumbCenterX, thumbCenterY)
        )
        
        // 绘制三个小标记点（可选，增强视觉识别）
        val markerRadius = 2.dp.toPx()
        val markerY = size.height * 0.15f
        
        // 左侧标记（关闭）
        drawCircle(
            color = if (mode == HintMode.DISABLED) thumbColor else backgroundColor.copy(alpha = 0.3f),
            radius = markerRadius,
            center = Offset(size.width * 0.17f, markerY)
        )
        
        // 中间标记（启用）
        drawCircle(
            color = if (mode == HintMode.ENABLED) thumbColor else backgroundColor.copy(alpha = 0.3f),
            radius = markerRadius,
            center = Offset(size.width * 0.5f, markerY)
        )
        
        // 右侧标记（自动）
        drawCircle(
            color = if (mode == HintMode.AUTO) thumbColor else backgroundColor.copy(alpha = 0.3f),
            radius = markerRadius,
            center = Offset(size.width * 0.83f, markerY)
        )
    }
}

