package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import io.github.x07xh.xiaoxu.ui.v2.UiV2Theme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberUpdatedState
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
    val latestOnSelect = rememberUpdatedState(onSelect)
    val selectIndex: (Int) -> Unit = remember {
        { index -> PrimaryTabs.getOrNull(index)?.let { latestOnSelect.value(it) } }
    }
    FloatingBottomBar(
        items = items,
        selectedIndex = PrimaryTabs.indexOf(selected).coerceAtLeast(0),
        backdrop = backdrop,
        // Original v1.0.83 selected tint; no custom palette reinterpretation.
        selectedContentColor = if (backdrop != null) Color(0xFF0057D9) else XiaoxuPalette.BrandDeep,
        onSelect = selectIndex
    )
}

/**
 * Reserves exactly the same responsive geometry as the actual dock
 * and the current system navigation inset. This contains no navigation UI:
 * the actual glass bar is overlaid once, outside the backdrop capture layer.
 * Scaffold must own content padding; child screens must not add a second inset.
 */
@Composable
internal fun PrimaryBottomNavigationReserve(glass: Boolean) {
    val geometry = rememberPrimaryBottomGeometry(glass)
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .height(geometry.contentReserve)
    )
}

/**
 * DSH-style expandable sidebar on wide tablets; the narrow rail and sidebar
 * are alternative renderers of the same five routes, never a second router.
 */
@Composable
internal fun PrimarySideNavigation(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    canExpand: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val frame = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
        .clip(UiV2Theme.GroupShape)
    if (canExpand && expanded) {
        Column(
            modifier = frame.width(220.dp).fillMaxHeight()
                .background(XiaoxuPalette.Surface)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("校序", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge, color = XiaoxuPalette.Ink)
                XiaoxuSoftIconButton(onClick = { expanded = false }) {
                    Icon(Icons.Outlined.Menu, contentDescription = "收起侧栏")
                }
            }
            PrimaryTabs.forEach { page ->
                NavigationDrawerItem(
                    label = { Text(page.primaryTabLabel) },
                    selected = selected == page,
                    onClick = { onSelect(page) },
                    icon = { Icon(page.primaryTabIcon, contentDescription = null) }
                )
            }
        }
    } else {
        NavigationRail(
            modifier = frame.verticalScroll(rememberScrollState()),
            containerColor = XiaoxuPalette.Surface,
            contentColor = XiaoxuPalette.Ink2,
            header = if (canExpand) {
                {
                    XiaoxuSoftIconButton(onClick = { expanded = true }) {
                        Icon(Icons.Outlined.Menu, contentDescription = "展开侧栏")
                    }
                }
            } else null
        ) {
            Spacer(Modifier.height(8.dp))
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
}

/** Phone drawer uses the same route descriptors as the rail and dock. */
@Composable
internal fun PrimaryNavigationDrawer(
    selected: MainDestination,
    onSelect: (MainDestination) -> Unit,
    onClose: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = XiaoxuPalette.Surface,
        drawerContentColor = XiaoxuPalette.Ink
    ) {
        Column(
            Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("校序", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall)
                XiaoxuSoftIconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭导航")
                }
            }
            PrimaryTabs.forEach { page ->
                NavigationDrawerItem(
                    label = { Text(page.primaryTabLabel) },
                    selected = selected == page,
                    onClick = { onSelect(page) },
                    icon = { Icon(page.primaryTabIcon, contentDescription = null) }
                )
            }
        }
    }
}
