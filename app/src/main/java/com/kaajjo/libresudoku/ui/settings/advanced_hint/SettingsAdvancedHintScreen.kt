package com.kaajjo.libresudoku.ui.settings.advanced_hint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaajjo.libresudoku.R
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintSettings
import com.kaajjo.libresudoku.ui.components.AnimatedNavigation
import com.kaajjo.libresudoku.ui.components.PreferenceRow
import com.kaajjo.libresudoku.ui.components.ScrollbarLazyColumn
import com.kaajjo.libresudoku.ui.gameshistory.ColorfulBadge
import com.kaajjo.libresudoku.ui.settings.SettingsCategory
import com.kaajjo.libresudoku.ui.settings.SettingsScaffoldLazyColumn
import com.kaajjo.libresudoku.ui.theme.ColorUtils.blend
import com.kaajjo.libresudoku.ui.theme.ColorUtils.harmonizeWithPrimary
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

@Destination(style = AnimatedNavigation::class)
@Composable
fun SettingsAdvancedHintScreen(
    viewModel: SettingsAdvancedHintViewModel = hiltViewModel(),
    navigator: DestinationsNavigator
) {
    val advancedHintEnabled by viewModel.advancedHintEnabled.collectAsStateWithLifecycle(
        PreferencesConstants.DEFAULT_ADVANCED_HINT
    )
    val advancedHintSettings by viewModel.advancedHintSettings.collectAsStateWithLifecycle(
        AdvancedHintSettings()
    )
    SettingsScaffoldLazyColumn(
        navigator = navigator,
        titleText = stringResource(R.string.advanced_hint_title)
    ) { paddingValues ->
        ScrollbarLazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                BigCardSwitch(
                    checked = advancedHintEnabled,
                    onClick = { viewModel.setAdvancedHintEnabled(!advancedHintEnabled) }
                )
            }
            item { SettingsCategory(title = stringResource(R.string.settings_advanced_hint_category_techniques)) }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_wrong_value_title),
                    mode = advancedHintSettings.checkWrongValue,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(checkWrongValue = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_full_house_group_title),
                    mode = advancedHintSettings.fullHouse,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(fullHouse = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_naked_single_title),
                    mode = advancedHintSettings.nakedSingle,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(nakedSingle = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_hidden_single_title),
                    mode = advancedHintSettings.hiddenSingle,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(hiddenSingle = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_missing_or_wrong_note_title),
                    mode = advancedHintSettings.checkMissingOrWrongNote,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(checkMissingOrWrongNote = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_locked_candidates_title),
                    mode = advancedHintSettings.lockedCandidates,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(lockedCandidates = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_naked_subsets_title),
                    mode = advancedHintSettings.nakedSubsets,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(nakedSubsets = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_hidden_subsets_title),
                    mode = advancedHintSettings.hiddenSubsets,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(hiddenSubsets = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_sue_de_coq_title),
                    mode = advancedHintSettings.sueDeCoq,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(sueDeCoq = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_x_wing_title),
                    mode = advancedHintSettings.xWings,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(xWings = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_xy_wing_title),
                    mode = advancedHintSettings.xyWings,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(xyWings = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_xyz_wing_title),
                    mode = advancedHintSettings.xyzWings,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(xyzWings = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_w_wing_title),
                    mode = advancedHintSettings.wWings,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(wWings = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_fish_patterns_title),
                    mode = advancedHintSettings.fishPatterns,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(fishPatterns = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_finned_fish_variants_title),
                    mode = advancedHintSettings.finnedFishVariants,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(finnedFishVariants = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_x_chain_title),
                    mode = advancedHintSettings.xChain,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(xChain = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_xy_chain_title),
                    mode = advancedHintSettings.xyChain,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(xyChain = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_aic_type1_title),
                    mode = advancedHintSettings.aicType1,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(aicType1 = newMode)
                        )
                    }
                )
            }
            item {
                TechniqueItem(
                    title = stringResource(R.string.hint_aic_type2_title),
                    mode = advancedHintSettings.aicType2,
                    onModeChange = { newMode ->
                        viewModel.updateAdvancedHintSettings(
                            advancedHintSettings.copy(aicType2 = newMode)
                        )
                    }
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            with(MaterialTheme.colorScheme) {
                                primaryContainer
                                    .blend(secondaryContainer)
                                    .copy(alpha = 0.75f)
                                    .compositeOver(surface)
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    ColorfulBadge(
                        text = stringResource(R.string.label_beta),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        foreground = MaterialTheme.colorScheme.onSecondaryContainer.harmonizeWithPrimary(),
                        background = MaterialTheme.colorScheme.secondaryContainer.harmonizeWithPrimary()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.advanced_hint_in_development),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun BigCardSwitch(
    checked: Boolean,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = { onClick(!checked) })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.advanced_hint_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer.harmonizeWithPrimary(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.padding(start = 12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onClick
            )
        }
    }
}

@Composable
fun TechniqueItem(
    title: String,
    mode: com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode,
    onModeChange: (com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode) -> Unit,
    modifier: Modifier = Modifier
) {
    PreferenceRow(
        modifier = modifier,
        title = title,
        onClick = {
            // 点击整行时，循环切换状态
            val nextMode = when (mode) {
                com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED -> 
                    com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED
                com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED -> 
                    com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.AUTO
                com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.AUTO -> 
                    com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.DISABLED
            }
            onModeChange(nextMode)
        },
        action = {
            com.kaajjo.libresudoku.ui.components.TriStateSwitch(
                mode = mode,
                onModeChange = onModeChange
            )
        }
    )
}