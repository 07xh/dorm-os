package io.github.x07xh.xiaoxu.data

import java.security.MessageDigest
import org.json.JSONObject

/**
 * Strict, sanitized, owner-checked catalog projection from the existing
 * same-origin official WebView reader. Only one complete verified page per
 * official filter is accepted; unknown schemas never create fake activities.
 *
 * No user identity, session data, external event IDs or raw JSON is persisted.
 */
fun parseLocalSecondClassCatalogActivities(
    payload: JSONObject,
    capturedAt: Long
): List<NativeSchoolRecord> {
    require(payload.optString("schema") == "xiaoxu/local-secondclass-scores-v1")
    require(payload.getBoolean("ok") && payload.getBoolean("identityMatched"))
    require(capturedAt > 0)
    val status = payload.getString("catalogStatus")
    require(status in setOf("verified_empty", "verified_nonempty", "nonempty_unverified", "unavailable"))
    val rows = payload.getJSONArray("catalogActivities")
    if (status != "verified_nonempty") {
        require(rows.length() == 0)
        return emptyList()
    }
    require(rows.length() in 1..120)
    val allowed = setOf("报名中", "待验证", "即将开始报名")
    val keys = mutableSetOf<String>()
    val records = (0 until rows.length()).map { i ->
        val raw = rows.getJSONObject(i)
        val filter = raw.getString("filter")
        val title = raw.getString("title")
        val ordinal = raw.getInt("ordinal")
        require(filter in allowed)
        require(title.length in 2..120 && title == title.trim() &&
            !Regex("[<>\\u0000-\\u001f]").containsMatchIn(title))
        require(ordinal in 0..49)
        val key = "$filter|$ordinal|$title"
        require(keys.add(key))
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(("second_class|catalog_activity|" + key).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        NativeSchoolRecord(
            provider = "second_class",
            kind = "catalog_activity",
            fingerprint = fingerprint,
            title = title,
            details = "筛选：$filter\n来源：学校活动广场（上次完整读取）",
            sourceHost = "dekt.ncwu.edu.cn",
            capturedAt = capturedAt
        )
    }
    require(records.isNotEmpty())
    return records
}
