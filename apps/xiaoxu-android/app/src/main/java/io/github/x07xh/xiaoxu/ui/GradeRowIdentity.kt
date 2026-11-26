package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/GradeRowIdentity.kt.
// Original authors and contributors.

/** Keep row state with the course across refreshes, including repeated attempts. */
internal fun gradeRowKeys(grades: List<GradeItemUi>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return grades.map { grade ->
        val identity = listOf(grade.year, grade.term, grade.courseCode, grade.courseName,
            grade.jxbId, grade.teachingClass).joinToString("") { it.length.toString() + ":" + it }
        val occurrence = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = occurrence + 1
        "$identity#$occurrence"
    }
}
