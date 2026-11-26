package com.tyust.course

import com.tyust.course.ui.system.AppSymbolSpec

/** Only the navigation data contract is adapted; the original CapsuleNavigationBar is unchanged. */
data class BottomNavItem(val route: String, val label: String, val symbol: AppSymbolSpec)

val xiaoxuNavigationItems = listOf(
    BottomNavItem("HOME", "今日", AppSymbolSpec.Courses),
    BottomNavItem("SCHEDULE", "课表", AppSymbolSpec.Schedule),
    BottomNavItem("TASKS", "任务", AppSymbolSpec.Grab),
    BottomNavItem("ACADEMIC", "校园", AppSymbolSpec.Grades),
    BottomNavItem("PROFILE", "我的", AppSymbolSpec.Settings)
)
