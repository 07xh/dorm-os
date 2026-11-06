package io.github.x07xh.xiaoxu

import android.app.Activity
import android.app.AlertDialog
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.github.x07xh.xiaoxu.data.SecondClassAutoRefresh
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.ui.CloudTimetablePanel
import io.github.x07xh.xiaoxu.ui.ConnectionsPanel
import io.github.x07xh.xiaoxu.ui.LocalSchoolImportReceipt
import io.github.x07xh.xiaoxu.ui.LocalTaskBackupPanel
import io.github.x07xh.xiaoxu.ui.OpenSourceCreditsPanel
import io.github.x07xh.xiaoxu.ui.RealAcademicHome
import io.github.x07xh.xiaoxu.ui.RealAcademicTaskHub
import io.github.x07xh.xiaoxu.ui.MessageCenterPanel
import io.github.x07xh.xiaoxu.ui.SecondClassPanel
import io.github.x07xh.xiaoxu.ui.CampusRunPanel
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import io.github.x07xh.xiaoxu.ui.XiaoxuCard
import io.github.x07xh.xiaoxu.ui.XiaoxuTopBar
import io.github.x07xh.xiaoxu.ui.PrimaryBottomNavigation
import io.github.x07xh.xiaoxu.ui.PrimarySideNavigation
import io.github.x07xh.xiaoxu.ui.primaryTabLabel
import io.github.x07xh.xiaoxu.ui.QuickEntry
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalDeadlineReminders
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/**
 * Android native entry: no WebView wrapper and no duplicate school credential collector.
 * Existing owner-auth and connector pages remain on the verified HTTPS website.
 */
