package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** School status evidence must be an exact source record, never a title guess. */
class ChaoxingSourceStatusTest {
    @Test
    fun acceptsOnlyOfficialWorkTupleAndSupportedStatus() {
        val id = "work:12345:6789:998877"
        for (status in listOf("todo", "submitted", "graded", "returned", "unknown")) {
            assertEquals(status, trustedChaoxingPlatformStatus("chaoxing", id, status))
        }
        assertNull(trustedChaoxingPlatformStatus("chaoxing", id, ""))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", id, "not_submitted"))
    }

    @Test
    fun rejectsUnlinkedLegacyAndCrossProviderRecords() {
        assertNull(trustedChaoxingPlatformStatus("wechat", "work:1:2:3", "submitted"))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", "legacy-117", "graded"))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", "work:1:2", "submitted"))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", "work:1:2:3:4", "submitted"))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", "work:1:2:3abc", "submitted"))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", null, "submitted"))
        assertNull(trustedChaoxingPlatformStatus("chaoxing", "work:1:2:3", null))
    }
}
