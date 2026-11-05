package io.github.x07xh.xiaoxu.data

import java.math.BigDecimal
import java.security.MessageDigest
import org.json.JSONObject

/** Only the fixed official-page reader's sanitized projection reaches Room. */
fun parseLocalSecondClassScores(payload: JSONObject, capturedAt: Long): List<NativeSchoolRecord> {
    require(payload.optString("schema") == "xiaoxu/local-secondclass-scores-v1")
    require(payload.getBoolean("ok") && payload.getBoolean("identityMatched"))
    require(payload.getInt("httpStatus") == 200 && capturedAt > 0)
    val rows = payload.getJSONArray("rows")
    require(rows.length() in 1..50)
    val seen = mutableSetOf<Int>()
    val labels = listOf("思想政治和道德修养", "社会实践与志愿服务", "科学技术与创新创业",
        "文化艺术与身心健康", "学生团体与社会工作", "技能培训")
    fun number(raw: Any): String {
        require(raw is Number)
        val n = raw.toDouble()
        require(n.isFinite() && n >= 0 && n <= 100000)
        return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
    }
    val result = (0 until rows.length()).map { i ->
        val row = rows.getJSONObject(i)
        val rawIndex = row.get("termIndex")
        require(rawIndex is Number && rawIndex.toDouble() == rawIndex.toInt().toDouble())
        val index = rawIndex.toInt()
        require(index in 1..100 && seen.add(index))
        val score = number(row.get("qualityScore"))
        val hours = number(row.get("volunteerHours"))
        val categories = row.getJSONArray("categories")
        require(categories.length() == 6)
        val details = "素质分：$score · 志愿服务时长：$hours\n" +
            labels.mapIndexed { j, label -> "$label：${number(categories.get(j))}" }.joinToString(" · ")
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest("second_class|credits|$index".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        NativeSchoolRecord("second_class", "credits", fingerprint,
            if (index == 100) "学校积分合计" else "第${index}学期积分",
            details, "dekt.ncwu.edu.cn", capturedAt)
    }
    require(seen.any { it != 100 })
    return result.sortedBy { if (it.title == "学校积分合计") 0 else 1 }
}
