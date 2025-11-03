package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * 绘画画布组件
 */
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    drawingState: DrawingState,
    onAddPoint: (Offset) -> Unit,
    onFinishPath: () -> Unit,
    onZoomChange: (scale: Float, offsetX: Float, offsetY: Float) -> Unit = { _, _, _ -> },
    enabled: Boolean = true,
    alpha: Float = 1f
) {
    // 记录画布尺寸
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    Box(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        // 缩放和平移状态（局部状态，只在手势过程中使用）
                        var scale = 1f
                        var offsetX = 0f
                        var offsetY = 0f
                        
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var pointer = down
                            var isDrawing = false
                            var isMultiTouch = false
                            
                            do {
                                val event = awaitPointerEvent()
                                val pointerCount = event.changes.size
                                
                                if (pointerCount > 1) {
                                    // 双指或多指操作 - 缩放和平移
                                    isMultiTouch = true
                                    if (isDrawing) {
                                        // 如果正在绘画，结束绘画
                                        onFinishPath()
                                        isDrawing = false
                                    }
                                    
                                    val zoomChange = event.calculateZoom()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val previousCentroid = event.calculateCentroid(useCurrent = false)
                                    
                                    // 更新缩放，限制最小缩放为1.0
                                    val newScale = (scale * zoomChange).coerceAtLeast(1f)
                                    
                                    // 计算平移
                                    val pan = centroid - previousCentroid
                                    
                                    // 应用缩放和平移
                                    scale = newScale
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    
                                    // 通知外部
                                    onZoomChange(scale, offsetX, offsetY)
                                    
                                    event.changes.forEach { it.consume() }
                                } else if (!isMultiTouch && pointerCount == 1) {
                                    // 单指操作 - 绘画
                                    val change = event.changes[0]
                                    
                                    if (!isDrawing) {
                                        // 开始绘画
                                        isDrawing = true
                                        val position = change.position
                                        
                                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                                            val relativeOffset = Offset(
                                                position.x / canvasSize.width,
                                                position.y / canvasSize.height
                                            )
                                            onAddPoint(relativeOffset)
                                        }
                                    } else if (change.positionChanged()) {
                                        // 继续绘画
                                        val position = change.position
                                        
                                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                                            val relativeOffset = Offset(
                                                position.x / canvasSize.width,
                                                position.y / canvasSize.height
                                            )
                                            onAddPoint(relativeOffset)
                                        }
                                    }
                                    
                                    change.consume()
                                    pointer = change
                                }
                            } while (event.changes.any { it.pressed })
                            
                            // 手势结束
                            if (isDrawing) {
                                onFinishPath()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // 绘制已完成的路径
            drawingState.paths.forEach { drawingPath ->
                if (drawingPath.points.isNotEmpty()) {
                    val path = Path().apply {
                        // 将相对坐标转换为当前画布的绝对坐标
                        val firstPoint = drawingPath.points.first()
                        moveTo(firstPoint.x * canvasWidth, firstPoint.y * canvasHeight)
                        drawingPath.points.drop(1).forEach { point ->
                            lineTo(point.x * canvasWidth, point.y * canvasHeight)
                        }
                    }
                    drawPath(
                        path = path,
                        color = drawingPath.color,
                        alpha = alpha,
                        style = Stroke(
                            width = drawingPath.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            // 绘制当前正在绘制的路径
            drawingState.currentPath?.let { currentPath ->
                if (currentPath.points.isNotEmpty()) {
                    val path = Path().apply {
                        // 将相对坐标转换为当前画布的绝对坐标
                        val firstPoint = currentPath.points.first()
                        moveTo(firstPoint.x * canvasWidth, firstPoint.y * canvasHeight)
                        currentPath.points.drop(1).forEach { point ->
                            lineTo(point.x * canvasWidth, point.y * canvasHeight)
                        }
                    }
                    drawPath(
                        path = path,
                        color = currentPath.color,
                        alpha = alpha,
                        style = Stroke(
                            width = currentPath.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

