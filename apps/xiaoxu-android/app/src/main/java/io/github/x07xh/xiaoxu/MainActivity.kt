package io.github.x07xh.xiaoxu

import android.app.Activity
import android.os.Build
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import io.github.x07xh.xiaoxu.ui.TimetableImportPreviewSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import io.github.x07xh.xiaoxu.ui.rememberLocalRows
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import io.github.x07xh.xiaoxu.data.LocalTimetableBackup
import io.github.x07xh.xiaoxu.data.NcwuHtmlXlsTimetableParser
import io.github.x07xh.xiaoxu.data.NcwuXlsxTimetableParser
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.ui.CloudTimetablePanel
import io.github.x07xh.xiaoxu.ui.ProfileSettingsPage
import io.github.x07xh.xiaoxu.ui.LocalSchoolImportReceipt
import io.github.x07xh.xiaoxu.ui.RealAcademicHome
import io.github.x07xh.xiaoxu.ui.RealAcademicTaskHub
import io.github.x07xh.xiaoxu.ui.MessageCenterPanel
import io.github.x07xh.xiaoxu.ui.CampusRunPanel
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import io.github.x07xh.xiaoxu.ui.xiaoxuSettingsRouteTransition
import io.github.x07xh.xiaoxu.ui.XiaoxuGroupedSection
import io.github.x07xh.xiaoxu.ui.XiaoxuEmpty
import io.github.x07xh.xiaoxu.ui.XiaoxuTopBar
import io.github.x07xh.xiaoxu.ui.PrimaryBottomNavigation
import io.github.x07xh.xiaoxu.ui.PrimaryBottomNavigationReserve
import io.github.x07xh.xiaoxu.ui.PrimarySideNavigation
import io.github.x07xh.xiaoxu.data.LocalTask
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
private data class PendingTimetableImport(
    val fileName: String,
    val imported: LocalTimetableBackup.Imported,
    val oldCount: Int
)

