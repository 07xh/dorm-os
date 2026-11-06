package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.SemesterCalendar

/**
 * Compact native setting surface. No school-login flow and no default school
 * dates or period times; all values are explicitly provided by the owner.
 */
@Composable
internal fun SemesterSettingsDialog(
    current: SemesterCalendar?,
    reminderEnabled: Boolean,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (SemesterCalendar, Boolean) -> Unit
) {
    var monday by rememberSaveable { mutableStateOf(current?.firstMonday?.toString() ?: "") }
    var weeks by rememberSaveable { mutableStateOf(current?.totalWeeks?.toString() ?: "") }
    var times by rememberSaveable { mutableStateOf(current?.editableTimes() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var wantReminders by rememberSaveable { mutableStateOf(reminderEnabled) }
    var selectedViewMode by rememberSaveable { mutableStateOf(viewMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期与作息设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("仅保存在本机。设置后才能自动定位本周和显示上课时间。")
                Text("课表视图")
                XiaoxuSegmentedControl(
                    options = listOf(
                        XiaoxuSegmentOption("week", "周视图"),
                        XiaoxuSegmentOption("day", "日视图")
                    ),
                    selectedKey = selectedViewMode,
                    onSelected = { selectedViewMode = it }
                )
                OutlinedTextField(
                    value = monday, onValueChange = { monday = it; error = null },
                    label = { Text("第1周周一（年-月-日）") },
                    placeholder = { Text("2026-09-07") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weeks, onValueChange = { weeks = it; error = null },
                    label = { Text("学期周数（1–35）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = times, onValueChange = { times = it; error = null },
                    label = { Text("各节起止时间（可选，每行一节）") },
                    placeholder = { Text("1 08:00-08:45\n2 08:55-09:40") },
                    minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = wantReminders, onCheckedChange = { wantReminders = it })
                    Text("本机上课提醒（约提前10分钟，系统可能延后）")
                }
                Text("提醒需要通知权限和完整节次时间；默认关闭，不联网。")
                if (error != null) {
                    Text(error!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // A view preference can be saved before the user configures the semester.
                // Partial/invalid semester fields still fail validation instead of being discarded.
                if (current == null && monday.isBlank() && weeks.isBlank() && times.isBlank()) {
                    onViewModeChange(selectedViewMode)
                    onDismiss()
                } else {
                    runCatching { SemesterCalendar.parse(monday, weeks, times) }
                        .onSuccess {
                            onViewModeChange(selectedViewMode)
                            onSave(it, wantReminders)
                        }
                        .onFailure { error = it.message ?: "设置无效" }
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onDismiss(); onExport() }) { Text("导出备份") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
