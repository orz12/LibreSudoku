package com.kaajjo.libresudoku.core.qqwing.advanced_hint

/**
 * With this, you can enable or disable techniques for advanced hint
 *
 * @constructor Everything is enabled by default
 */
data class AdvancedHintSettings(
    val fullHouse: Boolean = true,
    val nakedSingle: Boolean = true,
    val hiddenSingle: Boolean = true,
    val checkWrongValue: Boolean = true,
    val checkMissingOrWrongNote: Boolean = true,
    val lockedCandidates: Boolean = true,
    val nakedSubsets: Boolean = true,
    val hiddenSubsets: Boolean = true,
    val sueDeCoq: Boolean = true,
    val xWings: Boolean = true,
    val xyWings: Boolean = true,
    val xyzWings: Boolean = true,
    val wWings: Boolean = true,
    val fishPatterns: Boolean = true,
    val finnedFishVariants: Boolean = true,
    val xChain: Boolean = true,
    val xyChain: Boolean = true,
)
