package xyz.dormos.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.UUID

/** Compact Compose action picker, not a platform select popup or a new navigation page. */
@Composable
internal fun NativeScenesSection(
    scripts: List<PersonalScript>,
    onWriteNfc: (String) -> Unit,
    notify: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember { NativeSceneStore(context) }
    var scenes by remember { mutableStateOf(store.list()) }
    var editing by remember { mutableStateOf<NativeScene?>(null) }
    var rootApproval by remember { mutableStateOf<NativeScene?>(null) }
    val p = LocalV2Palette.current
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("原生动作场景", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("复制 · 打开应用 · SH · 人工确认", fontSize = 11.sp, color = p.muted)
        }
        OutlinedButton(
            onClick = {
                editing = NativeScene("native-" + UUID.randomUUID().toString(), "新建场景",
                    listOf(NativeStep(NativeKind.COPY, "填写需要复制的文本")))
            }, shape = RoundedCornerShape(12.dp)
        ) { Text("＋ 新建", fontSize = 13.sp) }
    }
    Spacer(Modifier.height(8.dp))
    Surface(color = p.surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, p.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
            if (scenes.isEmpty()) {
                Text("暂无原生场景", modifier = Modifier.padding(12.dp), color = p.muted)
            }
            scenes.forEachIndexed { index, scene ->
                if (index > 0) HorizontalDivider(color = p.line)
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { editing = scene }) {
                            Text(scene.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
                            Text(scene.actions.joinToString(" → ") { it.kind.title },
                                fontSize = 11.sp, color = p.muted, maxLines = 2)
                        }
                        TextButton(onClick = { editing = scene }) { Text("编辑") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { onWriteNfc(NativeSceneRules.uri(scene.id)) }) { Text("写 NFC") }
                        TextButton(onClick = {
                            if (scene.actions.any { it.kind == NativeKind.SH }) rootApproval = scene
                            else NativeSceneRunner.run(context, scene, false, notify)
                        }) { Text("测试") }
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("TapFlow 场景", NativeSceneRules.uri(scene.id)))
                            notify("已复制标签 URI")
                        }) { Text("复制 URI") }
                    }
                }
            }
        }
    }
    Text("原生场景存于本机；旧 KernelSU 场景独立保留。微信消息始终由你手动发送。",
        fontSize = 11.sp, color = p.muted,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp))
    editing?.let { scene ->
        NativeSceneEditorSheet(
            scene = scene, scripts = scripts,
            onSave = { changed ->
                if (store.save(changed)) {
                    scenes = store.list()
                    editing = null
                    notify("原生场景已保存")
                } else notify("场景内容无效或保存失败，请检查动作顺序")
            },
            onDelete = {
                if (store.delete(scene.id)) {
                    scenes = store.list()
                    editing = null
                } else notify("删除失败")
            },
            onDismiss = { editing = null }
        )
    }
    rootApproval?.let { scene ->
        AlertDialog(
            onDismissRequest = { rootApproval = null },
            title = { Text("确认运行 SH") },
            text = { Text("仅执行当前场景中明确绑定的已保存脚本，不运行 NFC 标签里的命令。请确认你已审核该脚本。") },
            confirmButton = {
                TextButton(onClick = {
                    rootApproval = null
                    NativeSceneRunner.run(context, scene, true, notify)
                }) { Text("确认执行") }
            },
            dismissButton = { TextButton(onClick = { rootApproval = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun NativeSceneEditorSheet(
    scene: NativeScene,
    scripts: List<PersonalScript>,
    onSave: (NativeScene) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = LocalV2Palette.current
    var name by remember(scene.id) { mutableStateOf(scene.name) }
    var actions by remember(scene.id) { mutableStateOf(scene.actions) }
    var selecting by remember { mutableIntStateOf(-1) }
    var deletePrompt by remember { mutableStateOf(false) }
    V2BottomSheet("编辑原生动作", "触碰 NFC 后按顺序执行 · 不需要无障碍", onDismiss) {
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= 50) name = it },
            label = { Text("场景名称") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(NativeSceneRules.uri(scene.id), color = p.muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        actions.forEachIndexed { index, action ->
            Surface(color = p.accentLow, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((index + 1).toString(), color = p.blue, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp))
                        Text(action.kind.title, color = p.ink, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { selecting = index }) { Text("更换") }
                        TextButton(onClick = {
                            if (index > 0) {
                                val copy = actions.toMutableList()
                                val x = copy.removeAt(index)
                                copy.add(index - 1, x)
                                actions = copy
                            }
                        }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = {
                            if (index < actions.lastIndex) {
                                val copy = actions.toMutableList()
                                val x = copy.removeAt(index)
                                copy.add(index + 1, x)
                                actions = copy
                            }
                        }, enabled = index < actions.lastIndex) { Text("↓") }
                        TextButton(onClick = { actions = actions.filterIndexed { i, _ -> i != index } }) {
                            Text("×")
                        }
                    }
                    if (action.kind == NativeKind.CHAT_DRAFT) {
                        // Split without validation so fields stay editable while partially empty.
                        val parts = action.value.split('\n', limit = 3)
                        val currentDraft = ChatDraft(
                            parts.getOrElse(0) { "com.tencent.mm" },
                            parts.getOrElse(1) { "" },
                            parts.getOrElse(2) { "" }
                        )
                        fun changeDraft(updated: ChatDraft) {
                            actions = actions.toMutableList().apply {
                                set(index, action.copy(value = ChatDraftCodec.encode(updated)))
                            }
                        }
                        OutlinedTextField(
                            value = currentDraft.appPackage,
                            onValueChange = { if (it.length <= 130) changeDraft(currentDraft.copy(appPackage = it)) },
                            label = { Text("聊天应用包名") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = currentDraft.chatName,
                            onValueChange = { if (it.length <= 80) changeDraft(currentDraft.copy(chatName = it)) },
                            label = { Text("群聊或联系人名称") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = currentDraft.message,
                            onValueChange = { if (it.length <= 1700) changeDraft(currentDraft.copy(message = it)) },
                            label = { Text("消息草稿（仅准备，不自动发送）") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            maxLines = 4
                        )
                        Text("当前将复制草稿并尝试交给聊天应用；自动识别聊天窗口仍需真机验收。",
                            fontSize = 11.sp, color = p.muted)
                    } else {
                        OutlinedTextField(
                            value = action.value,
                            onValueChange = { value ->
                                if (value.length <= 2000) actions = actions.toMutableList().apply {
                                    set(index, action.copy(value = value))
                                }
                            },
                            label = { Text(if (action.kind == NativeKind.SH) "已保存脚本 UUID" else "动作内容") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            maxLines = 3
                        )
                    }
                    if (action.kind == NativeKind.SH && scripts.isNotEmpty()) {
                        Text("已保存脚本：", color = p.muted, fontSize = 11.sp)
                        scripts.take(8).forEach { script ->
                            TextButton(onClick = {
                                actions = actions.toMutableList().apply {
                                    set(index, action.copy(value = script.id))
                                }
                            }) { Text(script.name, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { if (actions.size < 12) selecting = actions.size },
            modifier = Modifier.fillMaxWidth(), enabled = actions.size < 12,
            shape = RoundedCornerShape(13.dp)
        ) { Text("＋ 添加动作") }
        Spacer(Modifier.height(8.dp))
        Text("打开外部 App 或链接后，只能继续保留人工提示；SH 需要在 App 内确认，碰卡不会自动运行。",
            fontSize = 11.sp, color = p.muted)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onSave(scene.copy(name = name.trim(), actions = actions)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = NativeSceneRules.valid(scene.copy(name = name.trim(), actions = actions)),
            shape = RoundedCornerShape(14.dp)
        ) { Text("保存场景") }
        TextButton(onClick = { deletePrompt = true }, modifier = Modifier.fillMaxWidth()) {
            Text("删除场景", color = androidx.compose.ui.graphics.Color(0xFFB14455))
        }
    }
    if (selecting >= 0) {
        val which = selecting
        Dialog(onDismissRequest = { selecting = -1 }) {
            Surface(shape = RoundedCornerShape(22.dp), color = p.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("选择动作", color = p.ink, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(7.dp))
                    NativeKind.entries.forEach { kind ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                val defaultValue = when (kind) {
                                    NativeKind.COPY -> "请输入文字"
                                    NativeKind.APP -> "com.tencent.mm"
                                    NativeKind.URL -> "https://example.com"
                                    NativeKind.SH -> scripts.firstOrNull()?.id ?: ""
                                    NativeKind.CHAT_DRAFT -> ChatDraftCodec.encode(ChatDraft("com.tencent.mm", "目标群聊名称", "请输入消息"))
                                    NativeKind.MANUAL -> "检查内容并手动确认"
                                }
                                val list = actions.toMutableList()
                                if (which == list.size) list.add(NativeStep(kind, defaultValue))
                                else if (which < list.size) list[which] = NativeStep(kind, defaultValue)
                                actions = list
                                selecting = -1
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(kind.title, color = p.ink, fontSize = 15.sp)
                                Text(kind.hint, color = p.muted, fontSize = 11.sp)
                            }
                            Text("›", fontSize = 22.sp, color = p.blue)
                        }
                        if (kind != NativeKind.MANUAL) HorizontalDivider(color = p.line)
                    }
                }
            }
        }
    }
    if (deletePrompt) AlertDialog(
        onDismissRequest = { deletePrompt = false },
        title = { Text("删除原生场景？") },
        text = { Text("只删除本机配置，不会格式化实体 NFC 标签，也不影响旧 SH 场景。") },
        confirmButton = { TextButton(onClick = { deletePrompt = false; onDelete() }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deletePrompt = false }) { Text("取消") } }
    )
}