class MainActivity : ComponentActivity() {
    private var secondClassAutoJob: Job? = null
    private val dao by lazy { LocalTaskDatabase.open(applicationContext).tasks() }
    private val timetableFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importTimetableFile(uri)
    }
    private val timetableExportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) exportTimetableFile(uri) }
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
            if (permitted && count in 1..5000) { // Explicitly combined local Chaoxing pages.
                captureSerial++
                lastLocalCapture = LocalSchoolImportReceipt(
                    source!!, kind!!, count, captureSerial
                )
            }
        }
    }
    private var shouldRefreshAfterWebsite = false
    private var localLessons by mutableStateOf<List<VerifiedLesson>>(emptyList())
    private var pendingTimetableImport by mutableStateOf<PendingTimetableImport?>(null)
    private var timetableImportSaving by mutableStateOf(false)
    // Only the latest committed import/resume read may update visible lessons.
    private var localScheduleReadSerial = 0L
    // Changes even when only semester settings are restored and course rows stay equal.
    private var localScheduleRevision by mutableIntStateOf(0)
    private var taskReminderOpenCount by mutableIntStateOf(0)
    private var scheduleReminderOpenCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("xiaoxu_open_tasks_from_reminder", false)) {
            taskReminderOpenCount++
        }
        if (intent.getBooleanExtra("xiaoxu_open_schedule_from_reminder", false)) {
            scheduleReminderOpenCount++
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
                    scheduleReminderOpenCount = scheduleReminderOpenCount,
                    localLessons = localLessons,
                    localScheduleRevision = localScheduleRevision,
                    onLocalLessonsChanged = { saved ->
                        localScheduleReadSerial++
                        localLessons = saved
                        localScheduleRevision++
                    },
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
                    openFileExport = {
                        timetableExportPicker.launch("校序-课表备份-${java.time.LocalDate.now()}.json")
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
                pendingTimetableImport?.let { pending ->
                    TimetableImportPreviewSheet(
                        fileName = pending.fileName,
                        lessons = pending.imported.lessons,
                        oldCount = pending.oldCount,
                        hasSemesterSettings = pending.imported.calendar != null,
                        saving = timetableImportSaving,
                        onDismiss = { if (!timetableImportSaving) pendingTimetableImport = null },
                        onConfirm = { commitPendingTimetableImport() }
                    )
                }
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
                    val imported = when {
                        lowered.endsWith(".ics") || text.trimStart()
                            .removePrefix("\uFEFF").startsWith("BEGIN:VCALENDAR") ->
                            LocalTimetableBackup.Imported(LocalScheduleStore.parseIcs(text))
                        lowered.endsWith(".json") && text.trimStart()
                            .removePrefix("\uFEFF").startsWith("{") ->
                            LocalTimetableBackup.parse(text)
                        lowered.endsWith(".json") ->
                            LocalTimetableBackup.Imported(LocalScheduleStore.parseCourses(text))
                        lowered.endsWith(".xlsx") || (lowered.endsWith(".xls") &&
                            bytes.size() >= 2 && bytes.toByteArray()[0] == 0x50.toByte() &&
                            bytes.toByteArray()[1] == 0x4b.toByte()) ->
                            LocalTimetableBackup.Imported(
                                NcwuXlsxTimetableParser.parse(bytes.toByteArray()))
                        lowered.endsWith(".xls") || lowered.endsWith(".html") ||
                            lowered.endsWith(".htm") ->
                            LocalTimetableBackup.Imported(
                                NcwuHtmlXlsTimetableParser.parse(bytes.toByteArray()))
                        else -> throw IllegalArgumentException(
                            "支持 .ics、标准课表 .json、华水个人课表 .xlsx 和 HTML 格式 .xls；旧式二进制 .xls 暂不支持")
                    }
                    fileName to imported
                }
                val name = parsed.first
                val decodedName = if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(name))
                    Uri.decode(name) else name
                pendingTimetableImport = PendingTimetableImport(
                    fileName = decodedName,
                    imported = parsed.second,
                    oldCount = localLessons.size
                )
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity,
                    (error.message ?: "课表文件无法识别") + "；原有课表未修改",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /** The existing local store stays authoritative; only the confirmation surface changed. */
    private fun commitPendingTimetableImport() {
        val pending = pendingTimetableImport ?: return
        if (timetableImportSaving) return
        // A timetable edited while the picker was open must be re-reviewed, not silently replaced.
        if (localLessons.size != pending.oldCount) {
            pendingTimetableImport = null
            Toast.makeText(this, "课表已发生变化，请重新选择文件核对", Toast.LENGTH_LONG).show()
            return
        }
        timetableImportSaving = true
        lifecycleScope.launch {
            try {
                val (count, savedLessons, reminderOk) = withContext(Dispatchers.IO) {
                    val count = LocalScheduleStore.saveParsed(
                        applicationContext, pending.imported.lessons)
                    pending.imported.calendar?.let {
                        LocalSemesterConfigStore.save(applicationContext, it)
                    }
                    val updated = LocalScheduleStore.load(applicationContext)
                    val reminderOk = LocalCourseReminders.reconcileAfterSave(
                        applicationContext, updated,
                        LocalSemesterConfigStore.load(applicationContext))
                    Triple(count, updated, reminderOk)
                }
                // Supersede earlier resume reads only after the commit has succeeded.
                localScheduleReadSerial++
                localLessons = savedLessons
                localScheduleRevision++
                pendingTimetableImport = null
                Toast.makeText(this@MainActivity,
                    if (reminderOk) "成功导入 " + count + " 个课块"
                    else "已保存 " + count + " 个课块，但提醒维护失败；可重新打开课表重试",
                    Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(this@MainActivity,
                    error.message ?: "导入失败，请核对文件和旧课表",
                    Toast.LENGTH_LONG).show()
            } finally {
                timetableImportSaving = false
            }
        }
    }

    /** User-selected SAF export. No cloud, credential or background network operation. */
    private fun exportTimetableFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val lessons = LocalScheduleStore.load(applicationContext)
                    val backup = LocalTimetableBackup.export(
                        lessons, LocalSemesterConfigStore.load(applicationContext))
                    contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(backup.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalArgumentException("无法写入所选文件")
                    lessons.size
                }
                Toast.makeText(this@MainActivity,
                    "已导出 $count 个课块和本机学期设置", Toast.LENGTH_LONG).show()
            } catch (failure: Exception) {
                Toast.makeText(this@MainActivity,
                    failure.message ?: "课表导出失败", Toast.LENGTH_LONG).show()
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
        if (intent.getBooleanExtra("xiaoxu_open_schedule_from_reminder", false)) {
            scheduleReminderOpenCount++
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
            delay(1_000) // Render the native shell before starting the bounded school reader.
            SecondClassAutoRefresh.reconcile(applicationContext)
            SecondClassAutoRefresh.refreshIfDue(applicationContext)
        }
        val readSerial = ++localScheduleReadSerial
        lifecycleScope.launch {
            val lessons = withContext(Dispatchers.IO) {
                LocalScheduleStore.load(applicationContext)
            }
            if (readSerial == localScheduleReadSerial) {
                // Unblock the native page immediately; reminder maintenance can follow.
                localLessons = lessons
                localScheduleRevision++
                if (LocalCourseReminders.enabled(applicationContext)) {
                    launch(Dispatchers.IO) {
                        LocalCourseReminders.reconcileAfterSave(
                            applicationContext,
                            LocalScheduleStore.load(applicationContext),
                            LocalSemesterConfigStore.load(applicationContext))
                    }
                }
            }
        }
    }
}

