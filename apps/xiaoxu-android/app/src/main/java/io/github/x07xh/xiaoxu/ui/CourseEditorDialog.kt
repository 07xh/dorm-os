package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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


/**
 * 编辑课程与课表设置采用同一个全屏玻璃分组页面，不再使用狭窄系统表单弹窗。
 * 数据校验、仅修改选中周次和删除确认维持原有行为。
 */
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
    fun submit() {
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
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(XiaoxuPalette.PageGradient)
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = !busy,
                    onClick = onDismiss,
                    modifier = Modifier.size(46.dp)
                        .clip(CircleShape).background(Color.White.copy(alpha = 0.65f))
                ) { Icon(Icons.Outlined.Close, contentDescription = "关闭课程编辑") }
                Text(
                    if (original == null) "新增课程" else "编辑课程",
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontSize = 27.sp, fontWeight = FontWeight.SemiBold,
                    color = XiaoxuPalette.Ink
                )
                TextButton(enabled = !busy, onClick = ::submit) {
                    Text(if (busy) "保存中" else "保存", color = XiaoxuPalette.Brand, fontSize = 16.sp)
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                XiaoxuGroupedSection(title = "课程信息") {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EditorField(name, { name = it; localError = null }, "课程名称")
                        EditorField(teacher, { teacher = it }, "任课教师（可选）")
                        EditorField(location, { location = it }, "教室（可选）")
                    }
                }
                XiaoxuGroupedSection(title = "上课时间") {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        EditorField(weekday, { weekday = it }, "星期（1=周一，7=周日）", numeric = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            EditorField(start, { start = it }, "开始节", numeric = true,
                                modifier = Modifier.weight(1f))
                            EditorField(end, { end = it }, "结束节", numeric = true,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
                XiaoxuGroupedSection(title = "上课周次") {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EditorField(weeks, { weeks = it }, "周次（如1-16,18）", enabled = !weekOnly)
                        if (canScope) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(XiaoxuRadius.tile))
                                    .clickable(enabled = !busy) { weekOnly = !weekOnly },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = weekOnly,
                                    onCheckedChange = { weekOnly = it }, enabled = !busy)
                                Text("仅修改第$selectedWeek 周", color = XiaoxuPalette.Ink)
                            }
                        }
                    }
                }
                if (externalError != null || localError != null) {
                    Text(externalError ?: localError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 14.dp))
                }
                if (original != null) {
                    XiaoxuGroupedSection {
                        TextButton(
                            enabled = !busy,
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)
                        ) {
                            Text("删除课程", color = XiaoxuPalette.Danger)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text("确认删除课程") },
            text = {
                Text(if (weekOnly && canScope) "仅删除第$selectedWeek 周的这门课程？"
                    else "删除此课程全部周次？")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    confirmDelete = false
                    onDelete(weekOnly && canScope)
                }) { Text("确认删除", color = XiaoxuPalette.Danger) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EditorField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    numeric: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 58.dp),
        shape = RoundedCornerShape(XiaoxuRadius.tile),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = XiaoxuPalette.Brand,
            unfocusedBorderColor = XiaoxuPalette.Line,
            focusedLabelColor = XiaoxuPalette.BrandDeep,
            unfocusedLabelColor = XiaoxuPalette.Ink2,
            focusedTextColor = XiaoxuPalette.Ink,
            unfocusedTextColor = XiaoxuPalette.Ink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}
