package io.github.x07xh.xiaoxu.ui.v2

import kotlin.math.abs

/**
 * Motion-only quality policy for the selected dock lens. The resting material,
 * geometry, background capture, and navigation state are not changed.
 * Speeds use dp/s so a high-density screen is not treated as a faster gesture.
 */
internal object UiV2GlassMotionPolicy {
    private const val QualityStart = 600f
    private const val QualityFloor = 1200f

    fun motionFraction(velocityDp: Float): Float =
        if (velocityDp.isFinite()) (abs(velocityDp) / 900f).coerceIn(0f, 1f) else 0f

    fun lensQuality(velocityDp: Float): Float {
        if (!velocityDp.isFinite()) return 1f
        val t = ((abs(velocityDp) - QualityStart) / (QualityFloor - QualityStart)).coerceIn(0f, 1f)
        return 1f - 0.5f * t
    }

    fun allowChromaticFringe(velocityDp: Float): Boolean =
        velocityDp.isFinite() && abs(velocityDp) < QualityStart
}
