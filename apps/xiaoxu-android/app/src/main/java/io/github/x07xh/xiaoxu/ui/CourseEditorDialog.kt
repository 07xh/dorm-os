package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.VerifiedLesson

private fun parseWeekList(text: String): List<Int> {
    val weeks = mutableSetOf<Int>()
    val chunks = text.split(Regex("[,，、\\s]+")).filter { it.isNotBlank() }
    require(chunks.isNotEmpty()) { "请填写上课周次" }
    chunks.forEach { item ->
        if ('-' in item || '–' in item) {
            val parts = item.split('-', '–')
            require(parts.size == 2) { "周次格式不正确：$item" }
            val start = parts[0].toIntOrNull()
            val end = parts[1].toIntOrNull()
            require(start != null && end != null && start in 1..35 && end in start..35) {
                "周次范围不正确：$item"
            }
            weeks.addAll(start..end)
        } else {
            val week = item.toIntOrNull()
            require(week != null && week in 1..35) { "周次不正确：$item" }
            weeks += week
        }
    }
    return weeks.sorted()
}

/** A small editor inside the existing schedule screen, not another timetable page. */
@Composable
internal fun CourseEditorDialog(
    original: VerifiedLesson?,
    selectedWeek: Int?,
    busy: Boolean,
    externalError: String?,
    onDismiss: () -> Unit,
    onSave: (VerifiedLesson, Boolean) -> Unit,
    onDelete: (Boolean) -> Unit
) {
    var name by rememberSaveable(original?.id) { mutableStateOf(original?.title ?: "") }
    var teacher by rememberSaveable(original?.id) { mutableStateOf(original?.teacher ?: "") }
    var location by rememberSaveable(original?.id) { mutableStateOf(original?.location ?: "") }
    var weekday by rememberSaveable(original?.id) { mutableStateOf(original?.weekday?.toString() ?: "1") }
    var start by rememberSaveable(original?.id) { mutableStateOf(original?.startSection?.toString() ?: "1") }
    var end by rememberSaveable(original?.id) { mutableStateOf(original?.endSection?.toString() ?: "2") }
    var weeks by rememberSaveable(original?.id) {
        mutableStateOf(original?.weeks?.joinToString(",") ?: selectedWeek?.toString().orEmpty())
    }
    var weekOnly by rememberSaveable(original?.id, selectedWeek) { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val canScope = original != null && selectedWeek != null && selectedWeek in original.weeks

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (original == null) "新增课程" else "编辑课程") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it; localError = null }, label = { Text("课程名称") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teacher, { teacher = it }, label = { Text("任课教师（可选）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, { location = it }, label = { Text("教室（可选）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(weekday, { weekday = it }, label = { Text("星期（1=周一，7=周日）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("开始节") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("结束节") },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(weeks, { weeks = it }, label = { Text("上课周次（如1-16,18）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    enabled = !weekOnly)
                if (canScope) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = weekOnly, onCheckedChange = { weekOnly = it })
                        Text("仅修改第$selectedWeek 周")
                    }
                }
                if (externalError != null || localError != null) {
                    Text(externalError ?: localError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                runCatching {
                    val wd = weekday.trim().toIntOrNull()
                    val first = start.trim().toIntOrNull()
                    val last = end.trim().toIntOrNull()
                    require(wd != null && wd in 1..7) { "星期必须为1至7" }
                    require(first != null && last != null && first in 1..24 && last in first..24) {
                        "节次范围不正确"
                    }
                    val activeWeeks = if (weekOnly) listOf(selectedWeek!!) else parseWeekList(weeks)
                    require(name.trim().isNotEmpty()) { "课程名称不能为空" }
                    require(name.trim().length <= 120 && teacher.trim().length <= 90 &&
                        location.trim().length <= 130) { "课程信息超出长度限制" }
                    VerifiedLesson(
                        id = original?.id.orEmpty(),
                        term = original?.term.orEmpty(),
                        title = name.trim(),
                        teacher = teacher.trim(),
                        location = location.trim(),
                        weekday = wd, startSection = first, endSection = last,
                        weeks = activeWeeks
                    )
                }.onSuccess { onSave(it, weekOnly) }
                    .onFailure { localError = it.message ?: "课程内容无效" }
            }) { Text(if (busy) "保存中" else "保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (original != null) {
                    TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("删除") }
                }
                TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") }
            }
        }
    )
    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("确认删除课程") },
            text = {
                Text(if (weekOnly && canScope) "仅删除第$selectedWeek 周的这门课程？"
                    else "删除此课程全部周次？")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmDelete = false
                    onDelete(weekOnly && canScope)
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
