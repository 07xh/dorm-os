package com.dormos.nfc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val Blue = Color(0xFF3869E9)
private val Ink = Color(0xFF162339)
private val Muted = Color(0xFF718197)
private val Cloud = Color(0xFFF6F8FC)
private val Line = Color(0xFFE8EDF5)
private val Mint = Color(0xFF13765A)

class MainActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: SceneStore
    private lateinit var nfc: NfcEngine
    private val scenes = mutableStateListOf<Scene>()
    private val logs = mutableStateListOf<String>()
    private val knownScripts = mutableStateListOf<ModuleGateway.ScriptEntry>()
    private var moduleStatus by mutableStateOf<ModuleGateway.ModuleStatus?>(null)
    private var page by mutableIntStateOf(0)
    private var stateText by mutableStateOf("正在检查设备")
    private var rootOk by mutableStateOf<Boolean?>(null)
    private var busy by mutableStateOf(false)
    private var scanActive by mutableStateOf(false)
    private var editScene by mutableStateOf<Scene?>(null)
    private var addScene by mutableStateOf(false)
    private var confirmRun by mutableStateOf<Scene?>(null)
    private var confirmWrite by mutableStateOf<Scene?>(null)
    private var showLogs by mutableStateOf(false)
    private var activeScene by mutableStateOf<Scene?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SceneStore(this)
        scenes.addAll(store.all())
        val p = getSharedPreferences("dorm_history_v2", MODE_PRIVATE)
        logs.addAll(p.getString("recent", "")!!.lines().filter { it.isNotBlank() }.take(30))
        nfc = NfcEngine(this) { event -> runOnUiThread {
            when (event) {
                is ScanEvent.Message -> {
                    stateText = event.text
                    addLog(event.text)
                    if (event.success) scanActive = false
                }
                is ScanEvent.Found -> {
                    scanActive = false
                    nfc.stop()
                    val scene = scenes.find { it.id == event.id }
                    if (scene == null) stateText = "未知标签：未找到绑定，请在场景中重新配置"
                    else if (!scene.enabled) stateText = "场景 ${scene.name} 已关闭，未执行"
                    else runScene(scene)
                }
            }
        } }
        setContent { DormTheme { AppUi() } }
        nfc.dispatch(intent)
        refreshDiagnostics()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        nfc.dispatch(intent)
    }
    override fun onResume() { super.onResume(); if (::nfc.isInitialized) nfc.onResume() }
    override fun onPause() { if (::nfc.isInitialized) nfc.onPause(); super.onPause() }
    override fun onDestroy() { if (::nfc.isInitialized) nfc.stop(); worker.shutdownNow(); super.onDestroy() }

    private fun refreshDiagnostics() {
        worker.execute {
            val status = ModuleGateway.inspect()
            val scripts = ModuleGateway.knownScripts()
            runOnUiThread {
                rootOk = status.rootGranted
                moduleStatus = status
                knownScripts.clear()
                knownScripts.addAll(scripts)
                stateText = if (!nfc.available()) "NFC 未开启或不受支持" else status.message
            }
        }
    }
    private fun addLog(message: String) {
        val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add(0, "$stamp  ${message.replace('\n', ' ').take(140)}")
        while (logs.size > 30) logs.removeAt(logs.lastIndex)
        getSharedPreferences("dorm_history_v2", MODE_PRIVATE).edit().putString("recent", logs.joinToString("\n")).apply()
    }
    private fun saveScenes(new: List<Scene>) {
        scenes.clear(); scenes.addAll(new)
        store.save(new)
    }
    private fun runScene(scene: Scene) {
        if (busy) { stateText = "已有操作执行中"; return }
        if (!scene.enabled) { stateText = "场景已停用"; return }
        busy = true
        stateText = "正在执行：${scene.name}"
        worker.execute {
            val result = ModuleGateway.execute(scene.action)
            runOnUiThread {
                busy = false
                stateText = "${if (result.ok) "成功" else "失败"} · ${scene.name}：${result.message}"
                addLog(stateText)
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun AppUi() {
        Scaffold(
            containerColor = Cloud,
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                    listOf("首页", "场景", "工具").forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = page == index, onClick = { page = index },
                            icon = { Text(listOf("⌂", "◇", "⚙")[index], fontSize = 22.sp) },
                            label = { Text(title) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Blue, selectedTextColor = Blue,
                                indicatorColor = Color(0xFFEAF0FF))
                        )
                    }
                }
            }
        ) { safe ->
            Column(Modifier.fillMaxSize().padding(safe).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DORM OS", color = Muted, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text(listOf("NFC 助手", "我的场景", "工具箱")[page], color = Ink, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    }
                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                        Text("V2 · 原生", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = Blue, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                when (page) { 0 -> Home(); 1 -> Scenes(); else -> Tools() }
            }
        }
        if (addScene || editScene != null) {
            val current = editScene
            Editor(current, onDismiss = { addScene = false; editScene = null }) { name, action ->
                if (current == null) {
                    if (scenes.size < 50) saveScenes(scenes.toList() + store.fresh(name, action))
                } else saveScenes(scenes.map { if (it.id == current.id) it.copy(name = name, action = action) else it })
                addScene = false; editScene = null
            }
        }
        confirmRun?.let { scene -> AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text("执行 ${scene.name}？") },
            text = { Text("将通过已授权的 KernelSU 运行模块预设动作「${AllowedActions.items[scene.action]}」。") },
            confirmButton = { Button(onClick = { confirmRun = null; runScene(scene) }) { Text("确认执行") } },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("取消") } }
        ) }
        confirmWrite?.let { scene -> AlertDialog(
            onDismissRequest = { confirmWrite = null },
            title = { Text("写入 NFC 标签") },
            text = { Text("将把「${scene.name}」写入可写 NDEF 标签。标签原有 NDEF 数据将被覆盖。") },
            confirmButton = { Button(onClick = {
                confirmWrite = null; scanActive = true
                stateText = "等待写入：${scene.name}。请将标签贴近手机背面"
                nfc.startWrite(scene.id)
            }) { Text("开始写入") } },
            dismissButton = { TextButton(onClick = { confirmWrite = null }) { Text("取消") } }
        ) }
        activeScene?.let { scene ->
            ModalBottomSheet(onDismissRequest = { activeScene = null }, containerColor = Color.White) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(scene.name, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(AllowedActions.items[scene.action].orEmpty(), color = Muted, fontSize = 13.sp)
                    Button(
                        onClick = { activeScene = null; confirmRun = scene },
                        enabled = scene.enabled && moduleStatus?.legacyReady == true && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("执行场景", Modifier.padding(vertical = 5.dp)) }
                    OutlinedButton(onClick = { activeScene = null; editScene = scene }, modifier = Modifier.fillMaxWidth()) { Text("编辑场景") }
                    TextButton(onClick = { activeScene = null; confirmWrite = scene }, enabled = scene.enabled, modifier = Modifier.fillMaxWidth()) { Text("写入 NFC 标签") }
                    if (moduleStatus?.legacyReady != true) Text("当前无法执行模块动作：请在「工具」查看安装状态", color = Muted, fontSize = 12.sp)
                }
            }
        }
        if (showLogs) AlertDialog(onDismissRequest = { showLogs = false },
            title = { Text("运行记录") },
            text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) Text("暂无本机运行记录", color = Muted)
                else logs.forEach { Text(it, fontSize = 12.sp, color = Ink, modifier = Modifier.padding(vertical = 6.dp)) }
            } },
            confirmButton = { TextButton(onClick = { showLogs = false }) { Text("关闭") } })
    }
    @Composable private fun Home() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFEAF0FF)) {
                Column(Modifier.fillMaxWidth().padding(23.dp)) {
                    Text("设备已连接", color = Blue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text("轻碰标签，立即触发", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("无需后台常驻 · NFC 事件触发", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = {
                        if (scanActive) { nfc.stop(); scanActive = false; stateText = "已取消 NFC 扫描" }
                        else { nfc.startRead(); scanActive = true; stateText = "正在等待 NFC 标签…" }
                    }, enabled = !busy, shape = RoundedCornerShape(14.dp)) {
                        Text(if (scanActive) "取消扫描" else "扫描 NFC 标签", modifier = Modifier.padding(vertical = 5.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile("NFC", if (nfc.available()) "已开启" else "不可用", Modifier.weight(1f))
                InfoTile("KernelSU Root", when (rootOk) { true -> "已授权"; false -> "未授权"; else -> "检查中" }, Modifier.weight(1f))
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text("最近状态", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stateText, color = Ink, fontSize = 14.sp, lineHeight = 21.sp)
                    if (busy) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                }
            }
            SectionTitle("常用场景", "全部", onClick = { page = 1 })
            scenes.take(3).forEach { s -> SceneRow(s, onManage = { activeScene = s }) }
            Spacer(Modifier.height(12.dp))
        }
    }
    @Composable private fun Scenes() {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("一个场景绑定一个动作", color = Muted, fontSize = 13.sp)
                TextButton(onClick = { addScene = true }) { Text("＋ 新建场景") }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(scenes, key = { it.id }) { s -> SceneRow(s, onManage = { activeScene = s }) }
                item { Text("场景名和动作可修改。写入过的标签保留场景 ID，无需每次重写。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
            }
        }
    }
    @Composable private fun Tools() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("脚本与诊断集中管理，校园网不再单独建页。", color = Muted, fontSize = 13.sp)
            UiPanel("KernelSU 与模块状态", "只检测本机模块；不自动执行脚本") {
                Text(moduleStatus?.message ?: "正在检查模块…", color = Ink, fontSize = 13.sp)
                TextButton(onClick = { refreshDiagnostics() }) { Text("重新检测") }
            }
            UiPanel("脚本清单", "参考你提供的模块脚本管理方式，当前仅展示已知脚本是否存在") {
                if (knownScripts.isEmpty()) Text("检查中或尚未授权 Root", color = Muted, fontSize = 13.sp)
                knownScripts.forEach { entry ->
                    HorizontalDivider(color = Line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Ink, fontSize = 14.sp)
                            Text(entry.module, color = Muted, fontSize = 11.sp)
                        }
                        Text(if (entry.installed) "已检测" else "未检测到", color = if (entry.installed) Mint else Muted, fontSize = 12.sp)
                    }
                }
            }
            UiPanel("固定场景动作", "仅旧版模块可以执行以下动作；新版 Bridge 不允许误调用") {
                if (moduleStatus?.legacyReady != true) {
                    Text("模块尚未就绪，操作已禁用", color = Muted, fontSize = 12.sp)
                }
                AllowedActions.items.forEach { (id, label) ->
                    HorizontalDivider(color = Line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), fontSize = 14.sp, color = Ink)
                        TextButton(onClick = { confirmRun = Scene(id, label, id) }, enabled = moduleStatus?.legacyReady == true && !busy) { Text("运行") }
                    }
                }
            }
            UiPanel("历史记录", "只保存最近 30 条本地状态，不上传") {
                TextButton(onClick = { showLogs = true }) { Text("查看运行记录 (${logs.size})") }
            }
            UiPanel("关于", "Android 16 / 一加 Ace 6 · 仅用户主动授权 Root") {
                Text("NFC 不需要后台常驻；非 NDEF 标签可能不支持写入。", color = Muted, fontSize = 12.sp)
            }
        }
    }
    @Composable private fun SceneRow(s: Scene, onManage: () -> Unit) {
        Surface(shape = RoundedCornerShape(19.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onManage).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text("◇", color = Blue, fontSize = 23.sp)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(AllowedActions.items[s.action].orEmpty(), color = Muted, fontSize = 12.sp)
                    Text("点击管理", color = Blue, fontSize = 11.sp)
                }
                Switch(checked = s.enabled, onCheckedChange = { checked ->
                    saveScenes(scenes.map { if (it.id == s.id) it.copy(enabled = checked) else it })
                })
            }
        }
    }
}

