package com.kaajjo.libresudoku.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kaajjo.libresudoku.core.PreferencesConstants
import com.kaajjo.libresudoku.core.qqwing.GameDifficulty
import com.kaajjo.libresudoku.core.qqwing.GameType
import com.kaajjo.libresudoku.core.qqwing.advanced_hint.AdvancedHintSettings
import com.kaajjo.libresudoku.ui.settings.autoupdate.UpdateChannel
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Singleton

@Singleton
class AppSettingsManager(context: Context) {
    private val Context.createDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
    private val dataStore = context.createDataStore

    // first app launch
    private val firstLaunchKey = booleanPreferencesKey("first_launch")

    // input method (0 -> cell first, 1 -> digit first)
    private val inputMethodKey = intPreferencesKey("input_method")

    // mistakes limit
    private val mistakesLimitKey = booleanPreferencesKey("mistakes_limit")

    // disable hint button
    private val hintsDisabledKey = booleanPreferencesKey("hints_disabled")

    // show timer
    private val timerKey = booleanPreferencesKey("timer")

    // game reset resets timer
    private val resetTimerKey = booleanPreferencesKey("timer_reset")

    // highlight mistakes
    private val highlightMistakesKey = intPreferencesKey("mistakes_highlight")

    // highlight same numbers
    private val highlightIdenticalKey = booleanPreferencesKey("same_values_highlight")

    // count and show remaining uses for numbers
    private val remainingUseKey = booleanPreferencesKey("remaining_use")

    // highlight current position with horizontal and vertical lines
    private val positionLinesKey = booleanPreferencesKey("position_lines")

    // auto erase notes
    private val autoEraseNotesKey = booleanPreferencesKey("notes_auto_erase")

    // font size (0 automatic (default), 1 - small, 2 - medium, 3 - big)
    private val fontSizeKey = intPreferencesKey("board_font_size")

    // keep screen on
    private val keepScreenOnKey = booleanPreferencesKey("keep_screen_on")

    // first game
    private val firstGameKey = booleanPreferencesKey("first_game")

    // place function keyboard (undo, erase etc.) above the numbers keyboard
    private val funKeyboardOverNumKey = booleanPreferencesKey("fun_keyboard_over_numbers")

    // custom date format
    private val dateFormatKey = stringPreferencesKey("date_format")

    // whether to save the last selected type and difficulty in the HomeScreen
    private val saveSelectedGameDifficultyTypeKey =
        booleanPreferencesKey("save_last_selected_difficulty_type")

    // last selected difficulty and type
    private val lastSelectedGameDifficultyTypeKey =
        stringPreferencesKey("last_selected_difficulty_type")

    // URI for automatic backups
    private val backupUriKey = stringPreferencesKey("backup_persistent_uri")

    // Interval in hours between automatic backups
    private val autoBackupIntervalKey = longPreferencesKey("auto_backup_interval")

    // Max number of automatic backup files
    private val autoBackupsNumberKey = intPreferencesKey("auto_backups_max_number")

    private val lastBackupDateKey = longPreferencesKey("last_backup_date")

    private val advancedHintKey = booleanPreferencesKey("advanced_hint")

    // 改用intPreferencesKey存储HintMode (0=DISABLED, 1=ENABLED, 2=AUTO)
    // 使用_v2后缀避免与旧版本Boolean类型冲突
    private val ahFullHouseKey = intPreferencesKey("ah_full_house_v2")
    private val ahNakedSingleKey = intPreferencesKey("ah_naked_single_v2")
    private val ahHiddenSingleKey = intPreferencesKey("ah_hidden_single_v2")
    private val ahCheckWrongValueKey = intPreferencesKey("ah_check_wrong_value_v2")
    private val ahCheckMissingOrWrongNoteKey = intPreferencesKey("ah_check_missing_or_wrong_note_v2")
    private val ahLockedCandidatesKey = intPreferencesKey("ah_locked_candidates_v2")
    private val ahNakedSubsetsKey = intPreferencesKey("ah_naked_subsets_v2")
    private val ahHiddenSubsetsKey = intPreferencesKey("ah_hidden_subsets_v2")
    private val ahSueDeCoqKey = intPreferencesKey("ah_sue_de_coq_v2")
    private val ahXWingsKey = intPreferencesKey("ah_x_wings_v2")
    private val ahXYWingsKey = intPreferencesKey("ah_xy_wings_v2")
    private val ahXYZWingsKey = intPreferencesKey("ah_xyz_wings_v2")
    private val ahWWingsKey = intPreferencesKey("ah_w_wings_v2")
    private val ahFishPatternsKey = intPreferencesKey("ah_fish_patterns_v2")
    private val ahFinnedFishVariantsKey = intPreferencesKey("ah_finned_fish_variants_v2")
    private val ahXChainKey = intPreferencesKey("ah_x_chain_v2")
    private val ahXYChainKey = intPreferencesKey("ah_xy_chain_v2")
    private val ahAICType1Key = intPreferencesKey("ah_aic_type1_v2")
    private val ahAICType2Key = intPreferencesKey("ah_aic_type2_v2")
    
