package xyz.dormos.nfc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.nfc.NfcAdapter
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val canvas = Color(0xFFF7F8FC)
private val panel = Color.White
private val tint = Color(0xFFE2EBFF)
private val ink = Color(0xFF17253B)
private val dim = Color(0xFF718099)
private val actionBlue = Color(0xFF2865D8)
private val paleBorder = Color(0xFFE7ECF5)

class ManagerActivity : ComponentActivity() {
    private val bridge = RootModuleClient()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = actionBlue,
                    background = canvas,
                    surface = panel,
                    onSurface = ink,
                    secondary = tint
                )
            ) {
                ManagerScreen(
                    bridge = bridge,
                    openTagInspector = { startActivity(Intent(this, TagInspectorActivity::class.java)) },
                    openNdefWriter = { startActivity(Intent(this, NdefWriterActivity::class.java)) },
                    openNfcPreferences = {
                        if (Build.VERSION.SDK_INT >= 36) {
                            try { startActivity(Intent(NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE)) }
                            catch (_: Exception) { Toast.makeText(this, "请到系统设置 → 应用 → 特殊应用权限 → 通过 NFC 启动", Toast.LENGTH_LONG).show() }
                        } else Toast.makeText(this, "仅 Android 16 或更新版本提供此入口", Toast.LENGTH_SHORT).show()
                    },
                    notify = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
                    tagWriteIntent = { uuid ->
                        Intent(this, MainActivity::class.java)
                            .setAction(MainActivity.ACTION_WRITE_TAG)
                            .putExtra(MainActivity.EXTRA_SCENE_ID, uuid)
                    },
                    importFile = { uri, callback ->
                        try {
                            val name = contentResolver.query(
                                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                            )?.use { cursor ->
                                if (cursor.moveToFirst()) cursor.getString(0) else null
                            } ?: throw IllegalArgumentException("无法读取文件名")
                            if (!name.endsWith(".sh") ||
                                name.contains('/') || name.contains('\\')) {
                                throw IllegalArgumentException("只允许导入 .sh 文件")
                            }
                            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                                val result = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                while (result.size() <= 16384) {
                                    val count = input.read(buffer)
                                    if (count <= 0) break
                                    result.write(buffer, 0, count)
                                }
                                result.toByteArray()
                            } ?: throw IllegalStateException("无法打开文件")
                            if (bytes.size > 16384) throw IllegalArgumentException("脚本超过 16 KiB")
                            callback(name, String(bytes, Charsets.UTF_8))
                        } catch (e: Exception) {
                            Toast.makeText(this, "导入失败：${e.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ManagerScreen(
    bridge: RootModuleClient,
    openTagInspector: () -> Unit,
    openNdefWriter: () -> Unit,
    openNfcPreferences: () -> Unit,
    notify: (String) -> Unit,
    tagWriteIntent: (String) -> Intent,
    importFile: (android.net.Uri, (String, String) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    var current by rememberSaveable { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf(emptyList<PersonalScript>()) }
    var folders by remember { mutableStateOf(emptyList<PersonalFolder>()) }
    var scriptLocations by remember { mutableStateOf(emptyMap<String, String>()) }
    var activeFolder by rememberSaveable { mutableStateOf("root") }
    var folderDialog by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var movingScript by remember { mutableStateOf<PersonalScript?>(null) }
    var movingFolder by remember { mutableStateOf<PersonalFolder?>(null) }
    var renamingScript by remember { mutableStateOf<PersonalScript?>(null) }
    var previewScript by remember { mutableStateOf<PersonalScript?>(null) }
    var previewContent by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf<Pair<String, String>?>(null) }
    var confirmClearLogs by remember { mutableStateOf(false) }
    var editingNew by remember { mutableStateOf(true) }
    var installed by remember { mutableStateOf(false) }
    var receiverEnabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在检查模块…") }
    var output by remember { mutableStateOf("暂无执行记录") }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var editCode by remember { mutableStateOf("#!/system/bin/sh\n") }
    var confirmDelete by remember { mutableStateOf<PersonalScript?>(null) }
    var confirmRun by remember { mutableStateOf<PersonalScript?>(null) }
    var confirmBind by remember { mutableStateOf<PersonalScript?>(null) }
    var bindings by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf("") }
    var chooseSceneScript by remember { mutableStateOf(false) }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val info = bridge.status()
                    val list = bridge.listScripts()
                    val links = bridge.listBindings()
                    val history = bridge.logs()
                    Triple(listOf(info, list.second, links, history), list.first,
                        Pair(bridge.listFolders(), bridge.scriptFolders()))
                }
                val info = result.first[0]
                status = info.output
                installed = info.success && info.output.contains("DORM_MODULE=ready")
                receiverEnabled = info.output.contains("NFC_ENABLED=1")
                entries = result.second
                folders = result.third.first
                scriptLocations = result.third.second
                bindings = result.first[2].output
                logs = result.first[3].output
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = "模块检查失败：" + (error.message ?: error.javaClass.simpleName)
                notify(status)
            } finally {
                loading = false
            }
        }
    }

    fun runRoot(action: () -> RootResult, after: (RootResult) -> Unit = {}) {
        if (loading) return
        scope.launch {
            loading = true
            val result = try {
                withContext(Dispatchers.IO) { action() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                RootResult(70, "Root 操作异常：" + (error.message ?: error.javaClass.simpleName))
            }
            output = result.output.ifBlank { if (result.success) "执行完成" else "执行失败（${result.code}）" }
            val detail = result.output.lineSequence().firstOrNull { it.startsWith("DORM_") }
                ?: result.output.take(100).ifBlank { "执行失败（${result.code}）" }
            notify(if (result.success) "操作成功" else detail)
            loading = false
            after(result)
        }
    }

    fun openEditor(item: PersonalScript?) {
        if (item == null) {
            editId = bridge.newId()
            editingNew = true
            editName = "新建脚本.sh"
            editCode = "#!/system/bin/sh\n"
            editing = true
        } else {
            editId = item.id
            editingNew = false
            editName = item.name
            scope.launch {
                val pair = withContext(Dispatchers.IO) { bridge.readScript(item.id) }
                if (pair.first != null) {
                    editCode = pair.first!!
                    editing = true
                } else notify("读取失败：" + pair.second.output)
            }
        }
    }

    var pendingTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingScript by rememberSaveable { mutableStateOf<String?>(null) }
    val writeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val tag = pendingTag
        val script = pendingScript
        pendingTag = null
        pendingScript = null
        if (tag != null && script != null &&
            NfcProfiles.bindingMayCommit(
                tag, script, result.data?.getStringExtra(MainActivity.EXTRA_SCENE_ID),
                result.resultCode == Activity.RESULT_OK
            )) {
            val previousTag = result.data?.getStringExtra(MainActivity.EXTRA_PREVIOUS_TAG_ID)
            val oldBinding = NfcProfiles.previousBindingToRemove(previousTag, tag)
            runRoot({
                val bound = bridge.bind(tag, script)
                if (!bound.success || oldBinding == null) bound
                else {
                    val removed = bridge.unbind(oldBinding)
                    if (removed.success) RootResult(0, "新绑定成功，旧标签关联已清理。")
                    else RootResult(70, "新绑定已建立，但旧标签关联清理失败：" + removed.output)
                }
            }) { refresh() }
        } else {
            notify("写卡未通过验证，本次没有登记 NFC 绑定。")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importFile(uri) { name, code ->
                editId = bridge.newId()
                editingNew = true
                editName = name
                editCode = code
                editing = true
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().background(canvas)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (current) {
                            0 -> {
                                val hour = java.util.Calendar.getInstance()
                                    .get(java.util.Calendar.HOUR_OF_DAY)
                                when (hour) {
                                    in 5..11 -> "早上好"
                                    in 12..17 -> "下午好"
                                    else -> "晚上好"
                                }
                            }
                            1 -> "实用工具"
                            2 -> "我的场景"
                            else -> "设备设置"
                        },
                        color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (current) {
                            0 -> "DORM OS · NFC 助手"
                            1 -> "脚本、标签读写集中管理"
                            2 -> "按 NFC 标签组织你的 SH 自动化"
                            else -> "权限、执行记录与配置"
                        },
                        color = dim, fontSize = 12.sp
                    )
                }
                Surface(
                    onClick = { if (current == 3) current = 0 else current = 3 },
                    color = Color.White,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, paleBorder)
                ) {
                    Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                        if (current == 3) {
                            Text("‹", color = ink, fontSize = 26.sp)
                        } else {
                            V2SettingsIcon(ink)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val nfcEnabledNow =
                NfcAdapter.getDefaultAdapter(androidx.compose.ui.platform.LocalContext.current)
                    ?.isEnabled == true
            val sceneBindings = bindings.lineSequence().mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size != 2 || !NfcProfiles.validId(parts[0])) null
                else parts[0] to (entries.firstOrNull { it.id == parts[1] }?.name ?: "未知脚本")
            }.toList()
            when (current) {
                0 -> {
                    V2Home(
                        nfcOn = nfcEnabledNow,
                        rootReady = installed,
                        rootHint = status,
                        scenes = sceneBindings,
                        onScan = openTagInspector,
                        onScenes = { current = 2 },
                        onScene = { tagId, scriptName -> selectedTag = tagId to scriptName }
                    )
                }
                1 -> {
                    V2ToolsHeading(onScan = openTagInspector, onWrite = openNdefWriter)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("搜索脚本") },
                            modifier = Modifier.weight(1f),
                            singleLine = true, shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Button(onClick = { openEditor(null) }, enabled = !loading,
                            shape = RoundedCornerShape(14.dp)) { Text("+ 新建") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val folderTitle = folders.firstOrNull { it.id == activeFolder }?.name ?: "根目录"
                        TextButton(onClick = {
                            activeFolder = folders.firstOrNull { it.id == activeFolder }?.parent ?: "root"
                        }, enabled = activeFolder != "root") { Text("← 返回") }
                        Text("当前位置：$folderTitle", modifier = Modifier.weight(1f), color = dim, maxLines = 1)
                        TextButton(onClick = { folderName = ""; folderDialog = "create" }) { Text("+ 文件夹") }
                    }
                    folders.filter { it.parent == activeFolder }
                        .sortedBy { it.name }.forEach { dir ->
                            Section(null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📁 ${dir.name}", modifier = Modifier.weight(1f)
                                        .clickable { activeFolder = dir.id }, color = ink)
                                    TextButton(onClick = {
                                        folderName = dir.name; folderDialog = "rename:${dir.id}"
                                    }) { Text("改名") }
                                    TextButton(onClick = { movingFolder = dir }) { Text("移动") }
                                    TextButton(onClick = { folderDialog = "delete:${dir.id}" }) { Text("删除") }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                        enabled = !loading, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("↓ 导入本机 .sh 脚本") }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("我的脚本", modifier = Modifier.weight(1f), color = ink,
                            fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("${entries.size} 个", color = dim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    val shown = entries.filter { item ->
                        val location = scriptLocations[item.id] ?: "root"
                        (query.isNotBlank() || location == activeFolder) &&
                            (item.name.contains(query, ignoreCase = true) ||
                             (folders.firstOrNull { it.id == location }?.name?.contains(query, ignoreCase = true) == true))
                    }
                    if (shown.isEmpty()) Section("脚本列表") { Text("暂无脚本。可新建或导入自己的 .sh 文件。", color = dim) }
                    shown.forEach { item ->
                        var moreOpen by remember(item.id) { mutableStateOf(false) }
                        Section(null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoundedIcon("▤")
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = ink, fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(4.dp))
                                    Text("${item.bytes} B  ·  个人 SH", color = dim, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { confirmRun = item },
                                    modifier = Modifier.weight(1f), enabled = !loading,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                                    Text("运行", fontSize = 13.sp)
                                }
                                Button(onClick = { confirmBind = item },
                                    modifier = Modifier.weight(1f), enabled = !loading,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)) {
                                    Text("绑 NFC", fontSize = 13.sp)
                                }
                                Box {
                                    OutlinedButton(onClick = { moreOpen = true },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text("更多", fontSize = 13.sp)
                                    }
                                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                        DropdownMenuItem(text = { Text("编辑脚本") }, onClick = {
                                            moreOpen = false
                                            openEditor(item)
                                        })
                                        DropdownMenuItem(text = { Text("查看内容") }, onClick = {
                                            moreOpen = false
                                            previewScript = item
                                            previewContent = "正在读取…"
                                            scope.launch {
                                                val data = withContext(Dispatchers.IO) { bridge.readScript(item.id) }
                                                previewContent = data.first ?: data.second.output
                                            }
                                        })
                                        DropdownMenuItem(text = { Text("重命名") }, onClick = {
                                            moreOpen = false
                                            renamingScript = item
                                            folderName = item.name
                                        })
                                        DropdownMenuItem(text = { Text("移动到文件夹") }, onClick = {
                                            moreOpen = false
                                            movingScript = item
                                        })
                                        HorizontalDivider(color = paleBorder)
                                        DropdownMenuItem(text = { Text("删除脚本", color = Color(0xFFBD5360)) }, onClick = {
                                            moreOpen = false
                                            confirmDelete = item
                                        })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                2 -> {
                    V2Scenes(
                        scenes = sceneBindings,
                        enabled = receiverEnabled,
                        rootReady = installed,
                        busy = loading,
                        onEnable = { on ->
                            runRoot({ bridge.enableNfc(on) }) {
                                if (it.success) { receiverEnabled = on; refresh() }
                            }
                        },
                        onNew = {
                            if (entries.isEmpty()) {
                                current = 1
                                openEditor(null)
                            } else {
                                chooseSceneScript = true
                            }
                        },
                        onOpen = { tagId, scriptName -> selectedTag = tagId to scriptName }
                    )
                }
                else -> {
                    Section("个人配置") {
                        SummaryRow("⌘", "Root 框架", "仅支持 KernelSU", if (installed) "正常" else "未连接") { refresh() }
                        HorizontalDivider(color = canvas)
                        SummaryRow("◉", "NFC 状态", "由 Android NFC 系统分发", if (receiverEnabled) "已开启" else "已关闭") { current = 2 }
                        HorizontalDivider(color = canvas)
                        SummaryRow("▤", "执行记录", "只保留时间、脚本 ID 和结果", "›") { output = logs.ifBlank { "暂无记录" } }
                    }
                    Spacer(Modifier.height(12.dp))
                    Section("最近执行输出") { Text(output.takeLast(9000), color = dim, fontSize = 12.sp) }
                    Spacer(Modifier.height(12.dp))
                    Section("执行日志（最近 80 条）") {
                        Text(ExecutionLogFormatter.format(logs, entries),
                            color = dim, fontSize = 12.sp)
                        TextButton(onClick = { confirmClearLogs = true }, enabled = !loading) { Text("清空日志") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Section("Android 16 / 耗电验证") {
                        Text("事件驱动，无模块后台轮询；本机待机耗电尚未测量。", color = dim, fontSize = 13.sp)
                        Text("请检查系统特殊应用权限中的「通过 NFC 启动」，并分别实测锁屏与息屏触发。", color = dim, fontSize = 12.sp)
                        TextButton(onClick = openNfcPreferences) { Text("打开 NFC 启动权限设置") }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { refresh() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                        Text("刷新模块状态")
                    }
                }
            }
        }
        NavigationBar(
            containerColor = Color.White,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                Triple(0, "⌂", "首页"),
                Triple(2, "▦", "场景"),
                Triple(1, "⚒", "工具")
            ).forEach { entry ->
                NavigationBarItem(
                    selected = current == entry.first,
                    onClick = { current = entry.first },
                    icon = {
                        V2NavIcon(
                            which = when (entry.first) { 0 -> 0; 2 -> 1; else -> 2 },
                            color = if (current == entry.first) actionBlue else dim
                        )
                    },
                    label = { Text(entry.third, fontSize = 11.sp) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = actionBlue,
                        selectedTextColor = actionBlue,
                        indicatorColor = Color(0xFFE9F0FF),
                        unselectedIconColor = dim,
                        unselectedTextColor = dim
                    )
                )
            }
        }
    }

    if (chooseSceneScript) {
        AlertDialog(
            onDismissRequest = { chooseSceneScript = false },
            title = { Text("选择要绑定的 SH") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    Text("新场景将写入随机 NFC ID，脚本保存在 KernelSU 保护目录。",
                        color = dim, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    entries.forEach { item ->
                        TextButton(onClick = {
                            chooseSceneScript = false
                            confirmBind = item
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(item.name, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { chooseSceneScript = false }) { Text("取消") }
            }
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("SH 脚本编辑器") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it },
                        label = { Text("名称") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = editCode, onValueChange = { editCode = it },
                        label = { Text("脚本内容") }, minLines = 10, maxLines = 17,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                    Text("行数 ${editCode.lineSequence().count()} · ${editCode.toByteArray().size} B / 16384 B",
                        fontSize = 11.sp, color = dim)
                    Text("脚本将以 KernelSU Root 权限执行。请仅保存自己检查过的内容。", fontSize = 11.sp, color = dim)
                }
            },
            confirmButton = {
                TextButton(enabled = !loading, onClick = {
                    val creating = editingNew
                    val folder = activeFolder
                    runRoot({
                        val saved = bridge.saveScript(editId, editName, editCode)
                        if (saved.success && creating && folder != "root") bridge.moveScript(editId, folder)
                        else saved
                    }) { if (it.success) { editing = false; refresh() } }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } }
        )
    }
    confirmRun?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text("确认 Root 执行") },
            text = { Text("运行「${item.name}」？脚本将获得 Root 权限，请确认你信任其内容。") },
            confirmButton = {
                TextButton(onClick = { confirmRun = null; runRoot({ bridge.runScript(item.id) }) }) { Text("运行") }
            }, dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("取消") } }
        )
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除脚本") },
            text = { Text("确定删除「${item.name}」及其 NFC 绑定？") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; runRoot({ bridge.deleteScript(item.id) }) { refresh() } }) { Text("删除") }
            }, dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } }
        )
    }
    confirmBind?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmBind = null },
            title = { Text("绑定 NFC") },
            text = { Text("将「${item.name}」绑定到你自己的 NFC 标签。碰卡会以 Root 权限执行脚本。注意：NFC 标签内容可被复制，不是身份认证；请只绑定你已检查且可重复安全运行的脚本。确认继续？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBind = null
                    val tag = bridge.newId()
                    pendingTag = tag
                    pendingScript = item.id
                    try {
                        writeLauncher.launch(tagWriteIntent(tag))
                    } catch (error: Exception) {
                        pendingTag = null
                        pendingScript = null
                        notify("无法启动 NFC 写卡：" + (error.message ?: "请检查 NFC 是否开启"))
                    }
                }) { Text("绑定并写卡") }
            }, dismissButton = { TextButton(onClick = { confirmBind = null }) { Text("取消") } }
        )
    }

    if (folderDialog.isNotBlank()) {
        val action = folderDialog.substringBefore(':')
        val id = folderDialog.substringAfter(':', "")
        AlertDialog(
            onDismissRequest = { folderDialog = "" },
            title = { Text(when (action) { "create" -> "新建文件夹"; "rename" -> "重命名文件夹"; else -> "删除文件夹" }) },
            text = {
                Column {
                    if (action != "delete") {
                        OutlinedTextField(value = folderName, onValueChange = { folderName = it },
                            label = { Text("名称") }, singleLine = true)
                    } else {
                        Text("仅允许删除空文件夹。请先移动其中的脚本和子文件夹。")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = folderName.trim()
                    val parent = activeFolder
                    folderDialog = ""
                    runRoot({
                        when (action) {
                            "create" -> bridge.createFolder(bridge.newId(), parent, name)
                            "rename" -> bridge.renameFolder(id, name)
                            else -> bridge.deleteFolder(id)
                        }
                    }) { if (it.success) refresh() }
                }, enabled = !loading) { Text(if (action == "delete") "删除" else "保存") }
            },
            dismissButton = { TextButton(onClick = { folderDialog = "" }) { Text("取消") } }
        )
    }
    renamingScript?.let { item ->
        AlertDialog(
            onDismissRequest = { renamingScript = null },
            title = { Text("脚本改名") },
            text = { OutlinedTextField(value = folderName, onValueChange = { folderName = it },
                label = { Text("新名称（.sh）") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = folderName.trim()
                    renamingScript = null
                    runRoot({ bridge.renameScript(item.id, name) }) { if (it.success) refresh() }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamingScript = null }) { Text("取消") } }
        )
    }
    movingScript?.let { item ->
        AlertDialog(
            onDismissRequest = { movingScript = null },
            title = { Text("移动「${item.name}」") },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    (listOf(PersonalFolder("root", "", "根目录")) + folders).forEach { folder ->
                        TextButton(onClick = {
                            movingScript = null
                            runRoot({ bridge.moveScript(item.id, folder.id) }) { if (it.success) refresh() }
                        }) { Text("📁 ${folder.name}") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { movingScript = null }) { Text("取消") } }
        )
    }
    movingFolder?.let { item ->
        AlertDialog(
            onDismissRequest = { movingFolder = null },
            title = { Text("移动文件夹「${item.name}」") },
            text = {
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    (listOf(PersonalFolder("root", "", "根目录")) + folders)
                        .filter { it.id != item.id }.forEach { destination ->
                            TextButton(onClick = {
                                movingFolder = null
                                runRoot({ bridge.moveFolder(item.id, destination.id) }) {
                                    if (it.success) { activeFolder = "root"; refresh() }
                                }
                            }) { Text("📁 ${destination.name}") }
                        }
                    Text("不允许移动到自身或自己的子文件夹。", fontSize = 11.sp, color = dim)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { movingFolder = null }) { Text("取消") } }
        )
    }

    previewScript?.let { item ->
        AlertDialog(
            onDismissRequest = { previewScript = null },
            title = { Text("预览：${item.name}") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text("只读预览 · ${item.bytes} B · 不执行", fontSize = 12.sp, color = dim)
                    HorizontalDivider()
                    Text(previewContent.take(16384), fontSize = 12.sp, color = ink)
                }
            },
            confirmButton = { TextButton(onClick = { previewScript = null; openEditor(item) }) { Text("编辑") } },
            dismissButton = { TextButton(onClick = { previewScript = null }) { Text("关闭") } }
        )
    }
    selectedTag?.let { selected ->
        AlertDialog(
            onDismissRequest = { selectedTag = null },
            title = { Text("NFC 标签详情") },
            text = { Text("关联脚本：${selected.second}\n标签 ID：${selected.first}\n\n标签可被复制，不是身份认证；请勿绑定高风险脚本。") },
            confirmButton = { TextButton(onClick = {
                selectedTag = null
                runRoot({ bridge.unbind(selected.first) }) { if (it.success) refresh() }
            }) { Text("解除绑定") } },
            dismissButton = { TextButton(onClick = { selectedTag = null }) { Text("关闭") } }
        )
    }
    if (confirmClearLogs) {
        AlertDialog(
            onDismissRequest = { confirmClearLogs = false },
            title = { Text("清空执行日志") },
            text = { Text("仅清空日志，不删除任何脚本或标签绑定。") },
            confirmButton = { TextButton(onClick = {
                confirmClearLogs = false
                runRoot({ bridge.clearLogs() }) { if (it.success) refresh() }
            }) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmClearLogs = false }) { Text("取消") } }
        )
    }

}


@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(title, color = actionBlue, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 10.dp, bottom = 11.dp), fontSize = 15.sp)
    }
    Surface(
        shape = RoundedCornerShape(20.dp), color = panel,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, paleBorder)
    ) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun RoundedIcon(symbol: String) {
    Surface(shape = RoundedCornerShape(15.dp), color = tint) {
        Text(symbol, fontSize = 23.sp, color = actionBlue,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
private fun MiniAction(symbol: String, onClick: () -> Unit) {
    Text(symbol, color = actionBlue, fontSize = 18.sp, modifier = Modifier
        .padding(start = 4.dp).background(Color(0xffe7eafa), RoundedCornerShape(20.dp))
        .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp))
}

@Composable
private fun SummaryRow(icon: String, title: String, subtitle: String, trailing: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RoundedIcon(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = dim, fontSize = 12.sp, maxLines = 2)
        }
        Text(trailing, color = actionBlue)
    }
}
