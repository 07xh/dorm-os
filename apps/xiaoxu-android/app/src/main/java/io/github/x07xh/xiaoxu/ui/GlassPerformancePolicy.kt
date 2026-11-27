package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/GlassPerformancePolicy.kt
// Original authors and contributors; package adapted only.

/** 设备能力分类，仅供诊断与后续选择等价实现；不会关闭任何玻璃特性。 */
enum class GlassPerformanceTier {
    Material,
    Balanced,
    Full
}

data class GlassDeviceProfile(
    val sdkInt: Int,
    val supportedAbis: List<String>,
    val isLowRamDevice: Boolean
)

object GlassPerformancePolicy {
    private const val MIN_BACKDROP_SDK = 31

    fun resolve(profile: GlassDeviceProfile): GlassPerformanceTier {
        if (profile.sdkInt < MIN_BACKDROP_SDK) return GlassPerformanceTier.Material

        val isX86Runtime = profile.supportedAbis.any { abi ->
            abi.equals("x86", ignoreCase = true) ||
                abi.equals("x86_64", ignoreCase = true)
        }
        return if (profile.isLowRamDevice || isX86Runtime) {
            GlassPerformanceTier.Balanced
        } else {
            GlassPerformanceTier.Full
        }
    }
}
