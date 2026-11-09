package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDependency
import io.github.x07xh.xiaoxu.data.LocalTaskGraphNode
import io.github.x07xh.xiaoxu.data.localTaskGraphLayers
import io.github.x07xh.xiaoxu.data.localTaskGraphVisibleIds

/**
 * Read-only native relationship diagram for personally created tasks.
 *
 * A node opens the existing LocalTaskEditSheet; graph changes are saved by the
 * existing Room DAO transaction. This deliberately does not create a new editor,
 * school completion action or parallel task model.
 */
@Composable
internal fun LocalTaskDependencyGraphDialog(
    tasks: List<LocalTask>,
    dependencies: List<LocalTaskDependency>,
    onEditTask: (Long) -> Unit,
    onManageTasks: () -> Unit,
    onDismiss: () -> Unit
) {
    val layers = remember(tasks, dependencies) { localTaskGraphLayers(tasks, dependencies) }
    val allNodes = remember(layers) { layers.flatten() }
    var search by rememberSaveable { mutableStateOf("") }
    var focusedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    val visibleIds = remember(layers, search, focusedTaskId) {
        localTaskGraphVisibleIds(layers, focusedTaskId, search)
    }
    val scoped = remember(allNodes, visibleIds) {
        allNodes.filter { it.task.id in visibleIds }
    }
    val shown = remember(scoped, focusedTaskId) {
        val ids = scoped.take(64).mapTo(linkedSetOf()) { it.task.id }
        // A selected task must remain visible even beyond the first 64 graph nodes.
        if (focusedTaskId != null && focusedTaskId !in ids &&
            scoped.any { it.task.id == focusedTaskId }) {
            if (ids.isNotEmpty()) ids.remove(ids.last())
            ids.add(focusedTaskId!!)
        }
        ids
    }
    val total = allNodes.size
    val focusTitle = allNodes.firstOrNull { it.task.id == focusedTaskId }?.task?.title
    XiaoxuSheet(onDismiss = onDismiss, title = "本机任务关系图") {
        Text(
            "按前置关系从上到下排列，箭头表示完成前置后才解锁下一阶段。" +
                "仅包含本人设置的本机任务，不改变学校或云端任务。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        if (layers.isNotEmpty()) {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it.take(80)
                    focusedTaskId = null
                },
                label = { Text("搜索关联任务") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (focusTitle != null) {
                Row(modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("追踪链路：" + focusTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = XiaoxuPalette.Ink2,
                        maxLines = 2,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { focusedTaskId = null }) {
                        Text("显示全部")
                    }
                }
            }
        }
        if (layers.isEmpty()) {
            Text(
                if (dependencies.isEmpty()) "还没有建立先后关系。可在本机任务编辑页添加前置任务。"
                else "依赖关系暂时无法展示，请检查或重新编辑本机任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink2
            )
        } else if (shown.isEmpty()) {
            Text(
                "没有匹配的关联任务。可修改搜索关键词或查看全部。",
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink2
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                layers.forEach { layer ->
                    val visible = layer.filter { it.task.id in shown }
                    if (visible.isNotEmpty()) {
                        val level = layer.first().stage
                        if (level > 0) {
                            Text(
                                "↓ 前置完成后解锁",
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelMedium,
                                color = XiaoxuPalette.Ink2
                            )
                        }
                        Text(
                            "阶段 ${level + 1} · ${visible.size} 项",
                            style = MaterialTheme.typography.titleSmall,
                            color = XiaoxuPalette.Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                        visible.forEach { node ->
                            LocalTaskGraphNodeRow(
                                node = node,
                                onEditTask = onEditTask,
                                onFocusTask = { taskId ->
                                    search = ""
                                    focusedTaskId = taskId
                                },
                                focused = node.task.id == focusedTaskId
                            )
                        }
                    }
                }
            }
            if (total > shown.size) {
                Text(
                    "当前展示前 ${shown.size} 个关联任务，共 ${total} 个。" +
                        "其余任务可在本机任务管理页编辑。",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2
                )
            }
        }
        OutlinedButton(
            onClick = onManageTasks,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Icon(Icons.Outlined.AccountTree, contentDescription = null)
            Text("编辑本机任务与前置关系", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun LocalTaskGraphNodeRow(
    node: LocalTaskGraphNode,
    onEditTask: (Long) -> Unit,
    onFocusTask: (Long) -> Unit,
    focused: Boolean
) {
    val task = node.task
    val unfinished = node.prerequisites.count { !it.completed }
    val state = when {
        task.completed -> "已完成"
        unfinished > 0 -> "等待 ${unfinished} 项前置"
        else -> "现在可做"
    }
    val shape = RoundedCornerShape(XiaoxuRadius.card)
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(XiaoxuPalette.GlassCard)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassRim, shape)
            .clickable(onClickLabel = "编辑任务“${task.title}”的前置关系") {
                onEditTask(task.id)
            }
            .semantics {
                contentDescription = "${task.title}，${state}，点击编辑前置关系"
            }
            .heightIn(min = 62.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.AccountTree, contentDescription = null, tint = XiaoxuPalette.Ink2)
        Column(modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(task.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = XiaoxuPalette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                state,
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2
            )
            if (node.prerequisites.isNotEmpty()) {
                Text(
                    "← " + node.prerequisites.joinToString("、") { it.title },
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (node.unlocks.isNotEmpty()) {
                Text(
                    "→ " + node.unlocks.joinToString("、") { it.title },
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        TextButton(
            onClick = { onFocusTask(task.id) },
            modifier = Modifier.heightIn(min = 48.dp)
        ) { Text(if (focused) "已聚焦" else "追踪") }
    }
}
