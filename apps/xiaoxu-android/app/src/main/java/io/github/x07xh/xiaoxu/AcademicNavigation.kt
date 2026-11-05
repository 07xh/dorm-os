package io.github.x07xh.xiaoxu

enum class MainDestination { HOME, SCHEDULE, ACADEMIC, TASKS, PROFILE }
enum class AcademicDestination { NOTICES, GRADES, EXAMS, SECOND_CLASS, SERVICES }

data class AcademicNavigation(
    val destination: MainDestination = MainDestination.HOME,
    val academic: AcademicDestination = AcademicDestination.NOTICES,
    val messagesOpen: Boolean = false
) {
    fun selectMain(value: MainDestination) = copy(destination = value, messagesOpen = false)
    fun openSecondClass() = copy(destination = MainDestination.ACADEMIC,
        academic = AcademicDestination.SECOND_CLASS, messagesOpen = false)
    fun openMessages() = copy(messagesOpen = true)
    fun closeMessages() = copy(destination = MainDestination.HOME, messagesOpen = false)
}
