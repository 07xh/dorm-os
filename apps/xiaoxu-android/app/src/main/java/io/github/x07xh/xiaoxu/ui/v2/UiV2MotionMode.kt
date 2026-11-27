package io.github.x07xh.xiaoxu.ui.v2

internal enum class UiV2MotionMode(val title: String, val description: String) {
    Standard("标准", "平滑、克制的默认动画"),
    Fluid("灵动", "弹簧吸附与回位"),
    Reduced("减少动画", "直接切换，减少缩放和位移");

    companion object {
        fun parse(value: String?): UiV2MotionMode = entries.firstOrNull { it.name == value } ?: Standard
    }
}

internal fun uiV2EffectiveMotion(mode: UiV2MotionMode, systemScale: Float): UiV2MotionMode =
    if (systemScale == 0f) UiV2MotionMode.Reduced else mode
