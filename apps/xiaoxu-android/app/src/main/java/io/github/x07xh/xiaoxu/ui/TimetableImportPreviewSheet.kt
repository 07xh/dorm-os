package io.github.x07xh.xiaoxu.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.x07xh.xiaoxu.data.VerifiedLesson

@Composable
internal fun TimetableImportPreviewSheet(
    fileName: String, lessons: List<VerifiedLesson>, oldCount: Int,
    hasSemesterSettings: Boolean, saving: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    AlertDialog(onDismissRequest = { if(!saving) onDismiss() },
        title = { Text("确认替换本机课表") },
        text = { Text(fileName + "\n原有 " + oldCount + " 个课块，新文件 " + lessons.size +
            " 个课块。" + if(hasSemesterSettings) "\n同时恢复学期设置。" else "") },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !saving) { Text("确认导入") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } })
}
