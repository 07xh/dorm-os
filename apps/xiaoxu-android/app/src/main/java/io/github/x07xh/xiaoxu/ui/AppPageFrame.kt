package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal object AppSpacing {
    val pageHorizontal = 20.dp
    val pageVertical = 12.dp
    val section = 20.dp
    val item = 8.dp
}

/** Every feature shares this layout; no nested Scaffold or duplicate system insets. */
@Composable
internal fun AppPageFrame(
    title: String, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // Share the upstream SettingsScreen/SystemTopBar collapse treatment.
    // Observe consumed vertical motion without taking scroll away from the child.
    val travel = with(LocalDensity.current) { 64.dp.toPx() }.coerceAtLeast(1f)
    var lift by remember { mutableFloatStateOf(0f) }
    val observer = remember(travel) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                lift = (lift - consumed.y / travel).coerceIn(0f, 1f)
                return Offset.Zero
            }
        }
    }
    GlassPageScaffold(title = title, onBack = onBack, actions = actions, topBar = {
        Box(Modifier.moduleEntrance(0)) {
            SystemTopBar(title = title, collapseFraction = lift, navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            }, actions = actions)
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().nestedScroll(observer).padding(padding).consumeWindowInsets(padding)
            .padding(horizontal = AppSpacing.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.item), content = content)
    }
}

@Composable
internal fun AppEntry(title: String, summary: String? = null, onClick: () -> Unit) {
    InsetGroupedSection {
        AppGroupedEntry(title, summary, showDivider = false, onClick = onClick)
    }
}

@Composable
internal fun AppGroupedEntry(
    title: String, summary: String? = null, showDivider: Boolean = true,
    onClick: () -> Unit
) {
    InsetGroupedRow(title = title, subtitle = summary, showDivider = showDivider,
        trailing = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
        onClick = onClick)
}

@Composable
internal fun AppNotice(message: String, error: Boolean = false) {
    Text(message, style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = AppSpacing.item))
}

@Composable
internal fun <T> AppSnapshotContent(
    snapshot: PageSnapshot<T>, onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (snapshot) {
        PageSnapshot.Loading -> Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            PageLoadingIcon(modifier = Modifier.size(28.dp))
            AppNotice("正在读取数据")
        }
        is PageSnapshot.Unavailable -> {
            AppNotice(snapshot.message, error = true)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("重试") }
        }
        is PageSnapshot.Ready -> {
            if (snapshot.stale) AppNotice("当前显示上次读取结果，可能不是最新数据。")
            content(snapshot.value)
        }
    }
}
