package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CampusUtilitiesWebViewContractTest {
    @Test fun onlyFixedSchoolHttpsOriginCanRunARead() {
        assertTrue(CampusUtilitiesWebViewTransport.isOfficialOrigin(
            "https://mobile-ykt.ncwu.edu.cn/?orgId=2#/pages_home/home"))
        assertTrue(CampusUtilitiesWebViewTransport.isOfficialOrigin(
            "https://mobile-ykt.ncwu.edu.cn:443/?orgId=2"))
        for (bad in listOf(
            "http://mobile-ykt.ncwu.edu.cn/",
            "https://mobile-ykt.ncwu.edu.cn.attacker.example/",
            "https://mobile-ykt.ncwu.edu.cn@evil.example/",
            "https://mobile-ykt.ncwu.edu.cn:8443/",
            "https://authserver.ncwu.edu.cn/authserver/login",
            "https://mobile-ykt.ncwu.edu.cn.evil.example/"
        )) {
            assertFalse(bad, CampusUtilitiesWebViewTransport.isOfficialOrigin(bad))
        }
    }

    @Test fun schoolSm4Base64EcbPkcs7FixtureDecodes() {
        // Fixture independently encrypted with OpenSSL SM4-ECB and the H5's
        // public response-format key. No school token or actual personal data.
        val encrypted = "fPcjylG/Z5DDGrvd97lRce3PYbkkAj9LWXdU988M2D8x8J/ou61tg45Y5oVvUZ+YPTr7DI3+ULdO8wTvI39yKg=="
        assertEquals(
            """{"resultData":{"totalUsed":"12.50","details":{"totalCount":3}}}""",
            CampusUtilitiesSm4.decryptBase64(encrypted)
        )
    }
}
