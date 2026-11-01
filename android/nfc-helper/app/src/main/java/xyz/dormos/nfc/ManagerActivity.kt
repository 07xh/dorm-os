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
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val canvas = Color(0xFFF7F8FC)
private val panel = Color.White
private val tint = Color(0xFFE2EBFF)
private val ink = Color(0xFF17253B)
private val dim = Color(0xFF718099)
private val actionBlue = Color(0xFF2865D8)
private val paleBorder = Color(0xFFE7ECF5)

private fun readZipEntryLimited(input: ZipInputStream, maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IllegalArgumentException("单个 SH 文件超过 16 KiB")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
private data class ManagerSnapshot(
    val status: RootResult,
    val scripts: List<PersonalScript>,
    val scenes: List<PersonalScene>,
    val logs: RootResult,
    val folders: List<PersonalFolder>,
    val scriptFolders: Map<String, String>
)

class ManagerActivity : ComponentActivity() {
    private val bridge = RootModuleClient()
    private lateinit var inlineScanner: NfcInlineScanner
    private lateinit var inlineWriter: NfcSceneWriteSession
    private lateinit var inlineNdefWriter: NdefWriteSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inlineScanner = NfcInlineScanner(this)
        inlineWriter = NfcSceneWriteSession(this)
        inlineNdefWriter = NdefWriteSession(this)
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
                    inlineScanner = inlineScanner,
                    inlineWriter = inlineWriter,
                    inlineNdefWriter = inlineNdefWriter,
                    openNfcPreferences = {
                        if (Build.VERSION.SDK_INT >= 36) {
                            try { startActivity(Intent(NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE)) }
                            catch (_: Exception) { Toast.makeText(this, "请到系统设置 → 应用 → 特殊应用权限 → 通过 NFC 启动", Toast.LENGTH_LONG).show() }
                        } else Toast.makeText(this, "仅 Android 16 或更新版本提供此入口", Toast.LENGTH_SHORT).show()
                    },
                    notify = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
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

    override fun onResume() {
        super.onResume()
        if (::inlineScanner.isInitialized) inlineScanner.onResume()
        if (::inlineWriter.isInitialized) inlineWriter.onResume()
        if (::inlineNdefWriter.isInitialized) inlineNdefWriter.onResume()
    }

    override fun onPause() {
        if (::inlineScanner.isInitialized) inlineScanner.onPause()
        if (::inlineWriter.isInitialized) inlineWriter.onPause()
        if (::inlineNdefWriter.isInitialized) inlineNdefWriter.onPause()
        super.onPause()
    }
}

@Composable
private fun ManagerScreen(
    bridge: RootModuleClient,
    inlineScanner: NfcInlineScanner,
    inlineWriter: NfcSceneWriteSession,
    inlineNdefWriter: NdefWriteSession,
    openNfcPreferences: () -> Unit,
    notify: (String) -> Unit,
    importFile: (android.net.Uri, (String, String) -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Pager owns both gesture and button navigation, avoiding desynchronized tabs.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val current = tabIdForPage(pagerState.currentPage)
    fun navigateToTab(tabId: Int) {
        val page = tabNavigationOrder(tabId)
        if (pagerState.targetPage == page && !pagerState.isScrollInProgress) return
        scope.launch { pagerState.animateScrollToPage(page) }
    }
    // Each tab owns its scroll position: leaving a long tools list must not
    // reopen the short home page halfway down or apparently empty.
    val homeScroll = rememberScrollState()
    val scenesScroll = rememberScrollState()
    val toolsScroll = rememberScrollState()
    var entries by remember { mutableStateOf(emptyList<PersonalScript>()) }
    var folders by remember { mutableStateOf(emptyList<PersonalFolder>()) }
    var scriptLocations by remember { mutableStateOf(emptyMap<String, String>()) }
    var activeFolder by rememberSaveable { mutableStateOf("root") }
    var showFolders by rememberSaveable { mutableStateOf(false) }
    var folderDialog by remember { mutableStateOf("") }
    var folderName by remember { mutableStateOf("") }
    var movingScript by remember { mutableStateOf<PersonalScript?>(null) }
    var movingFolder by remember { mutableStateOf<PersonalFolder?>(null) }
    var renamingScript by remember { mutableStateOf<PersonalScript?>(null) }
    var previewScript by remember { mutableStateOf<PersonalScript?>(null) }
    var previewContent by remember { mutableStateOf("") }
    var scenes by remember { mutableStateOf(emptyList<PersonalScene>()) }
    var selectedScene by remember { mutableStateOf<PersonalScene?>(null) }
    var sceneFilter by rememberSaveable { mutableStateOf("全部") }
    var sceneEditorOpen by remember { mutableStateOf(false) }
    var editingScene by remember { mutableStateOf<PersonalScene?>(null) }
    var sceneNameDraft by rememberSaveable { mutableStateOf("") }
    var sceneScriptDraft by rememberSaveable { mutableStateOf("") }
    var confirmUnbindScene by remember { mutableStateOf<PersonalScene?>(null) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLogsSheet by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }
    var showInlineScan by remember { mutableStateOf(false) }
    var showNdefWrite by remember { mutableStateOf(false) }
    var pendingSceneWrite by remember { mutableStateOf<SceneWriteRequest?>(null) }
    var selectedTheme by rememberSaveable {
        mutableStateOf(
            context.getSharedPreferences("dorm_nfc_view", 0).getString("theme", "default")
                ?.takeIf { it in setOf("default", "night", "editorial") } ?: "default"
        )
    }
    var navStyle by rememberSaveable {
        mutableStateOf(
            context.getSharedPreferences("dorm_nfc_view", 0)
                .getString("nav_style", "classic")
                ?.takeIf { it in setOf("classic", "glass") } ?: "classic"
        )
    }
    var confirmClearLogs by remember { mutableStateOf(false) }
    var editingNew by remember { mutableStateOf(true) }
    var installed by remember { mutableStateOf(false) }
    var receiverEnabled by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("正在检查模块…") }
    var output by remember { mutableStateOf("暂无执行记录") }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var showCreateScript by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var editCode by remember { mutableStateOf("#!/system/bin/sh\n") }
    var confirmDelete by remember { mutableStateOf<PersonalScript?>(null) }
    var confirmRun by remember { mutableStateOf<PersonalScript?>(null) }
    var confirmBind by remember { mutableStateOf<PersonalScript?>(null) }
    var logs by remember { mutableStateOf("") }

    fun openInlineScanner() {
        if (pendingSceneWrite != null || showNdefWrite) {
            notify("请先完成或关闭当前 NFC 操作")
            return
        }
        inlineScanner.start()
        showInlineScan = true
    }

    fun openNdefWriter() {
        if (pendingSceneWrite != null || showInlineScan) {
            notify("请先完成或关闭当前 NFC 操作")
            return
        }
        if (inlineNdefWriter.start()) showNdefWrite = true
        else notify("上一项 NFC 写入仍在处理中，请先确认标签状态")
    }

    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val info = bridge.status()
                    val list = bridge.listScripts()
                    val sceneList = bridge.listScenes()
                    val history = bridge.logs()
                    ManagerSnapshot(
                        status = info,
                        scripts = list.first,
                        scenes = sceneList.first,
                        logs = history,
                        folders = bridge.listFolders(),
                        scriptFolders = bridge.scriptFolders()
                    )
                }
                status = snapshot.status.output
                installed = snapshot.status.success && snapshot.status.output.contains("DORM_MODULE=ready")
                receiverEnabled = snapshot.status.output.contains("NFC_ENABLED=1")
                entries = snapshot.scripts
                scenes = snapshot.scenes
                folders = snapshot.folders
                scriptLocations = snapshot.scriptFolders
                logs = snapshot.logs.output
                selectedScene?.let { currentScene ->
                    selectedScene = scenes.firstOrNull { it.tagId == currentScene.tagId }
                }
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

    fun beginScriptCreation() {
        showCreateScript = true
    }

    fun openSceneEditor(scene: PersonalScene? = null) {
        editingScene = scene
        sceneNameDraft = scene?.name ?: ""
        sceneScriptDraft = scene?.scriptId ?: entries.firstOrNull()?.id.orEmpty()
        sceneEditorOpen = true
    }

    fun beginSceneCreation() {
        if (entries.isEmpty()) {
            navigateToTab(1)
            beginScriptCreation()
            notify("先创建或导入一个 SH 脚本，再回来绑定 NFC 场景。")
        } else {
            openSceneEditor(null)
        }
    }

    fun beginSceneWrite(scriptId: String, sceneName: String) {
        if (pendingSceneWrite != null || loading) {
            notify("请先完成当前 NFC 操作")
            return
        }
        if (!NfcProfiles.validId(scriptId) || sceneName.isBlank()) {
            notify("请选择有效的 SH 脚本和场景名称")
            return
        }
        // A single Activity can have only one active ReaderMode session.
        if (showNdefWrite) {
            if (!inlineNdefWriter.stop()) {
                notify("普通 NFC 写入仍在处理中，暂不能切换到场景绑定")
                return
            }
            showNdefWrite = false
        }
        inlineScanner.stop()
        showInlineScan = false
        val request = SceneWriteRequest(
            tagId = bridge.newId(),
            scriptId = scriptId,
            sceneName = sceneName
        )
        val accepted = inlineWriter.begin(request) { verifiedRequest, outcome ->
            if (!SceneWritePolicy.mayCommit(outcome.state) ||
                !NfcProfiles.bindingMayCommit(
                    verifiedRequest.tagId, verifiedRequest.scriptId,
                    verifiedRequest.tagId, outcome.verified
                )) {
                inlineWriter.markRegistered(false, "回读验证失败，没有登记任何脚本绑定")
            } else if (loading) {
                inlineWriter.markRegistered(
                    false, "KernelSU 正在处理其他操作；标签已回读，但尚未登记绑定，请重新贴卡验证"
                )
            } else {
                val previous = NfcProfiles.previousBindingToRemove(
                    outcome.previousTagId, verifiedRequest.tagId
                )
                runRoot({
                    val bound = bridge.bind(verifiedRequest.tagId, verifiedRequest.scriptId)
                    if (!bound.success) {
                        bound
                    } else {
                        val named = bridge.saveScene(
                            verifiedRequest.tagId, verifiedRequest.sceneName
                        )
                        if (!named.success) {
                            val reversed = bridge.unbind(verifiedRequest.tagId)
                            if (reversed.success) {
                                RootResult(70, "标签已写入，但保存场景失败。本机绑定已撤销：" + named.output)
                            } else {
                                RootResult(70, "标签已写入，但场景登记失败且回滚未成功。请检查绑定：" +
                                    named.output + " / " + reversed.output)
                            }
                        } else if (previous == null) {
                            RootResult(0, "NFC 标签回读与场景登记全部成功")
                        } else {
                            val removed = bridge.unbind(previous)
                            if (removed.success) RootResult(0, "已建立新场景，旧标签绑定已清理")
                            else RootResult(70, "新场景已写入，但旧绑定清理失败，请手动处理：" + removed.output)
                        }
                    }
                }) { saved ->
                    inlineWriter.markRegistered(
                        saved.success,
                        if (saved.success) "标签和 KernelSU 场景绑定均已验证"
                        else "写卡可能已成功，但脚本绑定未完整签收：" + saved.output.take(180)
                    )
                    refresh()
                }
            }
        }
        if (accepted) pendingSceneWrite = request
        else notify("无法开启本次写卡，请先完成或取消上一项操作")
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

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                try {
                    val scripts = withContext(Dispatchers.IO) {
                        entries.map { script ->
                            val (body, response) = bridge.readScript(script.id)
                            if (!response.success || body == null) {
                                throw IllegalStateException("无法读取脚本「${script.name}」，备份已停止。")
                            }
                            script to body
                        }
                    }
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: throw IllegalStateException("无法创建备份文件")
                        ZipOutputStream(stream).use { zip ->
                            zip.putNextEntry(ZipEntry("manifest.txt"))
                            zip.write("DORMOS-NFC-SH-BACKUP/1\n".toByteArray(Charsets.UTF_8))
                            zip.closeEntry()
                            scripts.forEach { (script, body) ->
                                zip.putNextEntry(ZipEntry("scripts/${script.id}__${script.name}"))
                                zip.write(body.toByteArray(Charsets.UTF_8))
                                zip.closeEntry()
                            }
                        }
                    }
                    notify("已导出 ${scripts.size} 个 SH 脚本。")
                } catch (error: Exception) {
                    notify("备份导出失败：" + (error.message ?: error.javaClass.simpleName))
                } finally {
                    loading = false
                }
            }
        }
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loading = true
                try {
                    val summary = withContext(Dispatchers.IO) {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("无法打开备份文件")
                        ZipInputStream(input).use { zip ->
                            val manifest = zip.nextEntry
                                ?: throw IllegalArgumentException("备份文件为空")
                            if (manifest.name != "manifest.txt") {
                                throw IllegalArgumentException("不是触序 SH 脚本备份")
                            }
                            val header = String(readZipEntryLimited(zip, 1024), Charsets.UTF_8).trim()
                            zip.closeEntry()
                            if (header != "DORMOS-NFC-SH-BACKUP/1") {
                                throw IllegalArgumentException("备份版本不受支持")
                            }
                            var imported = 0
                            var skipped = 0
                            val pending = mutableListOf<Pair<String, String>>()
                            var totalBytes = 0
                            var processed = 0
                            var entry = zip.nextEntry
                            while (entry != null) {
                                processed++
                                if (processed > 100) throw IllegalArgumentException("备份条目超过 100 个")
                                if (!entry.isDirectory) {
                                    val path = entry.name
                                    val fileName = path.removePrefix("scripts/")
                                    val validPath = path.startsWith("scripts/") &&
                                        !path.contains("..") && !path.contains('\\') &&
                                        fileName.contains("__")
                                    val name = if (validPath) fileName.substringAfter("__") else ""
                                    val idPart = if (validPath) fileName.substringBefore("__") else ""
                                    if (!validPath || !ScriptRules.idValid(idPart) || !name.endsWith(".sh") ||
                                        name.contains('/') || name.contains('\\') || name.contains('|')) {
                                        skipped++
                                        readZipEntryLimited(zip, 16384)
                                    } else {
                                        val bytes = readZipEntryLimited(zip, 16384)
                                        totalBytes += bytes.size
                                        if (totalBytes > 2 * 1024 * 1024) {
                                            throw IllegalArgumentException("备份总大小超过 2 MiB")
                                        }
                                        val body = Charsets.UTF_8.newDecoder()
                                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                                        pending.add(name to body)
                                    }
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                            // Validate the complete archive before changing the Root script library.
                            // Malformed/truncated ZIP data must never produce an unnoticed partial restore.
                            pending.forEach { (name, body) ->
                                val saved = bridge.saveScript(bridge.newId(), name, body)
                                if (saved.success) imported++ else skipped++
                            }
                            "已导入 ${imported} 个脚本，跳过 ${skipped} 个无效条目"
                        }
                    }
                    notify(summary)
                } catch (error: Exception) {
                    notify("备份导入失败：" + (error.message ?: error.javaClass.simpleName))
                } finally {
                    loading = false
                    refresh()
                }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val p = v2Palette(selectedTheme)
    val pageCanvas = p.background
    val headerInk = p.ink
    val headerMuted = p.muted
    val scheme = if (selectedTheme == "night") {
        darkColorScheme(
            primary = p.blue, onPrimary = Color(0xFF102135),
            background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.accentLow, onSurfaceVariant = p.muted,
            outline = p.line
        )
    } else {
        lightColorScheme(
            primary = p.blue, onPrimary = Color.White,
            background = p.background, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
            surfaceVariant = p.accentLow, onSurfaceVariant = p.muted,
            outline = p.line
        )
    }
    CompositionLocalProvider(LocalV2Palette provides p) {
    MaterialTheme(colorScheme = scheme) {
    // Android 16 edge-to-edge: keep the clock and network icons above our title.
    SideEffect {
        if (Build.VERSION.SDK_INT >= 30) {
            val mask = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            (context as? Activity)?.window?.insetsController?.setSystemBarsAppearance(
                if (selectedTheme == "night") 0 else mask, mask
            )
        }
    }
    Column(Modifier.fillMaxSize().background(pageCanvas).statusBarsPadding()) {
        // Resolve page data once per state change, not once for each outgoing
        // and incoming AnimatedContent instance during a rapid tab switch.
        val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
        val nfcEnabledNow = nfcAdapter?.isEnabled == true
        val scriptNames = remember(entries) { entries.associate { it.id to it.name } }
        val homeScenes = remember(scenes, scriptNames) {
            scenes.filter { it.enabled }.map { scene ->
                Triple(scene.tagId, scene.name, scriptNames[scene.scriptId] ?: "未知脚本")
            }
        }
        val shownScripts = remember(entries, scriptLocations, showFolders, query, activeFolder, folders) {
            val folderNames = folders.associate { it.id to it.name }
            entries.filter { item ->
                val location = scriptLocations[item.id] ?: "root"
                (!showFolders || query.isNotBlank() || location == activeFolder) &&
                    (item.name.contains(query, ignoreCase = true) ||
                     folderNames[location]?.contains(query, ignoreCase = true) == true)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true,
            // There are only three top-level tabs. Keep the adjacent pages
            // composed before the gesture starts so their first composition
            // and measurement do not land on the swipe's animation frames.
            // This trades a small, bounded amount of idle memory for a
            // consistently warm page transition.
            beyondViewportPageCount = 1,
            key = { page -> tabIdForPage(page) }
        ) { page ->
        val visibleTab = tabIdForPage(page)
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(when (visibleTab) {
                    0 -> homeScroll
                    2 -> scenesScroll
                    else -> toolsScroll
                })
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (visibleTab) {
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
                            else -> "我的场景"
                        },
                        color = headerInk, fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (visibleTab) {
                            0 -> stringResource(R.string.app_brand)
                            1 -> "脚本、标签读写集中管理"
                            2 -> "按 NFC 标签组织你的 SH 自动化"
                            else -> "按触发方式组织你的 NFC 自动化"
                        },
                        color = headerMuted, fontSize = 12.sp
                    )
                }
                Surface(
                    onClick = {
                        when (visibleTab) {
                            0 -> showThemeSheet = true
                            1 -> showLogsSheet = true
                            2 -> beginSceneCreation()
                        }
                    },
                    color = p.surface,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, p.line)
                ) {
                    Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                        when (visibleTab) {
                            0 -> V2SettingsIcon(p.ink)
                            1 -> Text("☷", color = p.ink, fontSize = 22.sp)
                            else -> Text("+", color = p.ink, fontSize = 25.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            when (visibleTab) {
                0 -> {
                    V2Home(
                        nfcOn = nfcEnabledNow,
                        rootReady = installed,
                        rootHint = status,
                        scenes = homeScenes,
                        onScan = { openInlineScanner() },
                        onScenes = { navigateToTab(2) },
                        onScene = { tagId, _ -> selectedScene = scenes.firstOrNull { it.tagId == tagId } }
                    )
                }
                1 -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("脚本文件", modifier = Modifier.weight(1f),
                            color = p.ink, fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold)
                        TextButton(
                            onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                            enabled = !loading
                        ) { Text("导入") }
                        Spacer(Modifier.width(7.dp))
                        Button(onClick = { beginScriptCreation() }, enabled = !loading,
                            shape = RoundedCornerShape(14.dp)) { Text("+ 新建") }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("搜索脚本名称或文件夹") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                TextButton(onClick = { query = "" }) { Text("清空") }
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("共 ${entries.size} 个 SH", modifier = Modifier.weight(1f),
                            color = p.muted, fontSize = 12.sp)
                        TextButton(onClick = { showFolders = !showFolders }) {
                            Text(if (showFolders) "收起文件夹" else "文件夹管理")
                        }
                    }
                    if (!installed) {
                        Surface(
                            color = p.accentLow,
                            shape = RoundedCornerShape(15.dp),
                            border = BorderStroke(1.dp, p.line)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("KernelSU 尚未连接", color = p.ink,
                                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("先在 KernelSU 授权 App 并安装模块，才能保存或运行脚本。",
                                        color = p.muted, fontSize = 11.sp, lineHeight = 16.sp)
                                }
                                TextButton(onClick = { refresh() }, enabled = !loading) {
                                    Text("重试")
                                }
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                    }
                    if (showFolders) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val folderTitle = folders.firstOrNull { it.id == activeFolder }?.name ?: "根目录"
                            TextButton(onClick = {
                                activeFolder = folders.firstOrNull { it.id == activeFolder }?.parent ?: "root"
                            }, enabled = activeFolder != "root") { Text("← 返回") }
                            Text("当前位置：$folderTitle", modifier = Modifier.weight(1f), color = p.muted, maxLines = 1)
                            TextButton(onClick = { folderName = ""; folderDialog = "create" }) { Text("+ 文件夹") }
                        }
                        folders.filter { it.parent == activeFolder }
                            .sortedBy { it.name }.forEach { dir ->
                                var folderMenu by remember(dir.id) { mutableStateOf(false) }
                                Surface(color = p.surface, shape = RoundedCornerShape(17.dp),
                                    border = BorderStroke(1.dp, p.line)) {
                                    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                        .padding(start = 13.dp, end = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("▣", color = p.blue, fontSize = 20.sp)
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            dir.name,
                                            modifier = Modifier.weight(1f)
                                                .clickable { activeFolder = dir.id }
                                                .padding(vertical = 15.dp),
                                            color = p.ink,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                        Box {
                                            TextButton(onClick = { folderMenu = true },
                                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                                contentPadding = PaddingValues(0.dp)) {
                                                Text("⋯", color = p.muted, fontSize = 22.sp)
                                            }
                                            DropdownMenu(
                                                expanded = folderMenu,
                                                onDismissRequest = { folderMenu = false }
                                            ) {
                                                DropdownMenuItem(text = { Text("重命名") }, onClick = {
                                                    folderMenu = false
                                                    folderName = dir.name
                                                    folderDialog = "rename:${dir.id}"
                                                })
                                                DropdownMenuItem(text = { Text("移动文件夹") }, onClick = {
                                                    folderMenu = false
                                                    movingFolder = dir
                                                })
                                                HorizontalDivider(color = p.line)
                                                DropdownMenuItem(
                                                    text = { Text("删除文件夹", color = Color(0xFFBD5360)) },
                                                    onClick = {
                                                        folderMenu = false
                                                        folderDialog = "delete:${dir.id}"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                    }
                    Spacer(Modifier.height(10.dp))
                    val shown = shownScripts
                    Surface(
                        color = p.surface,
                        shape = RoundedCornerShape(19.dp),
                        border = BorderStroke(1.dp, p.line)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
                            if (shown.isEmpty()) {
                                Column(Modifier.padding(vertical = 19.dp)) {
                                    Text("暂无脚本", color = p.ink, fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(5.dp))
                                    Text("新建或导入自己的 .sh 文件后，可在这里运行或绑定 NFC。",
                                        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            } else {
                                shown.forEachIndexed { index, item ->
                                    var moreOpen by remember(item.id) { mutableStateOf(false) }
                                    if (index > 0) HorizontalDivider(color = p.line)
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(42.dp).background(p.iconBackground, RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) { Text("▤", color = p.blue, fontSize = 22.sp) }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, color = p.ink, fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis)
                                            Spacer(Modifier.height(4.dp))
                                            val folder = folders.firstOrNull { it.id == (scriptLocations[item.id] ?: "root") }?.name
                                            Text(
                                                "/${folder ?: "根目录"}/${item.name} · ${item.bytes} B",
                                                color = p.muted, fontSize = 11.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            onClick = { confirmRun = item },
                                            color = p.accentLow,
                                            shape = RoundedCornerShape(13.dp),
                                            enabled = !loading
                                        ) {
                                            Box(Modifier.size(39.dp), contentAlignment = Alignment.Center) {
                                                Text("⚡", color = p.blue, fontSize = 19.sp)
                                            }
                                        }
                                        Box {
                                            TextButton(onClick = { moreOpen = true },
                                                contentPadding = PaddingValues(horizontal = 7.dp)) {
                                                Text("⋯", color = p.muted, fontSize = 21.sp)
                                            }
                                            DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                                                DropdownMenuItem(text = { Text("绑定 NFC") }, onClick = {
                                                    moreOpen = false
                                                    confirmBind = item
                                                })
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
                                                HorizontalDivider(color = p.line)
                                                DropdownMenuItem(text = { Text("删除脚本", color = Color(0xFFBD5360)) }, onClick = {
                                                    moreOpen = false
                                                    confirmDelete = item
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Section("管理") {
                        SummaryRow(0, "运行记录", "查看真实触发与执行结果", "›") {
                            showLogsSheet = true
                        }
                        HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 66.dp))
                        SummaryRow(1, "配置与备份", "导出或恢复本机 SH 脚本", "›") {
                            showBackupSheet = true
                        }
                        HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 66.dp))
                        SummaryRow(2, "外观与交互", "主题与界面设置", "›") {
                            showThemeSheet = true
                        }
                        HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 66.dp))
                        SummaryRow(3, "KernelSU 状态", "检查 Root 模块与授权", if (installed) "正常" else "检查") {
                            refresh()
                        }
                        HorizontalDivider(color = p.line, modifier = Modifier.padding(start = 66.dp))
                        SummaryRow(4, "NFC 系统设置", "管理 Android NFC 开关与权限", "›") {
                            openNfcPreferences()
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    V2ToolsHeading(onScan = { openInlineScanner() }, onWrite = { openNdefWriter() })
                }
                2 -> {
                    V2Scenes(
                        scenes = scenes,
                        scriptNames = scriptNames,
                        filter = sceneFilter,
                        onFilter = { sceneFilter = it },
                        enabled = receiverEnabled,
                        rootReady = installed,
                        busy = loading,
                        onEnable = { on ->
                            runRoot({ bridge.enableNfc(on) }) {
                                if (it.success) { receiverEnabled = on; refresh() }
                            }
                        },
                        onNew = { beginSceneCreation() },
                        onOpen = { scene, _ -> selectedScene = scene }
                    )
                }
            }
        }
        }
        if (navStyle == "glass") {
            TapFlowGlassNavigation(
                selected = current,
                pagerState = pagerState,
                palette = p,
                night = selectedTheme == "night",
                onSelect = { navigateToTab(it) }
            )
        } else {
            NavigationBar(
                containerColor = p.surface,
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
                        onClick = { navigateToTab(entry.first) },
                        icon = {
                            val selected = current == entry.first
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.08f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "TapFlow selected navigation icon"
                            )
                            V2NavIcon(
                                which = when (entry.first) { 0 -> 0; 2 -> 1; else -> 2 },
                                color = if (selected) p.blue else p.muted,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            )
                        },
                        label = {
                            Text(
                                entry.third,
                                fontSize = 11.sp,
                                fontWeight = if (current == entry.first) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = p.blue,
                            selectedTextColor = p.blue,
                            indicatorColor = p.accentLow,
                            unselectedIconColor = p.muted,
                            unselectedTextColor = p.muted
                        )
                    )
                }
            }
        }
    }

    // Dialogs must inherit the same theme as the main three-tab workspace.
    CompositionLocalProvider(LocalV2Palette provides p) {
        MaterialTheme(colorScheme = scheme) {
    pendingSceneWrite?.let { request ->
        val scriptName = entries.firstOrNull { it.id == request.scriptId }?.name
            ?: "已选择的 SH 脚本"
        NfcSceneWriteSheet(
            sceneName = request.sceneName,
            scriptName = scriptName,
            session = inlineWriter,
            onDismiss = {
                if (inlineWriter.stop()) {
                    pendingSceneWrite = null
                    refresh()
                }
            },
            onReadTag = {
                if (inlineWriter.stop()) {
                    pendingSceneWrite = null
                    inlineScanner.start()
                    showInlineScan = true
                } else notify("写卡尚未结束，请保留标签并核对结果")
            },
            onNfcSettings = {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                } catch (_: Exception) {
                    notify("未找到系统 NFC 设置入口，请到系统设置手动开启")
                }
            }
        )
    }

    if (showNdefWrite) {
        NdefWriteSheet(
            session = inlineNdefWriter,
            onDismiss = {
                if (inlineNdefWriter.stop()) showNdefWrite = false
                else notify("写入或回读尚未结束，暂不能关闭")
            },
            onReadTag = {
                if (inlineNdefWriter.stop()) {
                    showNdefWrite = false
                    inlineScanner.start()
                    showInlineScan = true
                } else notify("写入或回读尚未结束，请先保持标签贴合")
            },
            onNfcSettings = {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                } catch (_: Exception) {
                    notify("未找到系统 NFC 设置入口，请到系统设置手动开启")
                }
            }
        )
    }

    if (showInlineScan) {
        NfcInlineScanSheet(
            scanner = inlineScanner,
            onDismiss = {
                inlineScanner.stop()
                showInlineScan = false
            },
            onNfcSettings = {
                try {
                    context.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                } catch (_: Exception) {
                    notify("找不到 NFC 设置入口，请到系统设置中手动开启 NFC")
                }
            }
        )
    }

    if (showCreateScript) {
        V2CreateScriptSheet(
            onContinue = { chosenName, template ->
                showCreateScript = false
                openEditor(null)
                editName = chosenName
                if (template == "device") {
                    editCode = "#!/system/bin/sh\nprintf 'Android: '\ngetprop ro.build.version.release\nprintf 'Device: '\ngetprop ro.product.model\n"
                }
            },
            onDismiss = { showCreateScript = false }
        )
    }

    if (editing) {
        V2ScriptEditorSheet(
            name = editName,
            code = editCode,
            isNew = editingNew,
            busy = loading,
            onNameChange = { editName = it },
            onCodeChange = { editCode = it },
            onSave = {
                val creating = editingNew
                val folder = activeFolder
                runRoot({
                    val saved = bridge.saveScript(editId, editName.trim(), editCode)
                    if (saved.success && creating && folder != "root") bridge.moveScript(editId, folder)
                    else saved
                }) { if (it.success) { editing = false; refresh() } }
            },
            onDismiss = { if (!loading) editing = false }
        )
    }
    confirmRun?.let { item ->
        V2RunConfirmSheet(
            scriptName = item.name,
            busy = loading,
            onConfirm = {
                confirmRun = null
                runRoot({ bridge.runScript(item.id) }) { refresh() }
            },
            onDismiss = { confirmRun = null }
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
        V2BindConfirmSheet(
            scriptName = item.name,
            busy = loading,
            onConfirm = {
                confirmBind = null
                beginSceneWrite(item.id, item.name.removeSuffix(".sh"))
            },
            onDismiss = { confirmBind = null }
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
                    Text("不允许移动到自身或自己的子文件夹。", fontSize = 11.sp, color = p.muted)
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
                    Text("只读预览 · ${item.bytes} B · 不执行", fontSize = 12.sp, color = p.muted)
                    HorizontalDivider()
                    Text(previewContent.take(16384), fontSize = 12.sp, color = p.ink)
                }
            },
            confirmButton = { TextButton(onClick = { previewScript = null; openEditor(item) }) { Text("编辑") } },
            dismissButton = { TextButton(onClick = { previewScript = null }) { Text("关闭") } }
        )
    }
    if (sceneEditorOpen) {
        V2SceneEditorSheet(
            initialName = sceneNameDraft,
            initialScriptId = sceneScriptDraft.ifBlank { null },
            scripts = entries,
            editing = editingScene != null,
            busy = loading,
            onSave = { name, scriptId ->
                val existing = editingScene
                if (existing == null) {
                    sceneEditorOpen = false
                    beginSceneWrite(scriptId, name)
                } else {
                    runRoot({
                        val rebound = if (scriptId == existing.scriptId)
                            RootResult(0, "脚本关联未改变")
                        else bridge.bind(existing.tagId, scriptId)
                        if (!rebound.success) rebound else bridge.saveScene(existing.tagId, name)
                    }) {
                        if (it.success) {
                            sceneEditorOpen = false
                            editingScene = null
                            selectedScene = null
                            refresh()
                        }
                    }
                }
            },
            onDismiss = {
                if (!loading) {
                    sceneEditorOpen = false
                    editingScene = null
                }
            }
        )
    }

    selectedScene?.let { scene ->
        V2SceneDetailSheet(
            scene = scene,
            scriptName = entries.firstOrNull { it.id == scene.scriptId }?.name ?: "脚本不存在",
            busy = loading,
            onToggle = { enabled ->
                runRoot({ bridge.setSceneEnabled(scene.tagId, enabled) }) {
                    if (it.success) {
                        selectedScene = scene.copy(enabled = enabled)
                        refresh()
                    }
                }
            },
            onEdit = {
                selectedScene = null
                openSceneEditor(scene)
            },
            onTest = {
                selectedScene = null
                val script = entries.firstOrNull { it.id == scene.scriptId }
                if (script == null) notify("关联脚本不存在，不能执行测试")
                else confirmRun = script
            },
            onUnbind = { confirmUnbindScene = scene },
            onDismiss = { selectedScene = null }
        )
    }

    confirmUnbindScene?.let { scene ->
        AlertDialog(
            onDismissRequest = { confirmUnbindScene = null },
            title = { Text("解除 NFC 绑定？") },
            text = { Text("「${scene.name}」解除后，标签仍保留编号，但不会再触发任何脚本。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnbindScene = null
                    selectedScene = null
                    runRoot({ bridge.unbind(scene.tagId) }) { refresh() }
                }) { Text("解除绑定") }
            },
            dismissButton = { TextButton(onClick = { confirmUnbindScene = null }) { Text("取消") } }
        )
    }

    if (showBackupSheet) {
        V2BackupSheet(
            busy = loading,
            onExport = {
                showBackupSheet = false
                backupExportLauncher.launch("dorm-nfc-sh-scripts.zip")
            },
            onImport = {
                showBackupSheet = false
                backupImportLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
            },
            onDismiss = { if (!loading) showBackupSheet = false }
        )
    }
    if (showLogsSheet) {
        V2LogsSheet(
            logs = ExecutionLogFormatter.format(logs, entries),
            output = output,
            onRefresh = { refresh() },
            onClear = { confirmClearLogs = true },
            onDismiss = { showLogsSheet = false }
        )
    }
    if (showThemeSheet) {
        V2ThemeSheet(
            selected = selectedTheme,
            onSelect = {
                selectedTheme = it
                context.getSharedPreferences("dorm_nfc_view", 0).edit()
                    .putString("theme", it).apply()
            },
            navStyle = navStyle,
            onNavStyle = {
                navStyle = it
                context.getSharedPreferences("dorm_nfc_view", 0).edit()
                    .putString("nav_style", it).apply()
            },
            onDismiss = { showThemeSheet = false }
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
    }        }
    }

}


@Composable
private fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalV2Palette.current
    if (title != null) {
        Text(title, color = p.blue, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 10.dp, bottom = 11.dp), fontSize = 15.sp)
    }
    Surface(
        shape = RoundedCornerShape(20.dp), color = p.surface,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, p.line)
    ) {
        Column(Modifier.padding(17.dp), content = content)
    }
}

@Composable
private fun SummaryRow(icon: Int, title: String, subtitle: String, trailing: String, onClick: () -> Unit) {
    val p = LocalV2Palette.current
    // One Material surface owns the entire hit target and ripple. The icon,
    // title, subtitle and trailing status are visual children of that same row.
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = p.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier.heightIn(min = 74.dp).padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).background(p.iconBackground, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                V2ManagementIcon(icon, p.blue, Modifier.size(23.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = p.muted, fontSize = 11.sp, maxLines = 2,
                    lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            if (trailing == "›") {
                Text("›", color = p.muted, fontSize = 23.sp)
            } else {
                Surface(color = p.accentLow, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        trailing,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = p.blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