@Composable private fun DormTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White, surface = Color.White, onSurface = Ink, background = Cloud)) { content() }
}
@Composable private fun InfoTile(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(value, color = if (value == "已开启" || value == "已授权") Mint else Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}
@Composable private fun SectionTitle(title: String, more: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 18.sp, color = Ink, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClick) { Text(more) }
    }
}
@Composable private fun UiPanel(title: String, caption: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(caption, color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Editor(scene: Scene?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(scene) { mutableStateOf(scene?.name ?: "") }
    var action by remember(scene) { mutableStateOf(scene?.action ?: "study") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(if (scene == null) "创建场景" else "编辑场景", fontWeight = FontWeight.Bold, fontSize = 23.sp, color = Ink)
            Spacer(Modifier.height(15.dp))
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it }, label = { Text("场景名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(15.dp))
            Text("执行动作", color = Ink, fontWeight = FontWeight.Medium)
            AllowedActions.items.forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().clickable { action = id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = action == id, onClick = { action = id })
                    Text(label, fontSize = 14.sp, color = Ink)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("NFC 标签与场景 ID 绑定，动作会使用当前场景配置。", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(15.dp))
            Button(onClick = { onSave(name.trim(), action) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("保存场景", modifier = Modifier.padding(vertical = 7.dp))
            }
        }
    }
}
