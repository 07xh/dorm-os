package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, glass/PhysicalLensSurface.kt and GlassCompat.kt.
// Platform-only optics; existing Xiaoxu performance tier gates all calls.
import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangularShape
import com.kyant.backdrop.BackdropEffectScope
import kotlin.math.abs

private fun isRuntimeLensEnabled() = Build.VERSION.SDK_INT >= 33

fun isLensShapeSupported(shape: Shape): Boolean =
    shape is RoundedRectangularShape || shape is CornerBasedShape

/**
 * 能不能开真透镜。
 *
 * 形状那一项**必须**在（见 [isLensShapeSupported]）：它不是画质优化，是防闪退。
 * 我曾把它当成"多余的防御"删掉，结果 `LiquidPicker` 的液滴融合体一上屏就抛
 * `UnsupportedOperationException`，打开即闪退。
 *
 * 顺带记下另一件已被证伪的判断：我曾认为 R8 full mode 裁掉了
 * `Capsule → RoundedRectangularShape` 的实现关系，导致 release 包全 App 无折射。
 * **不成立**：R8 mapping 里该接口原名保留未混淆，`dexdump` release APK 也能看到
 * `Lcom/kyant/shapes/Capsule;` 仍然声明 `Interfaces #0: RoundedRectangularShape`。
 * release 包看起来没有折射的真实原因在 `MainActivity.debugPiracyWatermark` 的注释里。
 *
 * 其余判据都是纯算术，与构建类型无关。**别再往这里加按类名反射的判据。**
 */
fun canUseLiquidLens(
    shape: Shape,
    refractionHeightPx: Float,
    refractionAmountPx: Float,
    minCornerRadiusPx: Float,
    minDimensionPx: Float
): Boolean =
    isRuntimeLensEnabled() &&
        isLensShapeSupported(shape) &&
        refractionHeightPx.isFinite() &&
        refractionAmountPx.isFinite() &&
        minCornerRadiusPx.isFinite() &&
        minDimensionPx.isFinite() &&
        refractionHeightPx > 0f &&
        refractionAmountPx > 0f &&
        minCornerRadiusPx > 0f &&
        minDimensionPx > 0f &&
        refractionHeightPx <= minCornerRadiusPx &&
        refractionAmountPx <= minDimensionPx

data class PhysicalLensParams(
    val blurPx: Float,
    val refractionHeightPx: Float,
    val refractionAmountPx: Float,
    val useLens: Boolean,
    val chromaticAberration: Boolean,
    /** API 31/32 色散近似的 RGB 分离偏移（px）；0 表示关闭。交互时才 > 0。 */
    val fringePx: Float = 0f
)

/**
 * 解析物理透镜参数。
 *
 * ## 为什么第一个参数是 `BackdropEffectScope` 而不是 `Density`
 *
 * `useLens` 的判定必须包含形状：库的 `lens()` 对不支持的形状会**直接抛异常闪退**
 * （见 [canUseLiquidLens]）。而形状必须与 `lens()` 自己读的那一个**是同一个对象**——
 * 曾经的做法是让调用点额外传一个 `shape` 实参，那是一份可以和
 * `drawBackdrop(shape = { … })` 悄悄不一致的副本。改成从 scope 上读之后，
 * 判据用的就是库将要用的那个形状，两者不可能再脱钩。
 *
 * `BackdropEffectScope : Density`，所以 `with(scope)` 里的 dp 换算照旧。
 */