class MainActivity : ComponentActivity() {
    private var secondClassAutoJob: Job? = null
    private val dao by lazy { LocalTaskDatabase.open(applicationContext).tasks() }
    private val timetableFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importTimetableFile(uri)
    }
    private val cloud by lazy { CloudClient(applicationContext) }
    private val schoolAuthReturn by lazy { SchoolAuthReturn(applicationContext) }
    private var returnedSchool by mutableStateOf<String?>(null)
    private var lastLocalCapture by mutableStateOf<LocalSchoolImportReceipt?>(null)
    private var captureSerial = 0L
    // A receipt is emitted ONLY by a successfully committed local Room import.
    // The result Intent contains no raw school record or credential.
    private val nativeSchoolLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val source = result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER)
            val kind = result.data?.getStringExtra(NativeSchoolConnectActivity.EXTRA_RETURN_KIND)
            val count = result.data?.getIntExtra(
                NativeSchoolConnectActivity.EXTRA_SAVED_COUNT, 0) ?: 0
            val permitted = when (source) {
                "chaoxing" -> kind in setOf("course", "homework")
                "second_class" -> kind in setOf("activity", "credits")
                else -> false
            }
            if (permitted && count in 1..500) { // Explicitly combined local Chaoxing pages.
                captureSerial++
                lastLocalCapture = LocalSchoolImportReceipt(
                    source!!, kind!!, count, captureSerial
                )
            }
        }
    }
    private var shouldRefreshAfterWebsite = false
    private var localLessons by mutableStateOf<List<VerifiedLesson>>(emptyList())
    // Only the latest committed import/resume read may update visible lessons.
    private var localScheduleReadSerial = 0L
    private var taskReminderOpenCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("xiaoxu_open_tasks_from_reminder", false)) {
            taskReminderOpenCount++
        }
        // 状态栏 / 导航栏与应用同底色：顶栏自己处理状态栏内边距，底部导航处理导航栏内边距。
        enableEdgeToEdge()
        // The Compose shell should appear before reading/parsing the local timetable.
        // onResume loads the snapshot from IO without blocking cold start.
        receiveSchoolReturn(intent)
        setContent {
            // 统一主题入口（Issue #268）：颜色 / 字级 / 圆角只在 ui/XiaoxuDesignSystem.kt 定义一次。
            XiaoxuTheme {
                NativeShell(
                    dao = dao, cloud = cloud, appLifecycle = lifecycle,
                    taskReminderOpenCount = taskReminderOpenCount,
                    localLessons = localLessons,
                    returnedSchool = returnedSchool,
                    localCaptureReturn = lastLocalCapture,
                    onSchoolReturnConsumed = { returnedSchool = null },
                    startSchoolAuthorization = { provider ->
                        val url = schoolAuthReturn.begin(provider, cloud.ownerSessionBinding())
                        if (url != null) {
                            shouldRefreshAfterWebsite = true
                            try {
                                CustomTabsIntent.Builder().setShowTitle(true)
                                    .build().launchUrl(this@MainActivity, url)
                            } catch (_: android.content.ActivityNotFoundException) {
                                startActivity(Intent(Intent.ACTION_VIEW, url)
                                    .addCategory(Intent.CATEGORY_BROWSABLE))
                            }
                        }
                    },
                    openFileImport = {
                        timetableFilePicker.launch(arrayOf("*/*"))
                    },
                    openLocalSchool = { provider ->
                        if (provider == "ncwu") {
                            startActivity(Intent(this@MainActivity, LocalQingguoImportActivity::class.java))
                        } else if (provider == "chaoxing" || provider == "second_class") {
                            lastLocalCapture = null
                            nativeSchoolLauncher.launch(
                                Intent(this@MainActivity, if (provider == "second_class")
                                    SecondClassHomeActivity::class.java else NativeSchoolConnectActivity::class.java)
                                    .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, provider)
                            )
                        }
                    },
                    consumeWebsiteReturn = {
                        val pending = shouldRefreshAfterWebsite
                        shouldRefreshAfterWebsite = false
                        pending
                    },
                    openWebsite = {
                    // Fixed origin: never forward app credentials or a school session.
                    shouldRefreshAfterWebsite = true
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://dorm-os-dk9.pages.dev/#settings"))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                    )
                }, openArticle = { url ->
                    val uri = Uri.parse(url)
                    if (uri.scheme == "https" && uri.host == "www2.ncwu.edu.cn" &&
                        (uri.path ?: "").startsWith("/jiaowu/info/")) {
                        startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
                    }
                })
            }
        }
    }

    /** SAF-only local file import. File bytes never leave the device. */
    private fun importTimetableFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val fileName = contentResolver.query(uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: "未命名文件"
                    val bytes = ByteArrayOutputStream()
                    contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(bytes.size() + count <= 1_000_000) {
                                "文件超过 1 MB，未导入"
                            }
                            bytes.write(buffer, 0, count)
                        }
                    } ?: throw IllegalArgumentException("无法打开所选文件")
                    val text = bytes.toString(Charsets.UTF_8.name())
                    val lowered = fileName.lowercase()
                    val lessons = when {
                        lowered.endsWith(".ics") || text.trimStart()
                            .removePrefix("\uFEFF").startsWith("BEGIN:VCALENDAR") ->
                            LocalScheduleStore.parseIcs(text)
                        lowered.endsWith(".json") -> LocalScheduleStore.parseCourses(text)
                        else -> throw IllegalArgumentException(
                            "请选择已转换的 .ics 或标准课表 .json；原始 .sleepdown 备份暂不支持直接读取")
                    }
                    fileName to lessons
                }
                val oldCount = localLessons.size
                val newCount = parsed.second.size
                val less = oldCount > 0 && newCount < oldCount
                // The earlier count-only popup concealed partial/incorrect
                // course rows. Show actual imported fields before overwriting.
                val preview = parsed.second
                    .sortedWith(compareBy({ it.weekday }, { it.startSection }, { it.title }))
                    .take(30)
                    .mapIndexed { index, lesson ->
                        val weekday = listOf("一", "二", "三", "四", "五", "六", "日")
                            .getOrElse(lesson.weekday - 1) { "?" }
                        "${index + 1}. ${lesson.title} · 周${weekday}第${lesson.startSection}–${lesson.endSection}节" +
                            " · ${lesson.weeks.joinToString(",")}周" +
                            " · ${lesson.location.ifBlank { "地点未提供" }}" +
                            " · ${lesson.teacher.ifBlank { "教师未提供" }}"
                    }.joinToString("\n")
                val summary = "文件：" + parsed.first +
                    "\n解析到 " + newCount + " 个课程课块" +
                    (if (oldCount > 0) "，原有 " + oldCount + " 个" else "") +
                    "。请逐项核对课程、星期、节次、周次、教师和教室。" +
                    (if (less) "\n\n本次课块更少，可能识别不全；建议保留旧课表。" else "") +
                    "\n\n本机解析详情（前 30 条）：\n" + preview +
                    (if (newCount > 30) "\n…其余 ${newCount - 30} 条未显示，请勿在未核对时覆盖已有课表。" else "") +
                    "\n\n文件导入仅保存在本机，不代表青果官方实时同步。"
                val saveImported = {
                    lifecycleScope.launch {
                        try {
                            val (count, savedLessons) = withContext(Dispatchers.IO) {
                                val count = LocalScheduleStore.saveParsed(
                                    applicationContext, parsed.second)
                                count to LocalScheduleStore.load(applicationContext)
                            }
                            // Invalidate previously started resume reads after commit.
                            localScheduleReadSerial++
                            localLessons = savedLessons
                            Toast.makeText(this@MainActivity, "成功导入 " + count + " 个课块",
                                Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            Toast.makeText(this@MainActivity,
                                error.message ?: "导入失败，旧课表未修改",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(if (less) "课表数量减少，请核对" else "确认导入课表文件")
                    .setMessage(summary)
                    .setNegativeButton("保留旧课表", null)
                    .setPositiveButton(if (less) "继续核对" else "确认保存") { _, _ ->
                        if (less) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("再次确认替换")
                                .setMessage("本次 " + newCount + " 个课块少于原有 " +
                                    oldCount + " 个；确定已经核对所有课程吗？")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("已核对，替换") { _, _ -> saveImported() }
                                .show()
                        } else saveImported()
                    }
                    .show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity,
                    (error.message ?: "课表文件无法识别") + "；原有课表未修改",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveSchoolReturn(intent)
        if (intent.getBooleanExtra("xiaoxu_open_tasks_from_reminder", false)) {
            taskReminderOpenCount++
        }
    }

    private fun receiveSchoolReturn(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val provider = schoolAuthReturn.consume(intent.data, cloud.ownerSessionBinding())
        if (provider != null) {
            shouldRefreshAfterWebsite = false
            returnedSchool = provider
        }
    }

    override fun onStop() {
        secondClassAutoJob?.cancel()
        secondClassAutoJob = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        secondClassAutoJob?.cancel()
        secondClassAutoJob = lifecycleScope.launch {
            delay(1_000) // Let the native shell display before WebView startup.
            SecondClassAutoRefresh.reconcile(applicationContext)
            SecondClassAutoRefresh.refreshIfDue(applicationContext)
        }
        val readSerial = ++localScheduleReadSerial
        lifecycleScope.launch {
            val lessons = withContext(Dispatchers.IO) {
                LocalScheduleStore.load(applicationContext)
            }
            if (readSerial == localScheduleReadSerial) localLessons = lessons
        }
    }
}

/**
 * 主页面切换动效时长（毫秒）。enter 与 exit 必须同长：
 * 旧实现 fadeOut(120) 比 fadeIn(180) 早 60ms 结束，中间 60ms 只有半透明新页，
 * 透出 Scaffold 背景色，观感就是"闪白"。
 */
private const val PageSwitchMs = 180

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShell(
    dao: LocalTaskDao,
    cloud: CloudClient,
    appLifecycle: Lifecycle,
    taskReminderOpenCount: Int,
    localLessons: List<VerifiedLesson>,
    returnedSchool: String?,
    localCaptureReturn: LocalSchoolImportReceipt?,
    onSchoolReturnConsumed: () -> Unit,
    startSchoolAuthorization: (String) -> Unit,
    openFileImport: () -> Unit,
    openLocalSchool: (String) -> Unit,
    consumeWebsiteReturn: () -> Boolean,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit
) {
    var current by rememberSaveable {
        mutableStateOf(if (taskReminderOpenCount > 0) MainDestination.TASKS else MainDestination.HOME)
    }
    var academic by rememberSaveable { mutableStateOf(AcademicDestination.OVERVIEW) }
    var messagesOpen by rememberSaveable { mutableStateOf(false) }
    val pageStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(taskReminderOpenCount) {
        if (taskReminderOpenCount > 0) {
            current = MainDestination.TASKS
            messagesOpen = false
        }
    }
    fun openSecondClass() {
        val next = AcademicNavigation(destination = current, academic = academic).openSecondClass()
        academic = next.academic
        current = next.destination
        messagesOpen = false
    }
    BackHandler(enabled = messagesOpen) {
        messagesOpen = false // The contextual inbox returns to the originating tab.
    }
    BackHandler(
        enabled = !messagesOpen && current == MainDestination.ACADEMIC &&
            academic == AcademicDestination.SERVICES
    ) {
        academic = AcademicDestination.OVERVIEW // Hidden service detail never becomes a fake selected top tab.
    }
    val localTasks by dao.observe().collectAsState(initial = emptyList())
    val reminderContext = LocalContext.current
    // Room emits after create / complete / delete / import; WorkManager refreshes only local opt-in jobs.
    LaunchedEffect(localTasks) {
        LocalDeadlineReminders.reconcile(reminderContext, localTasks)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(localCaptureReturn) {
        if (localCaptureReturn != null) {
            // Return to the matching feature after a successful local capture.
            messagesOpen = false
            current = if (localCaptureReturn.provider == "chaoxing") MainDestination.TASKS
                      else { academic = AcademicDestination.SECOND_CLASS; MainDestination.ACADEMIC }
        }
    }
    LaunchedEffect(returnedSchool) {
        val provider = returnedSchool ?: return@LaunchedEffect
        messagesOpen = false
        current = MainDestination.PROFILE
        try {
            cloud.completeSchoolReturn(provider)
        } finally {
            onSchoolReturnConsumed()
        }
    }
    DisposableEffect(appLifecycle, cloud, returnedSchool) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && returnedSchool == null) {
                val force = consumeWebsiteReturn()
                scope.launch {
                    if (force) cloud.refresh() else cloud.refreshOnForeground()
                }
            }
        }
        appLifecycle.addObserver(observer)
        onDispose { appLifecycle.removeObserver(observer) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabletLayout = maxWidth >= 700.dp
        Scaffold(
            containerColor = XiaoxuPalette.Bg,
            topBar = {
                // Every main destination, including the locally imported timetable, opens the inbox.
                if (!messagesOpen) XiaoxuTopBar(
                    title = "校序",
                    subtitle = current.primaryTabLabel,
                    refreshing = current != MainDestination.SCHEDULE && cloud.state.busy,
                    onRefresh = if (current == MainDestination.SCHEDULE) null
                        else ({ scope.launch { cloud.refresh() } }),
                    onMessages = { messagesOpen = true }
                )
            },
            bottomBar = {
                if (!tabletLayout && !messagesOpen) {
                    // A single renderer. NativeShell remains the sole navigation state owner.
                    PrimaryBottomNavigation(selected = current) {
                        current = it
                        messagesOpen = false
                    }
                }
            }
        ) { contentPadding ->
            Row(Modifier.fillMaxSize().padding(contentPadding)) {
                if (tabletLayout && !messagesOpen) {
                    PrimarySideNavigation(selected = current) {
                        current = it
                        messagesOpen = false
                    }
                }
                Surface(color = XiaoxuPalette.Bg, modifier = Modifier.fillMaxSize()) {
                    if (messagesOpen) {
                        pageStateHolder.SaveableStateProvider("messages") {
                            MessagesPage(openWebsite, cloud, openArticle, onBack = { messagesOpen = false })
                        }
                    } else {
                    // Per-tab SaveableStateProvider keeps subfilters and scroll position after tab changes.
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            // enter / exit 同长（PageSwitchMs），消除旧页先消失的半透明空档。
                            // 位移收到 width/32（约 33px）：只作方向提示，不做整页平移，
                            // 缩小课表这类重页在新旧共存期的重绘面积。
                            val forward = targetState.ordinal >= initialState.ordinal
                            val direction = if (forward) 1 else -1
                            (
                                fadeIn(animationSpec = tween(PageSwitchMs)) +
                                    slideInHorizontally(animationSpec = tween(PageSwitchMs)) { width ->
                                        direction * (width / 32)
                                    }
                                ) togetherWith fadeOut(animationSpec = tween(PageSwitchMs))
                        },
                        label = "page"
                    ) { page ->
                        pageStateHolder.SaveableStateProvider(page.name) {
                        when (page) {
                            MainDestination.HOME -> RealAcademicHome(localTasks, client = cloud,
                                localLessons = localLessons,
                                openSchedule = { current = MainDestination.SCHEDULE },
                                openTasks = { current = MainDestination.TASKS },
                                openMessages = { messagesOpen = true },
                                openProfile = { current = MainDestination.PROFILE },
                                openAcademic = { current = MainDestination.ACADEMIC; academic = AcademicDestination.OVERVIEW },
                                openLocalSchool = { provider ->
                                    if (provider == "second_class") openSecondClass()
                                    else openLocalSchool(provider)
                                },
                                openFileImport = openFileImport,
                                openGrades = {
                                    academic = AcademicDestination.GRADES
                                    current = MainDestination.ACADEMIC
                                },
                                openExams = {
                                    academic = AcademicDestination.EXAMS
                                    current = MainDestination.ACADEMIC
                                },
                                openSecondClass = { openSecondClass() },
                                openArticle = openArticle)
                            MainDestination.SCHEDULE -> CloudTimetablePanel(
                                localLessons = localLessons, openFileImport = openFileImport)
                            MainDestination.ACADEMIC -> AcademicPage(academic, { academic = it },
                                openWebsite, openProfile = { current = MainDestination.PROFILE },
                                cloud = cloud)
                            MainDestination.TASKS -> RealAcademicTaskHub(localTasks, dao, cloud, openWebsite,
                                openLocalSchool = openLocalSchool)
                            MainDestination.PROFILE -> ConnectionsPage(
                                openWebsite, cloud, { provider ->
                                    if (provider == "second_class") openSecondClass()
                                    else openLocalSchool(provider)
                                },
                                startSchoolAuthorization, localCaptureReturn
                            )
                        }
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessagesPage(
    openWebsite: () -> Unit,
    cloud: CloudClient,
    openArticle: (String) -> Unit,
    onBack: () -> Unit
) {
    MessageCenterPanel(cloud, openWebsite, openArticle,
        useStatusBarInset = false, onBack = onBack)
}

@Composable
private fun ConnectionsPage(
    openWebsite: () -> Unit, cloud: CloudClient,
    openLocalSchool: (String) -> Unit, startSchoolAuthorization: (String) -> Unit,
    localCaptureReturn: LocalSchoolImportReceipt?
) {
    var showCloudSecondClass by rememberSaveable { mutableStateOf(false) }
    var showLocalBackup by rememberSaveable { mutableStateOf(false) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    Content {
        // ConnectionsPanel starts with the real owner profile hero. Do not
        // duplicate "我的" as a second headline above the same information.
        item {
            ConnectionsPanel(cloud, openWebsite, openLocalSchool,
                startSchoolAuthorization, localCaptureReturn)
        }
        item {
            XiaoxuCard(onClick = { showCloudSecondClass = !showCloudSecondClass }) {
                Text(if (showCloudSecondClass) "收起云端个人快照"
                     else "云端第二课堂个人快照（可选）",
                    style = MaterialTheme.typography.titleSmall)
                Text("仅展示已保存的本人活动与积分；学校活动广场在「教务 → 第二课堂」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2)
            }
        }
        if (showCloudSecondClass) {
            item { SecondClassPanel(cloud, openWebsite) }
        }
        item {
            XiaoxuCard(onClick = { showLocalBackup = !showLocalBackup }) {
                Text(if (showLocalBackup) "收起本机任务备份"
                     else "本机任务备份与恢复",
                    style = MaterialTheme.typography.titleSmall)
                Text("按需使用系统文件选择器，不自动上传本机任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2)
            }
        }
        if (showLocalBackup) item { LocalTaskBackupPanel() }
        item {
            XiaoxuCard(onClick = { showCredits = !showCredits }) {
                Text(if (showCredits) "收起开源组件与致谢"
                     else "开源组件与致谢",
                    style = MaterialTheme.typography.titleSmall)
                Text("青果教务适配（MIT）与 SleepDown 导航动画（许可 1.1）的署名、修改说明与许可全文。",
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Ink2)
            }
        }
        if (showCredits) item { OpenSourceCreditsPanel() }
    }
}

@Composable
private fun AcademicPage(
    selected: AcademicDestination,
    select: (AcademicDestination) -> Unit,
    openWebsite: () -> Unit,
    openProfile: () -> Unit,
    cloud: CloudClient
) {
    val visibleTabs = listOf(
        AcademicDestination.OVERVIEW to "总览",
        AcademicDestination.GRADES to "成绩",
        AcademicDestination.EXAMS to "考试",
        AcademicDestination.SECOND_CLASS to "第二课堂"
    )
    val tabStateHolder = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        if (selected != AcademicDestination.SERVICES) {
            ScrollableTabRow(
                selectedTabIndex = visibleTabs.indexOfFirst { it.first == selected },
                edgePadding = 0.dp
            ) {
                visibleTabs.forEach { (destination, title) ->
                    Tab(selected = selected == destination, onClick = { select(destination) },
                        text = { Text(title) })
                }
            }
        }
        when (selected) {
            AcademicDestination.OVERVIEW -> Content {
                item { Heading("教务中心", "学业与学校服务 · 真实数据") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickEntry("成绩查询", Icons.Outlined.School,
                            { select(AcademicDestination.GRADES) }, Modifier.weight(1f))
                        QuickEntry("考试安排", Icons.Outlined.DateRange,
                            { select(AcademicDestination.EXAMS) }, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickEntry("第二课堂", Icons.Outlined.CheckCircle,
                            { select(AcademicDestination.SECOND_CLASS) }, Modifier.weight(1f))
                        QuickEntry("校园服务", Icons.Outlined.Home,
                            { select(AcademicDestination.SERVICES) }, Modifier.weight(1f))
                    }
                }
                item {
                    Text("成绩、考试未读取前仅展示连接状态；第二课堂沿用本人本机积分与活动记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AcademicDestination.SECOND_CLASS -> tabStateHolder.SaveableStateProvider("second_class") {
                SecondClassPage()
            }
            AcademicDestination.GRADES -> Content {
                item { Heading("成绩", "青果教务云端读取 · 与本机课表独立") }
                item {
                    Text("成绩接口尚未完成本人真实数据验收，当前不会展示模拟成绩。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item { TextButton(onClick = openProfile) { Text("查看青果教务连接") } }
            }
            AcademicDestination.EXAMS -> Content {
                item { Heading("考试", "尚未连接可验证的本人考试数据") }
                item { TextButton(onClick = openProfile) { Text("查看数据连接") } }
            }
            AcademicDestination.SERVICES -> Content {
                item { TextButton(onClick = { select(AcademicDestination.OVERVIEW) }) {
                    Text("返回教务总览")
                } }
                item { Heading("校园服务", "学校数据连接与服务") }
                item { CampusRunPanel(cloud) }
                item { TextButton(onClick = openProfile) { Text("管理数据连接") } }
                item { TextButton(onClick = openWebsite) { Text("打开校序网站") } }
            }
        }
    }
}
