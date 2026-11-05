package io.github.x07xh.xiaoxu
import io.github.x07xh.xiaoxu.data.NativeSchoolRecord

data class SecondClassSnapshot(
    val scores: List<NativeSchoolRecord>,
    val activities: List<NativeSchoolRecord>,
    val activitySummary: NativeSchoolRecord?
) { val activitiesComplete: Boolean get() = activitySummary != null }

fun secondClassSnapshot(records: List<NativeSchoolRecord>): SecondClassSnapshot {
    val local = records.filter { it.provider == "second_class" }
    val scores = local.filter { it.kind == "credits" }.sortedBy { if (it.title == "学校积分合计") 0 else 1 }
    val candidates = local.filter { it.kind in setOf("activity", "activity_summary") }
    val batch = candidates.singleOrNull { it.kind == "activity_summary" }
        ?.takeIf { summary -> candidates.all { it.capturedAt == summary.capturedAt } }
    return SecondClassSnapshot(scores, if (batch != null) candidates.filter { it.kind == "activity" } else emptyList(), batch)
}
