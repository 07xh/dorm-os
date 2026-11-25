package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.x07xh.xiaoxu.data.AcademicGradeRow
import kotlinx.coroutines.launch

/** Existing owner-scoped server snapshot, manually read. No school-side refresh here. */
@Composable
internal fun ColumnScope.GradesFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val cloud = data.cloud
    val scope = rememberCoroutineScope()
    if (!cloud.authenticated || cloud.ownerId.isNullOrBlank()) {
        SystemEmptyState("尚未连接云端账户", "连接后可手动读取已保存的成绩记录。")
        TextButton(onClick = { actions.navigate(AppRoute.CONNECTIONS) }) { Text("数据连接") }
        return
    }
    key(cloud.ownerId) {
        var term by rememberSaveable { mutableStateOf<String?>(null) }
        var expanded by remember { mutableStateOf(false) }
        val snapshot = cloud.grades.takeIf { cloud.gradesOwner == cloud.ownerId }
        Row {
            TextButton(onClick = { scope.launch { actions.readGrades() } }, enabled = !cloud.gradesBusy) {
                Text(if (cloud.gradesBusy) "读取中" else "读取已保存成绩")
            }
        }
        cloud.gradesError?.let { AppNotice(it, error = true) }
        if (snapshot == null) {
            if (cloud.gradesBusy) AppNotice("正在读取")
            else SystemEmptyState("尚未读取成绩", "手动读取已保存的个人成绩快照，不会发起教务系统同步。")
            return@key
        }
        if (snapshot.status == "not_connected") {
            SystemEmptyState("尚无成绩快照", "当前账户未保存成绩。")
            return@key
        }
        AppNotice(if (snapshot.provenance == "trusted_readonly") "教务可信只读快照 · 非实时查询"
            else "个人导入快照 · 未经教务服务核验")
        if (snapshot.status == "stale") AppNotice("快照已过期，请勿作为最新成绩。")
        snapshot.capturedAt?.let { AppNotice("快照时间：$it") }
        val terms = remember(snapshot) { snapshot.rows.map { it.term }.distinct().sortedDescending() }
        val selected = term?.takeIf { it in terms }
        Box {
            TextButton(onClick = { expanded = true }) { Text(selected ?: "全部学期") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("全部学期") }, onClick = { term = null; expanded = false })
                terms.forEach { value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = { term = value; expanded = false })
                }
            }
        }
        val rows = remember(snapshot, selected) { snapshot.rows.filter { selected == null || it.term == selected } }
        if (rows.isEmpty()) SystemEmptyState("快照中暂无成绩记录", "这是已读取的快照结果，不代表实时教务记录。")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            rows.groupBy { it.term }.toSortedMap(compareByDescending { it }).forEach { (group, list) ->
                item(key = "term:$group") { SystemSectionHeader(group) }
                items(list, key = { "${it.term}:${it.id}" }) { row ->
                    GradeItemRow(GradeItemUi(courseName = row.title,
                        grade = row.score.ifBlank { "未记录" }, credits = row.credits?.toString().orEmpty(),
                        gpa = row.gradePoint?.toString().orEmpty(), courseType = "",
                        term = row.term, jxbId = row.id, detail = row.note))
                }
            }
        }
    }
}
