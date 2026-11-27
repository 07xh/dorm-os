package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.NativeSchoolRecord
import org.junit.Assert.*
import org.junit.Test

class StudyCoursePreviewTest {
    private val binding = NativeSchoolRecord("chaoxing", "account_binding", "current", "",
        "a".repeat(64), "i.chaoxing.com", 100L)
    private val course = NativeSchoolRecord("chaoxing", "course", "b".repeat(64), "真实课程",
        "课程列表", "mooc2-ans.chaoxing.com", 100L)

    @Test fun onlyCurrentBoundCourseCatalogIsVisible() {
        assertNull(latestStudyCoursePreviews(listOf(course)))
        assertEquals(listOf(StudyCoursePreview(course.fingerprint, course.title, 100L)),
            latestStudyCoursePreviews(listOf(binding, course,
                course.copy(fingerprint = "c".repeat(64), capturedAt = 90L),
                course.copy(fingerprint = "d".repeat(64), details = "完整作业列表身份：scope:1:2"),
                course.copy(fingerprint = "e".repeat(64), kind = "homework"),
                course.copy(fingerprint = "f".repeat(64), sourceHost = "other.example"))))
    }

    @Test fun invalidOrAmbiguousOwnerBindingDoesNotExposeRecords() {
        assertNull(latestStudyCoursePreviews(listOf(binding.copy(details = "invalid"), course)))
        assertNull(latestStudyCoursePreviews(listOf(binding, binding, course)))
    }

    @Test fun missingCatalogDoesNotInventCourseProgress() {
        assertEquals(emptyList<StudyCoursePreview>(), latestStudyCoursePreviews(listOf(binding)))
    }
}
