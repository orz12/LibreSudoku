package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 缩放重置指示器 Modifier
 * 当缩放从大于1.0缩小回1.0（原始大小）时，显示发光动画提示用户已归位
 * 
 * @param scale 当前缩放比例
 * @param color 发光颜色，默认使用主题色
 * @param durationMillis 发光动画持续时间（毫秒）
 * @param glowRadius 发光半径（dp）
 */
@Composable
fun Modifier.scaleResetIndicator(
    scale: Float,
    color: Color? = null,
    durationMillis: Int = 600,
    glowRadius: Float = 60f
): Modifier {
    val glowColor = color ?: MaterialTheme.colorScheme.inversePrimary
    
    var showGlow by remember { mutableStateOf(false) }
    var wasZoomed by remember { mutableStateOf(false) }
    var triggerCount by remember { mutableStateOf(0) }
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (showGlow) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis),
        label = "glowAlpha"
    )
    
    // 监听缩放变化，检测从放大状态缩小回原大小
    LaunchedEffect(scale) {
        when {
            scale > 1f -> {
                // 记录曾经放大过
                wasZoomed = true
            }
            scale <= 1f && wasZoomed -> {
                // 从放大状态缩回原大小，触发发光
                wasZoomed = false
                triggerCount++
            }
        }
    }
    
    // 执行发光动画，使用triggerCount作为key确保每次触发都是新的协程
    LaunchedEffect(triggerCount) {
        if (triggerCount > 0) {
            showGlow = true
            // 延迟后开始淡出
            kotlinx.coroutines.delay(500)
            showGlow = false
        }
    }
    
    return this.then(
        Modifier.drawBehind {
            // 绘制外发光效果
            if (glowAlpha > 0) {
                val glowSize = glowRadius.dp.toPx()
                val colors = listOf(
                    glowColor.copy(alpha = 0.60f * glowAlpha),
                    glowColor.copy(alpha = 0.30f * glowAlpha),
                    glowColor.copy(alpha = 0.10f * glowAlpha),
                    Color.Transparent
                )
                
                // 绘制多层渐变来增强发光效果
                for (i in 0..2) {
                    val layerAlpha = (1f - i * 0.3f) * glowAlpha
                    val layerSize = glowSize * (1f + i * 0.5f)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = colors.map { it.copy(alpha = it.alpha * layerAlpha) },
                            center = Offset(size.width / 2, size.height / 2),
                            radius = (size.width.coerceAtLeast(size.height) / 2 + layerSize)
                        ),
                        topLeft = Offset(-layerSize, -layerSize),
                        size = Size(size.width + layerSize * 2, size.height + layerSize * 2)
                    )
                }
            }
        }
    )
}