/** Tab animation is a single opaque 360ms translated page; no two-layer fading. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShell(
    dao: LocalTaskDao,
    cloud: CloudClient,
    appLifecycle: Lifecycle,
    taskReminderOpenCount: Int,
    scheduleReminderOpenCount: Int,
    localLessons: List<VerifiedLesson>,
    localScheduleRevision: Int,
    onLocalLessonsChanged: (List<VerifiedLesson>) -> Unit,
    returnedSchool: String?,
    localCaptureReturn: LocalSchoolImportReceipt?,
    onSchoolReturnConsumed: () -> Unit,
    startSchoolAuthorization: (String) -> Unit,
    openFileImport: () -> Unit,
    openFileExport: () -> Unit,
    openLocalSchool: (String) -> Unit,
    consumeWebsiteReturn: () -> Boolean,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit
) {
    var current by rememberSaveable {
        mutableStateOf(when {
            taskReminderOpenCount > 0 -> MainDestination.TASKS
            scheduleReminderOpenCount > 0 -> MainDestination.SCHEDULE
            else -> MainDestination.HOME
        })
    }
    var academic by rememberSaveable { mutableStateOf(AcademicDestination.OVERVIEW) }
    var messagesOpen by rememberSaveable { mutableStateOf(false) }
    // The timetable's own settings entry stays on SCHEDULE; 我的 → 课表设置 uses a PROFILE child.
    var scheduleSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var profileSettingsRoute by rememberSaveable { mutableStateOf("home") }
    // One visibility rule for the bottom reserve, tablet rail and glass dock.
    // Child settings must not leave behind navigation chrome during page transitions.
    val showPrimaryNavigation = !messagesOpen &&
        !(current == MainDestination.SCHEDULE && scheduleSettingsOpen) &&
        !(current == MainDestination.PROFILE && profileSettingsRoute != "home")
    val pageStateHolder = rememberSaveableStateHolder()
    LaunchedEffect(taskReminderOpenCount) {
        if (taskReminderOpenCount > 0) {
            current = MainDestination.TASKS
            messagesOpen = false
        }
    }
    LaunchedEffect(scheduleReminderOpenCount) {
        if (scheduleReminderOpenCount > 0) {
            current = MainDestination.SCHEDULE
            messagesOpen = false
        }
    }
    fun openSecondClass() {
        val next = AcademicNavigation(destination = current, academic = academic).openSecondClass()
        academic = next.academic
        current = next.destination
        messagesOpen = false
    }
    BackHandler(enabled = current == MainDestination.SCHEDULE && scheduleSettingsOpen) {
        scheduleSettingsOpen = false
    }
    BackHandler(enabled = !messagesOpen && current == MainDestination.PROFILE &&
        profileSettingsRoute != "home") {
        profileSettingsRoute = if (profileSettingsRoute == "licenses") "about" else "home"
    }
    BackHandler(enabled = messagesOpen) {
        messagesOpen = false // The contextual inbox returns to the originating tab.
    }
    BackHandler(
        enabled = !messagesOpen && current == MainDestination.ACADEMIC &&
            academic != AcademicDestination.OVERVIEW
    ) {
        academic = AcademicDestination.OVERVIEW // Native back consistently leaves every academic detail.
    }
    val localTasks by rememberLocalRows(dao) { dao.observe() }
    val reminderContext = LocalContext.current
    // A Room checkbox update should reschedule only affected local deadlines,
    // rather than rewriting every WorkManager job. Keep the scheduling work on IO.
    val reminderSnapshot = remember { mutableStateOf<List<LocalTask>?>(null) }
    LaunchedEffect(localTasks) {
        val previous = reminderSnapshot.value
        withContext(Dispatchers.IO) {
            LocalDeadlineReminders.reconcile(
                reminderContext, localTasks, previousTasks = previous
            )
        }
        reminderSnapshot.value = localTasks
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
        profileSettingsRoute = "connections"
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

    BoxWithConstraints(Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient)) {
        val tabletLayout = maxWidth >= 700.dp
        // Glass samples the actual current page, not the navigation chrome itself.
        // Phone and tablet still share the one NativeShell and route state.
        val navBackdrop = if (!tabletLayout && showPrimaryNavigation && Build.VERSION.SDK_INT >= 33) {
            rememberLayerBackdrop()
        } else null
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().then(
                if (navBackdrop != null) Modifier.layerBackdrop(navBackdrop) else Modifier
            ),
            containerColor = Color.Transparent,
            // Keep Scaffold measurements stable while switching tabs. HOME owns
            // its own header within the animated page; other tabs already have headers.
            topBar = {},
            bottomBar = {
                if (!tabletLayout && showPrimaryNavigation) {
                    // Layout-only reserve. Actual dock sits outside the sampled subtree.
                    PrimaryBottomNavigationReserve(glass = navBackdrop != null)
                }
            }
        ) { contentPadding ->
            Row(Modifier.fillMaxSize().padding(contentPadding)) {
                if (tabletLayout && showPrimaryNavigation) {
                    PrimarySideNavigation(selected = current) { selected ->
                        current = selected
                        profileSettingsRoute = "home"
                        messagesOpen = false
                    }
                }
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxSize()) {
                    if (messagesOpen) {
                        pageStateHolder.SaveableStateProvider("messages") {
                            MessageCenterPanel(cloud, openWebsite, openArticle,
                                useStatusBarInset = false, onBack = { messagesOpen = false })
                        }
                    } else {
                    // Only one heavy tab is ever composed; no AnimatedContent overlap.
                    // Keep per-tab SaveableStateProvider so filters/scroll survive.
                    val page = current
                    Box(Modifier.fillMaxSize().clipToBounds()) {
                        pageStateHolder.SaveableStateProvider(page.name) {
                        // Full-size opaque layers and bounds clipping prevent old
                        // page labels from showing through while rapidly changing tabs.
                        Box(Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient)) {
                        when (page) {
                            MainDestination.HOME -> Column(Modifier.fillMaxSize()) {
                                XiaoxuTopBar(
                                    title = "今日",
                                    subtitle = null,
                                    refreshing = cloud.state.busy,
                                    onRefresh = { scope.launch { cloud.refresh() } },
                                    onMessages = { messagesOpen = true },
                                    applyStatusBarInset = false
                                )
                                Box(Modifier.weight(1f)) {
                                RealAcademicHome(localTasks, client = cloud,
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
                                }
                            }
                            MainDestination.SCHEDULE -> CloudTimetablePanel(
                                localLessons = localLessons, openFileImport = openFileImport,
                                openFileExport = openFileExport,
                                localScheduleRevision = localScheduleRevision,
                                onLocalLessonsChanged = onLocalLessonsChanged,
                                onMessages = { messagesOpen = true },
                                settingsOpen = scheduleSettingsOpen,
                                onSettingsOpenChanged = { scheduleSettingsOpen = it })
                            MainDestination.ACADEMIC -> AcademicPage(academic, { academic = it },
                                openWebsite, openProfile = { current = MainDestination.PROFILE },
                                cloud = cloud, onMessages = { messagesOpen = true })
                            MainDestination.TASKS -> RealAcademicTaskHub(localTasks, dao, cloud, openWebsite,
                                openLocalSchool = openLocalSchool,
                                onMessages = { messagesOpen = true })
                            MainDestination.PROFILE -> {
                                // Reuse the settings child route for the timetable
                                // child. It must not bypass the settings host on entry/back.
                                AnimatedContent(
                                    targetState = profileSettingsRoute == "timetable-settings" ||
                                        profileSettingsRoute == "timetable-editor",
                                    transitionSpec = {
                                        xiaoxuSettingsRouteTransition(
                                            if (initialState) "detail" else "home",
                                            if (targetState) "detail" else "home"
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize().clipToBounds(),
                                    label = "profileTimetableChild"
                                ) { timetableChild ->
                                    // Each target child owns an opaque full-size layer. The
                                    // timetable/editor must not show settings text behind it.
                                    Box(Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient)) {
                                    if (timetableChild) {
                                    // A native child of 我的/设置, not a switch to the timetable tab.
                                    // Keeping current == PROFILE avoids AnimatedContent rendering a
                                    // second full timetable page and glass dock during entry.
                                    CloudTimetablePanel(
                                        localLessons = localLessons,
                                        openFileImport = openFileImport,
                                        openFileExport = openFileExport,
                                        localScheduleRevision = localScheduleRevision,
                                        onLocalLessonsChanged = onLocalLessonsChanged,
                                        onMessages = { messagesOpen = true },
                                        settingsOpen = profileSettingsRoute == "timetable-settings",
                                        onSettingsOpenChanged = { open ->
                                            profileSettingsRoute =
                                                if (open) "timetable-settings" else "timetable-editor"
                                        },
                                        onSettingsClosed = { profileSettingsRoute = "home" },
                                        // Adding a course keeps the editor composed; only file
                                        // actions leave this route for the Activity picker.
                                        onSettingsFileAction = { profileSettingsRoute = "home" },
                                        onEditorClosed = { profileSettingsRoute = "home" }
                                    )
                                } else {
                                    ProfileSettingsPage(
                                        route = if (profileSettingsRoute == "timetable-settings" ||
                                            profileSettingsRoute == "timetable-editor") "home"
                                            else profileSettingsRoute,
                                        onRouteChange = { profileSettingsRoute = it },
                                        onScheduleSettings = {
                                            profileSettingsRoute = "timetable-settings"
                                        },
                                        client = cloud,
                                        openWebsite = openWebsite,
                                        openLocalSchool = { provider ->
                                            if (provider == "second_class") openSecondClass()
                                            else openLocalSchool(provider)
                                        },
                                        startSchoolAuthorization = startSchoolAuthorization,
                                        recentImport = localCaptureReturn,
                                        localTasks = localTasks,
                                        onMessages = { messagesOpen = true },
                                        onRefresh = { scope.launch { cloud.refresh() } }
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
                }
            }
        }
        if (!tabletLayout && showPrimaryNavigation) {
            Box(Modifier.align(Alignment.BottomCenter)) {
                PrimaryBottomNavigation(selected = current, backdrop = navBackdrop) { selected ->
                    current = selected
                    profileSettingsRoute = "home"
                    messagesOpen = false
                }
            }
        }
        }
    }
}

@Composable
private fun Content(
    topPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 860.dp),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp,
            top = topPadding, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun CompactPageHeading(
    title: String,
    onMessages: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
            color = XiaoxuPalette.Ink)
        if (onRefresh != null) {
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新数据",
                    tint = XiaoxuPalette.Ink2)
            }
        }
        IconButton(onClick = onMessages) {
            Icon(Icons.Outlined.NotificationsNone, contentDescription = "消息中心",
                tint = XiaoxuPalette.Ink2)
        }
    }
}

@Composable
private fun AcademicPage(
    selected: AcademicDestination,
    select: (AcademicDestination) -> Unit,
    openWebsite: () -> Unit,
    openProfile: () -> Unit,
    cloud: CloudClient,
    onMessages: () -> Unit
) {
    val tabStateHolder = rememberSaveableStateHolder()
    // A single academic child host without page-level motion.
    // The second-class Room data and per-destination saveable state remain owned here.
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            xiaoxuSettingsRouteTransition(
                if (initialState == AcademicDestination.OVERVIEW) "home" else "detail",
                if (targetState == AcademicDestination.OVERVIEW) "home" else "detail"
            )
        },
        modifier = Modifier.fillMaxSize().clipToBounds(),
        label = "academicChild"
    ) { academicRoute ->
    Column(
        modifier = Modifier.fillMaxSize().background(XiaoxuPalette.PageGradient),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 18.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (academicRoute != AcademicDestination.OVERVIEW) {
                IconButton(onClick = { select(AcademicDestination.OVERVIEW) }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回教务",
                        tint = XiaoxuPalette.Ink2)
                }
            }
            Box(Modifier.weight(1f)) {
                CompactPageHeading(
                    title = when (academicRoute) {
                        AcademicDestination.OVERVIEW -> "教务"
                        AcademicDestination.GRADES -> "成绩"
                        AcademicDestination.EXAMS -> "考试"
                        AcademicDestination.SECOND_CLASS -> "第二课堂"
                        AcademicDestination.SERVICES -> "校园服务"
                    },
                    onMessages = onMessages
                )
            }
        }
        when (academicRoute) {
            AcademicDestination.OVERVIEW -> Content(topPadding = 6.dp) {
                item {
                    // One continuous glass surface; no nested rectangle around the four rows.
                    XiaoxuGroupedSection {
                        AcademicActionRow("成绩", Icons.Outlined.School,
                                onClick = { select(AcademicDestination.GRADES) })
                            HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp))
                            AcademicActionRow("考试", Icons.Outlined.DateRange,
                                onClick = { select(AcademicDestination.EXAMS) })
                            HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp))
                            AcademicActionRow("第二课堂", Icons.Outlined.CheckCircle,
                                onClick = { select(AcademicDestination.SECOND_CLASS) })
                            HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        AcademicActionRow("校园服务", Icons.Outlined.Home,
                            onClick = { select(AcademicDestination.SERVICES) })
                    }
                }
            }
            AcademicDestination.SECOND_CLASS ->
                tabStateHolder.SaveableStateProvider("second_class") {
                    SecondClassPage()
                }
            AcademicDestination.GRADES -> Content(topPadding = 6.dp) {
                item {
                    XiaoxuEmpty(
                        title = "成绩尚未读取",
                        message = "尚无已核验的本人成绩，学校连接情况请在「我的」查看。",
                        actionLabel = "连接青果教务",
                        onAction = openProfile
                    )
                }
            }
            AcademicDestination.EXAMS -> Content(topPadding = 6.dp) {
                item {
                    XiaoxuEmpty(
                        title = "考试安排尚未读取",
                        message = "尚无已核验的本人考试安排，学校连接情况请在「我的」查看。",
                        actionLabel = "查看数据连接",
                        onAction = openProfile
                    )
                }
            }
            AcademicDestination.SERVICES -> Content {
                item { CampusRunPanel(cloud) }
                item {
                    XiaoxuGroupedSection {
                        AcademicActionRow("数据连接", Icons.Outlined.School,
                                onClick = openProfile)
                            HorizontalDivider(color = XiaoxuPalette.Line, thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 16.dp))
                        AcademicActionRow("打开校序网站", Icons.Outlined.Home,
                            onClick = openWebsite)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun AcademicActionRow(
    title: String,
    icon: ImageVector,
    status: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(XiaoxuPalette.BrandSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null,
                tint = XiaoxuPalette.BrandDeep, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall, color = XiaoxuPalette.Ink)
        if (status != null) {
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = XiaoxuPalette.Ink3)
            Spacer(Modifier.width(7.dp))
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
            tint = XiaoxuPalette.Ink3, modifier = Modifier.size(18.dp))
    }
}
