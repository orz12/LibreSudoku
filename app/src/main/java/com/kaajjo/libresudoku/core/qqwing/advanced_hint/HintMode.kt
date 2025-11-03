package com.kaajjo.libresudoku.core.qqwing.advanced_hint

/**
 * 高级提示技巧的三段式状态
 *
 * @property DISABLED 关闭 - 不使用该技巧
 * @property ENABLED 启用 - 手动使用该技巧（需要用户点击提示按钮）
 * @property AUTO 自动 - 自动检测并执行该技巧
 */
enum class HintMode {
    DISABLED,  // 关闭
    ENABLED,   // 启用（手动）
    AUTO;      // 自动

    companion object {
        /**
         * 从整数值转换为HintMode
         * @param value 0=DISABLED, 1=ENABLED, 2=AUTO
         */
        fun fromInt(value: Int): HintMode = when (value) {
            0 -> DISABLED
            1 -> ENABLED
            2 -> AUTO
            else -> ENABLED // 默认值
        }

        /**
         * 从Boolean转换为HintMode（用于兼容旧版本）
         * @param enabled true=ENABLED, false=DISABLED
         */
        fun fromBoolean(enabled: Boolean): HintMode = 
            if (enabled) ENABLED else DISABLED
    }

    /**
     * 转换为整数值（用于存储）
     */
    fun toInt(): Int = when (this) {
        DISABLED -> 0
        ENABLED -> 1
        AUTO -> 2
    }

    /**
     * 是否启用（启用或自动）
     */
    fun isActive(): Boolean = this != DISABLED

    /**
     * 是否自动模式
     */
    fun isAuto(): Boolean = this == AUTO
}

