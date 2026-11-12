package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One execution editor inside the existing source-aware task detail. All mutations stay in Room. */
@Composable
internal fun TaskExecutionPanel(
    root: ExecutionRoot,
    rootDone: Boolean,
    dao: TaskExecutionDao,
    onFinish: suspend () -> Boolean,
    onReopen: (suspend () -> Boolean)? = null,
    rootBlocked: Boolean = false,
    aiSuggest: (suspend (String) -> TaskBreakdownAiSuggestion)? = null
) {
    val all by rememberLocalRows(dao) { dao.observeSteps() }
    val edges by rememberLocalRows(dao) { dao.observeEdges() }
    val executions by rememberLocalRows(dao) { dao.observeExecutions() }
    val steps=remember(all,root) {all.filter {it.scope==root.scope && it.rootKey==root.key}}
    val ready=remember(steps,edges) {readyTaskSteps(steps,edges).mapTo(hashSetOf()) {it.id}}
    val scope=rememberCoroutineScope()
    var aiExpanded by rememberSaveable(root.scope,root.key) {mutableStateOf(false)}
    var aiNotes by rememberSaveable(root.scope,root.key) {mutableStateOf("")}
    var aiSuggestions by remember(root) {mutableStateOf<List<String>>(emptyList())}
    var aiBusy by remember(root) {mutableStateOf(false)}
    var adding by rememberSaveable(root.scope,root.key) {mutableStateOf(false)}
    var draft by rememberSaveable(root.scope,root.key) {mutableStateOf("")}
    var editId by rememberSaveable(root.scope,root.key) {mutableStateOf<Long?>(null)}
    var editTitle by rememberSaveable(root.scope,root.key) {mutableStateOf("")}
    var editDue by rememberSaveable(root.scope,root.key) {mutableStateOf("")}
    var chosen by remember(root) {mutableStateOf<Set<Long>>(emptySet())}
    var showBefore by rememberSaveable(root.scope,root.key) {mutableStateOf(false)}
    var deleting by remember(root) {mutableStateOf<TaskStep?>(null)}
    var busy by remember(root) {mutableStateOf(false)}
    var error by remember(root) {mutableStateOf<String?>(null)}
    var undoId by rememberSaveable(root.scope,root.key) {mutableStateOf<Long?>(null)}
    val remoteEnded=rootDone && root.scope.startsWith("wechat:")
    fun perform(action:suspend ()->Boolean) {
        if(busy) return
        busy=true;error=null
        scope.launch {
            try { if(!withExecutionAction(root) { action() }) error="操作未保存，请刷新任务或检查前置条件" }
            catch(cancelled:CancellationException) {throw cancelled}
            catch(_:Exception) {error="保存失败，输入已保留，请重试"}
            finally {busy=false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(steps.isEmpty()) "执行步骤" else "步骤 ${steps.count {it.completed}}/${steps.size}",
                style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
            TextButton(enabled=!busy && !remoteEnded,onClick={adding=!adding}) {
                Text(if(adding) "收起" else if(steps.isEmpty()) "分解成步骤" else "新增步骤")
            }
        }
        Text("个人步骤 · 本机保存",style=MaterialTheme.typography.labelSmall,color=XiaoxuPalette.Ink2)
        if(rootBlocked) Text("父任务正在等待前置完成",style=MaterialTheme.typography.bodySmall,color=XiaoxuPalette.Ink2)
        if(remoteEnded) {
            Text("先重新打开父任务，再新增或重开步骤",style=MaterialTheme.typography.bodySmall)
            if(onReopen!=null) TextButton(enabled=!busy,onClick={perform {if(onReopen()) {dao.reopen(root);true} else false}}) {Text("重新打开父任务")}
        }
        if(steps.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(min=56.dp,max=360.dp)) {
                items(steps,key={it.id}) {step->
                    var menu by remember(step.id) {mutableStateOf(false)}
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        TaskCompletionCheckbox(step.completed,
                            onCheckedChange={done->perform {
                                val saved=dao.toggleStep(root,step.id,done)
                                if(saved && done) undoId=step.id
                                saved
                            }},description="${step.title}，${if(step.completed) "已完成" else "未完成"}",
                            enabled=!busy && !remoteEnded && (step.completed || (!rootBlocked && step.id in ready)))
                        Column(Modifier.weight(1f).padding(vertical=6.dp).clickable {
                            editId=step.id;editTitle=step.title;editDue=step.due.orEmpty()
                            chosen=edges.filter {it.stepId==step.id}.mapTo(hashSetOf()) {it.prerequisiteId}
                            showBefore=false
                        }) {
                            Text(step.title,maxLines=2,overflow=TextOverflow.Ellipsis)
                            val before=edges.filter {it.stepId==step.id}.mapNotNull {edge->steps.firstOrNull {it.id==edge.prerequisiteId && !it.completed}}
                            val note=when {
                                step.completed->"已完成"
                                rootBlocked->"等待父任务前置"
                                before.isNotEmpty()->"等待："+before.joinToString("、") {it.title}
                                else->step.due?.let {"个人日期 $it"} ?: "现在可做"
                            }
                            Text(note,style=MaterialTheme.typography.labelSmall,color=XiaoxuPalette.Ink2,maxLines=2)
                        }
                        Box {
                            IconButton(onClick={menu=true},enabled=!busy) {Icon(Icons.Outlined.MoreVert,contentDescription="步骤操作：${step.title}")}
                            DropdownMenu(menu,onDismissRequest={menu=false}) {
                                DropdownMenuItem(text={Text("上移")},enabled=steps.first().id!=step.id,onClick={menu=false;perform {dao.moveStep(root,step.id,-1)}})
                                DropdownMenuItem(text={Text("下移")},enabled=steps.last().id!=step.id,onClick={menu=false;perform {dao.moveStep(root,step.id,1)}})
                                DropdownMenuItem(text={Text("删除步骤")},onClick={menu=false;deleting=step})
                            }
                        }
                    }
                }
            }
        }
        val undo=steps.firstOrNull {it.id==undoId && it.completed}
        if(undo!=null) TextButton(enabled=!busy && !remoteEnded,onClick={perform {dao.toggleStep(root,undo.id,false)}}) {Text("撤销完成 · ${undo.title}")}
        if(aiSuggest!=null && !remoteEnded) {
            TextButton(enabled=!busy && !aiBusy,onClick={aiExpanded=!aiExpanded}) {Text("AI 分解建议（可选）")}
            if(aiExpanded) {
                Text("点击生成才发送当前任务标题和补充说明到校序云端 AI；结果先预览，不会自动保存。",
                    style=MaterialTheme.typography.bodySmall,color=XiaoxuPalette.Ink2)
                OutlinedTextField(aiNotes,onValueChange={aiNotes=it.take(600)},label={Text("补充说明（可选）")},modifier=Modifier.fillMaxWidth(),enabled=!aiBusy)
                OutlinedButton(enabled=!busy && !aiBusy,onClick={
                    aiBusy=true;error=null
                    scope.launch {
                        try {
                            val result=aiSuggest(aiNotes)
                            if(result.error!=null) error=result.error else aiSuggestions=result.steps
                        } catch(cancelled:CancellationException) {throw cancelled}
                        catch(_:Exception) {error="AI 建议未生成，请重试或手动填写"}
                        finally {aiBusy=false}
                    }
                }) {Text(if(aiBusy) "生成中…" else "生成建议")}
                if(aiSuggestions.isNotEmpty()) {
                    Text("AI 建议 · 尚未保存",style=MaterialTheme.typography.labelMedium)
                    aiSuggestions.forEachIndexed {index,title->Text("${index+1}. $title",style=MaterialTheme.typography.bodySmall)}
                    TextButton(enabled=!busy && !aiBusy,onClick={
                        draft=aiSuggestions.joinToString("\n");adding=true;aiSuggestions=emptyList()
                    }) {Text(if(draft.isBlank()) "填入步骤编辑框" else "用建议替换当前草稿")}
                }
            }
        }
        if(adding) {
            OutlinedTextField(value=draft,onValueChange={draft=it.take(2400)},
                label={Text("每行一个步骤，最多 12 条")},minLines=2,maxLines=6,modifier=Modifier.fillMaxWidth())
            Row {
                TextButton(enabled=!busy,onClick={adding=false;draft=""}) {Text("取消")}
                Button(enabled=!busy && draft.isNotBlank() && !remoteEnded,onClick={perform {
                    val titles=draft.lines().map {it.trim()}.filter {it.isNotBlank()}
                    val saved=dao.addSteps(root,titles)
                    if(saved){draft="";adding=false};saved
                }}) {Text(if(busy) "保存中…" else "添加步骤")}
            }
        }
        val editing=steps.firstOrNull {it.id==editId}
        if(editing!=null) {
            OutlinedTextField(editTitle,onValueChange={editTitle=it.take(120)},label={Text("步骤名称")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(editDue,onValueChange={editDue=it.take(10)},label={Text("个人日期（YYYY-MM-DD，可留空）")},singleLine=true,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={showBefore=!showBefore}) {Text("前置步骤（${chosen.size}）")}
            if(showBefore) steps.filter {it.id!=editing.id}.forEach {candidate->
                val next=if(candidate.id in chosen) chosen-candidate.id else chosen+candidate.id
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(candidate.id in chosen,onCheckedChange={chosen=next},
                        enabled=!busy && (candidate.id in chosen || validStepPrerequisites(steps,edges,editing.id,next)))
                    Text(candidate.title,modifier=Modifier.weight(1f),maxLines=2)
                }
            }
            Row {
                TextButton(onClick={editId=null},enabled=!busy) {Text("取消")}
                Button(enabled=!busy,onClick={perform {
                    val saved=dao.saveStep(root,editing.id,editTitle.trim(),editDue.trim().ifBlank {null},editing.position,chosen)
                    if(saved)editId=null;saved
                }}) {Text("保存步骤")}
            }
        }
        val ended=executions.any {it.scope==root.scope && it.rootKey==root.key && it.ended}
        if(ended && !remoteEnded) {
            TextButton(enabled=!busy,onClick={perform {dao.reopen(root);true}}) {Text("重新打开个人步骤")}
        }
        if(steps.isNotEmpty() && steps.all {it.completed} && !rootDone && !ended) {
            Text("步骤已完成，待确认结束",style=MaterialTheme.typography.bodyMedium)
            Button(enabled=!busy && !rootBlocked,onClick={perform {
                if(dao.stepsFor(root.scope,root.key).let {it.isNotEmpty() && it.all {step->step.completed}} && onFinish()) dao.finish(root) else false
            }},modifier=Modifier.fillMaxWidth()) {Text("确认结束个人任务")}
        }
        if(error!=null) Text(error!!,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    }
    deleting?.let {step->
        AlertDialog(onDismissRequest={if(!busy)deleting=null},title={Text("删除步骤？")},
            text={Text("删除“${step.title}”也会解除其他步骤对它的前置要求。")},
            confirmButton={TextButton(enabled=!busy,onClick={perform {
                val saved=dao.removeStep(root,step.id);if(saved)deleting=null;saved
            }}){Text("删除")}},dismissButton={TextButton(onClick={deleting=null},enabled=!busy){Text("取消")}})
    }
}

@Composable
internal fun ExecutionStepListRow(
    step: TaskStep, parentTitle: String, progress: String, inheritedDate: String?,
    saving: Boolean, pendingChecked: Boolean?, onOpen: () -> Unit, onToggle: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=8.dp,vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically) {
        TaskCompletionCheckbox(checked=pendingChecked ?: step.completed,onCheckedChange=onToggle,
            description="完成步骤：${step.title}，所属任务：$parentTitle",enabled=!saving)
        Column(Modifier.weight(1f).clickable(onClick=onOpen).padding(vertical=6.dp)) {
            Text(step.title,style=MaterialTheme.typography.bodyLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("$parentTitle · $progress" + (inheritedDate?.let {" · $it"} ?: "") + if(saving) " · 保存中…" else "",
                style=MaterialTheme.typography.labelSmall,color=XiaoxuPalette.Ink2,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}
