package com.kaajjo.libresudoku.core.qqwing.advanced_hint

/**
 * 高级提示的技巧设置
 *
 * 每个技巧支持三种模式：
 * - DISABLED: 关闭
 * - ENABLED: 启用（手动）
 * - AUTO: 自动执行
 *
 * @constructor 所有技巧默认为ENABLED（启用但不自动）
 */
data class AdvancedHintSettings(
    val fullHouse: HintMode = HintMode.ENABLED,
    val nakedSingle: HintMode = HintMode.ENABLED,
    val hiddenSingle: HintMode = HintMode.ENABLED,
    val checkWrongValue: HintMode = HintMode.ENABLED,
    val checkMissingOrWrongNote: HintMode = HintMode.ENABLED,
    val lockedCandidates: HintMode = HintMode.ENABLED,
    val nakedSubsets: HintMode = HintMode.ENABLED,
    val hiddenSubsets: HintMode = HintMode.ENABLED,
    val sueDeCoq: HintMode = HintMode.ENABLED,
    val xWings: HintMode = HintMode.ENABLED,
    val xyWings: HintMode = HintMode.ENABLED,
    val xyzWings: HintMode = HintMode.ENABLED,
    val wWings: HintMode = HintMode.ENABLED,
    val fishPatterns: HintMode = HintMode.ENABLED,
    val finnedFishVariants: HintMode = HintMode.ENABLED,
    val xChain: HintMode = HintMode.ENABLED,
    val xyChain: HintMode = HintMode.ENABLED,
    val aicType1: HintMode = HintMode.ENABLED,
    val aicType2: HintMode = HintMode.ENABLED,
) {
    /**
     * 检查是否有任何技巧设置为自动模式
     */
    fun hasAutoMode(): Boolean {
        return fullHouse.isAuto() ||
                nakedSingle.isAuto() ||
                hiddenSingle.isAuto() ||
                checkWrongValue.isAuto() ||
                checkMissingOrWrongNote.isAuto() ||
                lockedCandidates.isAuto() ||
                nakedSubsets.isAuto() ||
                hiddenSubsets.isAuto() ||
                sueDeCoq.isAuto() ||
                xWings.isAuto() ||
                xyWings.isAuto() ||
                xyzWings.isAuto() ||
                wWings.isAuto() ||
                fishPatterns.isAuto() ||
                finnedFishVariants.isAuto() ||
                xChain.isAuto() ||
                xyChain.isAuto() ||
                aicType1.isAuto() ||
                aicType2.isAuto()
    }
}
