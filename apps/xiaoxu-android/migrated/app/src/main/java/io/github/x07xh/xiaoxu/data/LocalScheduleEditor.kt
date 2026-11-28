package io.github.x07xh.xiaoxu.data

/** Pure local meeting operations. Never change another week when editing only one week. */
object LocalScheduleEditor {
    /**
     * originalId = null means add. weekOnly = null changes the entire meeting.
     * Every write is validated by LocalScheduleStore before it is persisted.
     */
    fun upsert(
        lessons: List<VerifiedLesson>,
        originalId: String?,
        replacement: VerifiedLesson,
        weekOnly: Int? = null
    ): List<VerifiedLesson> {
        val original = originalId?.let { id ->
            lessons.singleOrNull { it.id == id }
                ?: throw IllegalArgumentException("课程已变化，请重新打开后编辑")
        }
        require(replacement.title.isNotBlank()) { "请填写课程名称" }
        require(replacement.weekday in 1..7 &&
            replacement.startSection in 1..24 &&
            replacement.endSection in replacement.startSection..24) { "星期或节次不正确" }
        require(replacement.weeks.isNotEmpty() &&
            replacement.weeks.all { it in 1..35 }) { "上课周次不正确" }
        require(weekOnly == null || (weekOnly in 1..35 &&
            (original == null || weekOnly in original.weeks))) { "该周没有这门课程" }

        val newMeeting = replacement.copy(
            weeks = (if (weekOnly == null) replacement.weeks else listOf(weekOnly)).distinct().sorted()
        )
        val kept = lessons.filterNot { original != null && it.id == original.id }.toMutableList()
        if (original != null && weekOnly != null) {
            val remainingWeeks = original.weeks.filterNot { it == weekOnly }
            if (remainingWeeks.isNotEmpty()) kept += original.copy(weeks = remainingWeeks)
        }
        val clash = kept.firstOrNull { other ->
            other.weekday == newMeeting.weekday &&
                other.startSection <= newMeeting.endSection &&
                newMeeting.startSection <= other.endSection &&
                other.weeks.any { it in newMeeting.weeks }
        }
        require(clash == null) {
            "与“${clash!!.title}”在相同周次和节次冲突，请先调整"
        }
        kept += newMeeting
        return kept
    }

    fun delete(
        lessons: List<VerifiedLesson>,
        originalId: String,
        weekOnly: Int? = null
    ): List<VerifiedLesson> {
        val original = lessons.singleOrNull { it.id == originalId }
            ?: throw IllegalArgumentException("课程已变化，请重新打开后操作")
        if (weekOnly != null) require(weekOnly in original.weeks) { "该周没有这门课程" }
        val kept = lessons.filterNot { it.id == originalId }.toMutableList()
        if (weekOnly != null) {
            val remaining = original.weeks.filterNot { it == weekOnly }
            if (remaining.isNotEmpty()) kept += original.copy(weeks = remaining)
        }
        return kept
    }
}
