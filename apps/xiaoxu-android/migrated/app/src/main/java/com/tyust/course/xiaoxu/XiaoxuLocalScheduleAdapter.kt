package com.tyust.course.xiaoxu

import androidx.compose.ui.graphics.Color
import com.tyust.course.manager.ScheduleSettingsManager
import com.tyust.course.schedule.ScheduleCourseRecord
import com.tyust.course.schedule.ScheduleIdentity
import com.tyust.course.schedule.ScheduleMaxWeeks
import com.tyust.course.schedule.ScheduleWeeks
import com.tyust.course.ui.screen.ScheduleCourseUi
import io.github.x07xh.xiaoxu.data.VerifiedLesson

/**
 * The only bridge between 校序's existing local timetable store and the
 * upstream schedule UI. It converts records; it does not recreate page layout,
 * navigation, rendering or interaction.
 */
object XiaoxuLocalScheduleAdapter {
    const val ACCOUNT_KEY = "default"
    const val SCHOOL_ID = "xiaoxu-local"

    fun termId(firstMonday: String?): String =
        firstMonday?.takeIf { it.isNotBlank() }?.let { "local:$it" } ?: "local:unconfigured"

    fun toUi(lessons: List<VerifiedLesson>, palette: List<Color>): List<ScheduleCourseUi> {
        require(palette.isNotEmpty()) { "Schedule palette cannot be empty" }
        return lessons.map { lesson ->
            ScheduleCourseUi(
                name = lesson.title,
                teacher = lesson.teacher,
                location = lesson.location,
                day = lesson.weekday,
                startPeriod = lesson.startSection,
                endPeriod = lesson.endSection,
                weeks = lesson.weeks.distinct().sorted().joinToString(",") { "${it}周" },
                color = palette[ScheduleIdentity.colorIndex(lesson.id, palette.size)],
                isCustom = false,
                customId = lesson.id,
                sourceId = lesson.id,
                id = lesson.id
            )
        }
    }

    fun toEditorCourse(lesson: VerifiedLesson) = ScheduleSettingsManager.CustomCourse(
        id = lesson.id,
        name = lesson.title,
        location = lesson.location,
        teacher = lesson.teacher,
        day = lesson.weekday,
        startPeriod = lesson.startSection,
        endPeriod = lesson.endSection,
        weeks = lesson.weeks.distinct().sorted().joinToString(",") { "${it}周" }
    )

    fun fromEditor(saved: ScheduleSettingsManager.CustomCourse, existing: VerifiedLesson?, term: String): VerifiedLesson {
        val parsed = ScheduleWeeks.parse(saved.weeks, ScheduleMaxWeeks)
        require(parsed.valid) { "周次格式不正确，请检查课程周次" }
        return VerifiedLesson(
            id = existing?.id ?: saved.id,
            term = term,
            title = saved.name.trim(),
            teacher = saved.teacher.trim(),
            location = saved.location.trim(),
            weekday = saved.day,
            startSection = saved.startPeriod,
            endSection = saved.endPeriod,
            weeks = parsed.weeks.toList().sorted()
        )
    }

    fun fromRecord(record: ScheduleCourseRecord, term: String): VerifiedLesson {
        val parsed = ScheduleWeeks.parse(record.weeks, ScheduleMaxWeeks)
        require(parsed.valid) { "课程周次无法恢复" }
        return VerifiedLesson(
            id = record.id,
            term = term,
            title = record.name,
            teacher = record.teacher,
            location = record.location,
            weekday = record.day,
            startSection = record.startPeriod,
            endSection = record.endPeriod,
            weeks = parsed.weeks.toList().sorted()
        )
    }
}
