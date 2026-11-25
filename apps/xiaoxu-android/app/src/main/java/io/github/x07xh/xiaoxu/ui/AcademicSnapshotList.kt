package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Shared academic view contract, not a second repository. */
internal data class AcademicListSnapshot<T>(
    val status: String, val provenance: String?, val capturedAt: String?, val rows: List<T>
)

@Composable
internal fun AcademicTermPicker(terms: List<String>, selected: String?, allLabel: String = "全部学期",
    onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = terms.isNotEmpty()) {
            Text(selected ?: allLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            terms.forEach { value ->
                DropdownMenuItem(text = { Text(value) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
internal fun AcademicReadAction(label: String, busy: Boolean, onRead: () -> Unit) {
    TextButton(onClick = onRead, enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(if (busy) "读取中" else label)
    }
}

@Composable
internal fun <T> ColumnScope.AcademicSnapshotList(
    title: String, owner: String?, snapshot: AcademicListSnapshot<T>?,
    busy: Boolean, error: String?, onConnect: () -> Unit, onRead: suspend () -> Unit,
    termOf: (T) -> String, keyOf: (T) -> String,
    sort: (List<T>) -> List<T> = { it },
    rowContent: @Composable (T) -> Unit
) {
    val scope = rememberCoroutineScope()
    if (owner.isNullOrBlank()) {
        SystemEmptyState("尚未连接云端账户", "连接后可手动读取已保存的个人记录。")
        TextButton(onClick = onConnect) { Text("数据连接") }
        return
    }
    key(owner, title) {
        var requestedTerm by rememberSaveable { mutableStateOf<String?>(null) }
        val terms = remember(snapshot) { snapshot?.rows.orEmpty().map(termOf).distinct().sortedDescending() }
        val selected = requestedTerm?.takeIf { it in terms }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            AcademicTermPicker(terms, selected, onSelect = { requestedTerm = it })
            AcademicReadAction("读取已保存$title", busy) { scope.launch { onRead() } }
        }
        error?.let { AppNotice(it, error = true) }
        // Avoid labelled non-local returns from the inline Compose key block.
        // Kotlin emitted a NON_LOCAL_RETURN marker rejected by D8.
        when {
            snapshot == null -> {
                if (!busy && error == null) SystemEmptyState("尚未读取$title", "手动读取个人快照，不会自动查询学校。")
            }
            snapshot.status == "not_connected" -> {
                SystemEmptyState("尚无${title}快照", "当前账户未保存相关记录，不代表学校没有记录。")
            }
            else -> {
        AppNotice(if (snapshot.provenance == "trusted_readonly") "教务可信只读快照 · 非实时查询"
            else "个人导入快照 · 未经教务服务核验")
        if (snapshot.status == "stale") AppNotice("快照已过期，请核对正式记录。")
        snapshot.capturedAt?.let { AppNotice("快照时间：$it") }
        val rows = remember(snapshot, selected) { sort(snapshot.rows.filter { selected == null || termOf(it) == selected }) }
        if (rows.isEmpty()) {
            SystemEmptyState("快照中暂无${title}记录", "仅代表已读取的快照，不代表实时教务记录。")
        } else {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            item(key = "summary") { SystemStatStrip(items = listOf("记录数" to rows.size.toString(),
                "学期" to (selected ?: "全部"))) }
            rows.groupBy(termOf).toSortedMap(compareByDescending { it }).forEach { (group, list) ->
                item(key = "term:$group") { SystemSectionHeader(group) }
                items(list, key = { "row:${termOf(it)}:${keyOf(it)}" }) { row -> rowContent(row) }
            }
        }
        }
            }
        }
    }
}
