package com.kaajjo.libresudoku.core.qqwing.advanced_hint

import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.qqwing.GameType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 自动提示管理器
 * 
 * 负责：
 * 1. 检测可自动执行的提示
 * 2. 管理自动执行状态
 * 3. 循环执行所有自动操作
 * 
 * @property onAutoHintDetected 检测到可自动执行的提示时的回调
 * @property onAutoHintApply 应用自动提示的回调（返回是否成功）
 */
class AutoHintManager(
    private val onAutoHintDetected: (hasAutoHint: Boolean) -> Unit = {},
    private val onAutoHintApply: suspend (AdvancedHintData) -> Boolean
) {
    // 是否有可自动执行的提示
    private val _hasAutoHint = MutableStateFlow(false)
    val hasAutoHint: StateFlow<Boolean> = _hasAutoHint.asStateFlow()
    
    // 是否正在自动执行
    private val _isAutoExecuting = MutableStateFlow(false)
    val isAutoExecuting: StateFlow<Boolean> = _isAutoExecuting.asStateFlow()
    
    // 当前检测到的自动提示
    private var currentAutoHint: AdvancedHintData? = null
    
    /**
     * 检测可自动执行的提示
     * 
     * @param board 当前棋盘
     * @param solvedBoard 解决方案棋盘
     * @param notes 当前笔记
     * @param type 游戏类型
     * @param settings 提示设置
     * @return 是否检测到可自动执行的提示
     */
    suspend fun checkForAutoHints(
        board: List<List<Cell>>,
        solvedBoard: List<List<Cell>>,
        notes: List<Note>,
        type: GameType,
        settings: AdvancedHintSettings
    ): Boolean {
        // 如果正在自动执行，不进行检测
        if (_isAutoExecuting.value) {
            return false
        }
        
        // 检查设置中是否有任何自动模式
        if (!settings.hasAutoMode()) {
            _hasAutoHint.value = false
            currentAutoHint = null
            onAutoHintDetected(false)
            return false
        }
        
        // 创建只包含自动模式的设置
        val autoSettings = createAutoOnlySettings(settings)
        
        // 使用AdvancedHint进行检测
        val advancedHint = AdvancedHint(
            type = type,
            board = board,
            solvedBoard = solvedBoard,
            notes = notes,
            settings = autoSettings
        )
        
        val hint = advancedHint.getEasiestHint()
        val hasHint = hint != null
        
        currentAutoHint = hint
        _hasAutoHint.value = hasHint
        onAutoHintDetected(hasHint)
        
        return hasHint
    }
    
    /**
     * 创建只包含自动模式的设置
     */
    private fun createAutoOnlySettings(settings: AdvancedHintSettings): AdvancedHintSettings {
        return AdvancedHintSettings(
            fullHouse = if (settings.fullHouse.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            nakedSingle = if (settings.nakedSingle.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            hiddenSingle = if (settings.hiddenSingle.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            checkWrongValue = if (settings.checkWrongValue.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            checkMissingOrWrongNote = if (settings.checkMissingOrWrongNote.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            lockedCandidates = if (settings.lockedCandidates.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            nakedSubsets = if (settings.nakedSubsets.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            hiddenSubsets = if (settings.hiddenSubsets.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            sueDeCoq = if (settings.sueDeCoq.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            xWings = if (settings.xWings.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            xyWings = if (settings.xyWings.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            xyzWings = if (settings.xyzWings.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            wWings = if (settings.wWings.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            fishPatterns = if (settings.fishPatterns.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            finnedFishVariants = if (settings.finnedFishVariants.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            xChain = if (settings.xChain.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            xyChain = if (settings.xyChain.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            aicType1 = if (settings.aicType1.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
            aicType2 = if (settings.aicType2.isAuto()) HintMode.ENABLED else HintMode.DISABLED,
        )
    }
    
    /**
     * 执行所有自动提示
     * 
     * 循环执行，直到没有可自动执行的提示
     * 
     * @param board 当前棋盘
     * @param solvedBoard 解决方案棋盘
     * @param notes 当前笔记
     * @param type 游戏类型
     * @param settings 提示设置
     * @param maxIterations 最大迭代次数（防止无限循环）
     * @return 执行的提示数量
     */
    suspend fun executeAutoHints(
        board: List<List<Cell>>,
        solvedBoard: List<List<Cell>>,
        notes: List<Note>,
        type: GameType,
        settings: AdvancedHintSettings,
        maxIterations: Int = 100
    ): Int {
        if (_isAutoExecuting.value) {
            return 0
        }
        
        _isAutoExecuting.value = true
        var executedCount = 0
        
        try {
            var currentBoard = board
            var currentNotes = notes
            var iterations = 0
            
            while (iterations < maxIterations) {
                // 检测可自动执行的提示
                val hasHint = checkForAutoHints(
                    currentBoard,
                    solvedBoard,
                    currentNotes,
                    type,
                    settings
                )
                
                if (!hasHint || currentAutoHint == null) {
                    break
                }
                
                // 执行提示
                val hint = currentAutoHint!!
                val success = onAutoHintApply(hint)
                
                if (!success) {
                    break
                }
                
                executedCount++
                iterations++
                
                // 获取更新后的棋盘和笔记（通过回调）
                // 注意：这里假设onAutoHintApply会更新外部状态
                // 在下一次循环时会自动获取新状态
            }
        } finally {
            _isAutoExecuting.value = false
            _hasAutoHint.value = false
            currentAutoHint = null
            onAutoHintDetected(false)
        }
        
        return executedCount
    }
    
    /**
     * 清除状态
     */
    fun clear() {
        _hasAutoHint.value = false
        _isAutoExecuting.value = false
        currentAutoHint = null
    }
}

