package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.MainDestination

/** All navigation destinations live here. Features request a route; only the shell
 * decides which primary destination is selected and how back navigation works.
 */
internal enum class AppRoute(val primary: MainDestination, val title: String) {
    HOME(MainDestination.HOME, "总览"),
    SCHEDULE(MainDestination.SCHEDULE, "课表"),
    ACADEMIC(MainDestination.ACADEMIC, "教务"),
    TASKS(MainDestination.TASKS, "任务"),
    PROFILE(MainDestination.PROFILE, "我的"),
    SCHEDULE_SETTINGS(MainDestination.SCHEDULE, "课表设置"),
    GRADES(MainDestination.ACADEMIC, "成绩"),
    EXAMS(MainDestination.ACADEMIC, "考试"),
    SECOND_CLASS(MainDestination.ACADEMIC, "第二课堂"),
    STUDY_PROGRESS(MainDestination.ACADEMIC, "学习进度"),
    CONNECTIONS(MainDestination.PROFILE, "数据连接"),
    TASK_BACKUP(MainDestination.PROFILE, "任务备份"),
    LICENSES(MainDestination.PROFILE, "开源许可");

    val parent: AppRoute? get() = when (this) {
        SCHEDULE_SETTINGS -> SCHEDULE
        GRADES, EXAMS, SECOND_CLASS, STUDY_PROGRESS -> ACADEMIC
        CONNECTIONS, TASK_BACKUP, LICENSES -> PROFILE
        else -> null
    }

    companion object {
        fun root(destination: MainDestination): AppRoute = when (destination) {
            MainDestination.HOME -> HOME
            MainDestination.SCHEDULE -> SCHEDULE
            MainDestination.ACADEMIC -> ACADEMIC
            MainDestination.TASKS -> TASKS
            MainDestination.PROFILE -> PROFILE
        }
    }
}

/** A missing snapshot is different from an empty, successfully read snapshot. */
internal sealed interface PageSnapshot<out T> {
    data object Loading : PageSnapshot<Nothing>
    data class Ready<T>(val value: T, val stale: Boolean = false) : PageSnapshot<T>
    data class Unavailable(val message: String) : PageSnapshot<Nothing>
}

internal fun <T> pageSnapshot(value: T?, unavailable: String? = null): PageSnapshot<T> =
    when {
        unavailable != null && value == null -> PageSnapshot.Unavailable(unavailable)
        value != null -> PageSnapshot.Ready(value, stale = unavailable != null)
        else -> PageSnapshot.Loading
    }
