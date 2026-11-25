package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
internal fun ColumnScope.ExamsFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val cloud = data.cloud
    val scope = rememberCoroutineScope()
    if (!cloud.authenticated || cloud.ownerId.isNullOrBlank()) {
        SystemEmptyState("尚未连接云端账户", "连接后可手动读取已保存的考试记录。")
        TextButton(onClick = { actions.navigate(AppRoute.CONNECTIONS) }) { Text("数据连接") }
        return
    }
    key(cloud.ownerId) {
        var term by rememberSaveable { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf(false) }
        val snapshot = cloud.exams.takeIf { cloud.examsOwner == cloud.ownerId }
        TextButton(onClick = { scope.launch { actions.readExams() } }, enabled = !cloud.examsBusy) {
            Text(if (cloud.examsBusy) "读取中" else "读取已保存考试")
        }
        cloud.examsError?.let { AppNotice(it, error = true) }
        if (snapshot == null) {
            if (cloud.examsBusy) AppNotice("正在读取")
            else SystemEmptyState("尚未读取考试", "读取个人考试快照，不会自动查询学校。")
            return@key
        }
        if (snapshot.status == "not_connected") {
            SystemEmptyState("尚无考试快照", "当前账户未保存考试记录，不能据此判断没有考试。")
            return@key
        }
        AppNotice(if (snapshot.provenance == "trusted_readonly") "教务可信只读快照 · 非实时查询"
            else "个人导入快照 · 未经教务服务核验")
        if (snapshot.status == "stale") AppNotice("快照已过期，请核对正式考试安排。")
        snapshot.capturedAt?.let { AppNotice("快照时间：$it") }
        val terms = remember(snapshot) { snapshot.rows.map { it.term }.distinct().sortedDescending() }
        val selected = term?.takeIf { it in terms }
        Box {
            TextButton(onClick = { expanded = true }) { Text(selected ?: "全部学期") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("全部学期") }, onClick = { term = null; expanded = false })
                terms.forEach { value -> DropdownMenuItem(text = { Text(value) },
                    onClick = { term = value; expanded = false }) }
            }
        }
        val rows = remember(snapshot, selected) {
            snapshot.rows.filter { selected == null || it.term == selected }
                .sortedWith(compareBy({ it.date }, { it.startTime }, { it.id }))
        }
        if (rows.isEmpty()) SystemEmptyState("快照中暂无考试记录", "仅代表已读取的快照，不代表实时教务安排。")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            rows.groupBy { it.term }.toSortedMap(compareByDescending { it }).forEach { (group, list) ->
                item(key = "term:$group") { SystemSectionHeader(group) }
                items(list, key = { "${it.term}:${it.id}" }) { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
                        ExamItemRow(ExamItemUi(courseName = row.title,
                            examTime = listOf(row.date, row.startTime, row.endTime).filter { it.isNotBlank() }.joinToString(" "),
                            location = row.location, seatNumber = "", examName = "考试",
                            teacher = row.teacher, sourceId = row.id))
                        if (row.note.isNotBlank()) AppNotice(row.note)
                    }
                }
            }
        }
    }
}
