package com.kaajjo.libresudoku.core.qqwing.advanced_hint

import com.kaajjo.libresudoku.core.Cell

/**
 * 链节点：表示一个单元格中的特定候选数
 * @param group 节点所属的分组（可选），用于在 Sue de Coq 等技巧中使用不同颜色显示不同组
 */
data class ChainNode(
    val cell: Cell,
    val value: Int,
    val group: Int = 0  // 默认为 0，表示不分组或主要组
)

/**
 * 链边类型
 */
enum class ChainEdgeType {
    STRONG,  // 强链，使用实线连接
    WEAK     // 弱链，使用虚线连接
}

/**
 * 链边：连接两个节点
 */
data class ChainEdge(
    val from: ChainNode,
    val to: ChainNode,
    val type: ChainEdgeType
)

/**
 * 完整的链：包含所有节点和边
 */
data class Chain(
    val nodes: List<ChainNode>,
    val edges: List<ChainEdge>
)

