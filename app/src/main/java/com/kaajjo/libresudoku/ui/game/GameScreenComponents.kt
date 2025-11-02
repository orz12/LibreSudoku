package com.kaajjo.libresudoku.ui.game

import android.content.res.Configuration
import android.os.Build.VERSION.SDK_INT
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.core.Cell
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintData
import com.kaajjo.libresudoku.core.utils.SudokuParser
import com.kaajjo.libresudoku.destinations.SettingsAdvancedHintScreenDestination
import com.kaajjo.libresudoku.ui.components.board.Board
import com.kaajjo.libresudoku.ui.game.components.*
import com.kaajjo.libresudoku.destinations.SettingsCategoriesScreenDestination
import com.kaajjo.libresudoku.ui.util.ReverseArrangement
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.PlayCircle
import android.os.Build

/**
 * 顶部操作栏组件
 */
@Composable
fun TopGameBar(
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    arrangement: Arrangement.Horizontal,
    viewModel: GameViewModel,
    navigator: DestinationsNavigator,
    drawingState: DrawingState,
    onDrawingStateChange: (DrawingState) -> Unit,
    restartButtonAnimation: Float,
    isLandscape: Boolean
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    Row(
        modifier = modifier,
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮（竖屏时在左侧）
        if (showBackButton && !isLandscape) {
            IconButton(onClick = { navigator.popBackStack() }) {
                Icon(
                    painter = painterResource(R.drawable.ic_round_arrow_back_24),
                    contentDescription = "返回"
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
        
        // 游戏结束时的显示解决方案按钮
        AnimatedVisibility(visible = viewModel.endGame && (viewModel.mistakesCount >= PreferencesConstants.MISTAKES_LIMIT || viewModel.giveUp)) {
            FilledTonalButton(
                onClick = { viewModel.showSolution = !viewModel.showSolution }
            ) {
                AnimatedContent(
                    if (viewModel.showSolution) stringResource(R.string.action_show_mine_sudoku)
                    else stringResource(R.string.action_show_solution),
                    label = "Show solution/mine button"
                ) {
                    Text(it)
                }
            }
        }
        
        // 暂停/继续按钮
        AnimatedVisibility(visible = !viewModel.endGame) {
            val rotationAngle by animateFloatAsState(
                targetValue = if (viewModel.gamePlaying) 0f else 360f,
                label = "Play/Pause game icon rotation"
            )
            IconButton(onClick = {
                if (!viewModel.gamePlaying) viewModel.startTimer() else viewModel.pauseTimer()
                viewModel.currCell = Cell(-1, -1, 0)
            }) {
                Icon(
                    modifier = Modifier.rotate(rotationAngle),
                    painter = painterResource(
                        if (viewModel.gamePlaying) R.drawable.ic_round_pause_24
                        else R.drawable.ic_round_play_24
                    ),
                    contentDescription = null
                )
            }
        }
        
        // 重新开始按钮
        AnimatedVisibility(visible = !viewModel.endGame) {
            IconButton(onClick = { viewModel.restartDialog = true }) {
                Icon(
                    modifier = Modifier.rotate(restartButtonAnimation),
                    painter = painterResource(R.drawable.ic_round_replay_24),
                    contentDescription = null
                )
            }
        }
        
        // 绘画模式按钮
        AnimatedVisibility(visible = !viewModel.endGame) {
            IconButton(onClick = { onDrawingStateChange(drawingState.toggleDrawingMode()) }) {
                Icon(
                    imageVector = Icons.Rounded.Brush,
                    contentDescription = "绘画模式",
                    tint = if (drawingState.isDrawingMode) MaterialTheme.colorScheme.primary 
                          else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // 更多菜单
        AnimatedVisibility(visible = !viewModel.endGame) {
            Box {
                IconButton(onClick = { viewModel.showMenu = !viewModel.showMenu }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                GameMenu(
                    expanded = viewModel.showMenu,
                    onDismiss = { viewModel.showMenu = false },
                    onGiveUpClick = {
                        viewModel.pauseTimer()
                        viewModel.giveUpDialog = true
                    },
                    onSettingsClick = {
                        navigator.navigate(SettingsCategoriesScreenDestination(launchedFromGame = true))
                        viewModel.showMenu = false
                    },
                    onExportClick = {
                        val stringBoard = SudokuParser().boardToString(viewModel.gameBoard, emptySeparator = '.')
                        clipboardManager.setText(AnnotatedString(stringBoard.uppercase()))
                        if (SDK_INT < 33) {
                            Toast.makeText(context, R.string.export_string_state_copied, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

/**
 * 游戏信息显示组件
 */
@Composable
fun GameInfoDisplay(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal,
    viewModel: GameViewModel,
    mistakesLimit: Boolean,
    errorHighlight: Int
) {
    AnimatedVisibility(visible = !viewModel.endGame) {
        Column(
            modifier = modifier,
            horizontalAlignment = horizontalAlignment
        ) {
            Text(
                text = stringResource(viewModel.gameDifficulty.resName),
                style = MaterialTheme.typography.bodySmall
            )
            
            if (mistakesLimit && errorHighlight != 0) {
                Text(
                    text = stringResource(
                        R.string.mistakes_number_out_of,
                        viewModel.mistakesCount,
                        3
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            val timerEnabled by viewModel.timerEnabled.collectAsStateWithLifecycle(
                initialValue = PreferencesConstants.DEFAULT_SHOW_TIMER
            )
            if (timerEnabled || viewModel.endGame) {
                Text(
                    text = viewModel.timeText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

