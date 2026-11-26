package com.tyust.course

import androidx.compose.ui.graphics.vector.ImageVector
import com.tyust.course.manager.StartupPage
import com.tyust.course.ui.system.AppSymbolSpec

// Original BottomNavItem declaration extracted from the pinned upstream MainActivity.kt.
sealed class BottomNavItem(
    val page: StartupPage,
    val symbol: AppSymbolSpec
) {
    val route: String get() = page.route
    val label: String get() = page.label
    val icon: ImageVector get() = symbol.outline
    object Courses : BottomNavItem(StartupPage.Courses, AppSymbolSpec.Courses)
    object Schedule : BottomNavItem(StartupPage.Schedule, AppSymbolSpec.Schedule)
    object Grab : BottomNavItem(StartupPage.Grab, AppSymbolSpec.Grab)
    object Grades : BottomNavItem(StartupPage.Grades, AppSymbolSpec.Grades)
    object Settings : BottomNavItem(StartupPage.Settings, AppSymbolSpec.Settings)

    companion object {
        val entries: List<BottomNavItem> get() = listOf(Courses, Schedule, Grab, Grades, Settings)
    }
}
