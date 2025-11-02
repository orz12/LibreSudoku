package com.kaajjo.libresudoku.core.qqwing.advanced_hint

import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.utils.SudokuUtils


/**
 * Implemented:
 * Naked single
 * Hidden single
 * Full House
 * TODO:
 * Hidden subsets (pair, triple, quadruple)
 * Naked subsets (pair, triple, quadruple)
 * Locked candidates (all types)
 * Wings (X, XY, XYZ)
 * Swordfish, Jellyfish
 * Chains and loops
 */

/**
 * **Very experimental** provides a hint
 *
 * @property type type of the game
 * @property board current sudoku board
 * @property solvedBoard solved sudoku board
 * @property notes notes for sudoku board
 * @property settings settings for an advanced hint
 */
class AdvancedHint(
    val type: GameType,
    private val board: List<List<Cell>>,
    private val solvedBoard: List<List<Cell>>,
    private var notes: List<Note> = emptyList(),
    private var settings: AdvancedHintSettings = AdvancedHintSettings()
) {
//    init {
//        if (notes.isEmpty()) {
//            notes = SudokuUtils().computeNotes(board, type)
//        }
//    }

    private val rows = getRows()
    private val columns = getColumns()
    private val boxIndex: Array<IntArray> by lazy {
        Array(type.size) { row ->
            IntArray(type.size) { col ->
                val sectionRow = row / type.sectionHeight
                val sectionColumn = col / type.sectionWidth
                val sectorsPerRow = type.size / type.sectionWidth
                sectionRow * sectorsPerRow + sectionColumn
            }
        }
    }
    private fun getBoxIndex(row: Int, col: Int): Int = boxIndex[row][col]
    private val boxes = getBoxes()
    private val cellNotesCache: Map<Pair<Int, Int>, Set<Int>> by lazy {
        notes.groupBy { Pair(it.row, it.col) }
            .mapValues { (_, notes) -> notes.map { it.value }.toSet() }
    }
    private val visibleCellsCache: Map<Pair<Int, Int>, List<Cell>> by lazy {
        val cache = mutableMapOf<Pair<Int, Int>, List<Cell>>()
        for (r in 0 until type.size) {
            for (c in 0 until type.size) {
                val rowCells = rows[r]
                val colCells = columns[c]
                val boxCells = boxes[getBoxIndex(r, c)]
                val set = LinkedHashSet<Cell>(rowCells.size + colCells.size + boxCells.size)
                set.addAll(rowCells)
                set.addAll(colCells)
                set.addAll(boxCells)
                val result = set.filterNot { it.row == r && it.col == c }
                cache[Pair(r, c)] = result
            }
        }
        cache
    }
    private val cellNoteIndex: Map<Triple<Int, Int, Int>, Note> by lazy {
        notes.associateBy { Triple(it.row, it.col, it.value) }
    }

    fun getEasiestHint(): AdvancedHintData? {
        val hint: AdvancedHintData? = null
        if (settings.checkWrongValue) checkForWrongValue()?.let { return it }
        if (settings.fullHouse) checkForFullHouse()?.let { return it }
        // 为了保证候选数准确，在此强制使用全盘计算候选数
        val tempNotes = if (checkAllNotes()) notes else SudokuUtils().computeNotes(board, type)
        if (settings.nakedSingle) checkForNakedSingle(tempNotes)?.let { return it }
        if (settings.hiddenSingle) checkForHiddenSingle(tempNotes)?.let { return it }

        if (settings.checkMissingOrWrongNote) {
            checkForMissingOrWrongNote()?.let { return it }
        } else {
            // 如果未启用候选数检测且没有全填候选数，则后续检测的正确性无法保证，提前退出
            if (!checkAllNotes()) return null
        }
        if (settings.lockedCandidates) checkForLockedCandidates()?.let { return it }
        if (settings.nakedSubsets) checkForNakedSubsets()?.let { return it }
        if (settings.hiddenSubsets) checkForHiddenSubsets()?.let { return it }
        if (settings.sueDeCoq) checkForSueDeCoq()?.let { return it }
        if (settings.xWings) checkForXWing()?.let { return it }
        if (settings.xyWings) checkForXYWing()?.let { return it }
        if (settings.xyzWings) checkForXYZWing()?.let { return it }
        if (settings.wWings) checkForWWing()?.let { return it }
        if (settings.fishPatterns) checkForFishPatterns()?.let { return it }
        if (settings.finnedFishVariants) checkForFinnedFishVariants()?.let { return it }
        if (settings.xChain) checkForXChain()?.let {return it}
        if (settings.xyChain) checkForXYChain()?.let { return it }
        return hint
    }

    private fun checkForWrongValue(): AdvancedHintData? {
        for (i in board.indices) {
            for (j in board.indices) {
                if (board[i][j].value != 0 && board[i][j].value != solvedBoard[i][j].value) {
                    return AdvancedHintData(
                        titleRes = R.string.hint_wrong_value_title,
                        textResWithArg = Pair(
                            R.string.hint_wron_value_detail,
                            listOf(
                                board[i][j].value.toString(),
                                cellStringFormat(board[i][j])
                            )
                        ),
                        targetCells = listOf(board[i][j]),
                        helpCells = emptyList()
                    )
                }
            }
        }
        return null
    }

    private fun checkAllNotes(): Boolean {
        if (notes.isEmpty()) return false
        return board.flatten().all{ cell ->
            cell.value != 0 || solvedBoard[cell.row][cell.col].value in getCellNotes(cell)
        }
    }

    private fun checkForMissingOrWrongNote(): AdvancedHintData? {
        val fullNotes = SudokuUtils().computeNotes(board, type)
        if (notes.isEmpty()) {
            //val randomEmptyCell = board.flatten().filter { it.value == 0 }.randomOrNull() ?: return null
//            val cellNotes = fullNotes.filter { it.row == randomEmptyCell.row && it.col == randomEmptyCell.col }
            return AdvancedHintData(
                titleRes = R.string.hint_missing_note_title,
                textResWithArg = Pair(
                    R.string.hint_missing_all_notes_detail,
                    emptyList()
                ),
                targetCells = board.flatten().filter { it.value == 0 },
                helpCells = emptyList(),
                notesToAdd = fullNotes
            )
        }
        val currentCellsNotes = notes.groupBy { Pair(it.row, it.col) }
        val fullCellsNotes = fullNotes.groupBy { Pair(it.row, it.col) }

        // 如果存在不处于fullCellsNotes中的候选数，则认为是错误的候选数
        val cellNotesToRemove = notes.filter { note ->
            val cellKey = Pair(note.row, note.col)
            val fullNotesForCell = fullCellsNotes[cellKey]
            return@filter fullNotesForCell?.none { it.value == note.value } ?: true
        }
        if (cellNotesToRemove.isNotEmpty()) {
            val targetCells = cellNotesToRemove.map { board[it.row][it.col] }.distinct()
            return AdvancedHintData(
                titleRes = R.string.hint_wrong_note_title,
                textResWithArg = Pair(
                    R.string.hint_wrong_note_detail,
                    listOf(
                        cellNotesToRemove.joinToString(",") { it.value.toString() },
                        targetCells.joinToString(",") { cellStringFormat(it) }
                    )
                ),
                targetCells = targetCells,
                helpCells = emptyList(),
                notesToRemove = cellNotesToRemove
            )
        }

        val cellsWithMissingNotes = fullCellsNotes.filter { (key, value) ->
            // if (!currentCellsNotes.containsKey(key)) return@filter true
            val currentNotesForCell = currentCellsNotes[key]
            val solvedValue = solvedBoard[key.first][key.second].value
            // if any note is not the solved value, we have missing notes
            return@filter currentNotesForCell?.all { it.value != solvedValue } ?: true
        }
        if (cellsWithMissingNotes.isEmpty()) return null
        val (cellKey, cellNotes) = cellsWithMissingNotes.entries.random()
        val cell = board[cellKey.first][cellKey.second]
        val currentNotesForCell = currentCellsNotes[cellKey]
        val cellNotesToAdd = cellNotes.filter { note ->
            return@filter currentNotesForCell?.none { it.value == note.value } ?: true
        }
        return AdvancedHintData(
            titleRes = R.string.hint_missing_note_title,
            textResWithArg = Pair(
                R.string.hint_missing_note_detail,
                listOf(
                    cellNotesToAdd.joinToString(",") { it.value.toString() },
                    cellStringFormat(cell)
                )
            ),
            targetCells = listOf(cell),
            helpCells = emptyList(),
            notesToAdd = cellNotesToAdd
        )
    }

    private fun checkForNakedSingle(tempNotes:  List<Note>): AdvancedHintData? {
        if (tempNotes.isEmpty()) return null
        val singles = tempNotes.groupBy { Pair(it.row, it.col) }
            .filter { it.value.size == 1 }
            .map { it.value }
            .randomOrNull()

        return if (!singles.isNullOrEmpty()) {
            val nakedSingle = singles.first()
            val cell = solvedBoard[nakedSingle.row][nakedSingle.col]
            return AdvancedHintData(
                titleRes = R.string.hint_naked_single_title,
                textResWithArg = Pair(
                    R.string.hint_naked_single_detail,
                    listOf(
                        cellStringFormat(cell),
                        cell.value.toString()
                    )
                ),
                targetCells = listOf(cell),
                helpCells = emptyList()
            )
        } else {
            null
        }
    }
    
    private fun checkForFullHouse(): AdvancedHintData? {
        val entities = listOf(boxes, rows, columns)

        for (entity in entities) {
            val hint = checkEntityForFullHouse(entity)
            if (hint != null) {
                return hint
            }
        }

        return null
    }

    private fun checkEntityForFullHouse(entity: List<List<Cell>>): AdvancedHintData? {
        for (group in entity) {
            if (group.count { it.value != 0 } == type.size - 1) {
                val emptyCell = group.find { it.value == 0 }
                if (emptyCell != null) {
                    val solvedCell = solvedBoard[emptyCell.row][emptyCell.col]
                    return AdvancedHintData(
                        titleRes = R.string.hint_full_house_group_title,
                        textResWithArg = Pair(
                            R.string.hint_full_house_group_detail,
                            listOf(
                                cellStringFormat(emptyCell),
                                solvedCell.value.toString()
                            )
                        ),
                        targetCells = listOf(solvedBoard[emptyCell.row][emptyCell.col]),
                        helpCells = board.flatten().filter { group.contains(it) }.filter { it != emptyCell }
                    )
                }
            }
        }
        return null
    }

    private fun checkForHiddenSingle( tempNotes:List<Note>): AdvancedHintData? {
        var hiddenSingle: Note? = null
        var helpCells: List<Cell>? = null
        val singlesInRow = tempNotes.groupBy { Pair(it.row, it.value) }
            .filter { it.value.size == 1 }
            .map { it.value }
            .randomOrNull()?.first()
        if (singlesInRow != null) {
            hiddenSingle = singlesInRow
            helpCells = rows[singlesInRow.row]
        }
        val singlesInColumn = tempNotes.groupBy { Pair(it.col, it.value) }
            .filter { it.value.size == 1 }
            .map { it.value }
            .randomOrNull()?.first()
        if (singlesInColumn != null) {
            hiddenSingle = singlesInColumn
            helpCells = columns[singlesInColumn.col]
        }
        val singlesInBox = tempNotes.groupBy { Pair(getBoxIndex(it.row, it.col), it.value) }
            .filter { it.value.size == 1 }
            .map { it.value }
            .randomOrNull()?.first()
        if (singlesInBox != null) {
            hiddenSingle = singlesInBox
            helpCells = boxes[getBoxIndex(singlesInBox.row, singlesInBox.col)]
        }
        if (hiddenSingle == null) return null
        val cell = solvedBoard[hiddenSingle.row][hiddenSingle.col]
        return AdvancedHintData(
            titleRes = R.string.hint_hidden_single_title,
            textResWithArg = Pair(
                R.string.hint_hidden_single_detail,
                listOf(
                    cellStringFormat(cell),
                    cell.value.toString()
                )
            ),
            targetCells = listOf(cell),
            helpCells = helpCells!!.filter { it.col != cell.col || it.row != cell.row }
        )
    }

    private fun checkForLockedCandidates(): AdvancedHintData? {
        if (notes.isEmpty()) return null

        // 检查指向型锁定候选数 (Pointing)
        val pointingHint = checkPointingLockedCandidates()
        if (pointingHint != null) {
            return pointingHint
        }

        // 检查占用型锁定候选数 (Claiming)
        val claimingHint = checkClaimingLockedCandidates()
        if (claimingHint != null) {
            return claimingHint
        }

        return null
    }

    /**
     * 检查指向型锁定候选数:
     * 当某个数字在一个宫内只能出现在某一行或某一列时，
     * 可以排除该数字在该行/列其他宫内的位置
     */
    private fun checkPointingLockedCandidates(): AdvancedHintData? {
        val size = type.size
        val sectionWidth = type.sectionWidth
        val sectionHeight = type.sectionHeight

        // 遍历每个宫
        for (boxNum in boxes.indices) {
            val box = boxes[boxNum]

            // 遍历每个可能的数字
            for (num in 1..size) {
                // 找出该宫内所有包含此数字的候选数单元格
                val cellsWithNote = box.filter { cell ->
                    cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                }

                if (cellsWithNote.size >= 2) {
                    // 检查是否所有单元格都在同一行
                    val rowsToCheck = cellsWithNote.map { it.row }.toSet()
                    if (rowsToCheck.size == 1) {
                        val row = rowsToCheck.first()
                        // 找到此行中不在当前宫内但包含该数字候选的单元格
                        val cellsToRemoveNote = rows[row]
                            .filter { cell ->
                                getBoxIndex(cell.row, cell.col) != boxNum &&
                                cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                            }

                        if (cellsToRemoveNote.isNotEmpty()) {
                            return createLockedCandidatesHint(
                                number = num,
                                sourceCells = cellsWithNote,
                                targetCells = cellsToRemoveNote,
                                isPointing = true,
                                isRow = true
                            )
                        }
                    }

                    // 检查是否所有单元格都在同一列
                    val colsToCheck = cellsWithNote.map { it.col }.toSet()
                    if (colsToCheck.size == 1) {
                        val col = colsToCheck.first()
                        // 找到此列中不在当前宫内但包含该数字候选的单元格
                        val cellsToRemoveNote = columns[col]
                            .filter { cell ->
                                getBoxIndex(cell.row, cell.col) != boxNum &&
                                cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                            }

                        if (cellsToRemoveNote.isNotEmpty()) {
                            return createLockedCandidatesHint(
                                number = num,
                                sourceCells = cellsWithNote,
                                targetCells = cellsToRemoveNote,
                                isPointing = true,
                                isRow = false
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 检查占用型锁定候选数:
     * 当某个数字在一行或一列中只能出现在某个宫内时，
     * 可以排除该数字在该宫内其他行/列的位置
     */
    private fun checkClaimingLockedCandidates(): AdvancedHintData? {
        val size = type.size

        // 检查行中的占用型锁定候选数
        for (rowNum in rows.indices) {
            val row = rows[rowNum]

            // 遍历每个可能的数字
            for (num in 1..size) {
                // 找出该行中所有包含此数字的候选数单元格
                val cellsWithNote = row.filter { cell ->
                    cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                }

                if (cellsWithNote.size >= 2) {
                    // 检查是否所有单元格都在同一个宫内
                    val boxNums = cellsWithNote.map { getBoxIndex(it.row, it.col) }.toSet()
                    if (boxNums.size == 1) {
                        val boxNum = boxNums.first()
                        // 找到该宫内不在当前行但包含该数字候选的单元格
                        val cellsToRemoveNote = boxes[boxNum]
                            .filter { cell ->
                                cell.row != rowNum &&
                                cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                            }

                        if (cellsToRemoveNote.isNotEmpty()) {
                            return createLockedCandidatesHint(
                                number = num,
                                sourceCells = cellsWithNote,
                                targetCells = cellsToRemoveNote,
                                isPointing = false,
                                isRow = true
                            )
                        }
                    }
                }
            }
        }

        // 检查列中的占用型锁定候选数
        for (colNum in columns.indices) {
            val col = columns[colNum]

            // 遍历每个可能的数字
            for (num in 1..size) {
                // 找出该列中所有包含此数字的候选数单元格
                val cellsWithNote = col.filter { cell ->
                    cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                }

                if (cellsWithNote.size >= 2) {
                    // 检查是否所有单元格都在同一个宫内
                    val boxNums = cellsWithNote.map { getBoxIndex(it.row, it.col) }.toSet()
                    if (boxNums.size == 1) {
                        val boxNum = boxNums.first()
                        // 找到该宫内不在当前列但包含该数字候选的单元格
                        val cellsToRemoveNote = boxes[boxNum]
                            .filter { cell ->
                                cell.col != colNum &&
                                cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true
                            }

                        if (cellsToRemoveNote.isNotEmpty()) {
                            return createLockedCandidatesHint(
                                number = num,
                                sourceCells = cellsWithNote,
                                targetCells = cellsToRemoveNote,
                                isPointing = false,
                                isRow = false
                            )
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * 创建锁定候选数提示数据对象
     */
    private fun createLockedCandidatesHint(
        number: Int,
        sourceCells: List<Cell>,
        targetCells: List<Cell>,
        isPointing: Boolean,
        isRow: Boolean
    ): AdvancedHintData {
        val titleRes = R.string.hint_locked_candidates_title

        val sourceGroup = if (isRow) sourceCells.first().row + 1 else sourceCells.first().col + 1
        val boxNumber = getBoxIndex(sourceCells.first().row, sourceCells.first().col) + 1

        val detailArgs = listOf(
            number.toString(),
            if (isPointing) boxNumber.toString() else sourceGroup.toString(),
            if (isPointing) sourceGroup.toString() else boxNumber.toString()
        )
        val colHint = if (isPointing) R.string.hint_locked_candidates_pointing_col_detail
            else R.string.hint_locked_candidates_claiming_col_detail
        val rowHint = if (isPointing) R.string.hint_locked_candidates_pointing_row_detail
            else R.string.hint_locked_candidates_claiming_row_detail

        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(
                if (isRow) rowHint else colHint,
                detailArgs
            ),
            targetCells = targetCells,
            helpCells = sourceCells,
            notesToRemove = targetCells.flatMap { cell ->
                notes.filter { note ->
                    note.row == cell.row && note.col == cell.col && note.value == number
                }
            }
        )
    }

    /**
     * 检查所有类型的显性数组（数对、三数组、四数组）
     */
    private fun checkForNakedSubsets(): AdvancedHintData? {
        if (notes.isEmpty()) return null

        // 检查行、列、宫中的显性数组
        val entities = listOf(
            rows.map { it to "row" } ,
            columns.map { it to "column" },
            boxes.map { it to "box" }
        ).flatten()

        for ((entity, entityType) in entities) {
            // 检查显性数对 (2个数字)
            val nakedPair = checkNakedSubsets(entity, 2)
            if (nakedPair != null) {
                return createSubsetsHint(nakedPair, entityType, isNaked = true)
            }

            // 检查显性三数组 (3个数字)
            val nakedTriple = checkNakedSubsets(entity, 3)
            if (nakedTriple != null) {
                return createSubsetsHint(nakedTriple, entityType, isNaked = true)
            }

            // 检查显性四数组 (4个数字)
            val nakedQuadruple = checkNakedSubsets(entity, 4)
            if (nakedQuadruple != null) {
                return createSubsetsHint(nakedQuadruple, entityType, isNaked = true)
            }
        }

        return null
    }

    /**
     * 检查特定大小的显性数组
     * @param entity 要检查的单元（行、列或宫）
     * @param size 数组大小（2-4）
     */
    private fun checkNakedSubsets(entity: List<Cell>, size: Int): SubsetData? {
        // 获取单元中所有有候选数的单元格及其候选数
        val entityCellsWithNotes = entity
            .filter { cell -> cell.value == 0 }
            .map { cell -> cell to getCellNotes(cell) }
            .filter { (_, notes) -> notes.isNotEmpty() }


        // 尝试找到size个单元格，它们的候选数集合的并集大小等于size
        // 且这些候选数只出现在这size个单元格中
        for (combination in entityCellsWithNotes.filter { (_, notes) -> notes.size <= size }.combinations(size)) {
            // cells为这size个单元格，allNotes为它们的候选数集合的并集
            val cells = combination.map { it.first }
            val allNotes = combination.flatMap { it.second }.toSet()

            if (allNotes.size != size) continue
            // 检查这些候选数是否只出现在这size个单元格中
            val otherCells = entityCellsWithNotes.filterNot { combination.contains(it) }
            val otherCellsWithThoseNotes = otherCells
                .map { (cell, notes) -> cell to notes.intersect(allNotes) }
                .filterNot { (_ , notes) -> notes.isEmpty()}

            if (otherCellsWithThoseNotes.isEmpty()) continue
            val notesToRemove = otherCellsWithThoseNotes.fold(emptyList<Note>()) { acc, (cell, notes) ->
                acc + notes.map { note ->
                    Note(cell.row, cell.col, note)
                }
            }

            return SubsetData(
                numbers = allNotes.toList().sorted(),
                cells = cells,
                notesToRemove = notesToRemove,
                helperCells = entity,
            )
        }

        return null
    }

    /**
     * 检查所有类型的隐性数组（数对、三数组、四数组）
     */
    private fun checkForHiddenSubsets(): AdvancedHintData? {
        if (notes.isEmpty()) return null

        // 检查行、列、宫中的隐性数组
        val entities = listOf(
            rows.map { it to "row" } ,
            columns.map { it to "column" },
            boxes.map { it to "box" }
        ).flatten()

        for ((entity, entityType) in entities) {
            // 获取单元中的所有候选数
            val allNotesInEntity = entity
                .flatMap { cell ->
                    notes.filter { note ->
                        note.row == cell.row && note.col == cell.col
                    }.map { it.value }
                }.toSet()

            // 检查隐性数对 (2个数字)
            if (allNotesInEntity.size >= 2) {
                val hiddenPair = checkHiddensubsets(entity, allNotesInEntity, 2)
                if (hiddenPair != null) {
                    return createSubsetsHint(hiddenPair, entityType, isNaked = false)
                }
            }

            // 检查隐性三数组 (3个数字)
            if (allNotesInEntity.size >= 3) {
                val hiddenTriple = checkHiddensubsets(entity, allNotesInEntity, 3)
                if (hiddenTriple != null) {
                    return createSubsetsHint(hiddenTriple, entityType, isNaked = false)
                }
            }

            // 检查隐性四数组 (4个数字)
            if (allNotesInEntity.size >= 4) {
                val hiddenQuadruple = checkHiddensubsets(entity, allNotesInEntity, 4)
                if (hiddenQuadruple != null) {
                    return createSubsetsHint(hiddenQuadruple, entityType, isNaked = false)
                }
            }
        }

        return null
    }

    /**
     * 检查特定大小的隐性数组
     * @param entity 要检查的单元（行、列或宫）
     * @param allNotes 单元中所有的候选数
     * @param size 数组大小（2-4）
     */
    private fun checkHiddensubsets(entity: List<Cell>, allNotes: Set<Int>, size: Int): SubsetData? {
        // 获取单元中所有有候选数的单元格
        val cellsWithNotes = entity
            .filter { cell -> cell.value == 0 }
            .map { cell ->
                val cellNotes = notes.filter { note ->
                    note.row == cell.row && note.col == cell.col
                }.map { it.value }.toSet()
                cell to cellNotes
            }
            .filter { (_, notes) -> notes.isNotEmpty() }

        // 尝试找到size个数字，它们只出现在size个单元格中
        for (numberCombination in allNotes.combinations(size)) {
            // 找到包含这些数字中至少一个的单元格
            val cellsContainingNumbers = cellsWithNotes
                .filter { (_, cellNotes) ->
                    cellNotes.intersect(numberCombination).isNotEmpty()
                }
                .map { it.first }
                .distinct()

            if (cellsContainingNumbers.size == size) {
                // 这些数字只出现在size个单元格中，形成隐性数组
                val notesToRemove = cellsContainingNumbers.flatMap { cell ->
                    notes.filter { note ->
                        note.row == cell.row &&
                                note.col == cell.col &&
                                !numberCombination.contains(note.value)
                    }
                }

                if (notesToRemove.isNotEmpty()) {
                    return SubsetData(
                        numbers = numberCombination.sorted(),
                        cells = cellsContainingNumbers,
                        notesToRemove = notesToRemove,
                        helperCells = entity
                    )
                }
            }
        }

        return null
    }

    /**
     * 创建数组提示数据对象
     */
    private fun createSubsetsHint(
        subsetData: SubsetData,
        entityType: String,
        isNaked: Boolean
    ): AdvancedHintData {
        val titleRes = if (isNaked) {
            R.string.hint_naked_subsets_title
        } else {
            R.string.hint_hidden_subsets_title
        }
        val detailRes = if (isNaked) {
            R.string.hint_naked_subsets_detail
        } else {
            R.string.hint_hidden_subsets_detail
        }

//        val entityNumber = when (entityType) {
//            "row" -> subsetData.cells.first().row + 1
//            "column" -> subsetData.cells.first().col + 1
//            "box" -> getBoxNumber(subsetData.cells.first().row, subsetData.cells.first().col) + 1
//            else -> 0
//        }

        val numbersStr = subsetData.numbers.joinToString(", ")
        val cellsStr = subsetData.cells.joinToString(", ") { cellStringFormat(it) }
        val targetCells = subsetData.notesToRemove
            .map { note -> Cell(note.row, note.col, 0) }
            .distinct()
        val detailArgs = listOf(
            cellsStr,
            numbersStr,
            targetCells.joinToString(", ") { cellStringFormat(it) }
        )

        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = targetCells,
            helpCells = subsetData.cells.filter { it !in targetCells },
            notesToRemove = subsetData.notesToRemove
        )
    }

    /**
     * 辅助数据类，存储数组相关信息
     */
    private data class SubsetData(
        val numbers: List<Int>,
        val cells: List<Cell>,
        val notesToRemove: List<Note>,
        val helperCells: List<Cell> = emptyList()
    )

    /**
     * 检查 Sue de Coq（融合待定数组/SDC）
     * 
     * 正确定义（基于标准描述）：
     * 
     * 设 C 为宫 B 与行/列 R 交集处的空单元格集合，|C| >= 2
     * 设 V 为 C 中出现的候选数集合，|V| >= |C| + 2
     * 
     * 需要在 B 和 R 中找到 |V| - |C| + n 个单元格（CB 在宫内，CR 在行/列内）：
     * - 每个区域至少有一个单元格
     * - 这些单元格包含至少 |V| - |C| 个来自 V 的候选数
     * - n 为不来自 V 的候选数数量
     * 
     * 设 VB 为 CB 的候选数集合，VR 为 CR 的候选数集合
     * 
     * **关键约束**：V 中的候选数不能同时出现在 VB 和 VR 中
     * 即：对于任意 x ∈ V，不能同时有 x ∈ VB 且 x ∈ VR
     * 
     * C 必须包含：
     * - V \ (VB ∪ VR)：不在 VB 或 VR 中的 V 的候选数
     * - |VB| - |CB| 个 VB 的元素
     * - |VR| - |CR| 个 VR 的元素
     * 
     * 排除规则：
     * - 从 B \ (C ∪ CB) 排除 VB ∪ (V \ VR)
     * - 从 R \ (C ∪ CR) 排除 VR ∪ (V \ VB)
     */
    private fun checkForSueDeCoq(): AdvancedHintData? {
        if (notes.isEmpty()) return null
        for (boxNum in boxes.indices) {
            val colsInBox = boxes[boxNum].map { it.col }
            val rowsInBox = boxes[boxNum].map { it.row }

            // 检查列与宫的交集
            for (colNum in colsInBox) {
                val hint = checkSueDeCoqForLineAndBox(
                    lineNum = colNum,
                    boxNum = boxNum,
                    isRow = false
                )
                if (hint != null) return hint
            }
            // 检查行与宫的交集
            for (rowNum in rowsInBox) {
                val hint = checkSueDeCoqForLineAndBox(
                    lineNum = rowNum,
                    boxNum = boxNum,
                    isRow = true
                )
                if (hint != null) return hint
            }
        }
        return null
    }

    /**
     * 统一检查行/列与宫的 Sue de Coq
     * @param lineNum 行号或列号
     * @param boxNum 宫号
     * @param isRow true 表示检查行，false 表示检查列
     */
    private fun checkSueDeCoqForLineAndBox(
        lineNum: Int,
        boxNum: Int,
        isRow: Boolean
    ): AdvancedHintData? {
        val line = if (isRow) rows[lineNum] else columns[lineNum]
        val box = boxes[boxNum]

        // 获取宫与行/列交集处的所有空单元格（候选C的池子）
        val allIntersectionCells = line.intersect(box.toSet()).filter { it.value == 0 }.toList()
        if (allIntersectionCells.size < 2) return null

        // 宫内其他空单元格（不在整个交集中）
        val boxOtherCells = box.filter { cell ->
            val isInIntersection = if (isRow) {
                cell.row == lineNum
            } else {
                cell.col == lineNum
            }
            !isInIntersection && cell.value == 0
        }
        if (boxOtherCells.isEmpty()) return null
        // 行/列内其他空单元格（不在宫中）
        val lineOtherCells = line.filter { 
            getBoxIndex(it.row, it.col) != boxNum && it.value == 0 
        }
        if (lineOtherCells.isEmpty()) return null

        val combinationNotesCache = mutableMapOf<Set<Cell>, Set<Int>>()

        fun getCombinationNotes(cells: List<Cell>): Set<Int> {
            val key = cells.toSet()
            return combinationNotesCache.getOrPut(key) {
                cells.flatMap { getCellNotes(it) }.toSet()
            }
        }

        // 枚举交集的子集作为C，优先选择较小的集合（|C| >= 2）
        for (cSize in 2..allIntersectionCells.size) {
            for (cCombination in allIntersectionCells.combinations(cSize)) {
                // V: 交集单元格C中的所有候选数，|V| >= |C| + 2
                // val vSet = cCombination.flatMap { getCellNotes(it) }.toSet()
                val vSet = getCombinationNotes(cCombination)
                if (vSet.size < cSize + 2) continue

                // 需要找到 |V| - |C| + n 个单元格（CB 和 CR）
                val requiredCellsFromV = vSet.size - cSize
                
                // 枚举 CB 的所有可能组合（至少1个）
                for (cbSize in 1..minOf(boxOtherCells.size, requiredCellsFromV + 3)) {
                    for (cbCombination in boxOtherCells.combinations(cbSize)) {
                        // val vb = cbCombination.flatMap { getCellNotes(it) }.toSet()
                        val vb = getCombinationNotes(cbCombination)
                        if (vb.isEmpty()) continue
                        
                        // VB 中来自 V 的候选数
                        val vbFromV = vb.intersect(vSet)
                        // VB 中不来自 V 的候选数（外部候选数）
                        val vbNotFromV = vb - vSet
                        
                        // 枚举 CR 的所有可能组合（至少1个）
                        for (crSize in 1..minOf(lineOtherCells.size, requiredCellsFromV + 3)) {
                            for (crCombination in lineOtherCells.combinations(crSize)) {
                                // val vr = crCombination.flatMap { getCellNotes(it) }.toSet()
                                val vr = getCombinationNotes(crCombination)
                                if (vr.isEmpty()) continue
                                
                                // VR 中来自 V 的候选数
                                val vrFromV = vr.intersect(vSet)
                                // VR 中不来自 V 的候选数（外部候选数）
                                val vrNotFromV = vr - vSet
                                
                                // **关键约束**：V 中的候选数不能同时出现在 VB 和 VR 中
                                val vInBoth = vbFromV.intersect(vrFromV)
                                if (vInBoth.isNotEmpty()) continue
                                
                                // 来自 V 的候选数总数
                                val totalFromV = vbFromV.size + vrFromV.size
                                // 不来自 V 的候选数总数 n
                                val n = vbNotFromV.size + vrNotFromV.size
                                
                                // 验证：|CB| + |CR| = |V| - |C| + n
                                if (cbSize + crSize != requiredCellsFromV + n) continue
                                
                                // 验证：至少 |V| - |C| 个候选数来自 V
                                if (totalFromV < requiredCellsFromV) continue
                                
                                // 验证 C 的结构（简化验证：确保 C 的候选数能容纳所需的分配）
                                // V \ (VB ∪ VR) 必须能装入 C
                                val vNotInVbVr = vSet - vbFromV - vrFromV
                                // |VB| - |CB| 和 |VR| - |CR| 的元素也需要装入 C
                                val neededInC = vNotInVbVr.size + (vb.size - cbSize) + (vr.size - crSize)
                                if (neededInC > cSize * type.size) continue // 粗略验证
                                
                                // 计算排除的候选数
                                val notesToRemove = mutableListOf<Note>()
                                val affectedCells = mutableListOf<Cell>()

                                // 从 B \ (C ∪ CB) 排除 VB ∪ (V \ VR)
                                val toRemoveFromBox = vb.union(vSet - vrFromV)
                                val boxCellsToCheck = box.filter { 
                                    it.value == 0 && 
                                    it !in cCombination && 
                                    it !in cbCombination 
                                }
                                for (cell in boxCellsToCheck) {
                                    val cellNotes = getCellNotes(cell)
                                    val toRemove = cellNotes.intersect(toRemoveFromBox)
                                    for (num in toRemove) {
                                        notesToRemove.add(Note(cell.row, cell.col, num))
                                        if (!affectedCells.contains(cell)) {
                                            affectedCells.add(cell)
                                        }
                                    }
                                }

                                // 从 R \ (C ∪ CR) 排除 VR ∪ (V \ VB)
                                val toRemoveFromLine = vr.union(vSet - vbFromV)
                                val lineCellsToCheck = line.filter { 
                                    it.value == 0 && 
                                    it !in cCombination && 
                                    it !in crCombination 
                                }
                                for (cell in lineCellsToCheck) {
                                    val cellNotes = getCellNotes(cell)
                                    val toRemove = cellNotes.intersect(toRemoveFromLine)
                                    for (num in toRemove) {
                                        notesToRemove.add(Note(cell.row, cell.col, num))
                                        if (!affectedCells.contains(cell)) {
                                            affectedCells.add(cell)
                                        }
                                    }
                                }

                                if (notesToRemove.isNotEmpty()) {
                                    return createSueDeCoqHint(
                                        boxNum = boxNum,
                                        lineNum = lineNum,
                                        isRowBased = isRow,
                                        intersectionCells = cCombination,
                                        cbCells = cbCombination,
                                        crCells = crCombination,
                                        intersectionNotes = vSet,
                                        vb = vb,
                                        vr = vr,
                                        notesToRemove = notesToRemove,
                                        affectedCells = affectedCells
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * 创建 Sue de Coq 提示数据对象
     * 
     * Sue de Coq 提示说明：
     * - 从宫与行/列交集中选取子集 C (|C| >= 2)，包含候选数集合 V (|V| >= |C| + 2)
     * - CB 和 CR 共 |V| - |C| + n 个单元格，包含候选数 VB 和 VR
     * - 关键：V 中的候选数不能同时出现在 VB 和 VR 中
     * - 从宫的其他单元格排除 VB 及 (V 中不在 VR 的部分)
     * - 从行/列的其他单元格排除 VR 及 (V 中不在 VB 的部分)
     * 
     * @param boxNum 宫的编号
     * @param lineNum 行或列的编号
     * @param isRowBased 是否为行基模式（true=行与宫，false=列与宫）
     * @param intersectionCells 从交集中选取的单元格子集 C
     * @param cbCells 宫内的单元格 CB
     * @param crCells 行/列内的单元格 CR
     * @param intersectionNotes 选取子集 C 的候选数集合 V
     * @param vb 宫内单元格的候选数集合 VB（可能包含 V 外的候选数）
     * @param vr 行/列内单元格的候选数集合 VR（可能包含 V 外的候选数）
     * @param notesToRemove 要移除的候选数
     * @param affectedCells 受影响的单元格
     */
    private fun createSueDeCoqHint(
        boxNum: Int,
        lineNum: Int,
        isRowBased: Boolean,
        intersectionCells: List<Cell>,
        cbCells: List<Cell>,
        crCells: List<Cell>,
        intersectionNotes: Set<Int>,
        vb: Set<Int>,
        vr: Set<Int>,
        notesToRemove: List<Note>,
        affectedCells: List<Cell>
    ): AdvancedHintData {
        val titleRes = R.string.hint_sue_de_coq_title

        // 格式化单元格信息
        val intersectionStr = intersectionCells.joinToString(", ") { cellStringFormat(it) }
        val cbStr = cbCells.joinToString(", ") { cellStringFormat(it) }
        val crStr = crCells.joinToString(", ") { cellStringFormat(it) }
        
        // 格式化候选数信息
        val vStr = intersectionNotes.sorted().joinToString(",")
        val vbStr = vb.sorted().joinToString(",")
        val vrStr = vr.sorted().joinToString(",")
        
        // 计算 VB ∩ V 和 VR ∩ V（来自 V 的部分）
        val vbFromV = vb.intersect(intersectionNotes)
        val vrFromV = vr.intersect(intersectionNotes)
        
        // 格式化删除信息：每个单元格显示要删除的候选数
        val cellNotesMap = notesToRemove.groupBy { Pair(it.row, it.col) }
        val removalsStr = cellNotesMap.entries
            .sortedBy { (key, _) -> key.first * 100 + key.second }
            .joinToString(",") { (key, notes) ->
                val cell = board[key.first][key.second]
                val notesStr = notes.map { it.value }.sorted().joinToString(",")
                "${cellStringFormat(cell)}($notesStr)"
            }
        
        // 解释为什么删除
        //val vNotInVr = (intersectionNotes - vrFromV).sorted().joinToString(",")
        //val vNotInVb = (intersectionNotes - vbFromV).sorted().joinToString(",")
        val deleteInBox = vb.union(intersectionNotes - vrFromV)
        val deleteInLine = vr.union(intersectionNotes - vbFromV)

        val (detailRes, detailArgs) = if (isRowBased) {
            Pair(
                R.string.hint_sue_de_coq_box_row_detail,
                listOf(
                    (boxNum + 1).toString(),           // %1 宫号
                    (lineNum + 1).toString(),          // %2 行号
                    intersectionStr,                    // %3 交集单元格
                    vStr,                              // %4 V 集合
                    cbStr,                             // %5 CB 单元格
                    vbStr,                             // %6 VB 集合
                    crStr,                             // %7 CR 单元格
                    vrStr,                             // %8 VR 集合
                    vbFromV.sorted().joinToString(","),// %9 VB 中来自 V 的部分
                    vrFromV.sorted().joinToString(","),// %10 VR 中来自 V 的部分
                    deleteInBox.sorted().joinToString(","), // %11 宫中删除的部分
                    deleteInLine.sorted().joinToString(","), // %12 行/列中删除的部分
                    removalsStr                        // %13 删除信息（带括号格式）
                )
            )
        } else {
            Pair(
                R.string.hint_sue_de_coq_box_col_detail,
                listOf(
                    (boxNum + 1).toString(),           // %1 宫号
                    (lineNum + 1).toString(),          // %2 列号
                    intersectionStr,                    // %3 交集单元格
                    vStr,                              // %4 V 集合
                    cbStr,                             // %5 CB 单元格
                    vbStr,                             // %6 VB 集合
                    crStr,                             // %7 CR 单元格
                    vrStr,                             // %8 VR 集合
                    vbFromV.sorted().joinToString(","),// %9 VB 中来自 V 的部分
                    vrFromV.sorted().joinToString(","),// %10 VR 中来自 V 的部分
                    deleteInBox.sorted().joinToString(","), // %11 宫中删除的部分
                    deleteInLine.sorted().joinToString(","), // %12 行/列中删除的部分
                    removalsStr                        // %13 删除信息（带括号格式）
                )
            )
        }

        val helpCells = (intersectionCells + cbCells + crCells).distinct()

        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = helpCells.filter { !affectedCells.contains(it) } + intersectionCells,
            notesToRemove = notesToRemove
        )
    }

    /**
     * 检查X-Wing模式
     * X-Wing是指当某个数字在两行中只出现在相同的两列时，可排除这两列中其他行的该数字
     * 或某个数字在两列中只出现在相同的两行时，可排除这两行中其他列的该数字
     */
    private fun checkForXWing(): AdvancedHintData? {
        if (notes.isEmpty()) return null

        val size = type.size

        // 检查行方向的X-Wing (数字在两行中仅出现在相同的两列)
        val rowXWing = checkRowXWing(size)
        if (rowXWing != null) {
            return rowXWing
        }

        // 检查列方向的X-Wing (数字在两列中仅出现在相同的两行)
        val colXWing = checkColumnXWing(size)
        if (colXWing != null) {
            return colXWing
        }

        return null
    }

    /**
     * 检查行方向的X-Wing
     * 当数字N在两行中仅出现在相同的两列时，可排除这两列中其他行的N
     */
    private fun checkRowXWing(size: Int): AdvancedHintData? {
        // 遍历每个可能的数字
        for (num in 1..size) {
            // 收集包含该数字候选的所有行及其出现的列
            val rowsWithNote = mutableMapOf<Int, Set<Int>>()

            for (rowNum in rows.indices) {
                val row = rows[rowNum]
                // 找出当前行中包含该数字候选的列
                val colsWithNote = row
                    .filter { cell ->
                        notes.any { note ->
                            note.row == cell.row && note.col == cell.col && note.value == num
                        }
                    }
                    .map { it.col }
                    .toSet()

                // 只关注有且仅有2个候选位置的行（X-Wing需要两行各有2个位置且列相同）
                if (colsWithNote.size == 2) {
                    rowsWithNote[rowNum] = colsWithNote
                }
            }

            // 检查是否有两行具有完全相同的候选列
            if (rowsWithNote.size >= 2) {
                // 遍历所有可能的两行组合
                val rowCombinations = rowsWithNote.keys.toList().combinations(2)
                for (rowPair in rowCombinations) {
                    val row1 = rowPair[0]
                    val row2 = rowPair[1]

                    // 如果两行的候选列完全相同，则形成X-Wing
                    if (rowsWithNote[row1] == rowsWithNote[row2]) {
                        val cols = rowsWithNote[row1] ?: continue
                        val colList = cols.toList()

                        // 找出这两列中其他行的该数字候选（这些是可以移除的）
                        val notesToRemove = mutableListOf<Note>()
                        val affectedCells = mutableListOf<Cell>()

                        for (col in cols) {
                            for (row in rows.indices) {
                                // 排除形成X-Wing的两行
                                if (row != row1 && row != row2) {
                                    val cell = board[row][col]
                                    // 收集该单元格中需要移除的候选数
                                    val note = cellNoteIndex[Triple(row, col, num)]
                                    if (note != null) {
                                        notesToRemove.add(note)
                                        affectedCells.add(cell)
                                    }
                                }
                            }
                        }

                        if (notesToRemove.isNotEmpty()) {
                            return createXWingHint(
                                number = num,
                                isRowBased = true,
                                baseRows = listOf(row1, row2),
                                baseCols = colList,
                                notesToRemove = notesToRemove,
                                affectedCells = affectedCells
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 检查列方向的X-Wing
     * 当数字N在两列中仅出现在相同的两行时，可排除这两行中其他列的N
     */
    private fun checkColumnXWing(size: Int): AdvancedHintData? {
        // 遍历每个可能的数字
        for (num in 1..size) {
            // 收集包含该数字候选的所有列及其出现的行
            val colsWithNote = mutableMapOf<Int, Set<Int>>()

            for (colNum in columns.indices) {
                val col = columns[colNum]
                // 找出当前列中包含该数字候选的行
                val rowsWithNote = col
                    .filter { cell ->
                        notes.any { note ->
                            note.row == cell.row && note.col == cell.col && note.value == num
                        }
                    }
                    .map { it.row }
                    .toSet()

                // 只关注有且仅有2个候选位置的列
                if (rowsWithNote.size == 2) {
                    colsWithNote[colNum] = rowsWithNote
                }
            }

            // 检查是否有两列具有完全相同的候选行
            if (colsWithNote.size >= 2) {
                // 遍历所有可能的两列组合
                val colCombinations = colsWithNote.keys.toList().combinations(2)
                for (colPair in colCombinations) {
                    val col1 = colPair[0]
                    val col2 = colPair[1]

                    // 如果两列的候选行完全相同，则形成X-Wing
                    if (colsWithNote[col1] == colsWithNote[col2]) {
                        val rows = colsWithNote[col1] ?: continue
                        val rowList = rows.toList()

                        // 找出这两行中其他列的该数字候选（这些是可以移除的）
                        val notesToRemove = mutableListOf<Note>()
                        val affectedCells = mutableListOf<Cell>()

                        for (row in rows) {
                            for (col in columns.indices) {
                                // 排除形成X-Wing的两列
                                if (col != col1 && col != col2) {
                                    val cell = board[row][col]
                                    // 收集该单元格中需要移除的候选数
                                    val note = notes.find {
                                        it.row == row && it.col == col && it.value == num
                                    }
                                    if (note != null) {
                                        notesToRemove.add(note)
                                        affectedCells.add(cell)
                                    }
                                }
                            }
                        }

                        if (notesToRemove.isNotEmpty()) {
                            return createXWingHint(
                                number = num,
                                isRowBased = false,
                                baseRows = rowList,
                                baseCols = listOf(col1, col2),
                                notesToRemove = notesToRemove,
                                affectedCells = affectedCells
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * 创建X-Wing提示数据对象
     */
    private fun createXWingHint(
        number: Int,
        isRowBased: Boolean,
        baseRows: List<Int>,
        baseCols: List<Int>,
        notesToRemove: List<Note>,
        affectedCells: List<Cell>
    ): AdvancedHintData {
        // 格式化行号和列号（转换为1-based索引）
        val formattedRows = baseRows.map { it + 1 }.joinToString(", ")
        val formattedCols = baseCols.map { it + 1 }.joinToString(", ")
        val affectedCellsStr = affectedCells.joinToString(", ") { cellStringFormat(it) }

        val (titleRes, detailRes, detailArgs) = if (isRowBased) {
            // 行基X-Wing
            Triple(
                R.string.hint_x_wing_title,
                R.string.hint_x_wing_row_detail,
                listOf(
                    number.toString(),
                    formattedRows,
                    formattedCols,
                    affectedCellsStr
                )
            )
        } else {
            // 列基X-Wing
            Triple(
                R.string.hint_x_wing_title,
                R.string.hint_x_wing_col_detail,
                listOf(
                    number.toString(),
                    formattedCols,
                    formattedRows,
                    affectedCellsStr
                )
            )
        }
        val helperCells = baseRows.flatMap { row ->
            baseCols.map { col -> board[row][col] }
        } + baseRows.flatMap { row -> rows[row] } + baseCols.flatMap { col -> columns[col] }

        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = helperCells.filter { !affectedCells.contains(it) },
            notesToRemove = notesToRemove
        )
    }

    // 扩展函数，生成集合的集合中，从各个集合元素中取出一个元素的所有可能组合
    private fun <T> Collection<Collection<T>>.pickOneFromEachCombination(): List<List<T>> {
        if (this.isEmpty()) return emptyList()
        return this.fold(listOf(emptyList())) { acc, collection ->
            acc.flatMap { prefix ->
                collection.map { element -> prefix + element }
            }
        }
    }


    /**
     * 扩展函数，生成集合的所有可能组合
     */
    private fun <T> Collection<T>.combinations(size: Int): Sequence<List<T>> = sequence {
        // if (size <= 0 || size > this.size) return emptyList()
        // if (size == 1) return this.map { listOf(it) }

        // val result = mutableListOf<List<T>>()
        // val iterator = this.iterator()
        // var index = 0

        // while (iterator.hasNext()) {
        //     val element = iterator.next()
        //     val remaining = this.drop(index + 1)

        //     for (combination in remaining.combinations(size - 1)) {
        //         result.add(listOf(element) + combination)
        //     }
        //     index++
        // }
        // return result
        if (size <= 0 || size > this@combinations.size) return@sequence
        if (size == 1) {
            this@combinations.forEach { yield(listOf(it)) }
            return@sequence
        }

        val list = this@combinations.toList()
        for (i in 0..list.size - size) {
            val first = list[i]
            val rest = list.subList(i + 1, list.size)
            for (combination in rest.combinations(size - 1)) {
                yield(listOf(first) + combination)
            }
        }
    }

    /**
     * 检查XY-Wing模式
     * XY-Wing由三个单元格组成：
     * - 一个XY单元格（有两个候选数X和Y）
     * - 一个XZ单元格（与XY在同一单元，有候选数X和Z）
     * - 一个YZ单元格（与XY在同一单元但不与XZ在同一单元，有候选数Y和Z）
     * 可排除XZ和YZ共同可见单元格中的Z
     */
    private fun checkForXYWing(): AdvancedHintData? {
        // 收集所有只有两个候选数的单元格
        val biValueCells = getBiValueCells()
        if (biValueCells.size < 3) return null

        // 遍历所有可能的XY单元格（有两个候选数的单元格）
        for (xyCell in biValueCells) {
            val xyNotes = getCellNotes(xyCell).toList()
            val x = xyNotes[0]
            val y = xyNotes[1]

            // 找到与XY单元格在同一组且包含X的双值单元格（XZ）
            val xzCandidates = biValueCells
                .filter { it != xyCell && areInSameGroup(xyCell, it) }
                .filter { cell -> getCellNotes(cell).contains(x) && !getCellNotes(cell).contains(y) }
                .filter { cell -> getCellNotes(cell).size == 2 }

            // 找到与XY单元格在同一组且包含Y的双值单元格（YZ）
            val yzCandidates = biValueCells
                .filter { it != xyCell && areInSameGroup(xyCell, it) }
                .filter { cell -> getCellNotes(cell).contains(y) && !getCellNotes(cell).contains(x) }
                .filter { cell -> getCellNotes(cell).size == 2 }

            // 检查XZ和YZ的组合
            for (xzCell in xzCandidates) {
                val z = getCellNotes(xzCell).first { it != x }

                for (yzCell in yzCandidates) {
                    // YZ单元格必须不与XZ单元格在同一单元
                    if (!getCellNotes(yzCell).contains(z) || areInSameGroup(xzCell, yzCell)) continue

                    // 找到XZ和YZ共同可见的单元格
                    val commonCells = getCommonVisibleCells(xzCell, yzCell)
                        .filter { cell -> cell.value == 0 } // 只考虑空单元格
                        .filter { cell -> getCellNotes(cell).contains(z) }

                    if (commonCells.isNotEmpty()) {
                        // 收集可移除的候选数
                        val notesToRemove = commonCells.flatMap { cell ->
                            notes.filter { note ->
                                note.row == cell.row && note.col == cell.col && note.value == z
                            }
                        }

                        if (notesToRemove.isNotEmpty()) {
                            return createXyOrXyzWingHint(
                                type = "XY",
                                pivotCell = xyCell,
                                wingCells = listOf(xzCell, yzCell),
                                number = z,
                                affectedCells = commonCells,
                                notesToRemove = notesToRemove,
                                x = x,
                                y = y,
                                z = z
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
    X-Chain 的链类型枚举*/
    private enum class ChainType {
        STRONG, // 仅强链（至少一真）
        WEAK, // 仅弱链（至少一假）
        STRONG_AND_WEAK, // 既是强链也是弱链（严格一真一假）
        NONE; // 无链

        fun isStrong(): Boolean = this == STRONG || this == STRONG_AND_WEAK
        fun isWeak(): Boolean = this == WEAK || this == STRONG_AND_WEAK
    }
    /**
    BFS 节点数据类
     */
    private data class XChainBfsNode (
        val currentCell: Cell,
        val path: List<Cell>,
        val lastChainType: ChainType?,
        val visited: Set<Cell>
    )
    /*
        检查X-Chain
        * X-Chain是单个数字构成的强弱交替链，由强链开头和结尾，中间必须强弱交替。
        * 最终链两端共同可见的该数字可以删除。
     */

    private fun checkForXChain(): AdvancedHintData? {
        if (notes.isEmpty ()) return null
        val size = type.size
        // 遍历每个可能的数字构建 X-Chain
        for (num in 1..size) {// 收集所有包含该数字候选的单元格（X-cells）
            val xCells = notes.filter { it.value == num }.map { note -> board[note.row][note.col] }.distinct().filter { cell -> cell.value == 0 }
            if (xCells.size < 5) continue // 至少需要 5 个单元格才能形成链和排除格
            // 构建链的邻接表：记录每个单元格的强链和弱链连接
            val adjacency = buildXChainAdjacency (xCells, num)
            if (adjacency.isEmpty()) continue
            // BFS 寻找最短有效 X-Chain
            val chainResult = findShortestValidXChain(xCells, adjacency, num)
            if (chainResult != null) return chainResult
        }
        return null
    }
    /**
    为 X-Chain 构建邻接表，区分强链和弱链*/
    private fun buildXChainAdjacency (xCells: List<Cell>, num: Int): Map<Cell, List<Pair<Cell, ChainType>>> {
        val adjacency = mutableMapOf<Cell, MutableList<Pair<Cell, ChainType>>>()
        // 为每个单元格找到所有同单元的其他 X-cell，判断链类型
        for (i in xCells.indices) {
            val cellA = xCells[i]
            for (j in i + 1 until xCells.size) {
                val cellB = xCells[j]
                if (!areInSameGroup(cellA, cellB)) continue // 仅考虑同单元（行 / 列 / 宫）的单元格
                // 判断链类型
                val chainType = determineChainType(cellA, cellB, num)
                if (chainType == ChainType.NONE) continue
                // 双向添加到邻接表
                adjacency.getOrPut (cellA) { mutableListOf () }.add (cellB to chainType)
                adjacency.getOrPut (cellB) { mutableListOf () }.add (cellA to chainType)
            }
        }
        return adjacency
    }
    /**
    判断两个同单元单元格之间的链类型
    强链：同一单元中该数字仅出现在这两个单元格（至少一个为真）
    弱链：同一单元中该数字出现在两个以上单元格（至少一个为假）
    互斥链：既是强链也是弱链（严格一真一假）*/
    private fun determineChainType (cellA: Cell, cellB: Cell, num: Int): ChainType {

        val xCountInCommonRow = if (cellA.row != cellB.row) 999 else rows[cellA.row].count { cell ->
            cell.value == 0 && (cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true)
        }
        val xCountInCommonCol = if (cellA.col != cellB.col) 999 else columns[cellA.col].count { cell ->
            cell.value == 0 && (cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true)
        }
        val xCountInCommonBox = if (
                getBoxIndex(cellA.row, cellA.col) != getBoxIndex(cellB.row, cellB.col)
            ) 999 else boxes[getBoxIndex(cellA.row, cellA.col)].count { cell ->
            cell.value == 0 && (cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true)
        }
        val xCountInGroup = intArrayOf(xCountInCommonRow, xCountInCommonCol, xCountInCommonBox).min()
        if (xCountInGroup == 999) throw IllegalStateException("not in group")
        return when (xCountInGroup) {
            2 -> ChainType.STRONG_AND_WEAK // 仅两个单元格，既是强链也是弱链（互斥）
            in 3..type.size -> ChainType.WEAK // 三个及以上，弱链
            else -> ChainType.NONE // 无效情况（少于 2 个）
        }
    }
    /**
    BFS 寻找最短有效 X-Chain
    有效链需满足：强弱交替，以强链结尾，两端有共同可见单元格且含该数字候选*/
    private fun findShortestValidXChain (xCells: List<Cell>,adjacency: Map<Cell, List<Pair<Cell, ChainType>>>,num: Int): AdvancedHintData? {
    // BFS 队列元素：(当前单元格，路径，最后一条链的类型，已访问单元格)
        val queue = ArrayDeque<XChainBfsNode>()
        // 初始化队列：从每个单元格开始，以强链为起点
        xCells.forEach {startCell ->
            queue.add(
                XChainBfsNode(
                    currentCell = startCell,
                    path = listOf (startCell),
                    lastChainType = null,
                    visited = setOf(startCell)
                )
            )
        }
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val currentCell = node.currentCell
            val currentPath = node.path
            val lastType = node.lastChainType
            val visited = node.visited
            // 遍历当前单元格的所有连接
            for ((neighbor, chainType) in adjacency [currentCell] ?: emptyList ()) {
                if (neighbor in visited) continue // 避免循环
                // 检查链类型是否符合交替规则
                val isValidTransition = when (lastType) {
                    null -> chainType.isStrong() // 第一条链必须是强链
                    ChainType.STRONG -> chainType.isWeak() // 强链后必须跟弱链
                    ChainType.STRONG_AND_WEAK -> {
                        if (currentPath.size % 2 == 0) chainType.isWeak() else chainType.isStrong()
                    }
                    ChainType.WEAK -> chainType.isStrong() // 弱链后必须跟强链
                    ChainType.NONE -> false
                }
                if (!isValidTransition) continue
                // 构建新路径
                val newPath = currentPath + neighbor
                val newVisited = visited + neighbor
                // 链长度至少为 4 且为偶数（包含起点和终点），且最后一条链必须是强链
                if (newPath.size >= 4 && newPath.size % 2 == 0 && chainType.isStrong()) {
                    // 检查路径两端是否有共同可见单元格
                    val start = newPath.first()
                    val end = newPath.last()
                    val commonVisible = getCommonVisibleCells(start, end)
                        .filter { it.value == 0 } // 仅考虑空单元格
                        .filter { it !in newPath }
                        .filter { cell -> cellNotesCache[Pair(cell.row, cell.col)]?.contains(num) == true }

                    if (commonVisible.isNotEmpty()) {
                        // 收集可移除的候选数
                        val notesToRemove =
                            commonVisible.flatMap { cell -> notes.filter { note -> note.row == cell.row && note.col == cell.col && note.value == num } }
                        if (notesToRemove.isNotEmpty()) {
                            return createXChainHint(
                                number = num,
                                chainPath = newPath,
                                affectedCells = commonVisible,
                                notesToRemove = notesToRemove
                            )
                        }
                    }
                }

                // 继续扩展路径（只保留较短路径，避免过长链）
                queue.add(
                    XChainBfsNode(
                        currentCell = neighbor,
                        path = newPath,
                        lastChainType = chainType,
                        visited = newVisited
                    )
                )
            }
        }
        return null
    }
    /**
        创建 X-Chain 提示数据对象
    **/
    private fun createXChainHint (number: Int,chainPath: List<Cell>,affectedCells: List<Cell>,notesToRemove: List<Note>): AdvancedHintData {
        val titleRes = R.string.hint_x_chain_title
        val detailRes = R.string.hint_x_chain_detail
        // 格式化链路径和受影响单元格
        // val chainPathStr = chainPath.joinToString ("-") { cellStringFormat (it) }
        val chainPathStr = chainPath.withIndex().fold("") { acc, (index, element) ->
            val cellStr = cellStringFormat(element)
            when {
                index == 0 -> cellStr
                index % 2 == 1 -> "$acc=$cellStr"
                else -> "$acc-$cellStr"
            }
        }
        val affectedCellsStr = affectedCells.joinToString (",") { cellStringFormat (it) }
        val detailArgs = listOf(number.toString(),chainPathStr,affectedCellsStr)
        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = chainPath,
            notesToRemove = notesToRemove
        )
    }

    /*
        检查XY-Chain
        * XY-Chain是XY-Wings的推广，由一系列双值单元格组成，这些单元格通过共享候选数连接在一起。
        * 通过这种链式连接，可以排除链两端单元格共同候选数的共同可见单元格。
     */
    private fun checkForXYChain(): AdvancedHintData? {
        // 1. 收集双值格（仅含2个候选数的单元格）
        val biValueCells = getBiValueCells()
        if (biValueCells.size < 3) return null  // 最短链需至少3个节点

        // 2. 缓存双值格的候选数（转为Set便于操作）
        val cellNotes = biValueCells.associateWith { getCellNotes(it).toSet() }

        // 3. 构建邻接表：仅保留同区且恰好共享1个候选数的相邻节点
        val adjacency = buildAdjacencyList(biValueCells, cellNotes)

        val result = bfsForShortestXYChain(adjacency, cellNotes)
        if (result != null) return result

        return null
    }

    /** 构建邻接表：key为节点，value为(相邻节点, 共享的候选数) */
    private fun buildAdjacencyList(
        biValueCells: List<Cell>,
        cellNotes: Map<Cell, Set<Int>>
    ): Map<Pair<Cell, Int>, List<Pair<Cell, Int>>> {
        val adjacency = mutableMapOf<Pair<Cell, Int>, MutableList<Pair<Cell, Int>>>()
        for (i in biValueCells.indices) {
            val a = biValueCells[i]
            val notesA = cellNotes[a]!!
            val boxA = getBoxIndex(a.row, a.col)

            for (j in i + 1 until biValueCells.size) {
                val b = biValueCells[j]
                val notesB = cellNotes[b]!!
                val boxB = getBoxIndex(b.row, b.col)

                // 检查同区（行/列/宫）及其共享的候选数
                if (isSameRegion(a, b, boxA, boxB) && notesA.intersect(notesB).isNotEmpty()) {
                    for (shared in notesA.intersect(notesB)) {
                        val remainB = notesB.first { it != shared }
                        val remainA = notesA.first { it != shared }
                        adjacency.getOrPut(Pair(a, shared)) { mutableListOf() }.add(b to remainB)
                        adjacency.getOrPut(Pair(b, shared)) { mutableListOf() }.add(a to remainA)
                    }
                }
            }
        }
        return adjacency
    }

    /** 检查两个单元格是否同区（行/列/宫） */
    private fun isSameRegion(a: Cell, b: Cell, boxA: Int, boxB: Int): Boolean {
        return a.row == b.row || a.col == b.col || boxA == boxB
    }

    /** BFS搜索最短XY链：返回第一个找到的有效链（保证最短） */
    private fun bfsForShortestXYChain(
        adjacency: Map<Pair<Cell, Int>, List<Pair<Cell, Int>>>,
        cellNotes: Map<Cell, Set<Int>>
    ): AdvancedHintData? {
        // BFS队列元素：(当前路径, 已访问节点, 相邻节点共享的候选数序列)
        val queue = ArrayDeque<Pair<List<Pair<Cell,Int>>, Set<Pair<Cell,Int>>>>()
        for (cellpair in adjacency.keys) {
            queue.add(Pair(listOf(cellpair), setOf(cellpair)))
        }
        while (queue.isNotEmpty()) {
            val (path, visited) = queue.removeFirst()  // BFS用removeFirst保证层次遍历
            val currentNodePair = path.last()

            // 遍历相邻节点扩展路径
            for (neighborPair in adjacency[currentNodePair] ?: emptyList()) {
                if (neighborPair in visited) continue

                val newPath = path + neighborPair
                val newVisited = visited + neighborPair

                // 路径长度≥3时检查有效性
                if (newPath.size >= 3) {
                    // 校验中间节点的候选数传递性（如a-b-c中，b的候选数必须是a-b和b-c的共享数）
//                    if (!isValidMiddleNodes(newPath, cellNotes)) continue

                    // 校验两端节点的候选数（排除传递序列首尾后必须相同）
                    val (isValid, x) = checkEndNodes(newPath, cellNotes)
                    if (isValid) {
                        // 找到有效链，返回提示（BFS保证这是最短链）
                        val start = newPath.first()
                        val end = newPath.last()
                        // 找到两端共同可见且含X的空单元格
                        val affectedCells = getCommonVisibleCells(start.first, end.first)
                            .filter { it.value == 0 && x in getCellNotes(it) }
                        if (affectedCells.isEmpty()) continue

                        // 收集需要移除的候选数标记
                        val notesToRemove = affectedCells.flatMap { cell ->
                            notes.filter { note ->
                                note.row == cell.row && note.col == cell.col && note.value == x
                            }
                        }
                        if (notesToRemove.isEmpty()) continue
                        return createXYChainHint(
                            path = newPath,
                            affectedCells = affectedCells,
                            numToRemove = x!!,
                            notesToRemove = notesToRemove
                        )
                    }
                }

                // 继续扩展路径（无论当前长度，都加入队列等待下一层处理）
                queue.add(Pair(newPath, newVisited))
            }
        }
        return null
    }


    /** 校验两端节点是否符合条件，返回(是否有效, 要排除的候选数X) */
    private fun checkEndNodes(
        path: List<Pair<Cell, Int>>,
        cellNotes: Map<Cell, Set<Int>>
    ): Pair<Boolean, Int?> {
        val startPair = path.first()
        val endPair = path.last()
        val startNoteRemain = cellNotes[startPair.first]!!.first { it != startPair.second }
        val endNoteRemain = endPair.second
        return if (startNoteRemain == endNoteRemain) Pair(true, startNoteRemain) else Pair(false, null)
    }

    /**
     * 检查W-Wing模式（含推广）
     * W-Wing由两个部分组成：
     * - 含有X的Group作为枢纽，枢纽的数量至少为2
     * - 每个X都与其它XY格各自在其它Group形成翼，这些Group中有且仅有这两个X
     * - 可排除这些翼格共同看到的单元格中的Y
     */
    private fun checkForWWing(): AdvancedHintData? {
        if (notes.isEmpty()) return null
        val size = type.size
        // 遍历每个可能的数字作为枢纽X
        for (x in 1..size) {
            // 收集所有候选数包含X的单元格
            val cellsWithX = notes.filter { it.value == x }
                .map { note -> board[note.row][note.col] }
                .distinct()
                .filter { cell -> cell.value == 0 }
            if (cellsWithX.size < 4) continue
            // 按行、列、宫分组
            val rowsWithX = cellsWithX.groupBy { it.row }.filter { it.value.size >= 2 }
            val colsWithX = cellsWithX.groupBy { it.col }.filter { it.value.size >= 2 }
            val boxesWithX = cellsWithX.groupBy { getBoxIndex(it.row, it.col) }.filter { it.value.size >= 2 }
            // 加起来并去重
            val groups = (mutableListOf<List<Cell>>()
                + rowsWithX.values
                + colsWithX.values
                + boxesWithX.values).distinctBy { group ->
                // 通过排序的单元格位置字符串来去重
                group.sortedBy { it.row * size + it.col }
                    .joinToString(separator = "-") { cellStringFormat(it) }
            }
            // 遍历每个组合作为枢纽
            for (pivotCells in groups) {
                // 遍历每个枢纽在其它group形成的数对
                var yMapOfPivotsAndWingCandidates = mutableMapOf<Int, MutableMap<Cell, MutableList<Cell>>>()
                for (pivot in pivotCells) {
                    val xPairsInSameGroupWithY = cellsWithX
                        .filter { !pivotCells.contains(it)
                                && areInSameGroup(pivot, it)
                                && getCellNotes(it).size == 2 && arePairWithX(pivot, it, x) }
                        // 如果有枢纽找不到数对，则跳过该枢纽组合
                    if (xPairsInSameGroupWithY.isEmpty()) {
                        yMapOfPivotsAndWingCandidates.clear()
                        break
                    }
                    // 按Y分组
                    for (cell in xPairsInSameGroupWithY) {
                        val y = getCellNotes(cell).first { it != x }
                        if (!yMapOfPivotsAndWingCandidates.containsKey(y)) {
                            yMapOfPivotsAndWingCandidates[y] = mutableMapOf()
                        }
                        if (!yMapOfPivotsAndWingCandidates[y]!!.containsKey(pivot)) {
                            yMapOfPivotsAndWingCandidates[y]!![pivot] = mutableListOf()
                        }
                        yMapOfPivotsAndWingCandidates[y]!![pivot]!!.add(cell)
                    }
                }
                if (yMapOfPivotsAndWingCandidates.isEmpty()) continue
                // 针对每个Y，取出每个枢纽的一个翼进行组合
                for ((y, pivotMapOfWingCandidates) in yMapOfPivotsAndWingCandidates) {
                    // 如果枢纽数量不足，跳过
                    if (pivotMapOfWingCandidates.size < pivotCells.size) continue
                    // 如果枢纽数量大于，则丢出一个错误
                    if (pivotMapOfWingCandidates.size > pivotCells.size) {
                        throw IllegalStateException("Pivot map size greater than pivot cells size")
                    }
                    // 遍历每个枢纽中取出一个翼的所有可能组合
                    val allPivotWingCandidates = pivotMapOfWingCandidates.values.toList()
                    val wingCombinations = allPivotWingCandidates.pickOneFromEachCombination()
                    // 遍历每个组合
                    for (wingCandidates in wingCombinations) {
                        if (wingCandidates.size < pivotCells.size) continue
                        // 如果大于则丢出一个错误
                        if (wingCandidates.size > pivotCells.size) {
                            throw IllegalStateException("Wing candidates size greater than pivot cells size")
                        }
                        // 如果有相同的翼则跳过
                        if (wingCandidates.distinct().size < wingCandidates.size) continue
                        val commonVisibleCells = getCommonVisibleCells(*wingCandidates.toTypedArray())
                            .filter { cell -> cell.value == 0 }
                        if (commonVisibleCells.isEmpty()) continue
                        // 找到可排除的候选数Y
                        val notesToRemove = commonVisibleCells.flatMap { cell ->
                            notes.filter { note ->
                                note.row == cell.row && note.col == cell.col && note.value == y
                            }
                        }
                        if (notesToRemove.isEmpty()) continue
                        return createWWingHint(
                            pivotCells = pivotCells,
                            wingCells = wingCandidates,
                            numberX = x,
                            numberY = y,
                            affectedCells = commonVisibleCells,
                            notesToRemove = notesToRemove
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * 检查XYZ-Wing模式
     * XYZ-Wing由三个单元格组成：
     * - 一个XYZ单元格（有三个候选数X、Y、Z）作为枢纽
     * - 一个XZ单元格（与XYZ在同一单元，有候选数X和Z）
     * - 一个YZ单元格（与XYZ在同一单元，有候选数Y和Z）
     * 可排除XYZ、XZ和YZ共同可见单元格中的Z
     */
    private fun checkForXYZWing(): AdvancedHintData? {
        // 收集所有有三个候选数的单元格作为潜在枢纽
        val triValueCells = getTriValueCells()
        if (triValueCells.isEmpty()) return null

        // 收集所有有两个候选数的单元格作为潜在翼细胞
        val biValueCells = getBiValueCells()
        if (biValueCells.size < 2) return null

        // 遍历所有可能的XYZ枢纽单元格
        for (xyzCell in triValueCells) {
            val xyzNotes = getCellNotes(xyzCell).toList()
            if (xyzNotes.size != 3) continue // 确保是三值单元格

            val x = xyzNotes[0]
            val y = xyzNotes[1]
            val z = xyzNotes[2]

            // 找到与XYZ在同一单元且包含X和Z的双值单元格（XZ）
            val xzCells = biValueCells
                .filter { it != xyzCell && areInSameGroup(xyzCell, it) }
                .filter { cell -> getCellNotes(cell) == setOf(x, z) }

            // 找到与XYZ在同一单元且包含Y和Z的双值单元格（YZ）
            val yzCells = biValueCells
                .filter { it != xyzCell && areInSameGroup(xyzCell, it) }
                .filter { cell -> getCellNotes(cell) == setOf(y, z) }

            // 检查XZ和YZ的组合
            for (xzCell in xzCells) {
                for (yzCell in yzCells) {
                    if (xzCell == yzCell) continue

                    // 找到三个单元格共同可见的单元格
                    val commonCells = getCommonVisibleCells(xyzCell, xzCell, yzCell)
                        .filter { cell -> cell.value == 0 } // 只考虑空单元格
                        .filter { cell -> getCellNotes(cell).contains(z) }

                    if (commonCells.isNotEmpty()) {
                        // 收集可移除的候选数
                        val notesToRemove = commonCells.flatMap { cell ->
                            notes.filter { note ->
                                note.row == cell.row && note.col == cell.col && note.value == z
                            }
                        }

                        if (notesToRemove.isNotEmpty()) {
                            return createXyOrXyzWingHint(
                                type = "XYZ",
                                pivotCell = xyzCell,
                                wingCells = listOf(xzCell, yzCell),
                                number = z,
                                affectedCells = commonCells,
                                notesToRemove = notesToRemove,
                                x = x,
                                y = y,
                                z = z
                            )
                        }
                    }
                }
            }
        }

        return null
    }

    private fun createWWingHint(
        pivotCells: List<Cell>,
        wingCells: List<Cell>,
        numberX: Int,
        numberY: Int,
        affectedCells: List<Cell>,
        notesToRemove: List<Note>
    ): AdvancedHintData {
        val titleRes = R.string.hint_w_wing_title
        val detailRes = R.string.hint_w_wing_detail

        val pivotStr = pivotCells.joinToString(", ") { cellStringFormat(it) }
        val wingStr = wingCells.joinToString(", ") { cellStringFormat(it) }
        val affectedStr = affectedCells.joinToString(", ") { cellStringFormat(it) }
        val detailArgs = listOf(
            numberX.toString(), numberY.toString(),
            pivotStr, wingStr, affectedStr
        )
        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = pivotCells + wingCells,
            notesToRemove = notesToRemove
        )
    }

    /**
     * 创建XY-Wing或XYZ-Wing提示数据对象
     */
    private fun createXyOrXyzWingHint(
        type: String,
        pivotCell: Cell,
        wingCells: List<Cell>,
        number: Int,
        affectedCells: List<Cell>,
        notesToRemove: List<Note>,
        x: Int,
        y: Int,
        z: Int
    ): AdvancedHintData {
        val titleRes = if (type == "XY") {
            R.string.hint_xy_wing_title
        } else {
            R.string.hint_xyz_wing_title
        }

        val detailRes = if (type == "XY") {
            R.string.hint_xy_wing_detail
        } else {
            R.string.hint_xyz_wing_detail
        }

        val pivotStr = cellStringFormat(pivotCell)
        val wing1Str = cellStringFormat(wingCells[0])
        val wing2Str = cellStringFormat(wingCells[1])
        val affectedStr = affectedCells.joinToString(", ") { cellStringFormat(it) }

        val detailArgs = listOf(
            x.toString(), y.toString(), z.toString(),
            pivotStr, wing1Str, wing2Str, affectedStr
        )

        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = listOf(pivotCell) + wingCells,
            notesToRemove = notesToRemove
        )
    }


    private fun createXYChainHint(
        path: List<Pair<Cell,Int>>,
        affectedCells: List<Cell>,
        numToRemove: Int,
        notesToRemove: List<Note>
    ): AdvancedHintData {
        val titleRes = R.string.hint_xy_chain_title
        val detailRes = R.string.hint_xy_chain_detail

        val pathStr = path.joinToString("→") { cellStringFormat(it.first) }
        val affectedStr = affectedCells.joinToString(", ") { cellStringFormat(it) }
        val numPathStr = numToRemove.toString() + "→" + path.joinToString("→") { it.second.toString() }
        val detailArgs = listOf(
            numToRemove.toString(),
            pathStr,
            numPathStr,
            affectedStr
        )
        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = path.map { it.first },
            notesToRemove = notesToRemove
        )

    }

    // 辅助方法：获取只有两个候选数的单元格
    private fun getBiValueCells(): List<Cell> {
        return board.flatten()
            .filter { cell -> cell.value == 0 }
            .filter { cell -> getCellNotes(cell).size == 2 }
    }

    // 辅助方法：获取有三个候选数的单元格
    private fun getTriValueCells(): List<Cell> {
        return board.flatten()
            .filter { cell -> cell.value == 0 }
            .filter { cell -> getCellNotes(cell).size == 3 }
    }

    // 辅助方法：获取单元格的候选数
    private fun getCellNotes(cell: Cell): Set<Int> {
        // return notes.filter { note ->
        //     note.row == cell.row && note.col == cell.col
        // }.map { it.value }.toSet()
        return cellNotesCache[Pair(cell.row, cell.col)] ?: emptySet()
    }

    // 辅助方法：检查两个单元格是否在同一组（行、列或宫）
    private fun areInSameGroup(cell1: Cell, cell2: Cell): Boolean {
        if (cell1.row == cell2.row) return true // 同一行
        if (cell1.col == cell2.col) return true // 同一列
        if (getBoxIndex(cell1.row, cell1.col) == getBoxIndex(cell2.row, cell2.col)) return true // 同一宫
        return false
    }

    // 辅助方法：判断两个候选数均含有X、在同一组的单元格，是否能形成数对
    private fun arePairWithX(cell1: Cell, cell2: Cell, x: Int): Boolean {
        if (cell1.row == cell2.row) {
            // 同一行
            val rowCells = rows[cell1.row]
            val otherCells = rowCells.filter { it != cell1 && it != cell2 && it.value == 0 }
            if (otherCells.none { getCellNotes(it).contains(x) }) return true
        }
        if (cell1.col == cell2.col) {
            // 同一列
            val colCells = columns[cell1.col]
            val otherCells = colCells.filter { it != cell1 && it != cell2 && it.value == 0 }
            if (otherCells.none { getCellNotes(it).contains(x) }) return true
        }
        if (getBoxIndex(cell1.row, cell1.col) == getBoxIndex(cell2.row, cell2.col)) {
            // 同一宫
            val boxCells = boxes[getBoxIndex(cell1.row, cell1.col)]
            val otherCells = boxCells.filter { it != cell1 && it != cell2 && it.value == 0 }
            if (otherCells.none { getCellNotes(it).contains(x) }) return true
        }
        return false
    }

    // 辅助方法：获取多个单元格共同可见的单元格（同一行、列或宫）
    private fun getCommonVisibleCells(vararg cells: Cell): List<Cell> {
        if (cells.isEmpty()) return emptyList()

        // 初始化为第一个单元格的可见单元格
        var commonCells = getVisibleCells(cells[0])

        // 逐步求交集
        for (i in 1 until cells.size) {
            commonCells = commonCells.intersect(getVisibleCells(cells[i])).toList()
            if (commonCells.isEmpty()) break
        }

        // 排除自身
        return commonCells.filter { cell ->
            !cells.any { it.row == cell.row && it.col == cell.col }
        }
    }

    // 辅助方法：获取一个单元格的所有可见单元格（同一行、列和宫）
    private fun getVisibleCells(cell: Cell): List<Cell> =
        visibleCellsCache[Pair(cell.row, cell.col)] ?: emptyList()

    /**
     * 检查鱼模式（Swordfish和Jellyfish）
     * Swordfish涉及3行3列，Jellyfish涉及4行4列
     */
    private fun checkForFishPatterns(): AdvancedHintData? {
        if (notes.isEmpty()) return null

        // 先检查更简单的Swordfish（3x3）
        val swordfish = checkFishPattern(3)
        if (swordfish != null) {
            return swordfish
        }

        // 再检查更复杂的Jellyfish（4x4）
        val jellyfish = checkFishPattern(4)
        if (jellyfish != null) {
            return jellyfish
        }

        return null
    }

    /**
     * 检查特定大小的鱼模式
     * @param size 鱼模式大小（3=Swordfish，4=Jellyfish）
     */
    private fun checkFishPattern(size: Int): AdvancedHintData? {
        val maxSize = type.size
        if (size < 3 || size > 4 || size > maxSize) return null

        // 检查行基鱼模式
        val rowFish = checkRowBasedFish(size)
        if (rowFish != null) {
            return rowFish
        }

        // 检查列基鱼模式
        val colFish = checkColumnBasedFish(size)
        if (colFish != null) {
            return colFish
        }

        return null
    }

    /**
     * 检查行基鱼模式
     * 当数字N在N行中仅出现在N列中时，可排除这N列中其他行的N
     */
    private fun checkRowBasedFish(size: Int): AdvancedHintData? {
        val fishType = if (size == 3) "Swordfish" else "Jellyfish"
        val maxRow = rows.size

        // 遍历每个可能的数字
        for (num in 1..type.size) {
            // 收集包含该数字候选且候选位置数量<=size的行
            val candidateRows = mutableMapOf<Int, Set<Int>>()
            for (rowNum in 0 until maxRow) {
                val colsWithNote = getColumnsWithNoteInRow(rowNum, num)
                if (colsWithNote.isNotEmpty() && colsWithNote.size <= size) {
                    candidateRows[rowNum] = colsWithNote
                }
            }

            // 需要至少有size行才能形成鱼模式
            if (candidateRows.size >= size) {
                // 检查所有可能的size行组合
                for (rowCombination in candidateRows.keys.toList().combinations(size)) {
                    // 获取这些行中所有出现该数字的列
                    val allCols = mutableSetOf<Int>()
                    rowCombination.forEach { row ->
                        allCols.addAll(candidateRows[row] ?: emptySet())
                    }

                    // 列的数量必须等于size才能形成鱼模式
                    if (allCols.size == size) {
                        // 验证每一行的候选列都是这些列的子集
                        val valid = rowCombination.all { row ->
                            (candidateRows[row] ?: emptySet()).all { it in allCols }
                        }

                        if (valid) {
                            // 找到可移除的候选数
                            val (notesToRemove, affectedCells) = findRemovableNotesInFish(
                                num = num,
                                baseRows = rowCombination,
                                baseCols = allCols.toList(),
                                isRowBased = true
                            )

                            if (notesToRemove.isNotEmpty()) {
                                return createFishHint(
                                    number = num,
                                    size = size,
                                    isRowBased = true,
                                    baseRows = rowCombination,
                                    baseCols = allCols.toList(),
                                    notesToRemove = notesToRemove,
                                    affectedCells = affectedCells
                                )
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * 检查列基鱼模式
     * 当数字N在N列中仅出现在N行中时，可排除这N行中其他列的N
     */
    private fun checkColumnBasedFish(size: Int): AdvancedHintData? {
        val maxCol = columns.size

        // 遍历每个可能的数字
        for (num in 1..type.size) {
            // 收集包含该数字候选且候选位置数量<=size的列
            val candidateCols = mutableMapOf<Int, Set<Int>>()
            for (colNum in 0 until maxCol) {
                val rowsWithNote = getRowsWithNoteInColumn(colNum, num)
                if (rowsWithNote.isNotEmpty() && rowsWithNote.size <= size) {
                    candidateCols[colNum] = rowsWithNote
                }
            }

            // 需要至少有size列才能形成鱼模式
            if (candidateCols.size >= size) {
                // 检查所有可能的size列组合
                for (colCombination in candidateCols.keys.toList().combinations(size)) {
                    // 获取这些列中所有出现该数字的行
                    val allRows = mutableSetOf<Int>()
                    colCombination.forEach { col ->
                        allRows.addAll(candidateCols[col] ?: emptySet())
                    }

                    // 行的数量必须等于size才能形成鱼模式
                    if (allRows.size == size) {
                        // 验证每一列的候选行都是这些行的子集
                        val valid = colCombination.all { col ->
                            (candidateCols[col] ?: emptySet()).all { it in allRows }
                        }

                        if (valid) {
                            // 找到可移除的候选数
                            val (notesToRemove, affectedCells) = findRemovableNotesInFish(
                                num = num,
                                baseRows = allRows.toList(),
                                baseCols = colCombination,
                                isRowBased = false
                            )

                            if (notesToRemove.isNotEmpty()) {
                                return createFishHint(
                                    number = num,
                                    size = size,
                                    isRowBased = false,
                                    baseRows = allRows.toList(),
                                    baseCols = colCombination,
                                    notesToRemove = notesToRemove,
                                    affectedCells = affectedCells
                                )
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * 找到鱼模式中可移除的候选数
     */
    private fun findRemovableNotesInFish(
        num: Int,
        baseRows: List<Int>,
        baseCols: List<Int>,
        isRowBased: Boolean
    ): Pair<List<Note>, List<Cell>> {
        val notesToRemove = mutableListOf<Note>()
        val affectedCells = mutableListOf<Cell>()

        if (isRowBased) {
            // 行基鱼模式：移除baseCols列中不在baseRows行中的num候选
            for (col in baseCols) {
                for (row in rows.indices) {
                    if (row !in baseRows) {
                        val cell = board[row][col]
                        cellNoteIndex[Triple(row, col, num)]?.let { note ->
                            notesToRemove.add(note)
                            affectedCells.add(cell)
                        }
                    }
                }
            }
        } else {
            // 列基鱼模式：移除baseRows行中不在baseCols列中的num候选
            for (row in baseRows) {
                for (col in columns.indices) {
                    if (col !in baseCols) {
                        val cell = board[row][col]
                        cellNoteIndex[Triple(row, col, num)]?.let { note ->
                            notesToRemove.add(note)
                            affectedCells.add(cell)
                        }
                    }
                }
            }
        }

        return Pair(notesToRemove, affectedCells)
    }

    /**
     * 创建鱼模式提示数据对象
     */
    private fun createFishHint(
        number: Int,
        size: Int,
        isRowBased: Boolean,
        baseRows: List<Int>,
        baseCols: List<Int>,
        notesToRemove: List<Note>,
        affectedCells: List<Cell>
    ): AdvancedHintData {
        val formattedRows = baseRows.sorted().map { it + 1 }.joinToString(", ")
        val formattedCols = baseCols.sorted().map { it + 1 }.joinToString(", ")
        val affectedCellsStr = affectedCells.joinToString(", ") { cellStringFormat(it) }

        val (titleRes, detailRes, detailArgs) = if (isRowBased) {
            Triple(
                if (size == 3) R.string.hint_swordfish_title else R.string.hint_jellyfish_title,
                if (size == 3) R.string.hint_swordfish_row_detail else R.string.hint_jellyfish_row_detail,
                listOf(
                    number.toString(),
                    formattedRows,
                    formattedCols,
                    affectedCellsStr
                )
            )
        } else {
            Triple(
                if (size == 3) R.string.hint_swordfish_title else R.string.hint_jellyfish_title,
                if (size == 3) R.string.hint_swordfish_col_detail else R.string.hint_jellyfish_col_detail,
                listOf(
                    number.toString(),
                    formattedCols,
                    formattedRows,
                    affectedCellsStr
                )
            )
        }

        // 收集所有构成鱼模式的单元格作为帮助单元格
        val helpCells = baseRows.flatMap { row ->
            baseCols.map { col -> board[row][col] }
        }.distinct() + baseRows.flatMap { row -> rows[row] } +
                baseCols.flatMap { col -> columns[col] }

        return AdvancedHintData(
            titleRes = titleRes,
            textResWithArg = Pair(detailRes, detailArgs),
            targetCells = affectedCells,
            helpCells = helpCells.filter { !affectedCells.contains(it) },
            notesToRemove = notesToRemove
        )
    }
    /**
     * 检查带鳍鱼模式变体：带鳍X-Wing、带鳍剑鱼、带鳍水母
     */
    private fun checkForFinnedFishVariants(): AdvancedHintData? {
        if (notes.isEmpty()) return null

        for (size in 2..4) {
            if (size > type.size) continue

            // 检查行基模式
            val rowHint = checkFinnedFishByOrientation(size, isRowBased = true)
            if (rowHint != null) return rowHint

            // 检查列基模式
            val colHint = checkFinnedFishByOrientation(size, isRowBased = false)
            if (colHint != null) return colHint
        }

        return null
    }

    /**
     * 按方向（行/列）检查特定带鳍鱼模式
     * @param size 鱼模式大小（2=X-Wing, 3=剑鱼, 4=水母）
     * @param isRowBased 是否为行基模式
     */
    private fun checkFinnedFishByOrientation(
        size: Int,
        isRowBased: Boolean
    ): AdvancedHintData? {
        for (num in 1..type.size) {
            val cellsWithNoteNum = notes.filter { it.value == num }
                .map { note -> board[note.row][note.col] }
                .distinct()
                .filter { cell -> cell.value == 0 }
            // Finned Sashimi至少要有2*size个候选数所在格
            if (cellsWithNoteNum.size < 2 * size) continue
            // 首先，根据方向收集候选行/列及其候选位置
            val cellGroups = cellsWithNoteNum.groupBy { if (isRowBased) it.row else it.col }
            if (cellGroups.size < size) continue
            // 鳍的最大数量限制：一行/列最多有宫的宽度/长度-1的鳍加一个鱼的部分
            val maxFinsInGroup = (if (isRowBased) type.sectionWidth else type.sectionHeight) - 1
            val finGroupsCandidates = cellGroups.filter { it.value.size in 2..size + maxFinsInGroup }
            if (finGroupsCandidates.size < size) continue
            for (fishCandidates in finGroupsCandidates.keys.toList().combinations(size)) {
                // 取出鱼在列/行的并集
                val allPositions = mutableMapOf<Int, MutableList<Cell>>()
                fishCandidates.forEach { group ->
                    val positions = finGroupsCandidates[group]!!
                    positions.forEach { pos ->
                        val key = if (isRowBased) pos.col else pos.row
                        if (!allPositions.containsKey(key)) {
                            allPositions[key] = mutableListOf()
                        }
                        allPositions[key]!!.add(pos)
                    }
                }
                // 如果并集的数量不在size+1到size+maxFinsInGroup之间，则不符合带鳍鱼的条件
                if (allPositions.size !in (size + 1)..(size + maxFinsInGroup)) continue
                // 从所有的列/行中分离鱼和鳍
                for (baseFishVersGroupsKey in allPositions.keys.toList().combinations(size)) {
                    val finPositions = allPositions.filter { (key, _) -> !baseFishVersGroupsKey.contains(key) }
                        .values.flatten()
                    val baseFishPositions = allPositions.filter { (key, _) -> baseFishVersGroupsKey.contains(key) }
                        .values.flatten()
                    // 鳍与鱼身均不能为空
                    if (finPositions.isEmpty() || baseFishPositions.isEmpty()) throw IllegalStateException("Fin groups or base fish positions cannot be empty")

                    // 如果鳍不在同一宫，则跳过该组合
                    val finBoxes = finPositions.map { getBoxIndex(it.row, it.col) }.toSet()
                    if (finBoxes.size != 1) continue
                    // 计算鱼鳍所在的行/列包含鱼身cell的最少数量
                    val finGroupKeys = finPositions.map { if (isRowBased) it.row else it.col }.toSet()
                    val minBaseFishInFinGroups = finGroupKeys.minOfOrNull { key ->
                        baseFishPositions.count { cell ->
                            if (isRowBased) cell.row == key else cell.col == key
                        }
                    } ?: 0
                    // 至少为1
                    if (minBaseFishInFinGroups < 1) continue
                    // 如果minBaseFishInFinGroups为2或更多，则判断是否成立标准带鳍鱼
                    if (minBaseFishInFinGroups >= 2) {
                        // 成立条件：鱼身部分所在的列/行均必须有至少两个鱼身
                        if (baseFishPositions.groupBy { if (isRowBased) it.col else it.row }
                                .map { it.value.size }
                                .any { it < 2 }
                        ) {
                            continue
                        }
                        val (notesToRemove, affectedCells) = findRemovableNotesInFinnedFish(
                            num = num,
                            baseKeys = fishCandidates,
                            basePositions = baseFishVersGroupsKey,
                            fins = finPositions,
                            isRowBased = isRowBased
                        )
                        if (notesToRemove.isNotEmpty()) {
                            // 创建并返回提示
                            return createFinnedFishHint(
                                size = size,
                                number = num,
                                baseKeys = fishCandidates,
                                basePositions = baseFishVersGroupsKey,
                                fins = finPositions,
                                isRowBased = isRowBased,
                                notesToRemove = notesToRemove,
                                affectedCells = affectedCells
                            )
                        }
                        continue
                    }
                    // 判断退化的带鳍鱼（Finned Sashimi），即每个鱼鳍所在的行/列仅有一个鱼身

                    val (notesToRemove, affectedCells) = findRemovableNotesInFinnedFish(
                        num = num,
                        baseKeys = fishCandidates,
                        basePositions = baseFishVersGroupsKey,
                        fins = finPositions,
                        isRowBased = isRowBased
                    )
                    if (notesToRemove.isNotEmpty()) {
                        // 创建并返回提示
                        return createFinnedSashimiHint(
                            size = size,
                            number = num,
                            baseKeys = fishCandidates,
                            basePositions = baseFishVersGroupsKey,
                            fins = finPositions,
                            isRowBased = isRowBased,
                            notesToRemove = notesToRemove,
                            affectedCells = affectedCells
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * 找到带鳍鱼中可移除的候选数
     */
    private fun findRemovableNotesInFinnedFish(
        num: Int,
        baseKeys: List<Int>,
        basePositions: List<Int>,
        fins: List<Cell>,
        isRowBased: Boolean
    ): Pair<List<Note>, List<Cell>> {
        val notesToRemove = mutableListOf<Note>()
        val affectedCells = mutableListOf<Cell>()
        // 由于前面已经确保所有鳍在同一宫，这里直接取第一个鳍的宫
        val finBox = fins[0].let { getBoxIndex(it.row, it.col) }

        if (isRowBased) {
            // 行基带鳍鱼：排除鳍所在宫与基本列的交叉
            for (col in basePositions) {
                for (row in rows.indices) {
                    if (row !in baseKeys && getBoxIndex(row, col) == finBox) {
                        addRemovableNote(num, row, col, notesToRemove, affectedCells)
                    }
                }
            }
        } else {
            // 列基带鳍鱼：排除鳍所在宫与基本行的交叉
            for (row in basePositions) {
                for (col in columns.indices) {
                    if (col !in baseKeys && getBoxIndex(row, col) == finBox) {
                        addRemovableNote(num, row, col, notesToRemove, affectedCells)
                    }
                }
            }
        }

        return Pair(notesToRemove, affectedCells)
    }

    /**
     * 创建带鳍鱼提示
     */
    private fun createFinnedFishHint(
        size: Int,
        number: Int,
        baseKeys: List<Int>,
        basePositions: List<Int>,
        fins: List<Cell>,
        isRowBased: Boolean,
        notesToRemove: List<Note>,
        affectedCells: List<Cell>
    ): AdvancedHintData {
        // 格式化行号/列号（转换为1-based）
        val baseKeysStr = baseKeys.sorted().map { it + 1 }.joinToString(", ")
        val basePositionsStr = basePositions.sorted().map { it + 1 }.joinToString(", ")
        val finsStr = fins.joinToString(", ") { cellStringFormat(it) }
        val affectedCellsStr = affectedCells.joinToString(", ") { cellStringFormat(it) }
        val variantName = when (size) {
            2 -> R.string.hint_finned_x_wing_title
            3 -> R.string.hint_finned_swordfish_title
            else -> R.string.hint_finned_jellyfish_title
        }
        val detailCol = when (size) {
            2 -> R.string.hint_finned_x_wing_col_detail
            3 -> R.string.hint_finned_swordfish_col_detail
            else -> R.string.hint_finned_jellyfish_col_detail
        }
        val detailRow = when (size) {
            2 -> R.string.hint_finned_x_wing_row_detail
            3 -> R.string.hint_finned_swordfish_row_detail
            else -> R.string.hint_finned_jellyfish_row_detail
        }

        return AdvancedHintData(
            titleRes = variantName,
            textResWithArg = Pair(
                if (isRowBased) detailRow else detailCol,
                listOf<String>(
                    number.toString(),
                    baseKeysStr,
                    basePositionsStr,
                    finsStr,
                    affectedCellsStr
                )
            ),
            targetCells = affectedCells,
            helpCells = baseKeys.flatMap { key ->
                basePositions.map { pos ->
                    if (isRowBased) board[key][pos] else board[pos][key]
                }
            }.distinct() + fins + fins,
            notesToRemove = notesToRemove
        )
    }


    /**
     * 创建带鳍生鱼片提示
     */
    private fun createFinnedSashimiHint(
        size: Int,
        number: Int,
        baseKeys: List<Int>,
        basePositions: List<Int>,
        fins: List<Cell>,
        isRowBased: Boolean,
        notesToRemove: List<Note>,
        affectedCells: List<Cell>
    ): AdvancedHintData {
        // 格式化行号/列号（转换为1-based）
        val baseKeysStr = baseKeys.sorted().map { it + 1 }.joinToString(", ")
        val basePositionsStr = basePositions.sorted().map { it + 1 }.joinToString(", ")
        val finsStr = fins.joinToString(", ") { cellStringFormat(it) }
        val affectedCellsStr = affectedCells.joinToString(", ") { cellStringFormat(it) }
        val variantName = when (size) {
            2 -> R.string.hint_finned_sashimi_x_wing_title
            3 -> R.string.hint_finned_sashimi_swordfish_title
            else -> R.string.hint_finned_sashimi_jellyfish_title
        }
        val detailCol = when (size) {
            2 -> R.string.hint_finned_sashimi_x_wing_col_detail
            3 -> R.string.hint_finned_sashimi_swordfish_col_detail
            else -> R.string.hint_finned_sashimi_jellyfish_col_detail
        }
        val detailRow = when (size) {
            2 -> R.string.hint_finned_sashimi_x_wing_row_detail
            3 -> R.string.hint_finned_sashimi_swordfish_row_detail
            else -> R.string.hint_finned_sashimi_jellyfish_row_detail
        }

        return AdvancedHintData(
            titleRes = variantName,
            textResWithArg = Pair(
                if (isRowBased) detailRow else detailCol,
                listOf(
                    number.toString(),
                    baseKeysStr,
                    basePositionsStr,
                    finsStr,
                    affectedCellsStr
                )
            ),
            targetCells = affectedCells,
            helpCells = baseKeys.flatMap { key ->
                basePositions.map { pos ->
                    if (isRowBased) board[key][pos] else board[pos][key]
                }
            }.distinct() + fins + fins,
            notesToRemove = notesToRemove
        )
    }


    // 辅助方法：添加可移除的候选数（复用自之前的实现）
    private fun addRemovableNote(
        num: Int,
        row: Int,
        col: Int,
        notesToRemove: MutableList<Note>,
        affectedCells: MutableList<Cell>
    ) {
        cellNoteIndex[Triple(row, col, num)]?.let { note ->
            notesToRemove.add(note)
            affectedCells.add(board[row][col])
        }
    }


    /**
     * 辅助方法：获取指定行中包含指定数字候选的列
     */
    private fun getColumnsWithNoteInRow(row: Int, number: Int): Set<Int> {
        return rows[row]
            .filter { cell -> cellNotesCache[Pair(row, cell.col)]?.contains(number) == true }
            .map { it.col }
            .toSet()
    }

    /**
     * 辅助方法：获取指定列中包含指定数字候选的行
     */
    private fun getRowsWithNoteInColumn(col: Int, number: Int): Set<Int> {
        return columns[col]
            .filter { cell -> cellNotesCache[Pair(cell.row, col)]?.contains(number) == true }
            .map { it.row }
            .toSet()
    }


    private fun getRows(): List<List<Cell>> {
        return board
    }

    private fun getColumns(): List<List<Cell>> {
        val cols = MutableList(type.size) { mutableListOf<Cell>() }
        for (i in 0 until type.size) {
            for (j in 0 until type.size) {
                cols[j].add(board[i][j])
            }
        }
        return cols.map { it.toList() }
    }

//    private fun getBoxNumber(row: Int, col: Int): Int {
//        val sectionRow = row / type.sectionHeight
//        val sectionColumn = col / type.sectionWidth
//        val sectorsPerRow = type.size / type.sectionWidth
//        return sectionRow * sectorsPerRow + sectionColumn
//    }

    private fun getBoxes(): List<List<Cell>> {
        val size = type.size
        val sectionWidth = type.sectionWidth
        val sectionHeight = type.sectionHeight

        val boxes = MutableList(sectionWidth * sectionHeight) { mutableListOf<Cell>() }
        for (i in 0 until size) {
            for (j in 0 until size) {
                val boxNumber = getBoxIndex(i, j)
                boxes[boxNumber].add(board[i][j])
            }
        }
        return boxes
    }

    private fun cellStringFormat(cell: Cell) = "r${cell.row + 1}c${cell.col + 1}"
}