package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import io.github.x07xh.xiaoxu.ui.northglass.NorthAppSymbolSpec
import io.github.x07xh.xiaoxu.MainDestination

/**
 * One presentation adapter for the five permanent destinations.
 *
 * The only navigation state owner is NativeShell. This file maps that state to
 * the single phone FloatingBottomBar renderer or the adaptive tablet rail.
 * MessageCenter is a contextual route, never an extra permanent tab.
 *
 * When the verified 北化课表 glass renderer is available, replace the chrome in
 * FloatingBottomBar, not this routing adapter or any business page.
 */
internal val PrimaryTabs = listOf(
    MainDestination.HOME,
    MainDestination.SCHEDULE,
    MainDestination.ACADEMIC,
    MainDestination.TASKS,
    MainDestination.PROFILE
)

internal val MainDestination.primaryTabLabel: String
    get() = when (this) {
        MainDestination.HOME -> "今日"
        MainDestination.SCHEDULE -> "课表"
        MainDestination.ACADEMIC -> "教务"
        MainDestination.TASKS -> "任务"
        MainDestination.PROFILE -> "我的"
    }

private val MainDestination.primaryNorthSymbol: NorthAppSymbolSpec
    get() = when (this) {
        MainDestination.HOME -> NorthAppSymbolSpec.Courses
        MainDestination.SCHEDULE -> NorthAppSymbolSpec.Schedule
        MainDestination.ACADEMIC -> NorthAppSymbolSpec.Grades
        MainDestination.TASKS -> NorthAppSymbolSpec.Grab
        MainDestination.PROFILE -> NorthAppSymbolSpec.Settings
    }

private val MainDestination.primaryTabIcon: ImageVector
    get() = when (this) {
        MainDestination.HOME -> Icons.Outlined.Home
        MainDestination.SCHEDULE -> Icons.Outlined.DateRange
        MainDestination.ACADEMIC -> Icons.Outlined.School
        MainDestination.TASKS -> Icons.Outlined.CheckCircle
        MainDestination.PROFILE -> Icons.Outlined.PersonOutline
    }

@Composable
internal fun PrimaryBottomNavigation(
    selected: MainDestination,
    backdrop: Backdrop?,
    onSelect: (MainDestination) -> Unit
) {
    // Stable items: no allocation of a new list on every animation frame.
    val items = remember {
        PrimaryTabs.map { FloatingBarItem(it.primaryTabLabel, it.primaryTabIcon, northSymbol = it.primaryNorthSymbol) }
    }
    FloatingBottomBar(
        items = items,
        selectedIndex = PrimaryTabs.indexOf(selected).coerceAtLeast(0),
        backdrop = backdrop,
        // Original v1.0.83 selected tint; no custom palette reinterpretation.
        selectedContentColor = if (backdrop != null) Color(0xFF0057D9) else XiaoxuPalette.BrandDeep,
        onSelect = { onSelect(PrimaryTabs[it]) }
    )
}

/**
 * Reserves precisely the same space as CourseSelector's 8 + 72 + 8dp dock
 * and the current system navigation inset. This contains no navigation UI:
 * the actual glass bar is overlaid once, outside the backdrop capture layer.
 * Scaffold must own content padding; child screens must not add a second inset.
 */
@Composable
internal fun PrimaryGlassNavigationReserve() {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .height(88.dp)
    )
}

@Composable
internal fun PrimarySideNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit
) {
    // One responsive alternative, not a second permanent navigation state.
    // Scrolling keeps all five entries accessible in short landscape windows.
    NavigationRail(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        containerColor = XiaoxuPalette.Surface,
        contentColor = XiaoxuPalette.Ink2
    ) {
        Spacer(Modifier.height(12.dp))
        PrimaryTabs.forEach { page ->
            NavigationRailItem(
                selected = selected == page,
                onClick = { onSelect(page) },
                icon = { Icon(page.primaryTabIcon, contentDescription = null) },
                label = { Text(page.primaryTabLabel) },
                alwaysShowLabel = true,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = XiaoxuPalette.BrandDeep,
                    selectedTextColor = XiaoxuPalette.BrandDeep,
                    indicatorColor = XiaoxuPalette.BrandSoft,
                    unselectedIconColor = XiaoxuPalette.Ink3,
                    unselectedTextColor = XiaoxuPalette.Ink3
                )
            )
        }
    }
}
