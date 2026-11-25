package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source layout: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, GradesScreen.kt (SemesterGradesContent, ExamScheduleContent).
// Original authors and contributors; adapted to owner-scoped readonly snapshots and shared page host.

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    var expanded by rememberSaveable { mutableStateOf(false) }
    val options = listOf(allLabel) + terms
    val index = (selected?.let { terms.indexOf(it) } ?: -1) + 1
    TextButton(onClick = { expanded = true }, enabled = terms.isNotEmpty()) {
        Text(selected ?: allLabel)
    }
    if (expanded && terms.isNotEmpty()) {
        GlassOptionWheelDialog(options, index,
            onConfirm = { choice ->
                onSelect(terms.getOrNull(choice - 1))
                expanded = false
            }, onDismiss = { expanded = false }, title = "学期")
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

/** Shared upstream grade/exam list page: header and rows scroll as one lazy list.
 * The feature adapters supply only verified local snapshot fields and read actions.
 */
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
        val usable = snapshot?.takeUnless { it.status == "not_connected" }
        val rows = remember(usable, selected) {
            sort(usable?.rows.orEmpty().filter { selected == null || termOf(it) == selected })
        }
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Column(Modifier.moduleEntrance(1), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        AcademicTermPicker(terms, selected, onSelect = { requestedTerm = it })
                        AcademicReadAction("读取已保存$title", busy) { scope.launch { onRead() } }
                    }
                    GradeRefreshStatus(busy, error.orEmpty())
                    when {
                        usable == null && snapshot?.status == "not_connected" ->
                            SystemEmptyState("尚无${title}快照", "当前账户未保存相关记录，不代表学校没有记录。")
                        usable == null && busy ->
                            AppNotice("正在读取已保存$title")
                        usable == null && error == null ->
                            SystemEmptyState("尚未读取$title", "手动读取个人快照，不会自动查询学校。")
                        usable != null -> {
                            AppNotice(if (usable.provenance == "trusted_readonly") "教务可信只读快照 · 非实时查询"
                                else "个人导入快照 · 未经教务服务核验")
                            if (usable.status == "stale") AppNotice("快照已过期，请核对正式记录。")
                            usable.capturedAt?.let { AppNotice("快照时间：$it") }
                            if (rows.isEmpty()) {
                                SystemEmptyState("快照中暂无${title}记录", "仅代表已读取的快照，不代表实时教务记录。")
                            } else {
                                SystemStatStrip(items = listOf("记录数" to rows.size.toString(),
                                    "学期" to (selected ?: "全部")))
                                SystemSectionHeader(title = "${title}明细")
                            }
                        }
                    }
                }
            }
            rows.groupBy(termOf).toSortedMap(compareByDescending { it }).forEach { (group, list) ->
                item(key = "term:$group") { SystemSectionHeader(group) }
                items(list, key = { "row:${termOf(it)}:${keyOf(it)}" },
                    contentType = { "academic-row" }) { row -> rowContent(row) }
            }
        }
    }
}

@Composable
private fun GradeRefreshStatus(isLoading: Boolean, error: String) {
    if (isLoading) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp)
        )
    } else if (error.isNotBlank()) {
        Text(error, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}

