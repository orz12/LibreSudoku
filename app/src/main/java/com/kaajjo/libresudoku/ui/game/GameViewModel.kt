package com.kaajjo.libresudoku.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.Cage
import com.kaajjo.libresudoku.core.qqwing.GameDifficulty
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.QQWingController
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHint
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData
import com.kaajjo.libresudoku.core.utils.GameState
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.core.utils.SudokuUtils
import com.kaajjo.libresudoku.core.utils.UndoRedoManager
import com.kaajjo.libresudoku.core.utils.toFormattedString
import com.kaajjo.libresudoku.data.database.model.Record
import com.kaajjo.libresudoku.data.database.model.SavedGame
import com.kaajjo.libresudoku.data.database.model.SudokuBoard
import com.kaajjo.libresudoku.data.datastore.AppSettingsManager
import com.kaajjo.libresudoku.data.datastore.ThemeSettingsManager
import com.kaajjo.libresudoku.domain.repository.RecordRepository
import com.kaajjo.libresudoku.domain.repository.SavedGameRepository
import com.kaajjo.libresudoku.domain.usecase.board.GetBoardUseCase
import com.kaajjo.libresudoku.domain.usecase.board.UpdateBoardUseCase
import com.kaajjo.libresudoku.domain.usecase.record.GetAllRecordsUseCase
import com.kaajjo.libresudoku.navArgs
import com.kaajjo.libresudoku.ui.game.components.ToolBarItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.Timer
import javax.inject.Inject
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedGameRepository: SavedGameRepository,
    private val appSettingsManager: AppSettingsManager,
    private val recordRepository: RecordRepository,
    private val updateBoardUseCase: UpdateBoardUseCase,
    private val getBoardUseCase: GetBoardUseCase,
    themeSettingsManager: ThemeSettingsManager,
    private val savedStateHandle: SavedStateHandle,
    private val getAllRecordsUseCase: GetAllRecordsUseCase
) : ViewModel() {
    init {
        val navArgs: GameScreenNavArgs = savedStateHandle.navArgs()
        val sudokuParser = SudokuParser()
        val continueSaved = navArgs.playedBefore

        viewModelScope.launch(Dispatchers.IO) {
            boardEntity = getBoardUseCase(navArgs.gameUid)
            val savedGame = savedGameRepository.get(boardEntity.uid)

            withContext(Dispatchers.Main) {
                gameType = boardEntity.type
                gameDifficulty = boardEntity.difficulty
            }


            withContext(Dispatchers.Default) {
                initialBoard = sudokuParser.parseBoard(
                    boardEntity.initialBoard,
                    boardEntity.type
                ).toList()
                initialBoard.forEach { cells ->
                    cells.forEach { cell ->
                        cell.locked = cell.value != 0
                    }
                }

                if (boardEntity.solvedBoard.isNotBlank() && !boardEntity.solvedBoard.contains("0")) {
                    solvedBoard = sudokuParser.parseBoard(
                        boardEntity.solvedBoard,
                        boardEntity.type
                    )
                    boardEntity.killerCages?.let { cagesString ->
                        cages = sudokuParser.parseKillerSudokuCages(cagesString)
                    }
                    for (i in solvedBoard.indices) {
                        for (j in solvedBoard.indices) {
                            solvedBoard[i][j].locked = initialBoard[i][j].locked
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        solveBoard()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (savedGame != null && continueSaved) {
                    restoreSavedGame(savedGame)
                } else {
                    gameBoard = initialBoard
                }
                size = gameBoard.size
                undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
                remainingUsesList = countRemainingUses(gameBoard)
                
                // 初始化GameStateManager
                initializeGameStateManager()
                
                // 标记初始化完成
                _isInitialized.emit(true)
            }
            saveGame()
        }
    }
    
    /**
     * 初始化游戏状态管理器
     */
    private fun initializeGameStateManager() {
        gameStateManager = GameStateManager()
    }
    
    /**
     * 获取当前GameStateManager配置
     * 
     * 注意：使用 gameType 而不是 boardEntity.type
     * 因为 boardEntity 是 lateinit，在异步初始化
     * 而 gameType 在 Main 线程中同步赋值，更安全
     */
    private fun getGameStateConfig(): GameStateManager.Config {
        return GameStateManager.Config(
            gameType = gameType,  // ✅ 使用已同步的 gameType
            solvedBoard = solvedBoard,
            mistakesMethod = mistakesMethod.value,
            autoEraseNotesEnabled = autoEraseNotes.value,
            onMistake = {
                mistakesMade++
                if (mistakesLimit.value) {
                    mistakesCount++
                    if (mistakesCount >= PreferencesConstants.MISTAKES_LIMIT) {
                        pauseTimer()
                        giveUp()
                        endGame = true
                    }
                }
            },
            onGameCompleted = {
                // 注意：实际的游戏完成处理在 setValueCell 中统一执行
                // 这里保留回调是为了保持 GameStateManager 的接口完整性
                // GameStateManager 负责"检测"完成，ViewModel 负责"处理"完成
            }
        )
    }

    var giveUp by mutableStateOf(false)

    val fontSize = appSettingsManager.fontSize
    val keepScreenOn = appSettingsManager.keepScreenOn

    var remainingUsesList by mutableStateOf(emptyList<Int>())
    val firstGame = appSettingsManager.firstGame
    private lateinit var boardEntity: SudokuBoard
    var size by mutableIntStateOf(9)
    var gameType by mutableStateOf(GameType.Unspecified)
    var gameDifficulty by mutableStateOf(GameDifficulty.Unspecified)

    // dialogs, menus
    var restartDialog by mutableStateOf(false)
    var showMenu by mutableStateOf(false)
    var showNotesMenu by mutableStateOf(false)
    var showUndoRedoMenu by mutableStateOf(false)

    // count remaining uses
    var remainingUse = appSettingsManager.remainingUse

    // timer
    var timerEnabled = appSettingsManager.timerEnabled

    // identical numbers highlight
    val identicalHighlight = appSettingsManager.highlightIdentical

    // mistakes checking method
    var mistakesMethod = appSettingsManager.highlightMistakes.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesConstants.DEFAULT_HIGHLIGHT_MISTAKES
    )

    var positionLines = appSettingsManager.positionLines
    val crossHighlight = themeSettingsManager.boardCrossHighlight
    val funKeyboardOverNum = appSettingsManager.funKeyboardOverNumbers

    var mistakesLimit = appSettingsManager.mistakesLimit.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesConstants.DEFAULT_MISTAKES_LIMIT
    )

    private var autoEraseNotes = appSettingsManager.autoEraseNotes.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesConstants.DEFAULT_AUTO_ERASE_NOTES
    )

    var resetTimerOnRestart = appSettingsManager.resetTimerEnabled

    var disableHints = appSettingsManager.hintsDisabled

    var endGame by mutableStateOf(false)
    var giveUpDialog by mutableStateOf(false)

    // mistakes
    // used for mistakes limit
    var mistakesCount by mutableIntStateOf(0)

    // notes
    var notesToggled by mutableStateOf(false)
    var notes by mutableStateOf(emptyList<Note>())

    private lateinit var initialBoard: List<List<Cell>>
    var gameBoard by mutableStateOf(List(9) { row -> List(9) { col -> Cell(row, col, 0) } })
    var solvedBoard = emptyList<List<Cell>>()
    var cages by mutableStateOf(emptyList<Cage>())

    var currCell by mutableStateOf(Cell(-1, -1, 0))
    private var undoRedoManager = UndoRedoManager(GameState(gameBoard, notes))
    private var sudokuUtils = SudokuUtils()
    var gameCompleted by mutableStateOf(false)

    // Selected number for digit first method
    var digitFirstNumber by mutableIntStateOf(0)
    private val inputMethod = appSettingsManager.inputMethod
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = PreferencesConstants.DEFAULT_INPUT_METHOD
        )

    // temporarily use digit first method when true
    private var overrideInputMethodDF by mutableStateOf(false)

    // show/hide solution (when give up)
    var showSolution by mutableStateOf(false)

    // when true, tapping on any cell will clear it
    var eraseButtonToggled by mutableStateOf(false)

    // used only in the game-completed section. Not saved anywhere
    var hintsUsed = 0
    var mistakesMade = 0
    var notesTaken = 0

    val allRecords by lazy { getAllRecordsUseCase(gameDifficulty, gameType) }

    val advancedHintEnabled = appSettingsManager.advancedHintEnabled
    private var _advancedHintMode = MutableStateFlow(false)
    val advancedHintMode = _advancedHintMode.asStateFlow()

    private var _advancedHintData = MutableStateFlow<AdvancedHintData?>(null)
    val advancedHintData = _advancedHintData.asStateFlow()

    private var _isLoadingAdvancedHint = MutableStateFlow(false)
    val isLoadingAdvancedHint = _isLoadingAdvancedHint.asStateFlow()

    private var _cellsToHighlight = MutableStateFlow<List<Cell>>(emptyList())
    val cellsToHighlight = _cellsToHighlight.asStateFlow()

    private var _cellsToHighlightTarget = MutableStateFlow<List<Cell>>(emptyList())
    val cellsToHighlightTarget = _cellsToHighlightTarget.asStateFlow()

    private var _advancedHintText = MutableStateFlow("")
    val advancedHintText = _advancedHintText.asStateFlow()

    /**
     * 初始化状态管理
     * 
     * 设计原则：
     * - 显式的初始化状态，避免依赖 lateinit 或默认值判断
     * - UI 层可以等待初始化完成后再调用需要初始化数据的方法
     * - 所有依赖初始化数据的操作都应检查此状态
     */
    private var _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    // 自动提示管理器
    private var _hasAutoHint = MutableStateFlow(false)
    val hasAutoHint = _hasAutoHint.asStateFlow()
    
    private var _isAutoExecuting = MutableStateFlow(false)
    val isAutoExecuting = _isAutoExecuting.asStateFlow()
    
    // 游戏状态管理器（延迟初始化）
    private lateinit var gameStateManager: GameStateManager

    private fun clearNotesAtCell(
        notes: List<Note>,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<Note> {
        return notes.minus(
            notes.filter { note ->
                note.row == row
                        && note.col == col
            }.toSet()
        )
    }

    private fun emptyNotes(): List<Note> = emptyList()

    fun clearNotes() {
        notes = emptyNotes()
        undoRedoManager.addState(
            GameState(gameBoard, notes)
        )
    }

    private fun addNote(note: Int, row: Int, col: Int): List<Note> {
        return notes.plus(Note(row, col, note))
    }

    private fun removeNote(note: Int, row: Int, col: Int): List<Note> =
        notes.minus(Note(row, col, note))

    private fun getBoardNoRef(): List<List<Cell>> =
        gameBoard.map { items -> items.map { item -> item.copy() } }

    private fun setValueCell(
        value: Int,
        row: Int = currCell.row,
        col: Int = currCell.col
    ): List<List<Cell>> {
        // 使用GameStateManager统一处理状态修改
        val result = gameStateManager.setCellValue(
            config = getGameStateConfig(),
            board = gameBoard,
            notes = notes,
            row = row,
            col = col,
            value = value,
            mode = if (_isAutoExecuting.value) GameStateManager.ExecutionMode.AUTO 
                   else GameStateManager.ExecutionMode.MANUAL,
            onStateChanged = { change ->
                // 只在手动模式下触发检测
                if (!_isAutoExecuting.value) {
                    checkBoardState()
                }
            }
        )
        
        // 更新notes（已经自动擦除了相关候选数）
        notes = result.notes
        
        // 更新currCell
        if (currCell.row == row && currCell.col == col) {
            currCell = currCell.copy(value = result.board[row][col].value)
        }
        
        // 更新剩余使用次数
        remainingUsesList = countRemainingUses(result.board)
        
        // 处理游戏完成
        // 设计原则：游戏完成时立即触发完整的处理流程
        // 不依赖 UI 层的 LaunchedEffect，确保在自动执行中也能正确处理
        if (result.completed && !gameCompleted) {
            gameCompleted = true
            // 立即触发游戏完成处理
            onGameComplete()
        }

        return result.board
    }

    private fun countRemainingUses(board: List<List<Cell>>): MutableList<Int> {
        val uses = mutableListOf<Int>()
        for (i in 0..size) {
            uses.add(size - sudokuUtils.countNumberInBoard(board, i + 1))
        }
        return uses
    }

    fun processInput(cell: Cell, remainingUse: Boolean, longTap: Boolean = false): Boolean {
        if (gamePlaying) {
            currCell =
                if (currCell.row == cell.row && currCell.col == cell.col && digitFirstNumber == 0) {
                    Cell(-1, -1)
                } else {
                    cell
                }

            if (currCell.row >= 0 && currCell.col >= 0 && !gameBoard[currCell.row][currCell.col].locked) {
                if ((inputMethod.value == 1 || overrideInputMethodDF) && digitFirstNumber > 0) {
                    if (!longTap) {
                        if ((remainingUsesList.size >= digitFirstNumber && remainingUsesList[digitFirstNumber - 1] > 0) || !remainingUse) {
                            processNumberInput(digitFirstNumber)
                            undoRedoManager.addState(GameState(gameBoard, notes))
                            if (notesToggled) currCell =
                                Cell(currCell.row, currCell.col, digitFirstNumber)
                        }
                    } else if (!currCell.locked) {
                        gameBoard = setValueCell(0)
                        setNote(digitFirstNumber)
                        undoRedoManager.addState(GameState(gameBoard, notes))
                    }
                } else if (eraseButtonToggled) {
                    val oldCell = currCell
                    processNumberInput(0)
                    if (oldCell.value != 0 && !oldCell.locked) {
                        undoRedoManager.addState(GameState(gameBoard, notes))
                    }
                }
                remainingUsesList = countRemainingUses(gameBoard)
                return true
            } else {
                return false
            }
        } else {
            return false
        }
    }

    fun processInputKeyboard(number: Int, longTap: Boolean = false) {
        if (gamePlaying) {
            if (!longTap) {
                if (inputMethod.value == 0 && !currCell.locked && currCell.col >= 0 && currCell.row >= 0) {
                    overrideInputMethodDF = false
                    digitFirstNumber = 0
                    processNumberInput(number)
                    undoRedoManager.addState(GameState(gameBoard, notes))
                } else if (inputMethod.value == 1) {
                    digitFirstNumber = if (digitFirstNumber == number) 0 else number
                    currCell = Cell(-1, -1, digitFirstNumber)
                }
            } else {
                if (inputMethod.value == 0) {
                    overrideInputMethodDF = true
                    digitFirstNumber = if (digitFirstNumber == number) 0 else number
                    currCell = Cell(-1, -1, digitFirstNumber)
                }
            }
            eraseButtonToggled = false
        }
    }


    fun processNumberInput(number: Int) {
        if (currCell.row >= 0 && currCell.col >= 0 && gamePlaying && !currCell.locked) {
            if (!notesToggled) {
                // Clear all note to set a number
                notes = clearNotesAtCell(notes, currCell.row, currCell.col)

                gameBoard = setValueCell(
                    if (gameBoard[currCell.row][currCell.col].value == number) 0 else number
                )
            } else {
                gameBoard = setValueCell(0)
                setNote(number)
                remainingUsesList = countRemainingUses(gameBoard)
            }
        }
    }

    fun setNotes(notesToAdd: List<Note>?, notesToRemove: List<Note>?) {
        // 使用GameStateManager统一处理候选数修改
        val result = gameStateManager.modifyNotes(
            board = gameBoard,
            notes = notes,
            notesToAdd = notesToAdd,
            notesToRemove = notesToRemove,
            mode = if (_isAutoExecuting.value) GameStateManager.ExecutionMode.AUTO 
                   else GameStateManager.ExecutionMode.MANUAL,
            onStateChanged = { change ->
                // 只在手动模式下触发检测
                if (!_isAutoExecuting.value) {
                    checkBoardState()
                }
            }
        )
        
        // 更新notes
        notes = result.notes
        
        // 更新统计
        if (notesToAdd != null) {
            notesTaken += notesToAdd.size
        }
        
        // 添加到撤销/重做历史
        undoRedoManager.addState(GameState(gameBoard, notes))
    }

    private fun setNote(number: Int) {
        // 使用GameStateManager统一处理单个候选数切换
        val result = gameStateManager.toggleNote(
            board = gameBoard,
            notes = notes,
            row = currCell.row,
            col = currCell.col,
            value = number,
            mode = if (_isAutoExecuting.value) GameStateManager.ExecutionMode.AUTO 
                   else GameStateManager.ExecutionMode.MANUAL,
            onStateChanged = { change ->
                // 只在手动模式下触发检测
                if (!_isAutoExecuting.value) {
                    checkBoardState()
                }
            }
        )
        
        // 更新统计（只在添加时计数）
        val note = Note(currCell.row, currCell.col, number)
        if (!notes.contains(note) && result.notes.contains(note)) {
            notesTaken++
        }
        
        // 更新notes
        notes = result.notes
    }

    var timeText by mutableStateOf("00:00")
    private var duration = Duration.ZERO
    private lateinit var timer: Timer
    var gamePlaying by mutableStateOf(false)

    fun startTimer() {
        if (!gamePlaying) {
            gamePlaying = true
            val updateRate = 50L

            timer = fixedRateTimer(initialDelay = updateRate, period = updateRate) {
                val prevTime = duration

                duration = duration.plus((updateRate * 1e6).toDuration(DurationUnit.NANOSECONDS))
                // update text every second
                if (prevTime.toInt(DurationUnit.SECONDS) != duration.toInt(DurationUnit.SECONDS)) {
                    timeText = duration.toFormattedString()
                    // save game
                    if (gameBoard.any { it.any { cell -> cell.value != 0 } }) {
                        viewModelScope.launch(Dispatchers.IO) {
                            saveGame()
                        }
                    }
                }
            }
            
            // 游戏开始时触发自动检测
            checkBoardState()
        }
    }

    fun pauseTimer() {
        gamePlaying = false
        timer.cancel()
    }

    fun toolbarClick(item: ToolBarItem) {
        if (gamePlaying) {
            when (item) {
                ToolBarItem.Undo -> {
                    if (undoRedoManager.canUndo()) {
                        undoRedoManager.undo().also {
                            gameBoard = it.board
                            notes = it.notes
                        }
                        checkMistakesAll()
                    }
                    remainingUsesList = countRemainingUses(gameBoard)
                }

                ToolBarItem.Redo -> {
                    if (undoRedoManager.canRedo()) {
                        undoRedoManager.redo()?.let {
                            gameBoard = it.board
                            notes = it.notes
                        }
                        checkMistakesAll()
                    }
                    remainingUsesList = countRemainingUses(gameBoard)
                }

                ToolBarItem.Hint -> {
                    useHint()
                }

                ToolBarItem.Note -> {
                    notesToggled = !notesToggled
                    eraseButtonToggled = false
                }

                ToolBarItem.Remove -> {
                    if (inputMethod.value == 1 || eraseButtonToggled) {
                        toggleEraseButton()
                        return
                    }
                    if (currCell.row >= 0 && currCell.col >= 0 && !currCell.locked) {
                        val prevValue = gameBoard[currCell.row][currCell.col].value
                        val notesInCell =
                            notes.count { note -> note.row == currCell.row && note.col == currCell.col }
                        notes = clearNotesAtCell(notes)
                        gameBoard = setValueCell(0)
                        if (prevValue != 0 || notesInCell != 0) {
                            undoRedoManager.addState(GameState(gameBoard, notes))
                        }
                    }
                }
            }
        }
    }

    private fun useHint() {
        if (solvedBoard.isEmpty()) solveBoard()
        if (currCell.row >= 0 && currCell.col >= 0 && !currCell.locked) {
            notes = clearNotesAtCell(notes, currCell.row, currCell.col)
            gameBoard = setValueCell(solvedBoard[currCell.row][currCell.col].value)

            val new = getBoardNoRef()
            new[currCell.row][currCell.col].error = false
            gameBoard = new

            duration = duration.plus(30.toDuration(DurationUnit.SECONDS))
            timeText = duration.toFormattedString()
            undoRedoManager.addState(GameState(gameBoard, notes))
            hintsUsed++
        }
    }

    fun resetGame(resetTimer: Boolean) {
        // stop and reset game
        notes = emptyNotes()
        currCell = Cell(-1, -1, 0)
        if (resetTimer) {
            duration = Duration.ZERO
            timeText = duration.toFormattedString()
        }
        digitFirstNumber = 0
        notesToggled = false
        undoRedoManager.clear()

        // init a new game with initial board
        gameBoard = initialBoard.map { items -> items.map { item -> item.copy() } }

        remainingUsesList = countRemainingUses(gameBoard)

        hintsUsed = 0
        mistakesMade = 0
        notesTaken = 0
    }

    private fun isValidCell(
        board: List<List<Cell>> = getBoardNoRef(),
        cell: Cell
    ): List<List<Cell>> {
        if (solvedBoard.isNotEmpty()) {
            board[cell.row][cell.col].error =
                solvedBoard[cell.row][cell.col].value != board[cell.row][cell.col].value
        } else {
            solveBoard()
        }
        return board
    }

    // 注意：游戏完成检测现在由 GameStateManager.isGameCompleted() 负责
    // 此函数已废弃，保留仅供参考或未来可能的其他用途

    fun computeNotes() {
        notes = sudokuUtils.computeNotes(gameBoard, gameType)
        undoRedoManager.addState(GameState(gameBoard, notes))
    }

    private suspend fun saveGame() {
        val savedGame = savedGameRepository.get(boardEntity.uid)
        val sudokuParser = SudokuParser()
        if (savedGame != null) {
            savedGameRepository.update(
                savedGame.copy(
                    timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                    currentBoard = sudokuParser.boardToString(gameBoard),
                    notes = sudokuParser.notesToString(notes),
                    mistakes = mistakesCount,
                    lastPlayed = ZonedDateTime.now()
                )
            )
        } else {
            savedGameRepository.insert(
                SavedGame(
                    uid = boardEntity.uid,
                    currentBoard = sudokuParser.boardToString(gameBoard),
                    notes = sudokuParser.notesToString(notes),
                    timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                    mistakes = mistakesCount,
                    lastPlayed = ZonedDateTime.now(),
                    startedAt = ZonedDateTime.now()
                )
            )
        }
    }

    private fun restoreSavedGame(savedGame: SavedGame?) {
        if (savedGame != null) {
            // restore timer and text
            duration = savedGame.timer.toKotlinDuration()
            timeText = duration.toFormattedString()

            mistakesCount = savedGame.mistakes
            val sudokuParser = SudokuParser()
            gameBoard = sudokuParser.parseBoard(
                savedGame.currentBoard,
                gameType
            )
            notes = sudokuParser.parseNotes(savedGame.notes)

            for (i in gameBoard.indices) {
                for (j in gameBoard.indices) {
                    gameBoard[i][j].locked = initialBoard[i][j].locked

                    if (gameBoard[i][j].value != 0 && !gameBoard[i][j].locked) {
                        if (mistakesMethod.value == 1) {
                            gameBoard[i][j].error =
                                !sudokuUtils.isValidCellDynamic(
                                    board = gameBoard,
                                    cell = gameBoard[i][j],
                                    type = gameType
                                )
                        } else {
                            gameBoard[i][j].error =
                                isValidCell(gameBoard, gameBoard[i][j])[i][j].error
                        }
                    }
                }
            }
        }
    }

    fun giveUp() {
        giveUp = true
        endGame = true
        currCell = Cell(-1, -1, 0)
        viewModelScope.launch(Dispatchers.IO) {
            val savedGame = savedGameRepository.get(boardEntity.uid)
            if (savedGame != null) {
                val sudokuParser = SudokuParser()
                savedGameRepository.update(
                    savedGame.copy(
                        timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                        currentBoard = sudokuParser.boardToString(gameBoard),
                        completed = true,
                        giveUp = true,
                        mistakes = mistakesCount,
                        canContinue = false,
                        finishedAt = ZonedDateTime.now()
                    )
                )
            }
        }
    }

    fun onGameComplete() {
        if (endGame) return

        pauseTimer()
        currCell = Cell(-1, -1, 0)
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 更新 SavedGame 的完成状态（关键！）
            val savedGame = savedGameRepository.get(boardEntity.uid)
            if (savedGame != null) {
                savedGameRepository.update(
                    savedGame.copy(
                        completed = true,
                        giveUp = false,
                        canContinue = false,
                        finishedAt = ZonedDateTime.now(),
                        timer = java.time.Duration.ofSeconds(duration.inWholeSeconds),
                        currentBoard = SudokuParser().boardToString(gameBoard),
                        notes = SudokuParser().notesToString(notes),
                        mistakes = mistakesCount,
                        lastPlayed = ZonedDateTime.now()
                    )
                )
            }
            
            // 2. 插入游戏记录
            recordRepository.insert(
                Record(
                    board_uid = boardEntity.uid,
                    type = boardEntity.type,
                    difficulty = boardEntity.difficulty,
                    date = ZonedDateTime.now(),
                    time = duration.toJavaDuration()
                )
            )
        }
        endGame = true
    }

    fun getFontSize(type: GameType = gameType, factor: Int): TextUnit {
        return sudokuUtils.getFontSize(type, factor)
    }

    fun setFirstGameFalse() {
        viewModelScope.launch(Dispatchers.IO) {
            appSettingsManager.setFirstGame(false)
        }
    }

    fun toggleEraseButton() {
        notesToggled = false
        currCell = Cell(-1, -1, 0)
        digitFirstNumber = -1
        eraseButtonToggled = !eraseButtonToggled
    }

    // to make sure that solvedBoard really contains a solved board
    private fun solveBoard() {
        val qqWing = QQWingController()
        val boardToSolve = boardEntity.initialBoard.map { it.digitToInt(13) }.toIntArray()
        val solved = qqWing.solve(boardToSolve, boardEntity.type)

        val newSolvedBoard = List(boardEntity.type.size) { row ->
            List(boardEntity.type.size) { col ->
                Cell(
                    row,
                    col,
                    0
                )
            }
        }
        for (i in 0 until size) {
            for (j in 0 until size) {
                newSolvedBoard[i][j].value = solved[i * size + j]
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sudokuParser = SudokuParser()
            updateBoardUseCase(
                boardEntity.copy(solvedBoard = sudokuParser.boardToString(newSolvedBoard))
            )
        }
        solvedBoard = newSolvedBoard

        for (i in solvedBoard.indices) {
            for (j in solvedBoard.indices) {
                solvedBoard[i][j].locked = initialBoard[i][j].locked
            }
        }
    }

    fun checkMistakesAll() {
        var new = getBoardNoRef()

        if (!this::initialBoard.isInitialized) return

        for (i in new.indices) {
            for (j in new.indices) {
                if (new[i][j].value != 0 && !new[i][j].locked) {
                    when (mistakesMethod.value) {
                        0 -> {
                            // mistake checking is off
                            new[i][j].error = false
                        }

                        1 -> {
                            // rules violations
                            new[i][j].error =
                                !sudokuUtils.isValidCellDynamic(new, new[i][j], gameType)
                        }

                        2 -> {
                            // check with final solution
                            new = isValidCell(new, new[i][j])
                        }
                    }
                }
            }
        }
        gameBoard = new
    }

    fun getAdvancedHint() {
        viewModelScope.launch(Dispatchers.Default) {
            _isLoadingAdvancedHint.emit(true)
            currCell = Cell(-1, -1, 0)
            _advancedHintData.emit(null)
            val hintSettings = runBlocking { appSettingsManager.advancedHintSettings.first() }
            val advancedHint = AdvancedHint(
                type = gameType,
                board = gameBoard,
                solvedBoard = solvedBoard,
                notes = notes,
                settings = hintSettings
            )
            val advancedHintData = advancedHint.getEasiestHint()
            _advancedHintData.emit(advancedHintData)
            _advancedHintMode.emit(true)
            _isLoadingAdvancedHint.emit(false)
        }
    }

    fun cancelAdvancedHint() {
        viewModelScope.launch(Dispatchers.IO) {
            _advancedHintMode.emit(false)
            // delay remove data to allow the UI to animate out
//            kotlinx.coroutines.delay(300)
//            _advancedHintData.emit(null)
        }
    }

    fun applyAdvancedHint() {
        viewModelScope.launch(Dispatchers.Default) {
            val cells = _advancedHintData.value?.targetCells
            val notesToAdd = _advancedHintData.value?.notesToAdd
            val notesToRemove = _advancedHintData.value?.notesToRemove
            if (notesToAdd != null || notesToRemove != null) {
                setNotes(notesToAdd, notesToRemove)
                cancelAdvancedHint()
                return@launch
            }
            if (cells != null) {
                for (cell in cells)  {
                    currCell = gameBoard[cell.row][cell.col]
                    digitFirstNumber = cell.value
                    processInput(cell, true)
                }
                cancelAdvancedHint()
            }
        }
    }

    /**
     * 检查盘面状态，触发自动提示检测
     * 
     * 设计原则：
     * - 依赖显式的初始化状态，而非隐式的类型判断
     * - 清晰的前置条件检查
     */
    private fun checkBoardState() {
        // 1. 检查游戏是否已初始化完成（使用显式状态）
        if (!_isInitialized.value) {
            return
        }
        
        // 2. 如果游戏未开始、已完成或正在自动执行，不进行检测
        if (!gamePlaying || gameCompleted || _isAutoExecuting.value) {
            return
        }
        
        viewModelScope.launch(Dispatchers.Default) {
            val hintSettings = runBlocking { appSettingsManager.advancedHintSettings.first() }
            
            // 只有设置了自动模式才进行检测
            if (!hintSettings.hasAutoMode()) {
                _hasAutoHint.emit(false)
                return@launch
            }
            
            // 创建只包含自动模式的设置
            val autoSettings = createAutoOnlySettings(hintSettings)
            
            // 检测是否有可执行的自动操作
            val advancedHint = com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHint(
                type = gameType,
                board = gameBoard,
                solvedBoard = solvedBoard,
                notes = notes,
                settings = autoSettings
            )
            
            val hintData = advancedHint.getEasiestHint()
            _hasAutoHint.emit(hintData != null)
        }
    }

    /**
     * 执行所有自动提示（循环执行直到没有可执行的自动操作）
     * 
     * 设计原则：
     * 1. 循环执行直到没有可用提示或游戏完成
     * 2. 不依赖固定迭代次数，而是依赖提示检测结果
     * 3. 每次应用提示后立即检查游戏状态
     */
    fun executeAutoHints() {
        viewModelScope.launch(Dispatchers.Main) {
            _isAutoExecuting.emit(true)
            
            try {
                val hintSettings = runBlocking { appSettingsManager.advancedHintSettings.first() }
                val autoSettings = createAutoOnlySettings(hintSettings)
                
                // 循环执行，直到没有更多提示或游戏完成
                while (!gameCompleted) {
                    // 在后台线程检测提示
                    val hintData = withContext(Dispatchers.Default) {
                        com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHint(
                            type = gameType,
                            board = gameBoard,
                            solvedBoard = solvedBoard,
                            notes = notes,
                            settings = autoSettings
                        ).getEasiestHint()
                    }
                    
                    // 没有更多提示，退出循环
                    if (hintData == null) break
                    
                    // 应用提示
                    applyAutoHintDataInternal(hintData)
                    
                    // 游戏完成立即退出
                    if (gameCompleted) break
                    
                    // 短暂延迟让UI更新
                    kotlinx.coroutines.delay(150)
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // 退出自动执行模式并清除快进按钮
                _isAutoExecuting.emit(false)
                _hasAutoHint.emit(false)
            }
        }
    }
    
    /**
     * 内部方法：应用自动提示数据
     * 使用ExecutionMode.AUTO，确保不触发递归检测
     */
    private fun applyAutoHintDataInternal(hintData: com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData) {
        val cells = hintData.targetCells
        val notesToAdd = hintData.notesToAdd
        val notesToRemove = hintData.notesToRemove
        
        // 先处理候选数修改
        if (notesToAdd != null || notesToRemove != null) {
            setNotes(notesToAdd, notesToRemove)
        }
        
        // 再处理填入数字（不是 else if，可能需要同时处理）
        if (cells != null && cells.isNotEmpty()) {
            for (cell in cells) {
                currCell = gameBoard[cell.row][cell.col]
                digitFirstNumber = cell.value
                gameBoard = setValueCell(cell.value, cell.row, cell.col)
            }
            undoRedoManager.addState(GameState(gameBoard, notes))
        }
    }
    
    /**
     * 创建只包含自动模式的设置
     */
    private fun createAutoOnlySettings(settings: com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintSettings): com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintSettings {
        return com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintSettings(
            fullHouse = if (settings.fullHouse.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            nakedSingle = if (settings.nakedSingle.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            hiddenSingle = if (settings.hiddenSingle.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            checkWrongValue = if (settings.checkWrongValue.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            checkMissingOrWrongNote = if (settings.checkMissingOrWrongNote.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            lockedCandidates = if (settings.lockedCandidates.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            nakedSubsets = if (settings.nakedSubsets.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            hiddenSubsets = if (settings.hiddenSubsets.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            sueDeCoq = if (settings.sueDeCoq.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            xWings = if (settings.xWings.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            xyWings = if (settings.xyWings.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            xyzWings = if (settings.xyzWings.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            wWings = if (settings.wWings.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            fishPatterns = if (settings.fishPatterns.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            finnedFishVariants = if (settings.finnedFishVariants.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            xChain = if (settings.xChain.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            xyChain = if (settings.xyChain.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            aicType1 = if (settings.aicType1.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
            aicType2 = if (settings.aicType2.isAuto()) com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED else com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED,
        )
    }

}
