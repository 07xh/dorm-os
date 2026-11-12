package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.parseLocalTaskBreakdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Manual approval is required: this UI never pretends to call an AI model. */
@Composable
internal fun LocalTaskBreakdownSheet(
    task: LocalTask,
    dao: LocalTaskDao,
    client: CloudClient,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(task.id) { mutableStateOf("") }
    var saving by remember(task.id) { mutableStateOf(false) }
    var aiLoading by remember(task.id) { mutableStateOf(false) }
    var aiSuggested by remember(task.id) { mutableStateOf<List<String>?>(null) }
    var showNotes by rememberSaveable(task.id) { mutableStateOf(false) }
    var notes by rememberSaveable(task.id) { mutableStateOf("") }
    var aiError by remember(task.id) { mutableStateOf<String?>(null) }
    var error by remember(task.id) { mutableStateOf<String?>(null) }
    val steps = remember(draft) { parseLocalTaskBreakdown(draft) }

    XiaoxuGlassDialog(
        title = "分解本机任务",
        onDismiss = { if (!saving && !aiLoading) onDismiss() },
        showDismissButton = false
    ) {
        Text(task.title, style = MaterialTheme.typography.titleSmall,
            color = XiaoxuPalette.Ink, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Text(
            "每行填写一个可执行步骤（2～12 条）。确认后按行序保存真实本机任务，" +
                "前一步完成才解锁下一步，全部完成后原任务才可完成。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        TextButton(
            onClick = { showNotes = !showNotes },
            enabled = !aiLoading && !saving
        ) { Text(if (showNotes) "收起 AI 补充说明" else "AI 补充说明（可选）") }
        if (showNotes) {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(600) },
                label = { Text("仅发送你主动填写的说明") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 3,
                enabled = !aiLoading && !saving
            )
        }
        OutlinedButton(
            onClick = {
                if (!aiLoading && !saving) {
                    aiLoading = true
                    aiError = null
                    aiSuggested = null
                    scope.launch {
                        try {
                            val suggestion = client.suggestLocalTaskBreakdown(task.title, notes)
                            if (suggestion.error != null) aiError = suggestion.error
                            else aiSuggested = suggestion.steps
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            aiError = "AI 建议生成失败，没有保存任务"
                        } finally {
                            aiLoading = false
                        }
                    }
                }
            },
            enabled = !aiLoading && !saving,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(if (aiLoading) "正在生成建议…" else "AI 生成分解建议")
        }
        Text("点击才发送当前任务标题和补充说明到校序的云端 AI；" +
            "不会发送本机任务库，也不会自动保存结果。AI 功能需云端登录及服务配置。",
            style = MaterialTheme.typography.labelSmall,
            color = XiaoxuPalette.Ink2)
        if (aiError != null) {
            Text(aiError!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        if (aiSuggested != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("AI 建议 · 尚未保存", style = MaterialTheme.typography.labelMedium,
                    color = XiaoxuPalette.Ink2)
                aiSuggested!!.forEachIndexed { index, step ->
                    Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall,
                        color = XiaoxuPalette.Ink)
                }
            }
            OutlinedButton(
                onClick = {
                    draft = aiSuggested!!.joinToString("\n")
                    aiSuggested = null
                    error = null
                },
                enabled = !saving && !aiLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (draft.isBlank()) "填入下方编辑框" else "用 AI 建议替换当前步骤") }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(2400); error = null },
            label = { Text("分解步骤（每行一条）") },
            placeholder = { Text("整理实验数据\n编写实验报告\n检查并提交报告") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 9
        )
        if (steps != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("将创建 ${steps.size} 条个人步骤：",
                    style = MaterialTheme.typography.labelMedium,
                    color = XiaoxuPalette.Ink2)
                steps.forEachIndexed { index, step ->
                    Text("${index + 1}. $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = XiaoxuPalette.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        } else if (draft.isNotBlank()) {
            Text("请输入 2～12 条不重复的步骤，每条不超过 120 字。",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Text("步骤沿用个人分类，不复制截止日期；不会更改学习通或云端任务状态。",
            style = MaterialTheme.typography.labelSmall,
            color = XiaoxuPalette.Ink2)
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                onClick = onDismiss,
                enabled = !saving && !aiLoading,
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("取消") }
            Button(
                enabled = !saving && !aiLoading && !task.completed && steps != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                onClick = {
                    if (steps == null || saving) return@Button
                    saving = true
                    error = null
                    val approved = draft
                    scope.launch {
                        try {
                            if (dao.tryBreakDown(task.id, approved)) onDismiss()
                            else error = "保存失败：任务已完成、已删除或步骤不符合要求"
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "本机保存失败，未创建部分任务，请重试"
                        } finally {
                            saving = false
                        }
                    }
                }
            ) { Text(if (saving) "保存中…" else "创建步骤") }
        }
    }
}
