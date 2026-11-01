package xyz.dormos.nfc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val sheetInk = Color(0xFF172234)
private val sheetMuted = Color(0xFF7A8798)
private val sheetBlue = Color(0xFF2F65E8)
private val sheetLine = Color(0xFFE8EDF4)

@Composable
internal fun V2BottomSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color(0x660D1728)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
                color = Color.White,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 21.dp, vertical = 13.dp)
                ) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).size(width = 38.dp, height = 4.dp)
                            .background(Color(0xFFDDE3EC), RoundedCornerShape(50))
                    )
                    Spacer(Modifier.height(17.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, color = sheetInk, fontSize = 20.sp,
                                fontWeight = FontWeight.Bold)
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(subtitle, color = sheetMuted, fontSize = 12.sp)
                            }
                        }
                        TextButton(onClick = onDismiss, contentPadding = PaddingValues(8.dp)) {
                            Text("关闭", color = sheetMuted)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    content()
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun V2SceneEditorSheet(
    initialName: String,
    initialScriptId: String?,
    scripts: List<PersonalScript>,
    editing: Boolean,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var scriptId by remember(initialScriptId) { mutableStateOf(initialScriptId ?: scripts.firstOrNull()?.id.orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    val selected = scripts.firstOrNull { it.id == scriptId }

    V2BottomSheet(
        title = if (editing) "编辑场景" else "新建场景",
        subtitle = "NFC 标签触发 · 使用本机 KernelSU SH",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("场景名称") },
            placeholder = { Text("例如：回到宿舍") },
            shape = RoundedCornerShape(14.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text("触发方式", color = sheetMuted, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        Surface(
            color = Color(0xFFF2F6FF),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDE7F8))
        ) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                V2NfcMark(Modifier.size(22.dp), sheetBlue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("NFC 标签", color = sheetInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("仅在读取到已绑定的实体标签时触发", color = sheetMuted, fontSize = 11.sp)
                }
                Text("已选", color = sheetBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("执行动作", color = sheetMuted, fontSize = 12.sp)
        Spacer(Modifier.height(7.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = scripts.isNotEmpty() && !busy,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(selected?.name ?: if (scripts.isEmpty()) "暂无 SH，请先创建或导入" else "选择 SH 脚本",
                    modifier = Modifier.weight(1f), color = sheetInk)
                Text("⌄", color = sheetMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                scripts.forEach { script ->
                    DropdownMenuItem(
                        text = { Text(script.name) },
                        onClick = { scriptId = script.id; expanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "场景名称与脚本绑定保存在 KernelSU 模块中。标签只写入随机编号，不写入账号、密码或 Shell 命令。",
            color = sheetMuted, fontSize = 11.sp, lineHeight = 17.sp
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onSave(name.trim(), scriptId) },
            modifier = Modifier.fillMaxWidth().height(49.dp),
            enabled = !busy && name.isNotBlank() && selected != null,
            shape = RoundedCornerShape(15.dp)
        ) { Text(if (busy) "处理中…" else if (editing) "保存修改" else "保存并绑定 NFC") }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(47.dp),
            enabled = !busy,
            shape = RoundedCornerShape(15.dp)
        ) { Text("取消") }
    }
}

@Composable
internal fun V2SceneDetailSheet(
    scene: PersonalScene,
    scriptName: String,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onUnbind: () -> Unit,
    onDismiss: () -> Unit
) {
    V2BottomSheet(title = scene.name, subtitle = "NFC 场景详情", onDismiss = onDismiss) {
        Surface(
            color = Color(0xFFF4F7FC),
            shape = RoundedCornerShape(17.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, sheetLine)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("触发方式", color = sheetMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text("NFC 标签", color = sheetInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                HorizontalDivider(color = sheetLine)
                Spacer(Modifier.height(13.dp))
                Text("执行动作", color = sheetMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(scriptName, color = sheetInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(13.dp))
                HorizontalDivider(color = sheetLine)
                Spacer(Modifier.height(13.dp))
                Text("标签编号", color = sheetMuted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(scene.tagId, color = sheetInk, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(13.dp))
        Surface(color = Color(0xFFF8FAFD), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("启用这个场景", color = sheetInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(if (scene.enabled) "标签触发时允许执行脚本" else "已停用，标签不会触发脚本",
                        color = sheetMuted, fontSize = 11.sp)
                }
                Switch(checked = scene.enabled, onCheckedChange = onToggle, enabled = !busy)
            }
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(
                onClick = onEdit, modifier = Modifier.weight(1f),
                enabled = !busy, shape = RoundedCornerShape(14.dp)
            ) { Text("编辑") }
            Button(
                onClick = onTest, modifier = Modifier.weight(1f),
                enabled = !busy && scene.enabled, shape = RoundedCornerShape(14.dp)
            ) { Text("执行测试") }
        }
        TextButton(onClick = onUnbind, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) {
            Text("解除 NFC 绑定", color = Color(0xFFB44C59))
        }
    }
}

@Composable
internal fun V2RunConfirmSheet(
    scriptName: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    V2BottomSheet("确认执行脚本", "KernelSU Root 权限操作", onDismiss) {
        Surface(color = Color(0xFFFFF6E9), shape = RoundedCornerShape(15.dp)) {
            Text(
                "即将执行「$scriptName」。请确认这是你自己编写或检查过的脚本。执行结果将以 Root 模块实际返回值为准。",
                modifier = Modifier.padding(14.dp), color = Color(0xFF80572D),
                fontSize = 12.sp, lineHeight = 18.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onConfirm, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(49.dp),
            shape = RoundedCornerShape(15.dp)) { Text(if (busy) "正在执行…" else "确认执行") }
        OutlinedButton(onClick = onDismiss, enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(47.dp),
            shape = RoundedCornerShape(15.dp)) { Text("取消") }
    }
}

@Composable
internal fun V2LogsSheet(
    logs: String,
    output: String,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    V2BottomSheet("运行记录", "显示 KernelSU 模块返回的真实执行记录", onDismiss) {
        Text(
            logs.ifBlank { "暂无执行记录" },
            color = sheetInk, fontSize = 12.sp, lineHeight = 19.sp
        )
        if (output.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text("最近输出", color = sheetMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(output.takeLast(5000), color = sheetInk, fontSize = 11.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)) { Text("刷新") }
            TextButton(onClick = onClear, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)) { Text("清空记录", color = Color(0xFFB44C59)) }
        }
    }
}

@Composable
internal fun V2ThemeSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    V2BottomSheet("外观与交互", "选择 DORM NFC 的显示风格", onDismiss) {
        listOf(
            Triple("default", "一加简约", "蓝白配色 · 清晰主操作"),
            Triple("night", "深色控制台", "低光环境 · 深色背景"),
            Triple("editorial", "暖白极简", "柔和背景 · 克制强调")
        ).forEach { (id, title, desc) ->
            val active = selected == id
            Surface(
                onClick = { onSelect(id) },
                color = if (active) Color(0xFFEAF0FF) else Color.White,
                shape = RoundedCornerShape(15.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (active) Color(0xFFB9CCF2) else sheetLine
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, color = sheetInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(desc, color = sheetMuted, fontSize = 11.sp)
                    }
                    if (active) Text("✓", color = sheetBlue, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)) { Text("完成") }
    }
}
