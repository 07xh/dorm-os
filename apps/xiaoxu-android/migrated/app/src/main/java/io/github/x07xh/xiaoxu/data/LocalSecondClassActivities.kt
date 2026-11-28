package io.github.x07xh.xiaoxu.data

import java.math.BigDecimal
import java.security.MessageDigest
import org.json.JSONObject

/** Full, identity-checked personal activity pages; no names, student IDs or sessions. */
fun parseLocalSecondClassActivities(payload: JSONObject, capturedAt: Long): List<NativeSchoolRecord> {
    require(payload.getBoolean("activitiesComplete") && payload.getBoolean("identityMatched"))
    val items = payload.getJSONArray("activities")
    require(items.length() <= 250 && capturedAt > 0)
    val ids = mutableSetOf<String>()
    fun fingerprint(id: String) = MessageDigest.getInstance("SHA-256")
        .digest(("second_class|activity|" + id).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    val records = (0 until items.length()).map { i ->
        val row = items.getJSONObject(i)
        val id = row.getString("id")
        require(id.matches(Regex("^[A-Za-z0-9:_-]{1,120}$")) && ids.add(id))
        val title = row.getString("title").trim()
        require(title.isNotBlank() && title.length <= 120)
        val time = if (row.isNull("startTime")) "学校未提供" else row.getString("startTime").also {
            require(it.matches(Regex("^20\\d{2}-\\d{2}-\\d{2}(?: \\d{2}:\\d{2}:\\d{2})?$")))
        }
        val signup = when (row.getString("signupStatus")) {
            "registered" -> "已报名"; "unknown" -> "学校未明确"; else -> error("invalid_status")
        }
        val settlement = when (row.getString("settlementStatus")) {
            "settled" -> "已结算"; "unsettled" -> "未结算"; "awaiting_settlement" -> "待结算"
            "unverified" -> "尚未验证"; "unknown" -> "学校未明确"; else -> error("invalid_status")
        }
        require(row.getString("reviewStatus") == "unknown")
        val verify = if (row.isNull("verificationCode")) "学校未提供" else row.getString("verificationCode").also {
            require(it.matches(Regex("^[0-9]$")))
        }
        val points = if (row.has("points")) {
            val raw = row.get("points")
            require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() in 0.0..100000.0)
            require(row.getString("settlementStatus") == "settled")
            "\n已结算素质分：" + BigDecimal.valueOf(raw.toDouble()).stripTrailingZeros().toPlainString()
        } else ""
        NativeSchoolRecord("second_class", "activity", fingerprint(id), title,
            "开始时间：$time\n报名：$signup · 结算：$settlement\n学校审核／验证码：$verify（不推断审批通过）$points",
            "dekt.ncwu.edu.cn", capturedAt)
    }
    return listOf(NativeSchoolRecord("second_class", "activity_summary", fingerprint("summary"),
        "本人报名活动", "已完整读取 ${records.size} 条；活动分数不与学校积分合计相加。",
        "dekt.ncwu.edu.cn", capturedAt)) + records
}
