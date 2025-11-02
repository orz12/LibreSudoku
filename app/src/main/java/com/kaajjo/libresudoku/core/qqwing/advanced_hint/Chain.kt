package com.kaajjo.libresudoku.core.qqwing.advanced_hint

import com.kaajjo.libresudoku.core.Cell

/**
 * 链节点：表示一个单元格中的特定候选数
 */
data class ChainNode(
    val cell: Cell,
    val value: Int
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

