package io.github.x07xh.xiaoxu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationMaterialPolicyTest {
    @Test fun legacyDeviceUsesOpaque() {
        assertEquals(NavigationMaterialTier.Opaque, navigationMaterialTier(30, false, listOf("arm64-v8a")))
    }
    @Test fun midRangeAndLowMemoryAvoidCapture() {
        assertEquals(NavigationMaterialTier.Translucent, navigationMaterialTier(32, false, listOf("arm64-v8a")))
        assertEquals(NavigationMaterialTier.Translucent, navigationMaterialTier(35, true, listOf("arm64-v8a")))
        assertEquals(NavigationMaterialTier.Translucent, navigationMaterialTier(35, false, listOf("x86_64")))
    }
    @Test fun modernArmUsesBackdrop() {
        assertEquals(NavigationMaterialTier.Refractive, navigationMaterialTier(35, false, listOf("arm64-v8a")))
    }
}
