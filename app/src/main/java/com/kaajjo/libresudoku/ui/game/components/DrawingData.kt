package com.kaajjo.libresudoku.ui.game.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 表示一条绘画路径
 * @param points 相对坐标点列表（0-1范围），相对于画布的宽高比例
 * @param color 路径颜色
 * @param strokeWidth 线条宽度
 */
data class DrawingPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float = 5f
)

/**
 * 绘画状态管理
 */
data class DrawingState(
    val paths: List<DrawingPath> = emptyList(),
    val currentPath: DrawingPath? = null,
    val currentColor: Color = Color.Red,
    val isDrawingMode: Boolean = false
) {
    fun addPoint(point: Offset): DrawingState {
        val newPoints = (currentPath?.points ?: emptyList()) + point
        return copy(
            currentPath = DrawingPath(
                points = newPoints,
                color = currentColor
            )
        )
    }

    fun finishPath(): DrawingState {
        return if (currentPath != null && currentPath.points.size > 1) {
            copy(
                paths = paths + currentPath,
                currentPath = null
            )
        } else {
            copy(currentPath = null)
        }
    }

    fun undo(): DrawingState {
        return if (paths.isNotEmpty()) {
            copy(paths = paths.dropLast(1))
        } else {
            this
        }
    }

    fun clear(): DrawingState {
        return copy(paths = emptyList(), currentPath = null)
    }

    fun changeColor(color: Color): DrawingState {
        return copy(currentColor = color)
    }

    fun toggleDrawingMode(): DrawingState {
        return copy(isDrawingMode = !isDrawingMode)
    }
    
    companion object {
        val Saver: Saver<DrawingState, Any> = listSaver(
            save = { state ->
                listOf(
                    // 保存已完成的路径
                    state.paths.flatMap { path ->
                        listOf(
                            path.color.toArgb(),
                            path.strokeWidth,
                            path.points.size
                        ) + path.points.flatMap { listOf(it.x, it.y) }
                    },
                    // 保存当前路径
                    state.currentPath?.let { path ->
                        listOf(
                            path.color.toArgb(),
                            path.strokeWidth,
                            path.points.size
                        ) + path.points.flatMap { listOf(it.x, it.y) }
                    } ?: emptyList<Any>(),
                    // 保存当前颜色
                    state.currentColor.toArgb(),
                    // 保存绘画模式状态
                    state.isDrawingMode
                )
            },
            restore = { list ->
                val pathsData = list[0] as List<*>
                val currentPathData = list[1] as List<*>
                val currentColorArgb = list[2] as Int
                val isDrawingMode = list[3] as Boolean
                
                // 恢复已完成的路径
                val paths = mutableListOf<DrawingPath>()
                var i = 0
                while (i < pathsData.size) {
                    val colorArgb = pathsData[i] as Int
                    val strokeWidth = pathsData[i + 1] as Float
                    val pointsCount = pathsData[i + 2] as Int
                    i += 3
                    
                    val points = mutableListOf<Offset>()
                    repeat(pointsCount) {
                        val x = pathsData[i] as Float
                        val y = pathsData[i + 1] as Float
                        points.add(Offset(x, y))
                        i += 2
                    }
                    
                    paths.add(DrawingPath(points, Color(colorArgb), strokeWidth))
                }
                
                // 恢复当前路径
                val currentPath = if (currentPathData.isNotEmpty()) {
                    var j = 0
                    val colorArgb = currentPathData[j] as Int
                    val strokeWidth = currentPathData[j + 1] as Float
                    val pointsCount = currentPathData[j + 2] as Int
                    j += 3
                    
                    val points = mutableListOf<Offset>()
                    repeat(pointsCount) {
                        val x = currentPathData[j] as Float
                        val y = currentPathData[j + 1] as Float
                        points.add(Offset(x, y))
                        j += 2
                    }
                    
                    DrawingPath(points, Color(colorArgb), strokeWidth)
                } else null
                
                DrawingState(
                    paths = paths,
                    currentPath = currentPath,
                    currentColor = Color(currentColorArgb),
                    isDrawingMode = isDrawingMode
                )
            }
        )
    }
}

