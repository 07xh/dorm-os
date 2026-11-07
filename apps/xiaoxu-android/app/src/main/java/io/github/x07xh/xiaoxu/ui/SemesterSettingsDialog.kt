package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
    onDismiss: () -> Unit,
    onSave: (SemesterCalendar) -> Unit
) {
    var monday by rememberSaveable { mutableStateOf(current?.firstMonday?.toString() ?: "") }
    var weeks by rememberSaveable { mutableStateOf(current?.totalWeeks?.toString() ?: "") }
    var times by rememberSaveable { mutableStateOf(current?.editableTimes() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

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
                if (error != null) {
                    Text(error!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { SemesterCalendar.parse(monday, weeks, times) }
                    .onSuccess { onSave(it) }
                    .onFailure { error = it.message ?: "设置无效" }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