fun resolvePhysicalLens(
    scope: BackdropEffectScope,
    material: GlassMaterialSpec,
    minCornerRadiusPx: Float,
    minDimensionPx: Float,
    interactionProgress: Float = 0f,
    motionIntensity: Float = 0f,
    enableBlur: Boolean = true,
    allowChromaticAberration: Boolean = true,
    /** 仅选中透镜使用：静止态也保持弱色散，交互时由运动强度继续增强。 */
    chromaticAberrationAtRest: Boolean = false,
    /**
     * true：折射随按压从 floor 抬到满额（选中透镜，静止也保留弱折射）。
     * false：静止即满额基础折射，按压只小幅增强（轨道）。
     */
    pressScalesRefraction: Boolean = false,
    /** pressScalesRefraction=true 时的静止折射下限，避免静止像灰片。 */
    refractionFloor: Float = 0.55f
): PhysicalLensParams = with(scope) {
    val progress = interactionProgress.coerceIn(0f, 1f)
    val motion = motionIntensity.coerceIn(0f, 1f)
    val blurPx = if (enableBlur && material.blurDp > 0f) {
        material.blurDp.dp.toPx()
    } else {
        0f
    }

    // 真 RuntimeShader/lens 仅 API 33+；API31/32 保留 blur + RGB 分离色散近似
    if (!isRuntimeLensEnabled()) {
        val fringeDp = if (allowChromaticAberration && material.chromaticAberration) {
            val restFringe = if (chromaticAberrationAtRest) 0.55f else 0f
            (restFringe + progress * 1.8f + motion * 1.2f).coerceIn(0f, 2.2f)
        } else {
            0f
        }
        return@with PhysicalLensParams(
            blurPx = blurPx,
            refractionHeightPx = 0f,
            refractionAmountPx = 0f,
            useLens = false,
            chromaticAberration = false,
            fringePx = fringeDp.dp.toPx()
        )
    }

    val (heightScale, amountScale) = lensScales(
        pressScalesRefraction, refractionFloor, progress, motion
    )

    val refractionHeight = (material.refractionHeightDp.dp.toPx() * heightScale)
        .coerceIn(0f, minCornerRadiusPx)
    val refractionAmount = (material.refractionAmountDp.dp.toPx() * amountScale)
        .coerceIn(0f, minDimensionPx * 0.40f)

    val useLens = canUseLiquidLens(
        shape = shape,
        refractionHeightPx = refractionHeight,
        refractionAmountPx = refractionAmount,
        minCornerRadiusPx = minCornerRadiusPx,
        minDimensionPx = minDimensionPx
    )
    val chromatic = useLens &&
        allowChromaticAberration &&
        material.chromaticAberration &&
        (chromaticAberrationAtRest || progress > 0.05f || motion > 0.05f)

    PhysicalLensParams(
        blurPx = blurPx,
        refractionHeightPx = refractionHeight,
        refractionAmountPx = refractionAmount,
        useLens = useLens,
        chromaticAberration = chromatic
    )
}

/**
 * 折射强度的缩放：返回 (斜坡宽度系数, 位移幅度系数)。
 *
 * **两条路读同一个函数** —— [resolvePhysicalLens]（API33+ 走平台 AGSL）和
 * [glassLensOpticsFrom]（API31/32 走离屏 ES 2.0）。目标是同一个控件在两种平台上
 * 的折射**观感一致**，所以随按压/速度的变化曲线必须逐帧相同。
 *
 * 曾经离屏那条路自己写了一份"两者同乘一个数"的版本，理由是想让
 * `位移/斜坡` 比值恒定。那条推理本身没错（比值确实决定边缘挤压的陡度），
 * 但代价是 31/32 与 33+ 的曲线不同 —— 同一个控件在两种机器上折射强度对不上，
 * 那就不是"在低版本上复现 AGSL"，而是做出了第二种效果。既然目标是复现，
 * 曲线就以 33+ 为准。
 */
private fun lensScales(
    pressScalesRefraction: Boolean,
    refractionFloor: Float,
    progress: Float,
    motion: Float
): Pair<Float, Float> {
    val floor = refractionFloor.coerceIn(0f, 1f)
    return if (pressScalesRefraction) {
        // 选中透镜：静止保留 floor 折射；按压/速度抬到 1
        val base = floor + (1f - floor) * progress
        (base * (1f + motion * 0.25f)) to (base * (1f + motion * 0.35f))
    } else {
        // 轨道：静止满额弱折射，按压小幅增强
        (1f + progress * 0.20f + motion * 0.10f) to
            (1f + progress * 0.30f + motion * 0.15f)
    }
}

fun motionIntensityFromVelocity(
    velocityX: Float,
    fullEffectVelocity: Float
): Float = (abs(velocityX) / fullEffectVelocity.coerceAtLeast(1f)).coerceIn(0f, 1f)
