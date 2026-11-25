package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.pageHorizontal)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            actions()
        }
        Column(Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.item),
            content = content)
    }
}

@Composable
internal fun AppEntry(title: String, summary: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = summary?.let { text -> { Text(text) } },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) }
        )
    }
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
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
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
