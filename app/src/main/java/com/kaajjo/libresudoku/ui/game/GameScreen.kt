package com.kaajjo.libresudoku.ui.game

import android.content.res.Configuration
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.Note
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData
import com.kaajjo.libresudoku.destinations.SettingsAdvancedHintScreenDestination
import com.kaajjo.libresudoku.ui.components.AdvancedHintContainer
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.kaajjo.libresudoku.ui.components.board.Board
import com.kaajjo.libresudoku.ui.game.components.DefaultGameKeyboard
import com.kaajjo.libresudoku.ui.game.components.DrawingCanvas
import com.kaajjo.libresudoku.ui.game.components.DrawingState
import com.kaajjo.libresudoku.ui.game.components.DrawingToolbar
import com.kaajjo.libresudoku.ui.game.components.scaleResetIndicator
import com.kaajjo.libresudoku.ui.game.components.NotesMenu
import com.kaajjo.libresudoku.ui.game.components.SquareGameKeyboard
import com.kaajjo.libresudoku.ui.game.components.ToolBarItem
import com.kaajjo.libresudoku.ui.game.components.ToolbarItem
import com.kaajjo.libresudoku.ui.game.components.UndoRedoMenu
import com.kaajjo.libresudoku.ui.onboarding.FirstGameDialog
import com.kaajjo.libresudoku.ui.util.ReverseArrangement
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

// ============================================================================
// 配置数据类 - 组织相关参数，减少传参复杂度
// ============================================================================

/**
 * 棋盘显示配置
 * 封装所有与棋盘视觉显示相关的参数
 */
data class BoardDisplayConfig(
    val blur: androidx.compose.ui.unit.Dp,
    val scale: Float,
    val fontSizeValue: androidx.compose.ui.unit.TextUnit,
    val fontSizeFactor: Int,
    val renderNotes: Boolean
)

/**
 * 棋盘高亮配置
 * 封装所有与高亮显示相关的参数
 */
data class BoardHighlightConfig(
    val identicalNumbers: Boolean,
    val errors: Int,
    val positionLines: Boolean,
    val crossHighlight: Boolean
)

/**
 * 高级提示配置
 * 封装高级提示相关的状态
 */
data class AdvancedHintConfig(
    val mode: Boolean,
    val data: AdvancedHintData?
)

/**
 * 游戏偏好设置
 * 封装用户的游戏偏好配置
 */
data class GamePreferences(
    val remainingUse: Boolean,
    val funKeyboardOverNum: Boolean,
    val mistakesLimit: Boolean
)

/**
 * 游戏UI状态
 * 封装游戏界面的核心状态
 */
data class GameUiState(
    val localView: View,
    val isLandscape: Boolean,
    val renderNotes: Boolean,
    val onRenderNotesChange: (Boolean) -> Unit
)

// ============================================================================

