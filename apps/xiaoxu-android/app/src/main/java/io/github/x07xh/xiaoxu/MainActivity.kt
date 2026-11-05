package io.github.x07xh.xiaoxu

import android.app.Activity
import android.app.AlertDialog
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.LocalScheduleStore
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.ui.CloudTimetablePanel
import io.github.x07xh.xiaoxu.ui.ConnectionsPanel
import io.github.x07xh.xiaoxu.ui.LocalSchoolImportReceipt
import io.github.x07xh.xiaoxu.ui.LocalTaskBackupPanel
import io.github.x07xh.xiaoxu.ui.RealAcademicHome
import io.github.x07xh.xiaoxu.ui.RealAcademicTaskHub
import io.github.x07xh.xiaoxu.ui.MessageCenterPanel
import io.github.x07xh.xiaoxu.ui.SecondClassPanel
import io.github.x07xh.xiaoxu.ui.XiaoxuPalette
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import io.github.x07xh.xiaoxu.ui.XiaoxuTopBar
import io.github.x07xh.xiaoxu.data.LocalTaskDao
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 状态栏 / 导航栏与应用同底色：顶栏自己处理状态栏内边距，底部导航处理导航栏内边距。
        enableEdgeToEdge()
        localLessons = LocalScheduleStore.load(this)
        receiveSchoolReturn(intent)
        setContent {
            // 统一主题入口（Issue #268）：颜色 / 字级 / 圆角只在 ui/XiaoxuDesignSystem.kt 定义一次。
            XiaoxuTheme {
                NativeShell(
                    dao = dao, cloud = cloud, appLifecycle = lifecycle,
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
                    openLocalImport = {
                        startActivity(Intent(this@MainActivity, LocalQingguoImportActivity::class.java))
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
                    try {
                        val count = LocalScheduleStore.saveParsed(this@MainActivity, parsed.second)
                        localLessons = LocalScheduleStore.load(this@MainActivity)
                        Toast.makeText(this@MainActivity, "成功导入 " + count + " 个课块",
                            Toast.LENGTH_LONG).show()
                    } catch (error: Exception) {
                        Toast.makeText(this@MainActivity, error.message ?: "导入失败，旧课表未修改",
                            Toast.LENGTH_LONG).show()
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
    }

    private fun receiveSchoolReturn(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val provider = schoolAuthReturn.consume(intent.data, cloud.ownerSessionBinding())
        if (provider != null) {
            shouldRefreshAfterWebsite = false
            returnedSchool = provider
        }
    }

    override fun onResume() {
        super.onResume()
        localLessons = LocalScheduleStore.load(this)
    }
}

private enum class Page(val label: String, val symbol: ImageVector) {
    HOME("今日", Icons.Outlined.Home),
    SCHEDULE("课表", Icons.Outlined.DateRange),
    ACADEMIC("教务", Icons.Outlined.School),
    TASKS("任务", Icons.Outlined.CheckCircle),
    MESSAGES("消息", Icons.Outlined.MailOutline),
    PROFILE("我的", Icons.Outlined.PersonOutline)
}

/** 海报版底栏顺序（Issue #284）：今日 / 课表 / 任务 / 消息 / 我的。教务入口移到首页宫格。 */
private val PrimaryTabs = listOf(Page.HOME, Page.SCHEDULE, Page.TASKS, Page.MESSAGES, Page.PROFILE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeShell(
    dao: LocalTaskDao,
    cloud: CloudClient,
    appLifecycle: Lifecycle,
    localLessons: List<VerifiedLesson>,
    returnedSchool: String?,
    localCaptureReturn: LocalSchoolImportReceipt?,
    onSchoolReturnConsumed: () -> Unit,
    startSchoolAuthorization: (String) -> Unit,
    openLocalImport: () -> Unit,
    openFileImport: () -> Unit,
    openLocalSchool: (String) -> Unit,
    consumeWebsiteReturn: () -> Boolean,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Page.HOME) }
    // 海报版式（Issue #284）：默认开启，用户可在真实数据模式顶栏切回。
    val pageStateHolder = rememberSaveableStateHolder()
    var academic by rememberSaveable { mutableStateOf(AcademicDestination.NOTICES) }
    fun openSecondClass() {
        val next = AcademicNavigation(academic = academic).openSecondClass()
        academic = next.academic
        current = Page.valueOf(next.destination.name)
    }
    BackHandler(enabled = current == Page.MESSAGES) {
        current = Page.valueOf(AcademicNavigation(academic = academic).openMessages().closeMessages().destination.name)
    }
    val localTasks by dao.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(localCaptureReturn) {
        if (localCaptureReturn != null) {
            // Return to the matching feature after a successful local capture.
            current = if (localCaptureReturn.provider == "chaoxing") Page.TASKS
                      else { academic = AcademicDestination.SECOND_CLASS; Page.ACADEMIC }
        }
    }
    LaunchedEffect(returnedSchool) {
        val provider = returnedSchool ?: return@LaunchedEffect
        current = Page.PROFILE
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
                // 消息页独立标题；所有页面仍使用同一真实数据源。
                if (current != Page.MESSAGES) XiaoxuTopBar(
                        title = "校序",
                        subtitle = current.label,
                        refreshing = cloud.state.busy,
                        onRefresh = { scope.launch { cloud.refresh() } },
                        onMessages = if (current == Page.HOME) ({ current = Page.MESSAGES }) else null
                    )
            },
            bottomBar = {
                if (!tabletLayout) {
                    NavigationBar(
                        containerColor = XiaoxuPalette.Surface,
                        contentColor = XiaoxuPalette.Ink2,
                        tonalElevation = 0.dp
                    ) {
                        Page.entries.filter { it in PrimaryTabs }.sortedBy { PrimaryTabs.indexOf(it) }.forEach { page ->
                            NavigationBarItem(
                                selected = current == page,
                                onClick = { current = page },
                                icon = { Icon(page.symbol, contentDescription = null) },
                                label = { Text(page.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = XiaoxuPalette.BrandDeep,
                                    selectedTextColor = XiaoxuPalette.BrandDeep,
                                    indicatorColor = XiaoxuPalette.BrandSoft,
                                    unselectedIconColor = XiaoxuPalette.Ink3,
                                    unselectedTextColor = XiaoxuPalette.Ink3
                                )
                            )
                        }
                    }
                }
            }
        ) { contentPadding ->
            Row(Modifier.fillMaxSize().padding(contentPadding)) {
                if (tabletLayout) {
                    NavigationRail(
                        containerColor = XiaoxuPalette.Surface,
                        contentColor = XiaoxuPalette.Ink2
                    ) {
                        Spacer(Modifier.height(12.dp))
                        Page.entries.filter { it in PrimaryTabs }.sortedBy { PrimaryTabs.indexOf(it) }.forEach { page ->
                            NavigationRailItem(
                                selected = current == page,
                                onClick = { current = page },
                                icon = { Icon(page.symbol, contentDescription = null) },
                                label = { Text(page.label) },
                                alwaysShowLabel = true,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = XiaoxuPalette.BrandDeep,
                                    selectedTextColor = XiaoxuPalette.BrandDeep,
                                    indicatorColor = XiaoxuPalette.BrandSoft,
                                    unselectedIconColor = XiaoxuPalette.Ink3,
                                    unselectedTextColor = XiaoxuPalette.Ink3
                                )
                            )
                        }
                    }
                }
                Surface(color = XiaoxuPalette.Bg, modifier = Modifier.fillMaxSize()) {
                    // 轻量过渡：淡入 + 轻微横向位移，不做整页滑屏，避免课表重排闪烁。
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            val forward = targetState.ordinal >= initialState.ordinal
                            val direction = if (forward) 1 else -1
                            (
                                fadeIn(animationSpec = tween(180)) +
                                    slideInHorizontally(animationSpec = tween(200)) { width ->
                                        direction * (width / 18)
                                    }
                                ) togetherWith fadeOut(animationSpec = tween(120))
                        },
                        label = "page"
                    ) { page ->
                        when (page) {
                            Page.HOME -> RealAcademicHome(localTasks, client = cloud,
                                localLessons = localLessons,
                                openSchedule = { current = Page.SCHEDULE },
                                openTasks = { current = Page.TASKS },
                                openMessages = { current = Page.MESSAGES },
                                openProfile = { current = Page.PROFILE },
                                openAcademic = { current = Page.ACADEMIC },
                                openLocalSchool = { provider ->
                                    if (provider == "second_class") openSecondClass()
                                    else openLocalSchool(provider)
                                })
                            Page.SCHEDULE -> SchedulePage(openWebsite, cloud,
                                openProfile = { current = Page.PROFILE },
                                localLessons = localLessons, openLocalImport = openLocalImport,
                                openFileImport = openFileImport)
                            Page.ACADEMIC -> pageStateHolder.SaveableStateProvider("academic") {
                                AcademicPage(academic, { academic = it }, cloud, openWebsite, openArticle,
                                    openProfile = { current = Page.PROFILE })
                            }
                            Page.TASKS -> RealAcademicTaskHub(localTasks, dao, cloud, openWebsite,
                                openLocalSchool = openLocalSchool)
                            Page.MESSAGES -> MessagesPage(openWebsite, cloud, openArticle)
                            Page.PROFILE -> ConnectionsPage(
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
private fun SchedulePage(
    openWebsite: () -> Unit, cloud: CloudClient, openProfile: () -> Unit,
    localLessons: List<VerifiedLesson>, openLocalImport: () -> Unit,
    openFileImport: () -> Unit
) {
    // 课表页自持整屏滚动（星期条 + 课程列表），因此不再外包一层 Content/LazyColumn。
    CloudTimetablePanel(cloud, openWebsite, openProfile, localLessons, openLocalImport,
        openFileImport)
}

@Composable
private fun MessagesPage(
    openWebsite: () -> Unit,
    cloud: CloudClient,
    openArticle: (String) -> Unit
) {
    // 消息中心自持整屏滚动（搜索 / 来源筛选 / 全文 sheet），因此不再外包一层 Content。
    MessageCenterPanel(cloud, openWebsite, openArticle, useStatusBarInset = true)
}

@Composable
private fun ConnectionsPage(
    openWebsite: () -> Unit, cloud: CloudClient,
    openLocalSchool: (String) -> Unit, startSchoolAuthorization: (String) -> Unit,
    localCaptureReturn: LocalSchoolImportReceipt?
) {
    var showCloudSecondClass by rememberSaveable { mutableStateOf(false) }
    Content {
        item { Heading("我的", "学校数据先在手机读取；云端同步是可选功能") }
        item {
            ConnectionsPanel(cloud, openWebsite, openLocalSchool,
                startSchoolAuthorization, localCaptureReturn)
        }
        item {
            TextButton(onClick = { showCloudSecondClass = !showCloudSecondClass }) {
                Text(if (showCloudSecondClass) "收起云端第二课堂详情"
                     else "查看云端第二课堂详情（可选）")
            }
        }
        if (showCloudSecondClass) {
            item { SecondClassPanel(cloud, openWebsite) }
        }
        // 本机任务 JSON 备份与合并恢复（PR #213）保持原样。
        item { LocalTaskBackupPanel() }
    }
}

@Composable
private fun AcademicPage(selected: AcademicDestination, select: (AcademicDestination) -> Unit,
    cloud: CloudClient, openWebsite: () -> Unit, openArticle: (String) -> Unit, openProfile: () -> Unit) {
    val labels = listOf("通知", "成绩", "考试", "第二课堂", "校园服务")
    val tabStateHolder = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 0.dp) {
            AcademicDestination.entries.forEachIndexed { index, item ->
                Tab(selected = selected == item, onClick = { select(item) }, text = { Text(labels[index]) })
            }
        }
        when (selected) {
            AcademicDestination.NOTICES -> MessageCenterPanel(cloud, openWebsite, openArticle)
            AcademicDestination.SECOND_CLASS -> tabStateHolder.SaveableStateProvider("second_class") { SecondClassPage() }
            AcademicDestination.GRADES, AcademicDestination.EXAMS -> Content {
                item { Heading(labels[selected.ordinal], "尚未连接可验证的本人数据") }
                item { TextButton(onClick = openProfile) { Text("查看数据连接") } }
            }
            AcademicDestination.SERVICES -> Content {
                item { Heading("校园服务", "学校数据连接与服务") }
                item { TextButton(onClick = openProfile) { Text("管理数据连接") } }
                item { TextButton(onClick = openWebsite) { Text("打开校序网站") } }
            }
        }
    }
}
