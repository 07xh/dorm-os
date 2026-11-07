package io.github.x07xh.xiaoxu.data

import java.security.MessageDigest
import org.json.JSONObject

/**
 * School activity directory: currently only the three exact filters with
 * HAR-observed zero-record envelopes have a validated data contract.
 *
 * Run only after the existing score reader has verified the visible student's
 * identity against a semester score row in the same official WebView session.
 * No student code, raw payload, cookie or activity ID is persisted here.
 */
fun parseLocalSecondClassCatalogEmpty(
    payload: JSONObject,
    capturedAt: Long
): List<NativeSchoolRecord> {
    require(payload.optString("schema") == "xiaoxu/local-secondclass-scores-v1")
    require(payload.getBoolean("ok") && payload.getBoolean("identityMatched"))
    require(capturedAt > 0)
    val status = payload.getString("catalogStatus")
    require(status in setOf("verified_empty", "verified_nonempty", "nonempty_unverified", "unavailable"))
    val filters = payload.getJSONArray("catalogEmptyFilters")
    if (status !in setOf("verified_empty", "verified_nonempty")) {
        require(filters.length() == 0)
        return emptyList()
    }
    val required = listOf("报名中", "待验证", "即将开始报名")
    require(filters.length() <= required.size)
    val actual = (0 until filters.length()).map { filters.getString(it) }
    require(actual.distinct().size == actual.size && actual.all { it in required })
    if (status == "verified_empty") require(actual == required)
    // Only the filters actually confirmed empty may be stored. In a mixed
    // nonempty snapshot, creating empty rows for all filters conflicts with
    // real activity cards and rejects the entire Room transaction.
    return actual.map { title ->
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("second_class|catalog_empty|" + title).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        NativeSchoolRecord(
            provider = "second_class",
            kind = "catalog_empty",
            fingerprint = digest,
            title = title,
            details = "学校本次查询返回 0 条（仅限此筛选）",
            sourceHost = "dekt.ncwu.edu.cn",
            capturedAt = capturedAt
        )
    }
}