@Destination(style = AnimatedNavigation::class, navArgsDelegate = GameScreenNavArgs::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel = hiltViewModel(), navigator: DestinationsNavigator) {
    val localView = LocalView.current // vibration
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val firstGame by viewModel.firstGame.collectAsStateWithLifecycle(initialValue = false)
    val resetTimer by
            viewModel.resetTimerOnRestart.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_GAME_RESET_TIMER
            )
    val mistakesLimit by
            viewModel.mistakesLimit.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_MISTAKES_LIMIT
            )
    val errorHighlight by
            viewModel.mistakesMethod.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_HIGHLIGHT_MISTAKES
            )
    val keepScreenOn by
            viewModel.keepScreenOn.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_KEEP_SCREEN_ON
            )
    val remainingUse by
            viewModel.remainingUse.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_REMAINING_USES
            )
    val highlightIdentical by
            viewModel.identicalHighlight.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_HIGHLIGHT_IDENTICAL
            )
    val positionLines by
            viewModel.positionLines.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_POSITION_LINES
            )
    val crossHighlight by
            viewModel.crossHighlight.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_BOARD_CROSS_HIGHLIGHT
            )
    val funKeyboardOverNum by
            viewModel.funKeyboardOverNum.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_FUN_KEYBOARD_OVER_NUM
            )

    val fontSizeFactor by
            viewModel.fontSize.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_FONT_SIZE_FACTOR
            )
    val fontSizeValue by
            remember(fontSizeFactor, viewModel.gameType) {
                mutableStateOf(viewModel.getFontSize(factor = fontSizeFactor))
            }
    val advancedHintEnabled by
            viewModel.advancedHintEnabled.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_ADVANCED_HINT
            )
    val advancedHintMode by viewModel.advancedHintMode.collectAsStateWithLifecycle(false)
    val advancedHintData by viewModel.advancedHintData.collectAsStateWithLifecycle(null)
    val hasAutoHint by viewModel.hasAutoHint.collectAsStateWithLifecycle(false)
    val isAutoExecuting by viewModel.isAutoExecuting.collectAsStateWithLifecycle(false)
    
    // 等待初始化完成的状态
    val isInitialized by viewModel.isInitialized.collectAsState()
    
    // 控制新手教程显示的状态
    var showFirstGameDialog by rememberSaveable { mutableStateOf(false) }
    
    if (keepScreenOn) {
        KeepScreenOn()
    }

    // 新手教程对话框
    if (showFirstGameDialog) {
        viewModel.pauseTimer()
        FirstGameDialog(
                onFinished = {
                    showFirstGameDialog = false
                    viewModel.setFirstGameFalse()
                    viewModel.startTimer()
                }
        )
    }

    var restartButtonAngleState by remember { mutableFloatStateOf(0f) }
    val restartButtonAnimation: Float by
            animateFloatAsState(
                    targetValue = restartButtonAngleState,
                    animationSpec = tween(durationMillis = 250),
                    label = "restartButtonAnimation"
            )

    val boardBlur by
            animateDpAsState(
                    targetValue = if (viewModel.gamePlaying || viewModel.endGame) 0.dp else 10.dp,
                    label = "Game board blur"
            )
    val boardScale by
            animateFloatAsState(
                    targetValue = if (viewModel.gamePlaying || viewModel.endGame) 1f else 0.90f,
                    label = "Game board scale"
            )

    var renderNotes by remember { mutableStateOf(true) }

    // 绘画功能状态（使用 rememberSaveable 保存横竖屏切换时的状态）
    // 默认使用 Material3 的 primary 颜色
    val defaultDrawingColor = MaterialTheme.colorScheme.primary
    var drawingState by
            rememberSaveable(stateSaver = DrawingState.Saver) {
                mutableStateOf(DrawingState(currentColor = defaultDrawingColor))
            }

    // 获取屏幕方向
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 创建配置对象 - 组织相关参数
    val boardDisplayConfig = BoardDisplayConfig(
            blur = boardBlur,
            scale = boardScale,
            fontSizeValue = fontSizeValue,
            fontSizeFactor = fontSizeFactor,
            renderNotes = renderNotes
    )

    val boardHighlightConfig = BoardHighlightConfig(
            identicalNumbers = highlightIdentical,
            errors = errorHighlight,
            positionLines = positionLines,
            crossHighlight = crossHighlight
    )

    val hintConfig = AdvancedHintConfig(
            mode = advancedHintMode,
            data = advancedHintData
    )

    val gamePreferences = GamePreferences(
            remainingUse = remainingUse,
            funKeyboardOverNum = funKeyboardOverNum,
            mistakesLimit = mistakesLimit
    )

    val gameUiState = GameUiState(
            localView = localView,
            isLandscape = isLandscape,
            renderNotes = renderNotes,
            onRenderNotesChange = { renderNotes = it }
    )

    // 完全去掉 Scaffold，直接构建布局
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (isLandscape) {
            // 横屏模式：左中右三栏布局
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧：返回按钮（固定宽度）
                IconButton(
                        onClick = { navigator.popBackStack() },
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                ) {
                    Icon(
                            painter = painterResource(R.drawable.ic_round_arrow_back_24),
                            contentDescription = "返回"
                    )
                }

                // 中间：棋盘（占据大部分空间）
                Box(
                        modifier =
                                Modifier.fillMaxHeight()
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                ) {
                    GameBoard(
                            viewModel = viewModel,
                            displayConfig = boardDisplayConfig,
                            highlightConfig = boardHighlightConfig,
                            hintConfig = hintConfig,
                            localView = localView,
                            drawingState = drawingState,
                            onDrawingStateChange = { transform ->
                                drawingState = transform(drawingState)
                            },
                            remainingUse = gamePreferences.remainingUse
                    )
                }

                // 右侧：控制按钮和工具（限制最大宽度）
                Column(
                        modifier =
                                Modifier.width(480.dp)
                                        .fillMaxHeight()
                                        .padding(
                                                start = 12.dp,
                                                end = 12.dp,
                                                top = 12.dp,
                                                bottom = 12.dp
                                        )
                ) {
                    // 顶部按钮行和游戏信息
                    Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 使用抽取的组件
                        TopGameBar(
                                modifier = Modifier.fillMaxWidth(),
                                showBackButton = false,
                                arrangement = Arrangement.SpaceEvenly,
                                viewModel = viewModel,
                                navigator = navigator,
                                drawingState = drawingState,
                                onDrawingStateChange = { drawingState = it },
                                restartButtonAnimation = restartButtonAnimation,
                                isLandscape = isLandscape
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 使用抽取的游戏信息显示组件
                        GameInfoDisplay(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                viewModel = viewModel,
                                mistakesLimit = mistakesLimit,
                                errorHighlight = errorHighlight
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 下方内容区域 - 优先显示 AdvancedHint
                    AnimatedContent(advancedHintMode, label = "advanced_hint_landscape") {
                            isAdvancedHint ->
                        if (isAdvancedHint) {
                            AdvancedHintSection(
                                    advancedHintData = advancedHintData,
                                    viewModel = viewModel,
                                    navigator = navigator
                            )
                        } else {
                            BottomContentSection(
                                    viewModel = viewModel,
                                    drawingState = drawingState,
                                    onDrawingStateChange = { drawingState = it },
                                    preferences = gamePreferences,
                                    uiState = gameUiState
                            )
                        }
                    }
                }
            }
            // 横屏布局结束
        } else {
            // 竖屏布局
            Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // 顶部：操作栏和游戏信息
                TopGameBar(
                        modifier = Modifier.fillMaxWidth(),
                        showBackButton = true,
                        arrangement = Arrangement.SpaceBetween,
                        viewModel = viewModel,
                        navigator = navigator,
                        drawingState = drawingState,
                        onDrawingStateChange = { drawingState = it },
                        restartButtonAnimation = restartButtonAnimation,
                        isLandscape = isLandscape
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 游戏信息显示（横向分布）
                GameInfoDisplay(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        viewModel = viewModel,
                        mistakesLimit = mistakesLimit,
                        errorHighlight = errorHighlight
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 中间：棋盘
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GameBoard(
                            viewModel = viewModel,
                            displayConfig = boardDisplayConfig,
                            highlightConfig = boardHighlightConfig,
                            hintConfig = hintConfig,
                            localView = localView,
                            drawingState = drawingState,
                            onDrawingStateChange = { transform ->
                                drawingState = transform(drawingState)
                            },
                            remainingUse = gamePreferences.remainingUse
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底部：工具栏和键盘
                AnimatedContent(advancedHintMode) { targetState ->
                    if (targetState) {
                        AdvancedHintSection(
                                advancedHintData = advancedHintData,
                                viewModel = viewModel,
                                navigator = navigator
                        )
                    } else {
                        BottomContentSection(
                                viewModel = viewModel,
                                drawingState = drawingState,
                                onDrawingStateChange = { drawingState = it },
                                preferences = gamePreferences,
                                uiState = gameUiState
                        )
                    }
                }
            }
        }
    }

    // dialogs
    if (viewModel.restartDialog) {
        viewModel.pauseTimer()
        AlertDialog(
                title = { Text(stringResource(R.string.action_reset_game)) },
                text = { Text(stringResource(R.string.reset_game_text)) },
                dismissButton = {
                    TextButton(
                            onClick = {
                                viewModel.restartDialog = false
                                viewModel.startTimer()
                            }
                    ) { Text(stringResource(R.string.dialog_no)) }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                restartButtonAngleState -= 360
                                viewModel.resetGame(resetTimer)
                                viewModel.restartDialog = false
                                viewModel.startTimer()
                            }
                    ) { Text(stringResource(R.string.dialog_yes)) }
                },
                onDismissRequest = {
                    viewModel.restartDialog = false
                    viewModel.startTimer()
                }
        )
    } else if (viewModel.giveUpDialog) {
        viewModel.pauseTimer()
        AlertDialog(
                title = { Text(stringResource(R.string.action_give_up)) },
                text = { Text(stringResource(R.string.give_up_text)) },
                dismissButton = {
                    TextButton(
                            onClick = {
                                viewModel.giveUpDialog = false
                                viewModel.startTimer()
                            }
                    ) { Text(stringResource(R.string.dialog_no)) }
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                viewModel.giveUp()
                                viewModel.giveUpDialog = false
                                viewModel.pauseTimer()
                            }
                    ) { Text(stringResource(R.string.dialog_yes)) }
                },
                onDismissRequest = {
                    viewModel.giveUpDialog = false
                    viewModel.startTimer()
                },
        )
    }

    LaunchedEffect(viewModel.mistakesMethod) { viewModel.checkMistakesAll() }

    // 初始化完成后的统一处理逻辑
    LaunchedEffect(isInitialized) {
        if (isInitialized && !viewModel.endGame && !viewModel.gameCompleted) {
            if (firstGame) {
                // 首次游戏：显示新手教程（教程内部会暂停计时器）
                showFirstGameDialog = true
            } else {
                // 非首次游戏：直接启动计时器
                viewModel.startTimer()
            }
        }
    }

    LaunchedEffect(viewModel.gameCompleted) {
        if (viewModel.gameCompleted) {
            viewModel.onGameComplete()
        }
    }

    // so that the timer doesn't run in the background
    // https://stackoverflow.com/questions/66546962/jetpack-compose-how-do-i-refresh-a-screen-when-app-returns-to-foreground/66807899#66807899
    OnLifecycleEvent { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                if (viewModel.gamePlaying) viewModel.startTimer()
            }
            Lifecycle.Event.ON_PAUSE -> {
                viewModel.pauseTimer()
                viewModel.currCell = Cell(-1, -1, 0)
            }
            Lifecycle.Event.ON_DESTROY -> viewModel.pauseTimer()
            else -> {}
        }
    }
}