    // 兼容旧版本的Boolean key（用于迁移）
    private val ahFullHouseKeyLegacy = booleanPreferencesKey("ah_full_house")
    private val ahNakedSingleLegacy = booleanPreferencesKey("ah_naked_single")
    private val ahHiddenSingleLegacy = booleanPreferencesKey("ah_hidden_single")
    private val ahCheckWrongValueLegacy = booleanPreferencesKey("ah_check_wrong_value")
    private val ahCheckMissingOrWrongNoteLegacy = booleanPreferencesKey("ah_check_missing_or_wrong_note")
    private val ahLockedCandidatesLegacy = booleanPreferencesKey("ah_locked_candidates")
    private val ahNakedSubsetsLegacy = booleanPreferencesKey("ah_naked_subsets")
    private val ahHiddenSubsetsLegacy = booleanPreferencesKey("ah_hidden_subsets")
    private val ahSueDeCoqLegacy = booleanPreferencesKey("ah_sue_de_coq")
    private val ahXWingsLegacy = booleanPreferencesKey("ah_x_wings")
    private val ahXYWingsLegacy = booleanPreferencesKey("ah_xy_wings")
    private val ahXYZWingsLegacy = booleanPreferencesKey("ah_xyz_wings")
    private val ahWWingsLegacy = booleanPreferencesKey("ah_w_wings")
    private val ahFishPatternsLegacy = booleanPreferencesKey("ah_fish_patterns")
    private val ahFinnedFishVariantsLegacy = booleanPreferencesKey("ah_finned_fish_variants")
    private val ahXChainLegacy = booleanPreferencesKey("ah_x_chain")
    private val ahXYChainLegacy = booleanPreferencesKey("ah_xy_chain")
    private val ahAICType1Legacy = booleanPreferencesKey("ah_aic_type1")
    private val ahAICType2Legacy = booleanPreferencesKey("ah_aic_type2")

    private val autoUpdateChannelKey = intPreferencesKey("auto_update")
    private val updateDismissedNameKey = stringPreferencesKey("update_dismissed_name") // name of the update that was dismissed

    suspend fun setFirstLaunch(value: Boolean) {
        dataStore.edit { settings ->
            settings[firstLaunchKey] = value
        }
    }

    val firstLaunch = dataStore.data.map { preferences ->
        preferences[firstLaunchKey] ?: true
    }

