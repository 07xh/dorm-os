package io.github.x07xh.xiaoxu.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM regression for the real JS -> Kotlin -> Room catalog hand-off. */
class LocalSecondClassCatalogContractTest {
    private fun payload(
        status: String,
        emptyFilters: List<String>,
        activities: JSONArray = JSONArray()
    ) = JSONObject()
        .put("schema", "xiaoxu/local-secondclass-scores-v1")
        .put("ok", true)
        .put("identityMatched", true)
        .put("catalogStatus", status)
        .put("catalogEmptyFilters", JSONArray(emptyFilters))
        .put("catalogActivities", activities)

    @Test
    fun mixedNonemptySnapshotDoesNotInventEmptyFilters() {
        val active = JSONArray().put(JSONObject()
            .put("filter", "报名中")
            .put("title", "合成活动测试")
            .put("ordinal", 0))
        val json = payload(
            "verified_nonempty",
            listOf("待验证", "即将开始报名"),
            active
        )
        val empty = parseLocalSecondClassCatalogEmpty(json, 12345L)
        val real = parseLocalSecondClassCatalogActivities(json, 12345L)
        assertEquals(listOf("待验证", "即将开始报名"), empty.map { it.title })
        assertEquals(listOf("合成活动测试"), real.map { it.title })
        assertTrue(real.none { row ->
            empty.any { zero -> row.details.startsWith("筛选：${zero.title}\n") }
        })
    }

    @Test
    fun allEmptySnapshotOnlyCreatesThreeKnownEmptyFilters() {
        val json = payload(
            "verified_empty",
            listOf("报名中", "待验证", "即将开始报名")
        )
        assertEquals(3, parseLocalSecondClassCatalogEmpty(json, 12345L).size)
        assertTrue(parseLocalSecondClassCatalogActivities(json, 12345L).isEmpty())
    }

    @Test
    fun unverifiedNonemptyDoesNotCreateFalseEmptyRecords() {
        val json = payload("nonempty_unverified", emptyList())
        assertTrue(parseLocalSecondClassCatalogEmpty(json, 12345L).isEmpty())
        assertTrue(parseLocalSecondClassCatalogActivities(json, 12345L).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownFilterIsRejected() {
        parseLocalSecondClassCatalogEmpty(
            payload("verified_nonempty", listOf("任意未知筛选")), 12345L
        )
    }
}