@Composable
fun GameBoard(
        viewModel: GameViewModel,
        displayConfig: BoardDisplayConfig,
        highlightConfig: BoardHighlightConfig,
        hintConfig: AdvancedHintConfig,
        localView: View,
        drawingState: DrawingState,
        onDrawingStateChange: ((DrawingState) -> DrawingState) -> Unit,
        remainingUse: Boolean
) {
    // 缩放和平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    // 动画状态
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "offsetY"
    )
    
    Box(
        modifier = Modifier
            .scaleResetIndicator(scale = scale)
            .graphicsLayer(
                scaleX = animatedScale,
                scaleY = animatedScale,
                translationX = animatedOffsetX,
                translationY = animatedOffsetY
            )
    ) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            AnimatedVisibility(
                    visible = !viewModel.gamePlaying && !viewModel.endGame,
                    enter = expandVertically(clip = false) + fadeIn(),
                    exit = shrinkVertically(clip = false) + fadeOut()
            ) {
                Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).shadow(12.dp)
                )
            }
        }
        Board(
                modifier = Modifier.blur(displayConfig.blur).scale(displayConfig.scale, displayConfig.scale),
                board = if (!viewModel.showSolution) viewModel.gameBoard else viewModel.solvedBoard,
                size = viewModel.size,
                mainTextSize = displayConfig.fontSizeValue,
                autoFontSize = displayConfig.fontSizeFactor == 0,
                notes = viewModel.notes,
                selectedCell = viewModel.currCell,
                onClick = { cell ->
                    if (!drawingState.isDrawingMode) {
                        viewModel.processInput(
                                cell = cell,
                                remainingUse = remainingUse,
                        )
                        if (!viewModel.gamePlaying) {
                            localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.startTimer()
                        }
                    }
                },
                onLongClick = { cell ->
                    if (!drawingState.isDrawingMode &&
                                    viewModel.processInput(cell, remainingUse, longTap = true)
                    ) {
                        localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                },
                identicalNumbersHighlight = highlightConfig.identicalNumbers,
                errorsHighlight = highlightConfig.errors != 0,
                positionLines = highlightConfig.positionLines,
                notesToRemoveHightlight =
                        if (hintConfig.mode) hintConfig.data?.notesToRemove ?: emptyList()
                        else emptyList(),
                notesToHighlight =
                        when {
                            hintConfig.mode -> emptyList()
                            viewModel.digitFirstNumber > 0 ->
                                    viewModel.notes.filter {
                                        it.value == viewModel.digitFirstNumber
                                    }
                            else -> emptyList()
                        },
                enabled =
                        viewModel.gamePlaying && !viewModel.endGame && !drawingState.isDrawingMode,
                questions =
                        !(viewModel.gamePlaying || viewModel.endGame) &&
                                SDK_INT < Build.VERSION_CODES.R,
                renderNotes = displayConfig.renderNotes && !viewModel.showSolution,
                zoomable =
                        viewModel.gameType == GameType.Default12x12 ||
                                viewModel.gameType == GameType.Killer12x12,
                crossHighlight = highlightConfig.crossHighlight,
                cages = viewModel.cages,
                cellsToHighlight = if (hintConfig.mode) hintConfig.data?.helpCells else null,
                cellsToHighlightTarget =
                        if (hintConfig.mode) hintConfig.data?.targetCells
                        else null,
                chain = if (hintConfig.mode) hintConfig.data?.chain else null
        )

        // 绘画画布叠加层
        DrawingCanvas(
                modifier = Modifier.matchParentSize(),
                drawingState = drawingState,
                onAddPoint = { point -> onDrawingStateChange { it.addPoint(point) } },
                onFinishPath = { onDrawingStateChange { it.finishPath() } },
                onZoomChange = { newScale, newOffsetX, newOffsetY ->
                    scale = newScale
                    // 当缩小到原大时，自动回到原位置
                    if (newScale <= 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX = newOffsetX
                        offsetY = newOffsetY
                    }
                },
                enabled = drawingState.isDrawingMode,
                alpha = if (drawingState.isDrawingMode) 1f else 0.5f
        )
    }
}

