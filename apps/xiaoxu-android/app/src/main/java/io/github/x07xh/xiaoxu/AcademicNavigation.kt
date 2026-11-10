package io.github.x07xh.xiaoxu

/** The five permanent navigation destinations. Messages are a contextual full-screen route. */
enum class MainDestination { HOME, SCHEDULE, ACADEMIC, TASKS, PROFILE }
enum class AcademicDestination { OVERVIEW, GRADES, EXAMS, SECOND_CLASS, SERVICES, ELECTIVES }

/** Academic route transition used by NativeShell. The shell owns message visibility. */
data class AcademicNavigation(
    val destination: MainDestination = MainDestination.HOME,
    val academic: AcademicDestination = AcademicDestination.OVERVIEW
) {
    fun selectAcademic(value: AcademicDestination) = copy(
        destination = MainDestination.ACADEMIC, academic = value
    )
    fun openSecondClass() = selectAcademic(AcademicDestination.SECOND_CLASS)
}
