package io.github.x07xh.xiaoxu.ui

/** Select the cheapest material that meets the device's capability.
 * Pure decision function; independent from rendering and school data.
 * Refraction is intentionally restricted to API 33+ on the current library.
 */
internal enum class NavigationMaterialTier { Opaque, Translucent, Refractive }

internal fun navigationMaterialTier(
    api: Int, lowMemory: Boolean, abis: List<String>
): NavigationMaterialTier {
    return when (GlassPerformancePolicy.resolve(GlassDeviceProfile(api, abis, lowMemory))) {
        GlassPerformanceTier.Material -> NavigationMaterialTier.Opaque
        GlassPerformanceTier.Balanced -> NavigationMaterialTier.Translucent
        GlassPerformanceTier.Full -> if (api < 33) NavigationMaterialTier.Translucent
            else NavigationMaterialTier.Refractive
    }
}