/**
 * 游戏工具栏按钮行组件
 * 包含 Undo、Hint、Notes、Eraser 和 AdvancedHint 按钮
 */
@Composable
fun GameToolbarRow(
        viewModel: GameViewModel,
        uiState: GameUiState,
        modifier: Modifier = Modifier
) {
    val hintsDisabled by
            viewModel.disableHints.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_HINTS_DISABLED
            )
    val advancedHintEnabled by
            viewModel.advancedHintEnabled.collectAsStateWithLifecycle(
                    initialValue = PreferencesConstants.DEFAULT_ADVANCED_HINT
            )
    val isLoadingAdvancedHint by viewModel.isLoadingAdvancedHint.collectAsStateWithLifecycle(false)
    val hasAutoHint by viewModel.hasAutoHint.collectAsStateWithLifecycle(false)
    val isAutoExecuting by viewModel.isAutoExecuting.collectAsStateWithLifecycle(false)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        // Undo 按钮
        Box(modifier = Modifier.weight(1f)) {
            UndoRedoMenu(
                    expanded = viewModel.showUndoRedoMenu,
                    onDismiss = { viewModel.showUndoRedoMenu = false },
                    onRedoClick = { viewModel.toolbarClick(ToolBarItem.Redo) }
            )
            ToolbarItem(
                    painter = painterResource(R.drawable.ic_round_undo_24),
                    onClick = { viewModel.toolbarClick(ToolBarItem.Undo) },
                    onLongClick = { viewModel.showUndoRedoMenu = true }
            )
        }

        // Hint 按钮
        if (!hintsDisabled) {
            ToolbarItem(
                    modifier = Modifier.weight(1f),
                    painter = painterResource(R.drawable.ic_lightbulb_stars_24),
                    onClick = { viewModel.toolbarClick(ToolBarItem.Hint) }
            )
        }

        // Notes 按钮
        Box(modifier = Modifier.weight(1f)) {
            NotesMenu(
                    expanded = viewModel.showNotesMenu,
                    onDismiss = { viewModel.showNotesMenu = false },
                    onComputeNotesClick = { viewModel.computeNotes() },
                    onClearNotesClick = { viewModel.clearNotes() },
                    renderNotes = uiState.renderNotes,
                    onRenderNotesClick = { uiState.onRenderNotesChange(!uiState.renderNotes) }
            )
            ToolbarItem(
                    painter = painterResource(R.drawable.ic_round_edit_24),
                    toggled = viewModel.notesToggled,
                    onClick = { viewModel.toolbarClick(ToolBarItem.Note) },
                    onLongClick = {
                        if (viewModel.gamePlaying) {
                            uiState.localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.showNotesMenu = true
                        }
                    }
            )
        }

        // Eraser 按钮
        ToolbarItem(
                modifier = Modifier.weight(1f),
                painter = painterResource(R.drawable.ic_eraser_24),
                toggled = viewModel.eraseButtonToggled,
                onClick = { viewModel.toolbarClick(ToolBarItem.Remove) },
                onLongClick = {
                    if (viewModel.gamePlaying) {
                        uiState.localView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.toggleEraseButton()
                    }
                }
        )

        // Advanced Hint / 快进 按钮
        if (advancedHintEnabled) {
            Box(modifier = Modifier.weight(1f)) {
                ToolbarItem(
                        painter = rememberVectorPainter(
                            if (hasAutoHint) Icons.Rounded.FastForward 
                            else Icons.Rounded.AutoAwesome
                        ),
                        isLoading = isLoadingAdvancedHint || isAutoExecuting,
                        onClick = {
                            if (viewModel.gamePlaying) {
                                if (hasAutoHint) {
                                    // 执行自动提示
                                    viewModel.executeAutoHints()
                                } else {
                                    // 获取手动提示
                                    viewModel.getAdvancedHint()
                                }
                            }
                        }
                )
                
                // 红点提示
                if (hasAutoHint && !isAutoExecuting) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp, top = 8.dp)
                            .size(10.dp)
                            .background(
                                color = MaterialTheme.colorScheme.error,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }
        }
    }
}

