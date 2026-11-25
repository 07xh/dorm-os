package io.github.x07xh.xiaoxu.ui

/** Select the cheapest material that meets the device's capability.
 * Pure decision function; independent from rendering and school data.
 * Refraction is intentionally restricted to API 33+ on the current library.
 */
internal enum class NavigationMaterialTier { Opaque, Translucent, Refractive }

internal fun navigationMaterialTier(
    api: Int, lowMemory: Boolean, abis: List<String>
): NavigationMaterialTier {
    if (api < 31) return NavigationMaterialTier.Opaque
    if (api < 33 || lowMemory || abis.any { it.lowercase().startsWith("x86") })
        return NavigationMaterialTier.Translucent
    return NavigationMaterialTier.Refractive
}