    suspend fun setMistakesLimit(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[mistakesLimitKey] = enabled
        }
    }

    val mistakesLimit = dataStore.data.map { preferences ->
        preferences[mistakesLimitKey] ?: PreferencesConstants.DEFAULT_MISTAKES_LIMIT
    }

    suspend fun setHintsDisabled(disabled: Boolean) {
        dataStore.edit { settings ->
            settings[hintsDisabledKey] = disabled
        }
    }

    val hintsDisabled = dataStore.data.map { preferences ->
        preferences[hintsDisabledKey] ?: PreferencesConstants.DEFAULT_HINTS_DISABLED
    }

    suspend fun setTimer(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[timerKey] = enabled
        }
    }

    val timerEnabled = dataStore.data.map { preferences ->
        preferences[timerKey] ?: PreferencesConstants.DEFAULT_SHOW_TIMER
    }

    suspend fun setResetTimer(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[resetTimerKey] = enabled
        }
    }

    val resetTimerEnabled = dataStore.data.map { preferences ->
        preferences[resetTimerKey] ?: PreferencesConstants.DEFAULT_GAME_RESET_TIMER
    }

    suspend fun setHighlightMistakes(value: Int) {
        dataStore.edit { settings ->
            settings[highlightMistakesKey] = value
        }
    }

    val highlightMistakes = dataStore.data.map { preferences ->
        preferences[highlightMistakesKey] ?: PreferencesConstants.DEFAULT_HIGHLIGHT_MISTAKES
    }

    suspend fun setSameValuesHighlight(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[highlightIdenticalKey] = enabled
        }
    }

    val highlightIdentical = dataStore.data.map { preferences ->
        preferences[highlightIdenticalKey] ?: PreferencesConstants.DEFAULT_HIGHLIGHT_IDENTICAL
    }

    suspend fun setRemainingUse(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[remainingUseKey] = enabled
        }
    }

    val remainingUse = dataStore.data.map { preferences ->
        preferences[remainingUseKey] ?: PreferencesConstants.DEFAULT_REMAINING_USES
    }

    suspend fun setPositionLines(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[positionLinesKey] = enabled
        }
    }

    val positionLines = dataStore.data.map { preferences ->
        preferences[positionLinesKey] ?: PreferencesConstants.DEFAULT_POSITION_LINES
    }

    suspend fun setAutoEraseNotes(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[autoEraseNotesKey] = enabled
        }
    }

    val autoEraseNotes = dataStore.data.map { preferences ->
        preferences[autoEraseNotesKey] ?: PreferencesConstants.DEFAULT_AUTO_ERASE_NOTES
    }

    suspend fun setInputMethod(value: Int) {
        dataStore.edit { settings ->
            settings[inputMethodKey] = value
        }
    }

    val inputMethod = dataStore.data.map { preferences ->
        preferences[inputMethodKey] ?: PreferencesConstants.DEFAULT_INPUT_METHOD
    }

    suspend fun setFontSize(value: Int) {
        dataStore.edit { settings ->
            settings[fontSizeKey] = value
        }
    }

    val fontSize = dataStore.data.map { preferences ->
        preferences[fontSizeKey] ?: PreferencesConstants.DEFAULT_FONT_SIZE_FACTOR
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[keepScreenOnKey] = enabled
        }
    }

    val keepScreenOn = dataStore.data.map { preferences ->
        preferences[keepScreenOnKey] ?: PreferencesConstants.DEFAULT_KEEP_SCREEN_ON
    }

    suspend fun setFirstGame(value: Boolean) {
        dataStore.edit { settings ->
            settings[firstGameKey] = value
        }
    }

    val firstGame = dataStore.data.map { preferences ->
        preferences[firstGameKey] ?: true
    }

    suspend fun setFunKeyboardOverNum(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[funKeyboardOverNumKey] = enabled
        }
    }

    val funKeyboardOverNumbers = dataStore.data.map { prefs ->
        prefs[funKeyboardOverNumKey] ?: PreferencesConstants.DEFAULT_FUN_KEYBOARD_OVER_NUM
    }

    suspend fun setDateFormat(format: String) {
        dataStore.edit { settings ->
            settings[dateFormatKey] = format
        }
    }

    val dateFormat = dataStore.data.map { prefs ->
        prefs[dateFormatKey] ?: ""
    }

    suspend fun setSaveSelectedGameDifficultyType(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[saveSelectedGameDifficultyTypeKey] = enabled
        }
    }

    /**
     * Whether to save the last selected type and difficulty in the HomeScreen
     */
    val saveSelectedGameDifficultyType = dataStore.data.map { prefs ->
        prefs[saveSelectedGameDifficultyTypeKey]
            ?: PreferencesConstants.DEFAULT_SAVE_LAST_SELECTED_DIFF_TYPE
    }

    suspend fun setLastSelectedGameDifficultyType(
        difficulty: GameDifficulty,
        type: GameType
    ) {
        dataStore.edit { settings ->
            var difficultyAndType = when (difficulty) {
                GameDifficulty.Unspecified -> "0"
                GameDifficulty.Simple -> "1"
                GameDifficulty.Easy -> "2"
                GameDifficulty.Moderate -> "3"
                GameDifficulty.Hard -> "4"
                GameDifficulty.Challenge -> "5"
                GameDifficulty.Custom -> "6"
            }
            difficultyAndType += ";"
            difficultyAndType += when (type) {
                GameType.Unspecified -> "0"
                GameType.Default9x9 -> "1"
                GameType.Default12x12 -> "2"
                GameType.Default6x6 -> "3"
                GameType.Killer9x9 -> "4"
                GameType.Killer12x12 -> "5"
                GameType.Killer6x6 -> "6"
            }
            settings[lastSelectedGameDifficultyTypeKey] = difficultyAndType
        }
    }

    /**
     * Last selected difficulty and type. Returns Pair<GameDifficulty, GameType>
     */
    val lastSelectedGameDifficultyType = dataStore.data.map { prefs ->
        var gameDifficulty = GameDifficulty.Easy
        var gameType = GameType.Default9x9

        val key = prefs[lastSelectedGameDifficultyTypeKey] ?: ""
        if (key.isNotEmpty() && key.contains(";")) {
            gameDifficulty = when (key.substring(0, key.indexOf(";"))) {
                "0" -> GameDifficulty.Unspecified
                "1" -> GameDifficulty.Simple
                "2" -> GameDifficulty.Easy
                "3" -> GameDifficulty.Moderate
                "4" -> GameDifficulty.Hard
                "5" -> GameDifficulty.Challenge
                "6" -> GameDifficulty.Custom
                else -> GameDifficulty.Easy
            }
            gameType = when (key.substring(key.indexOf(";") + 1)) {
                "0" -> GameType.Unspecified
                "1" -> GameType.Default9x9
                "2" -> GameType.Default12x12
                "3" -> GameType.Default6x6
                "4" -> GameType.Killer9x9
                "5" -> GameType.Killer12x12
                "6" -> GameType.Killer6x6
                else -> GameType.Default9x9
            }
        }
        Pair(gameDifficulty, gameType)
    }

    suspend fun setBackupUri(uri: String) {
        dataStore.edit { settings ->
            settings[backupUriKey] = uri
        }
    }

    val backupUri = dataStore.data.map { prefs -> prefs[backupUriKey] ?: "" }

    suspend fun setAutoBackupInterval(hours: Long) {
        dataStore.edit { settings ->
            settings[autoBackupIntervalKey] = hours
        }
    }

    val autoBackupInterval = dataStore.data.map { prefs ->
        prefs[autoBackupIntervalKey] ?: PreferencesConstants.DEFAULT_AUTOBACKUP_INTERVAL
    }

    suspend fun setAutoBackupsNumber(value: Int) {
        dataStore.edit { settings ->
            settings[autoBackupsNumberKey] = value
        }
    }

    val autoBackupsNumber = dataStore.data.map { prefs ->
        prefs[autoBackupsNumberKey] ?: PreferencesConstants.DEFAULT_AUTO_BACKUPS_NUMBER
    }

    suspend fun setLastBackupDate(date: ZonedDateTime) {
        dataStore.edit {  settings ->
            settings[lastBackupDateKey] = date.toInstant().epochSecond
        }
    }

    val lastBackupDate = dataStore.data.map { prefs ->
        val date = prefs[lastBackupDateKey]
        if (date != null) {
            ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(date),
                ZoneId.systemDefault()
            )
        } else {
            null
        }



    }

    val advancedHintEnabled = dataStore.data.map { settings ->
        settings[advancedHintKey] ?: PreferencesConstants.DEFAULT_ADVANCED_HINT
    }

    suspend fun setAdvancedHint(enabled: Boolean) {
        dataStore.edit { settings ->
            settings[advancedHintKey] = enabled
        }
    }

    val advancedHintSettings = dataStore.data.map { settings ->
        // 辅助函数：读取HintMode，支持从旧版本Boolean迁移
        fun getHintMode(key: Preferences.Key<Int>, legacyKey: Preferences.Key<Boolean>): com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode {
            val intValue = settings[key]
            if (intValue != null) {
                return com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.fromInt(intValue)
            }
            // 尝试从旧版本Boolean迁移
            val boolValue = settings[legacyKey]
            return if (boolValue != null) {
                com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.fromBoolean(boolValue)
            } else {
                com.kaajjo.libresudoku.core.qqwing.advanced_hint.HintMode.ENABLED // 默认值
            }
        }
        
        AdvancedHintSettings(
            fullHouse = getHintMode(ahFullHouseKey, ahFullHouseKeyLegacy),
            nakedSingle = getHintMode(ahNakedSingleKey, ahNakedSingleLegacy),
            hiddenSingle = getHintMode(ahHiddenSingleKey, ahHiddenSingleLegacy),
            checkWrongValue = getHintMode(ahCheckWrongValueKey, ahCheckWrongValueLegacy),
            checkMissingOrWrongNote = getHintMode(ahCheckMissingOrWrongNoteKey, ahCheckMissingOrWrongNoteLegacy),
            lockedCandidates = getHintMode(ahLockedCandidatesKey, ahLockedCandidatesLegacy),
            nakedSubsets = getHintMode(ahNakedSubsetsKey, ahNakedSubsetsLegacy),
            hiddenSubsets = getHintMode(ahHiddenSubsetsKey, ahHiddenSubsetsLegacy),
            sueDeCoq = getHintMode(ahSueDeCoqKey, ahSueDeCoqLegacy),
            xWings = getHintMode(ahXWingsKey, ahXWingsLegacy),
            xyWings = getHintMode(ahXYWingsKey, ahXYWingsLegacy),
            xyzWings = getHintMode(ahXYZWingsKey, ahXYZWingsLegacy),
            wWings = getHintMode(ahWWingsKey, ahWWingsLegacy),
            fishPatterns = getHintMode(ahFishPatternsKey, ahFishPatternsLegacy),
            finnedFishVariants = getHintMode(ahFinnedFishVariantsKey, ahFinnedFishVariantsLegacy),
            xChain = getHintMode(ahXChainKey, ahXChainLegacy),
            xyChain = getHintMode(ahXYChainKey, ahXYChainLegacy),
            aicType1 = getHintMode(ahAICType1Key, ahAICType1Legacy),
            aicType2 = getHintMode(ahAICType2Key, ahAICType2Legacy),
        )
    }

    suspend fun updateAdvancedHintSettings(ahSettings: AdvancedHintSettings) {
        dataStore.edit { settings ->
            settings[ahFullHouseKey] = ahSettings.fullHouse.toInt()
            settings[ahNakedSingleKey] = ahSettings.nakedSingle.toInt()
            settings[ahHiddenSingleKey] = ahSettings.hiddenSingle.toInt()
            settings[ahCheckWrongValueKey] = ahSettings.checkWrongValue.toInt()
            settings[ahCheckMissingOrWrongNoteKey] = ahSettings.checkMissingOrWrongNote.toInt()
            settings[ahLockedCandidatesKey] = ahSettings.lockedCandidates.toInt()
            settings[ahNakedSubsetsKey] = ahSettings.nakedSubsets.toInt()
            settings[ahHiddenSubsetsKey] = ahSettings.hiddenSubsets.toInt()
            settings[ahSueDeCoqKey] = ahSettings.sueDeCoq.toInt()
            settings[ahXWingsKey] = ahSettings.xWings.toInt()
            settings[ahXYWingsKey] = ahSettings.xyWings.toInt()
            settings[ahXYZWingsKey] = ahSettings.xyzWings.toInt()
            settings[ahWWingsKey] = ahSettings.wWings.toInt()
            settings[ahFishPatternsKey] = ahSettings.fishPatterns.toInt()
            settings[ahFinnedFishVariantsKey] = ahSettings.finnedFishVariants.toInt()
            settings[ahXChainKey] = ahSettings.xChain.toInt()
            settings[ahXYChainKey] = ahSettings.xyChain.toInt()
            settings[ahAICType1Key] = ahSettings.aicType1.toInt()
            settings[ahAICType2Key] = ahSettings.aicType2.toInt()
        }
    }

    val autoUpdateChannel = dataStore.data.map { settings ->
        val channel = settings[autoUpdateChannelKey] ?: PreferencesConstants.DEFAULT_AUTOUPDATE_CHANNEL
        when (channel) {
            0 -> UpdateChannel.Disabled
            1 -> UpdateChannel.Stable
            2 -> UpdateChannel.Beta
            else -> UpdateChannel.Disabled
        }
    }

    suspend fun setAutoUpdateChannel(channel: UpdateChannel) {
        dataStore.edit { settings ->
            settings[autoUpdateChannelKey] = when (channel) {
                UpdateChannel.Disabled -> 0
                UpdateChannel.Stable -> 1
                UpdateChannel.Beta -> 2
            }
        }
    }

    val updateDismissedName = dataStore.data.map { settings ->
        settings[updateDismissedNameKey] ?: ""
    }

    suspend fun setUpdateDismissedName(name: String) {
        dataStore.edit { settings ->
            settings[updateDismissedNameKey] = name
        }
    }

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> {
                DateTimeFormatter.ofPattern(
                    DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                        FormatStyle.SHORT,
                        null,
                        IsoChronology.INSTANCE,
                        Locale.getDefault()
                    )
                )
            }

            else -> {
                DateTimeFormatter.ofPattern(format, Locale.getDefault())
            }
        }
    }
}