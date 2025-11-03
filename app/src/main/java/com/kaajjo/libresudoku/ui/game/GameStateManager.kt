package com.kaajjo.libresudoku.ui.game

import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.utils.SudokuUtils

/**
 * 游戏状态管理器
 * 
 * 职责：
 * 1. 统一管理所有游戏状态修改
 * 2. 自动处理副作用（擦除候选数、检查错误等）
 * 3. 提供清晰的状态修改接口
 * 4. 通知外部状态变化
 * 
 * 设计原则：
 * - 单一入口：所有状态修改都通过此类
 * - 清晰的副作用链：修改 → 自动擦除 → 检查错误 → 通知变化
 * - 模式切换：支持自动模式（跳过通知）和手动模式（触发通知）
 */
class GameStateManager {
    private val sudokuUtils = SudokuUtils()
    
    /**
     * 配置参数
     */
    data class Config(
        val gameType: GameType,
        val solvedBoard: List<List<Cell>>,
        val mistakesMethod: Int,
        val autoEraseNotesEnabled: Boolean,
        val onMistake: () -> Unit = {},
        val onGameCompleted: () -> Unit = {}
    )
    
    /**
     * 执行模式
     */
    enum class ExecutionMode {
        MANUAL,      // 手动模式：会触发状态变化通知
        AUTO         // 自动模式：不触发状态变化通知（避免递归）
    }
    
    /**
     * 修改cell的值
     * 
     * @param config 配置参数
     * @param board 当前棋盘
     * @param notes 当前候选数
     * @param row 行
     * @param col 列
     * @param value 新值
     * @param mode 执行模式
     * @param onStateChanged 状态变化回调（仅在手动模式下调用）
     * @return 修改后的状态
     */
    fun setCellValue(
        config: Config,
        board: List<List<Cell>>,
        notes: List<Note>,
        row: Int,
        col: Int,
        value: Int,
        mode: ExecutionMode = ExecutionMode.MANUAL,
        onStateChanged: (GameStateChange) -> Unit = {}
    ): GameState {
        var newBoard = copyBoard(board)
        var newNotes = notes
        
        // 1. 设置cell值
        newBoard[row][col].value = value
        
        // 2. 如果设置了非零值，清除该位置的所有候选数
        if (value != 0) {
            newNotes = clearNotesAtCell(newNotes, row, col)
            
            // 3. 自动擦除相关候选数
            if (config.autoEraseNotesEnabled) {
                newNotes = autoEraseRelatedNotes(config, newBoard, newNotes, newBoard[row][col])
            }
            
            // 4. 检查错误
            newBoard = checkMistakes(config, newBoard, row, col)
        } else {
            // 清除值时，清除错误标记
            newBoard[row][col].error = false
        }
        
        // 5. 检查游戏是否完成
        val completed = isGameCompleted(config, newBoard)
        
        // 6. 创建结果状态
        val result = GameState(
            board = newBoard,
            notes = newNotes,
            completed = completed,
            changeType = if (value != 0) ChangeType.CELL_VALUE_SET else ChangeType.CELL_VALUE_CLEAR
        )
        
        // 7. 通知状态变化（仅在手动模式下，避免递归检测）
        if (mode == ExecutionMode.MANUAL) {
            onStateChanged(GameStateChange.BoardModified(result))
        }
        
        // 8. 游戏完成处理（无论什么模式都要执行）
        // 设计原则：游戏完成是一个"事件"，必须立即处理
        // 不应该受执行模式影响（手动完成和自动完成都是完成）
        if (completed) {
            config.onGameCompleted()
        }
        
        return result
    }
    
    /**
     * 添加或删除候选数
     */
    fun toggleNote(
        board: List<List<Cell>>,
        notes: List<Note>,
        row: Int,
        col: Int,
        value: Int,
        mode: ExecutionMode = ExecutionMode.MANUAL,
        onStateChanged: (GameStateChange) -> Unit = {}
    ): GameState {
        val note = Note(row, col, value)
        val newNotes = if (notes.contains(note)) {
            notes.minus(note)
        } else {
            notes.plus(note)
        }
        
        val result = GameState(
            board = board,
            notes = newNotes,
            completed = false,
            changeType = ChangeType.NOTES_MODIFIED
        )
        
        if (mode == ExecutionMode.MANUAL) {
            onStateChanged(GameStateChange.NotesModified(result))
        }
        
        return result
    }
    
