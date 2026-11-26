package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.MainDestination

/** Business route list and adaptive tablet rail; phone navigation belongs to NorthPrimaryHost. */
internal val PrimaryTabs = listOf(
    MainDestination.HOME,
    MainDestination.SCHEDULE,
    MainDestination.TASKS,
    MainDestination.ACADEMIC,
    MainDestination.PROFILE
)

internal val MainDestination.primaryTabLabel: String
    get() = when (this) {
        MainDestination.HOME -> "今日"
        MainDestination.SCHEDULE -> "课表"
        MainDestination.ACADEMIC -> "校园"
        MainDestination.TASKS -> "任务"
        MainDestination.PROFILE -> "我的"
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
