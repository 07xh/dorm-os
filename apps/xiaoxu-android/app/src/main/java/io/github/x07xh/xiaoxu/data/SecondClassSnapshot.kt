package io.github.x07xh.xiaoxu.data

import org.json.JSONObject

/**
 * Separate provenance of records, school-reported aggregate and user browser exports.
 * No local school session, HTML or credentials are persisted by this model.
 */
data class SecondClassActivity(
    val id: String, val title: String, val date: String?,
    val activityStatus: String?, val reviewStatus: String?,
    val hours: Double?, val points: Double?
)
data class SecondClassOfficialScore(
    val qualityScore: Double?, val volunteerHours: Double?
)
data class SecondClassPersonalScore(
    val qualityScore: Double?, val volunteerHours: Double?
)
data class SecondClassSnapshot(
    val status: String,
    val provenance: String?,
    val lastSuccessAt: String?,
    val activities: List<SecondClassActivity>,
    val officialScore: SecondClassOfficialScore?,
    val personalScore: SecondClassPersonalScore?
)

private fun JSONObject.optionalText(name: String, max: Int = 120): String? =
    if (isNull(name) || !has(name)) null else optString(name)
        .takeIf { it.isNotBlank() && it != "null" && it.length <= max }

private fun JSONObject.optionalNumber(name: String, limit: Double): Double? {
    if (!has(name) || isNull(name)) return null
    val raw = opt(name)
    if (raw !is Number) throw IllegalArgumentException("invalid_number")
    return raw.toDouble().takeIf { it.isFinite() && it >= 0.0 && it <= limit }
        ?: throw IllegalArgumentException("invalid_range")
}

/** Fail closed on unknown status, provenance or an inconsistent aggregate. */
fun parseSecondClassSnapshot(root: JSONObject): SecondClassSnapshot {
    require(root.optString("source") == "second_class")
    require(root.optString("kind") == "second-class")
    val status = root.optString("status")
    require(status in setOf("ready", "stale", "not_connected"))
    if (status == "not_connected") {
        require(root.optJSONArray("records")?.length() == 0)
        return SecondClassSnapshot(status, null, null, emptyList(), null, null)
    }
    val provenance = root.getString("provenance")
    require(provenance == "trusted_readonly" || provenance == "user_import")
    val checked = root.getString("lastSuccessAt")
    require(checked.length in 20..40 && java.time.Instant.parse(checked).toEpochMilli() > 0)
    val array = root.getJSONArray("records")
    require(array.length() <= 250)
    val items = buildList {
        val known = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val row = array.getJSONObject(i)
            val id = row.getString("externalId")
            val title = row.getString("title").trim()
            require(id.matches(Regex("^[A-Za-z0-9:_-]{1,120}$")) && known.add(id))
            require(title.isNotBlank() && title.length <= 120)
            val activityStatus = row.optionalText("activityStatus", 24)
            val reviewStatus = row.optionalText("reviewStatus", 24)
            require(activityStatus == null ||
                activityStatus in setOf("registered", "completed", "cancelled", "unknown"))
            require(reviewStatus == null ||
                reviewStatus in setOf("pending", "approved", "rejected", "attended", "unknown"))
            add(SecondClassActivity(
                id = id, title = title, date = row.optionalText("date", 10),
                activityStatus = activityStatus, reviewStatus = reviewStatus,
                hours = row.optionalNumber("hours", 10000.0),
                points = row.optionalNumber("points", 100000.0)
            ))
        }
    }
    val count = root.optJSONObject("summary")?.optInt("recordCount", -1) ?: -1
    require(count == items.size)
    val official = if (provenance == "trusted_readonly" && !root.isNull("officialScore")) {
        val score = root.getJSONObject("officialScore")
        require(score.optString("school") == "ncwu")
        val reported = score.getBoolean("isSchoolReportedAggregate")
        val aggregate = score.optJSONObject("aggregate")
        if (reported) {
            require(aggregate != null)
            SecondClassOfficialScore(
                aggregate.optionalNumber("qualityScore", 100000.0),
                aggregate.optionalNumber("volunteerHours", 100000.0)
            )
        } else {
            require(aggregate == null)
            null
        }
    } else null
    val imported = if (provenance == "user_import" && !root.isNull("personalScore")) {
        val score = root.getJSONObject("personalScore")
        require(score.optString("provenance") == "user_browser_export")
        SecondClassPersonalScore(
            score.optionalNumber("qualityScore", 100000.0),
            score.optionalNumber("volunteerHours", 100000.0)
        )
    } else null
    return SecondClassSnapshot(status, provenance, checked, items, official, imported)
}
