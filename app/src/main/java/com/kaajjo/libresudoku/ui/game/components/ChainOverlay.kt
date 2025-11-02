package com.kaajjo.libresudoku.ui.game.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.Chain
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.ChainEdgeType
import com.kaajjo.libresudoku.ui.components.board.getNoteColumnNumber
import com.kaajjo.libresudoku.ui.components.board.getNoteRowNumber
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 绘制 X-Chain 的可视化覆盖层
 * 
 * @param chain 要绘制的链
 * @param boardSize 数独盘的大小（9x9, 12x12 等）
 * @param cellSize 单元格的大小（像素）
 * @param cellSizeDivWidth 单元格内笔记格的宽度
 * @param notePaint 用于测量文本的 Paint 对象
 * @param killerSumBounds killer cage 的边界矩形
 */
@Composable
fun ChainOverlay(
    chain: Chain?,
    boardSize: Int,
    cellSize: Float,
    cellSizeDivWidth: Float,
    notePaint: Paint,
    killerSumBounds: android.graphics.Rect,
    modifier: Modifier = Modifier
) {
    if (chain == null) return
    
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier.fillMaxSize()) {
        // 计算笔记位置的辅助函数（与 BoardDrawUtil.drawNotes 中完全一致）
        fun getNotePosition(row: Int, col: Int, value: Int): Offset {
            val noteCol = getNoteColumnNumber(value, boardSize)
            val noteRow = getNoteRowNumber(value, boardSize)
            
            val noteBounds = android.graphics.Rect()
            notePaint.getTextBounds("1", 0, 1, noteBounds)
            val cellDivHeight = (cellSize - killerSumBounds.height() * 1.5f) / floor(sqrt(boardSize.toFloat()))
            
            val textToDraw = value.toString(16).uppercase()
            val noteTextMeasure = notePaint.measureText(textToDraw)
            
            // 与 BoardDrawUtil 中完全一致的 horizontalPadding 计算
            val horizontalPadding =
                if (noteRow == 0) noteTextMeasure / 3f
                else if (noteRow == 1) 0f
                else if (noteRow == 2 && value > 9) 0f
                else -(noteTextMeasure / 3f)
            
            // 与 BoardDrawUtil 中完全一致的位置计算
            val x = col * cellSize + cellSizeDivWidth / 2f + (cellSizeDivWidth * noteRow) + horizontalPadding
            val y = row * cellSize + noteBounds.height() * 1.5f + killerSumBounds.height() + (cellDivHeight * noteCol) - (noteBounds.height() * 0.5f)
            
            return Offset(x, y)
        }
        
        // 计算圆圈半径（与 BoardDrawUtil 中完全一致）
        val cellDivHeight = (cellSize - killerSumBounds.height() * 1.5f) / floor(sqrt(boardSize.toFloat()))
        val radius = minOf(cellSizeDivWidth, cellDivHeight) * 0.55f
        
        // 绘制所有边（连接线）
        chain.edges.forEach { edge ->
            val fromPos = getNotePosition(edge.from.cell.row, edge.from.cell.col, edge.from.value)
            val toPos = getNotePosition(edge.to.cell.row, edge.to.cell.col, edge.to.value)
            
            val isSameCell = edge.from.cell.row == edge.to.cell.row && 
                             edge.from.cell.col == edge.to.cell.col
            
            // 确定线条样式 - 更细的线条
            val strokeWidth = 1.5.dp.toPx()
            val pathEffect = if (edge.type == ChainEdgeType.WEAK) {
                // 虚线
                PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            } else {
                // 实线
                null
            }
            
            if (isSameCell) {
                // 同一个单元格内的两个节点，使用弧线连接
                // 计算两个圆心的连接向量
                val dx = toPos.x - fromPos.x
                val dy = toPos.y - fromPos.y
                val distance = sqrt(dx * dx + dy * dy)
                
                // if (distance <= 2 * radius) continue
                // 计算单位向量和垂直单位向量
                val unitDx = dx / distance
                val unitDy = dy / distance
                val perpDx = -unitDy  // 垂直向量（逆时针旋转90度）
                val perpDy = unitDx
                
                // 计算控制点（弧线向外弯曲）
                val midX = (fromPos.x + toPos.x) / 2
                val midY = (fromPos.y + toPos.y) / 2
                val cellCenterX = edge.from.cell.col * cellSize + cellSize / 2f
                val cellCenterY = edge.from.cell.row * cellSize + cellSize / 2f
                
                // 计算中点到单元格中心的方向
                val toCenterDx = midX - cellCenterX
                val toCenterDy = midY - cellCenterY
                val toCenterLen = sqrt(toCenterDx * toCenterDx + toCenterDy * toCenterDy)
                
                // 决定弧线弯曲的方向（远离单元格中心）
                // 通过点积判断哪个垂直方向更远离中心
                val offset = cellSize * 0.35f
                val dotProduct = if (toCenterLen > 0) {
                    (perpDx * toCenterDx + perpDy * toCenterDy) / toCenterLen
                } else {
                    1f
                }
                
                // 选择远离中心的垂直方向
                val finalPerpDx = if (dotProduct > 0) perpDx else -perpDx
                val finalPerpDy = if (dotProduct > 0) perpDy else -perpDy
                
                // 计算控制点
                val controlX = midX + finalPerpDx * offset
                val controlY = midY + finalPerpDy * offset
                
                // 计算两个圆的公切线的切点
                // 切点位于圆心沿垂直方向偏移 radius 的位置
                val adjustedFromX = fromPos.x + finalPerpDx * radius
                val adjustedFromY = fromPos.y + finalPerpDy * radius
                val adjustedToX = toPos.x + finalPerpDx * radius
                val adjustedToY = toPos.y + finalPerpDy * radius
                
                val path = Path().apply {
                    moveTo(adjustedFromX, adjustedFromY)
                    quadraticBezierTo(
                        controlX, controlY,
                        adjustedToX, adjustedToY
                    )
                }
                
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = pathEffect
                    )
                )
            } else {
                // 不同单元格的节点，使用直线连接
                // 计算方向向量并归一化
                val dx = toPos.x - fromPos.x
                val dy = toPos.y - fromPos.y
                val distance = sqrt(dx * dx + dy * dy)
                
                if (distance > 2 * radius) {
                    // 只有当距离大于两倍半径时才绘制线条
                    val unitDx = dx / distance
                    val unitDy = dy / distance
                    
                    // 调整起点和终点，从圆圈边缘开始和结束
                    val adjustedFromX = fromPos.x + unitDx * radius
                    val adjustedFromY = fromPos.y + unitDy * radius
                    val adjustedToX = toPos.x - unitDx * radius
                    val adjustedToY = toPos.y - unitDy * radius
                    
                    drawLine(
                        color = primaryColor,
                        start = Offset(adjustedFromX, adjustedFromY),
                        end = Offset(adjustedToX, adjustedToY),
                        strokeWidth = strokeWidth,
                        pathEffect = pathEffect
                    )
                }
            }
        }
        
        // 绘制所有节点（圆圈）
        chain.nodes.forEach { node ->
            val nodePos = getNotePosition(node.cell.row, node.cell.col, node.value)
            
            // 绘制圆圈 - 更细的圆圈边框
            drawCircle(
                color = primaryColor,
                radius = radius,
                center = nodePos,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

