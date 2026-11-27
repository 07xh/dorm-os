package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CampusUtilitiesPortalTest {
    @Test fun schoolOwnedWaterAndElectricRoutesAreStable() {
        assertEquals(
            "https://mobile-ykt.ncwu.edu.cn/?orgId=2#/pages_payment/waterFees/waterFees",
            CampusUtilitiesPortal.officialUrl(CampusUtilityKind.WATER)
        )
        assertEquals(
            "https://mobile-ykt.ncwu.edu.cn/?orgId=2#/pages_payment/electrictyFees/electrictyFees",
            CampusUtilitiesPortal.officialUrl(CampusUtilityKind.ELECTRIC)
        )
    }

    @Test fun noIdentityOrCredentialInSharedLinks() {
        for (kind in CampusUtilityKind.entries) {
            val url = CampusUtilitiesPortal.officialUrl(kind)
            assertFalse(url.contains("accessToken", ignoreCase = true))
            assertFalse(url.contains("refreshToken", ignoreCase = true))
            assertFalse(url.contains("room=", ignoreCase = true))
            assertFalse(url.contains("user=", ignoreCase = true))
        }
    }
}
