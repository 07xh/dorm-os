package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.MainDestination

/** Only the Xiaoxu route-to-upstream-symbol adapter remains. */
internal val MainDestination.upstreamSymbol: AppSymbolSpec get() = when (this) {
    MainDestination.HOME -> AppSymbolSpec.Courses
    MainDestination.SCHEDULE -> AppSymbolSpec.Schedule
    MainDestination.ACADEMIC -> AppSymbolSpec.Grades
    MainDestination.TASKS -> AppSymbolSpec.Grab
    MainDestination.PROFILE -> AppSymbolSpec.Settings
}
