package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLinkPolicyTest {
    @Test fun keepsThreeOrderedHttpsLinksWithoutDuplicates() {
        val links = listOf(
            "https://school.example/report",
            "https://school.example/参考表",
            "https://school.example/submit"
        )
        assertEquals(links, safeCloudLinks(links))
        assertEquals(links.take(2), safeCloudLinks(
            listOf(links[0], links[0], links[1])
        ))
    }

    @Test fun ignoresUnsafeOrMalformedLinksWithoutBreakingOtherTasks() {
        assertEquals(listOf("https://school.example/submit"),
            safeCloudLinks(listOf(
                "http://school.example/report",
                "javascript:alert(1)",
                "https://user:password@school.example/private",
                "https://school.example:abc/report",
                "https://school.example:99999/report",
                "https://school.example/submit"
            ).takeLast(3)))
        assertTrue(safeCloudLinks(listOf("file:///sdcard/private", "https://")).isEmpty())
    }

    @Test fun appliesLengthControlsAndSupportsLegacyEmptyLists() {
        assertEquals(emptyList<String>(), safeCloudLinks(emptyList()))
        assertTrue(safeCloudLinks(listOf("https://school.example/" + "a".repeat(520))).isEmpty())
        assertTrue(safeCloudLinks(listOf("https://school.example/\nunsafe")).isEmpty())
    }

    @Test fun cloudTaskRetainsLinksForTaskDetails() {
        val links = safeCloudLinks(listOf("https://school.example/submit"))
        val task = CloudTask("a", "实验报告", "todo", "manual", "wechat", null, links = links)
        assertEquals(links, task.links)
    }
}
