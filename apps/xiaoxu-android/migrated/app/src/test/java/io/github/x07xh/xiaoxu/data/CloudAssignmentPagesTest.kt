package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAssignmentPagesTest {
    private fun task(id: String) = CloudTask(id, "真实任务$id", "todo", "homework", "chaoxing", null)

    @Test fun paginatesUntilShortFinalPage() {
        val pages = CloudAssignmentPages(pageSize = 2, maxRecords = 5)
        pages.append(listOf(task("a"), task("b")), responseLimit = 2, responseOffset = 0)
        assertFalse(pages.complete)
        assertEquals(2, pages.nextOffset)
        pages.append(listOf(task("c")), responseLimit = 2, responseOffset = 2)
        assertTrue(pages.complete)
        assertEquals(listOf("a", "b", "c"), pages.verifiedRows().map { it.id })
    }

    @Test fun exactPageBoundaryNeedsAnEmptyFinalPage() {
        val pages = CloudAssignmentPages(pageSize = 2, maxRecords = 2)
        pages.append(listOf(task("a"), task("b")), responseLimit = 2, responseOffset = 0)
        assertFalse(pages.complete)
        assertFails { pages.verifiedRows() }
        pages.append(emptyList(), responseLimit = 2, responseOffset = 2)
        assertTrue(pages.complete)
        assertEquals(2, pages.verifiedRows().size)
    }

    @Test fun duplicateIdsAcrossPagesNeverCertifyFreshness() {
        val pages = CloudAssignmentPages(pageSize = 2, maxRecords = 5)
        pages.append(listOf(task("a"), task("b")), responseLimit = 2, responseOffset = 0)
        assertFails { pages.append(listOf(task("b")), responseLimit = 2, responseOffset = 2) }
        assertFalse(pages.complete)
        assertEquals(2, pages.nextOffset)
    }

    @Test fun invalidOffsetOrServerLimitIsRejected() {
        val wrongOffset = CloudAssignmentPages(pageSize = 2)
        assertFails { wrongOffset.append(listOf(task("a")), responseLimit = 2, responseOffset = 2) }
        val wrongLimit = CloudAssignmentPages(pageSize = 2)
        assertFails { wrongLimit.append(listOf(task("a")), responseLimit = 1, responseOffset = 0) }
    }

    @Test fun boundedReadCannotClaimCompleteWhenMoreThanMaxRecordsExist() {
        val pages = CloudAssignmentPages(pageSize = 2, maxRecords = 2)
        pages.append(listOf(task("a"), task("b")), responseLimit = 2, responseOffset = 0)
        assertFails { pages.append(listOf(task("c")), responseLimit = 2, responseOffset = 2) }
        assertFails { pages.verifiedRows() }
    }

    @Test fun malformedOrRepeatedRowsAreRejectedBeforeMutation() {
        val pages = CloudAssignmentPages(pageSize = 2)
        assertFails { pages.append(listOf(task(""), task("x")), 2, 0) }
        assertFails { pages.append(listOf(task("x"), task("x")), 2, 0) }
        assertEquals(0, pages.nextOffset)
        assertFalse(pages.complete)
    }

    @Test fun confirmedWechatWriteWinsOverOlderInFlightSnapshot() {
        val confirmed = CloudTask("w-1", "微信任务", "done", "homework",
            "wechat", null, revision = 4)
        val oldNetwork = confirmed.copy(status = "todo", revision = 3)
        val merged = preferConfirmedWechatRevisions(listOf(oldNetwork), listOf(confirmed))
        assertEquals(1, merged.size)
        assertEquals("done", merged.single().status)
        assertEquals(4, merged.single().revision)
    }

    @Test fun newerServerRevisionStillWinsAndNoRecordsAreFabricated() {
        val locallyConfirmed = CloudTask("w-1", "微信任务", "done", "homework",
            "wechat", null, revision = 4)
        val newerNetwork = locallyConfirmed.copy(status = "todo", revision = 5)
        val course = CloudTask("c-1", "课程任务", "todo", "homework",
            "chaoxing", null, revision = 2)
        val merged = preferConfirmedWechatRevisions(
            listOf(newerNetwork, course), listOf(locallyConfirmed, course.copy(status = "done",
                revision = 9), locallyConfirmed.copy(id = "not-fetched"))
        )
        assertEquals(listOf("w-1", "c-1"), merged.map { it.id })
        assertEquals("todo", merged.first().status)
        assertEquals(5, merged.first().revision)
        assertEquals("todo", merged.last().status)
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: IllegalArgumentException) { failed = true }
        catch (_: IllegalStateException) { failed = true }
        assertTrue("Expected invalid or incomplete pagination to be rejected", failed)
    }
}
