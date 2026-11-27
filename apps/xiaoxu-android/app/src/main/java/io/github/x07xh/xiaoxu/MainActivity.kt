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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import io.github.x07xh.xiaoxu.data.LocalTimetableBackup
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.ui.LocalSchoolImportReceipt
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import io.github.x07xh.xiaoxu.ui.NativeShell
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
    private var reminderTaskId by mutableStateOf<Long?>(null)
    private var reminderLessonId by mutableStateOf<String?>(null)
    private var reminderLessonWeekday by mutableIntStateOf(0)
    private var scheduleReminderOpenCount by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("xiaoxu_open_tasks_from_reminder", false)) {
            taskReminderOpenCount++
            reminderTaskId = intent.getLongExtra("xiaoxu_open_task_id", -1L)
                .takeIf { it > 0L }
        }
        if (intent.getBooleanExtra("xiaoxu_open_schedule_from_reminder", false)) {
            scheduleReminderOpenCount++
            reminderLessonId = intent.getStringExtra("xiaoxu_open_lesson_id")
                ?.takeIf { it.isNotBlank() }
            reminderLessonWeekday = intent.getIntExtra("xiaoxu_open_lesson_weekday", 0)
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
                    requestSecondClassRefresh = ::requestSecondClassRefresh,
                    taskReminderOpenCount = taskReminderOpenCount,
                    reminderTaskId = reminderTaskId,
                    onReminderTaskConsumed = { reminderTaskId = null },
                    scheduleReminderOpenCount = scheduleReminderOpenCount,
                    reminderLessonId = reminderLessonId,
                    reminderLessonWeekday = reminderLessonWeekday,
                    onReminderCourseConsumed = { reminderLessonId = null },
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
                        else -> throw IllegalArgumentException(
                            "请选择已转换的 .ics、标准课表 .json 或校序课表备份；原始 .sleepdown 备份暂不支持直接读取")
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
            reminderTaskId = intent.getLongExtra("xiaoxu_open_task_id", -1L)
                .takeIf { it > 0L }
        }
        if (intent.getBooleanExtra("xiaoxu_open_schedule_from_reminder", false)) {
            scheduleReminderOpenCount++
            reminderLessonId = intent.getStringExtra("xiaoxu_open_lesson_id")
                ?.takeIf { it.isNotBlank() }
            reminderLessonWeekday = intent.getIntExtra("xiaoxu_open_lesson_weekday", 0)
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

    // One Activity-owned job for foreground entry and resume. Room keeps the last
    // verified snapshot visible; the coordinator owns cache age and identity checks.
    private fun requestSecondClassRefresh() {
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) ||
            secondClassAutoJob?.isActive == true) return
        secondClassAutoJob = lifecycleScope.launch {
            withContext(Dispatchers.IO) { SecondClassAutoRefresh.reconcile(applicationContext) }
            SecondClassAutoRefresh.refreshIfDue(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()
        requestSecondClassRefresh()
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