    /**
     * 批量修改候选数
     */
    fun modifyNotes(
        board: List<List<Cell>>,
        notes: List<Note>,
        notesToAdd: List<Note>?,
        notesToRemove: List<Note>?,
        mode: ExecutionMode = ExecutionMode.MANUAL,
        onStateChanged: (GameStateChange) -> Unit = {}
    ): GameState {
        var newNotes = notes
        
        if (notesToAdd != null) {
            newNotes = newNotes.plus(notesToAdd).distinct()
        }
        if (notesToRemove != null) {
            newNotes = newNotes.minus(notesToRemove.toSet())
        }
        
        val result = GameState(
            board = board,
            notes = newNotes,
            completed = false,
            changeType = ChangeType.NOTES_MODIFIED
        )
        
        if (mode == ExecutionMode.MANUAL) {
            onStateChanged(GameStateChange.NotesModified(result))
        }
        
        return result
    }
    
    // ============================================================================
    // 私有辅助方法
    // ============================================================================
    
    /**
     * 复制棋盘（深拷贝）
     */
    private fun copyBoard(board: List<List<Cell>>): List<List<Cell>> {
        return board.map { row -> row.map { cell -> cell.copy() } }
    }
    
    /**
     * 清除指定位置的所有候选数
     */
    private fun clearNotesAtCell(notes: List<Note>, row: Int, col: Int): List<Note> {
        return notes.filterNot { it.row == row && it.col == col }
    }
    
    /**
     * 自动擦除相关候选数
     * 当一个cell被填入数字后，擦除同行、同列、同宫的相关候选数
     */
    private fun autoEraseRelatedNotes(
        config: Config,
        board: List<List<Cell>>,
        notes: List<Note>,
        cell: Cell
    ): List<Note> {
        return sudokuUtils.autoEraseNotes(board, notes, cell, config.gameType)
    }
    
    /**
     * 检查错误
     */
    private fun checkMistakes(config: Config, board: List<List<Cell>>, row: Int, col: Int): List<List<Cell>> {
        val newBoard = copyBoard(board)
        
        when (config.mistakesMethod) {
            0 -> {
                // 不检查错误
                newBoard[row][col].error = false
            }
            1 -> {
                // 规则冲突检查
                newBoard[row][col].error = 
                    !sudokuUtils.isValidCellDynamic(newBoard, newBoard[row][col], config.gameType)
                
                // 重新检查其他有错误标记的cell
                newBoard.forEach { cells ->
                    cells.forEach { cell ->
                        if (cell.value != 0 && cell.error) {
                            cell.error = !sudokuUtils.isValidCellDynamic(newBoard, cell, config.gameType)
                        }
                    }
                }
            }
            2 -> {
                // 与答案对比
                if (config.solvedBoard.isNotEmpty()) {
                    newBoard[row][col].error = 
                        config.solvedBoard[row][col].value != newBoard[row][col].value
                }
            }
        }
        
        // 如果有错误，通知
        if (newBoard[row][col].error) {
            config.onMistake()
        }
        
        return newBoard
    }
    
    /**
     * 检查游戏是否完成
     */
    private fun isGameCompleted(config: Config, board: List<List<Cell>>): Boolean {
        if (config.solvedBoard.isEmpty()) return false
        
        for (i in config.solvedBoard.indices) {
            for (j in config.solvedBoard.indices) {
                if (config.solvedBoard[i][j].value != board[i][j].value) {
                    return false
                }
            }
        }
        return true
    }
}

/**
 * 游戏状态
 */
data class GameState(
    val board: List<List<Cell>>,
    val notes: List<Note>,
    val completed: Boolean,
    val changeType: ChangeType
)

/**
 * 变化类型
 */
enum class ChangeType {
    CELL_VALUE_SET,      // 设置cell值
    CELL_VALUE_CLEAR,    // 清除cell值
    NOTES_MODIFIED       // 修改候选数
}

/**
 * 游戏状态变化事件
 */
sealed class GameStateChange {
    data class BoardModified(val state: GameState) : GameStateChange()
    data class NotesModified(val state: GameState) : GameStateChange()
}

