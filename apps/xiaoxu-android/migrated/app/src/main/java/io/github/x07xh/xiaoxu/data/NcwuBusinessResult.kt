package io.github.x07xh.xiaoxu.data

import org.json.JSONObject

data class NcwuExamRound(val title: String, val message: String)
data class NcwuBusinessResult(
    val message: String = "尚未查询学校接口",
    val loading: Boolean = false,
    val authorizationRequired: Boolean = false,
    val checkedAt: String? = null,
    val rounds: List<NcwuExamRound> = emptyList()
)

private val labels = mapOf(
    "evaluation_required" to "学校要求完成评教后才能查看成绩",
    "schema_review_required" to "接口已响应，正式明细尚未验证",
    "syntactically_parsed" to "已识别明细结构，待正式数据核验",
    "reported_empty" to "所查考试轮次暂无场次返回（待核验）",
    "no_rounds_reported" to "本学期未返回考试轮次（待核验）",
    "unrecognized" to "学校响应格式暂未识别"
)
private val roundStatuses = setOf("reported_empty", "schema_review_required", "unrecognized", "syntactically_parsed")

/** Accept only the status-only API contract. Never promote candidates to official grades/exams. */
internal fun parseNcwuBusinessResult(kind: String, json: JSONObject): NcwuBusinessResult {
    require(kind in setOf("grades", "exams") && json.optString("kind") == kind &&
        json.opt("verified") == false && json.opt("complete") == false)
    val status = json.getString("status")
    require(status in if (kind == "grades") setOf("evaluation_required", "schema_review_required", "syntactically_parsed")
        else roundStatuses + "no_rounds_reported")
    val checkedAt = json.getString("checkedAt")
    java.time.Instant.parse(checkedAt)
    val rounds = if (kind == "exams") {
        val array = json.getJSONArray("rounds")
        require(array.length() in 0..12 && array.length() == json.getInt("roundCount"))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val title = row.getString("title")
            val value = row.getString("status")
            require(title.isNotBlank() && title.length <= 100 && value in roundStatuses)
            NcwuExamRound(title, labels.getValue(value))
        }
    } else emptyList()
    return NcwuBusinessResult(message = labels.getValue(status), checkedAt = checkedAt, rounds = rounds)
}
