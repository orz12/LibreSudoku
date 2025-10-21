package com.kaajjo.libresudoku.core.qqwing.advanced_hint

import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note

/**
 * Data that [AdvancedHint] returns
 *
 * @property titleRes resource id for title
 * @property textResWithArg resource id and arguments for string resource
 * @property targetCells target cells, the result of hint
 * @property helpCells cells that help understand the hint
 * @property notesToAdd notes to add, the result of hint
 * @property notesToRemove notes to remove, the result of hint
 */
data class AdvancedHintData(
    val titleRes: Int,
    val textResWithArg: Pair<Int, List<String>>,
    val targetCells: List<Cell>,
    val helpCells: List<Cell>,
    val notesToAdd: List<Note>? = null,
    val notesToRemove: List<Note>? = null,
)