/**
 * AdvancedHint 显示区域组件
 * 处理有数据和无数据两种情况
 */
@Composable
fun AdvancedHintSection(
        advancedHintData: AdvancedHintData?,
        viewModel: GameViewModel,
        navigator: DestinationsNavigator
) {
    advancedHintData?.let { hintData ->
        AdvancedHintContainer(
                advancedHintData = hintData,
                onApplyClick = { viewModel.applyAdvancedHint() },
                onBackClick = { viewModel.cancelAdvancedHint() },
                onSettingsClick = { navigator.navigate(SettingsAdvancedHintScreenDestination) }
        )
    }
    if (advancedHintData == null) {
        NoAdvancedHintContainer(
                navigator = navigator,
                onBackClick = { viewModel.cancelAdvancedHint() }
        )
    }
}

/** "无高级提示"的占位组件 */
@Composable
fun NoAdvancedHintContainer(navigator: DestinationsNavigator, onBackClick: () -> Unit) {
    AdvancedHintContainer(
            advancedHintData =
                    AdvancedHintData(
                            titleRes = R.string.advanced_hint_no_hint_title,
                            textResWithArg = Pair(R.string.advanced_hint_no_hint, emptyList()),
                            targetCells = emptyList(),
                            helpCells = emptyList()
                    ),
            onApplyClick = null,
            onBackClick = onBackClick,
            onSettingsClick = { navigator.navigate(SettingsAdvancedHintScreenDestination) }
    )
}

