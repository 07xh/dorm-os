package io.github.x07xh.xiaoxu

/** The five permanent navigation destinations. Messages are a contextual full-screen route. */
enum class MainDestination { HOME, SCHEDULE, ACADEMIC, TASKS, PROFILE }
enum class AcademicDestination { OVERVIEW, GRADES, EXAMS, SECOND_CLASS, SERVICES }

/** Pure navigation transitions, independent of Compose or school credentials. */
data class AcademicNavigation(
    val destination: MainDestination = MainDestination.HOME,
    val academic: AcademicDestination = AcademicDestination.OVERVIEW,
    val messagesOpen: Boolean = false
) {
    fun selectMain(value: MainDestination) = copy(destination = value, messagesOpen = false)
    fun selectAcademic(value: AcademicDestination) = copy(
        destination = MainDestination.ACADEMIC, academic = value, messagesOpen = false
    )
    fun openSecondClass() = selectAcademic(AcademicDestination.SECOND_CLASS)
    fun openMessages() = copy(messagesOpen = true)
    /** Closing messages restores the exact originating main page and academic subsection. */
    fun closeMessages() = copy(messagesOpen = false)
}
