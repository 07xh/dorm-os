package com.tyust.course.academic.plugin

import com.tyust.course.model.SchoolConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BundledServiceProvidersTest {
    private val assets = File("src/main/assets")

    @Test fun ncwuMessageEntryUsesTheExistingServiceExtensionSlots() {
        val packages = BundledServiceProviders.load { File(assets, it).readBytes() }
        val pkg = packages[BundledServiceProviders.CLOUD_MESSAGES_ID]
            ?: fail("built-in NCWU message service missing")
        assertTrue(pkg.bundled)
        assertFalse(pkg.official)
        assertTrue(pkg.manifest.isService)
        assertEquals("messages", pkg.manifest.service!!.getJSONArray("pages")
            .getJSONObject(0).getString("id"))

        val school = SchoolConfig("ncwu", "华北水利水电大学", "jwmis.ncwu.edu.cn", "https")
        assertTrue(ServicePluginContract.matches(pkg.manifest, school))
        for (placement in listOf("home", "schedule", "grades")) {
            val entries = ServiceEntryPreferences.declared(listOf(pkg), school, placement)
            assertEquals(1, entries.size)
            assertEquals("消息中心", entries.single().declaration.getString("title"))
            assertEquals(BundledServiceProviders.CLOUD_MESSAGES_PAGE,
                entries.single().declaration.getString("pageId"))
        }
    }

    @Test fun bundledServiceIsNotMarkedAsAnOfficialCatalogSignature() {
        val pkg = BundledServiceProviders.load { File(assets, it).readBytes() }
            .getValue(BundledServiceProviders.CLOUD_MESSAGES_ID)
        assertFalse(pkg.official)
        assertTrue(pkg.bundled)
        assertEquals(emptySet<String>(), pkg.manifest.permissions)
        assertTrue(pkg.manifest.network.isEmpty())
    }
}