/**
 * 绘画工具栏包装组件
 */
@Composable
fun DrawingToolbarSection(
        drawingState: DrawingState,
        onDrawingStateChange: (DrawingState) -> Unit,
        modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        DrawingToolbar(
                modifier = if (modifier != Modifier) Modifier.fillMaxWidth() else Modifier,
                selectedColor = drawingState.currentColor,
                onColorSelected = { color -> onDrawingStateChange(drawingState.changeColor(color)) },
                onUndo = { onDrawingStateChange(drawingState.undo()) },
                onClear = { onDrawingStateChange(drawingState.clear()) },
                canUndo = drawingState.paths.isNotEmpty()
        )
    }
}

/**
 * 游戏键盘与工具栏组合组件
 */
@Composable
fun GameKeyboardSection(
        viewModel: GameViewModel,
        preferences: GamePreferences,
        uiState: GameUiState,
        modifier: Modifier = Modifier
) {
    Column(
            verticalArrangement =
                    if (preferences.funKeyboardOverNum) ReverseArrangement else Arrangement.Top,
            modifier = modifier
    ) {
        // 根据横竖屏显示不同的键盘
        if (uiState.isLandscape) {
            SquareGameKeyboard(
                    size = viewModel.size,
                    remainingUses = if (preferences.remainingUse) viewModel.remainingUsesList else null,
                    onClick = { viewModel.processInputKeyboard(number = it) },
                    onLongClick = {
                        viewModel.processInputKeyboard(number = it, longTap = true)
                    },
                    selected = viewModel.digitFirstNumber
            )
        } else {
            DefaultGameKeyboard(
                    size = viewModel.size,
                    remainingUses = if (preferences.remainingUse) viewModel.remainingUsesList else null,
                    onClick = { viewModel.processInputKeyboard(number = it) },
                    onLongClick = {
                        viewModel.processInputKeyboard(number = it, longTap = true)
                    },
                    selected = viewModel.digitFirstNumber
            )
        }

        GameToolbarRow(
                viewModel = viewModel,
                uiState = uiState,
                modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

/**
 * 游戏完成统计包装组件
 */
@Composable
fun GameCompletedSection(
        viewModel: GameViewModel,
        preferences: GamePreferences,
        modifier: Modifier = Modifier
) {
    val allRecords by
            viewModel.allRecords.collectAsStateWithLifecycle(initialValue = emptyList())

    AfterGameStats(
            modifier = modifier,
            difficulty = viewModel.gameDifficulty,
            type = viewModel.gameType,
            hintsUsed = viewModel.hintsUsed,
            mistakesMade = viewModel.mistakesMade,
            mistakesLimit = preferences.mistakesLimit,
            mistakesLimitCount = viewModel.mistakesCount,
            giveUp = viewModel.giveUp,
            notesTaken = viewModel.notesTaken,
            records = allRecords,
            timeText = viewModel.timeText
    )
}

/**
 * 底部内容区域组件
 * 统一处理绘画模式、游戏模式和游戏完成的切换
 */
@Composable
fun BottomContentSection(
        viewModel: GameViewModel,
        drawingState: DrawingState,
        onDrawingStateChange: (DrawingState) -> Unit,
        preferences: GamePreferences,
        uiState: GameUiState
) {
    AnimatedContent(!viewModel.endGame, label = "game_content") { isGameActive ->
        if (isGameActive) {
            AnimatedContent(
                    drawingState.isDrawingMode,
                    label = if (uiState.isLandscape) "drawing_mode_landscape" else "drawing_mode_portrait"
            ) { isDrawing ->
                if (isDrawing) {
                    DrawingToolbarSection(
                            drawingState = drawingState,
                            onDrawingStateChange = onDrawingStateChange,
                            modifier = if (uiState.isLandscape) Modifier else Modifier.fillMaxWidth()
                    )
                } else {
                    GameKeyboardSection(
                            viewModel = viewModel,
                            preferences = preferences,
                            uiState = uiState
                    )
                }
            }
        } else {
            GameCompletedSection(
                    viewModel = viewModel,
                    preferences = preferences,
                    modifier = if (uiState.isLandscape) Modifier else Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event -> eventHandler.value(owner, event) }

        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable fun KeepScreenOn() = AndroidView({ View(it).apply { keepScreenOn = true } })
