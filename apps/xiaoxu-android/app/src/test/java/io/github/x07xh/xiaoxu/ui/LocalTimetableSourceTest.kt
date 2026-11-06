package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.VerifiedLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local timetable source is independent from any cloud school session. */
class LocalTimetableSourceTest {
    private fun lesson(title: String) = VerifiedLesson(
        id = "L1", term = "2026-2027-1", title = title, teacher = "教师待核对",
        location = "教室待核对", weekday = 4, startSection = 1, endSection = 2,
        weeks = listOf(9, 10)
    )

    @Test fun importedLessonsArePassedThroughWithoutInventingOrFiltering() {
        val lessons = listOf(lesson("高等数学"), lesson("工程制图"))
        val view = localImportedScheduleView(lessons)
        assertSame("必须使用本机已导入的课程", lessons, view.lessons)
        assertEquals(listOf("高等数学", "工程制图"), view.lessons.map { it.title })
        assertEquals(listOf(9, 10), view.lessons.first().weeks)
    }

    @Test fun localImportNeverClaimsOfficialCloudVerification() {
        val view = localImportedScheduleView(listOf(lesson("Python")))
        assertEquals("华水青果 · 本机导入（待本人核对）", view.sourceLabel)
        assertTrue(view.sourceLabel.contains("本机导入"))
        assertTrue(view.sourceLabel.contains("待本人核对"))
        assertFalse(view.sourceLabel.contains("可信只读"))
    }

    @Test fun missingImportDoesNotSynthesizeCourses() {
        val view = localImportedScheduleView(emptyList())
        assertTrue(view.lessons.isEmpty())
        assertTrue(view.sourceLabel.contains("待本人核对"))
    }

    @Test fun replacingImportDoesNotMergeStaleLocalLessons() {
        val old = localImportedScheduleView(listOf(lesson("旧课表")))
        val updated = localImportedScheduleView(listOf(lesson("新课表")))
        assertEquals(listOf("旧课表"), old.lessons.map { it.title })
        assertEquals(listOf("新课表"), updated.lessons.map { it.title })
    }
}
