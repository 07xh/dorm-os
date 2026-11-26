package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Business content spacing only; no page, window, scroll or navigation implementation.
internal object AppSpacing {
    val formMaxWidth = 680.dp
    val workspaceMaxWidth = 1200.dp
    val pageHorizontal = 20.dp
    val pageVertical = 12.dp
    val section = 20.dp
    val item = 8.dp
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
