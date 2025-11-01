package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
                    Modifier.pointerInput(canvasSize) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                // 将绝对坐标转换为相对坐标（0-1范围）
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    val relativeOffset = Offset(
                                        offset.x / canvasSize.width,
                                        offset.y / canvasSize.height
                                    )
                                    onAddPoint(relativeOffset)
                                }
                            },
                            onDrag = { change, _ ->
                                // 将绝对坐标转换为相对坐标（0-1范围）
                                if (canvasSize.width > 0 && canvasSize.height > 0) {
                                    val relativeOffset = Offset(
                                        change.position.x / canvasSize.width,
                                        change.position.y / canvasSize.height
                                    )
                                    onAddPoint(relativeOffset)
                                }
                            },
                            onDragEnd = {
                                onFinishPath()
                            }
                        )
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

