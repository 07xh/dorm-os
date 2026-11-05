package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/**
 * Android native entry: no WebView wrapper and no duplicate school credential collector.
 * Existing owner-auth and connector pages remain on the verified HTTPS website.
 */
class MainActivity : ComponentActivity() {
    private val dao by lazy { LocalTaskDatabase.open(applicationContext).tasks() }
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
        localLessons = LocalScheduleStore.load(this)
        receiveSchoolReturn(intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2563EB),
                    background = Color(0xFFF5F8FD),
                    surface = Color.White
                )
            ) {
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
                    openLocalSchool = { provider ->
                        if (provider == "ncwu") {
                            startActivity(Intent(this@MainActivity, LocalQingguoImportActivity::class.java))
                        } else if (provider == "chaoxing" || provider == "second_class") {
                            lastLocalCapture = null
                            nativeSchoolLauncher.launch(
                                Intent(this@MainActivity, NativeSchoolConnectActivity::class.java)
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
    HOME("首页", Icons.Outlined.Home),
    SCHEDULE("课表", Icons.Outlined.DateRange),
    TASKS("任务", Icons.Outlined.CheckCircle),
    MESSAGES("消息", Icons.Outlined.MailOutline),
    PROFILE("我的", Icons.Outlined.PersonOutline)
}

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
    openLocalSchool: (String) -> Unit,
    consumeWebsiteReturn: () -> Boolean,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Page.HOME) }
    val localTasks by dao.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(localCaptureReturn) {
        if (localCaptureReturn != null) {
            // Imported courses/homework belong in Tasks; second-class records
            // currently have their full saved-record view under Profile.
            current = if (localCaptureReturn.provider == "chaoxing") Page.TASKS
                      else Page.PROFILE
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
            topBar = {
                TopAppBar(
                    title = {
                        Text("校序", fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge)
                    },
                    actions = {
                        IconButton(onClick = { scope.launch { cloud.refresh() } },
                            enabled = !cloud.state.busy) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新云端与官方通知")
                        }
                    }
                )
            },
            bottomBar = {
                if (!tabletLayout) {
                    NavigationBar {
                        Page.entries.forEach { page ->
                            NavigationBarItem(
                                selected = current == page,
                                onClick = { current = page },
                                icon = { Icon(page.symbol, contentDescription = null) },
                                label = { Text(page.label) }
                            )
                        }
                    }
                }
            }
        ) { contentPadding ->
            Row(Modifier.fillMaxSize().padding(contentPadding)) {
                if (tabletLayout) {
                    NavigationRail {
                        Page.entries.forEach { page ->
                            NavigationRailItem(
                                selected = current == page,
                                onClick = { current = page },
                                icon = { Icon(page.symbol, contentDescription = null) },
                                label = { Text(page.label) },
                                alwaysShowLabel = true
                            )
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    when (current) {
                        Page.HOME -> RealAcademicHome(localTasks, client = cloud,
                            localLessons = localLessons,
                            openSchedule = { current = Page.SCHEDULE },
                            openTasks = { current = Page.TASKS },
                            openMessages = { current = Page.MESSAGES },
                            openProfile = { current = Page.PROFILE },
                            openLocalSchool = openLocalSchool)
                        Page.SCHEDULE -> SchedulePage(openWebsite, cloud,
                            openProfile = { current = Page.PROFILE },
                            localLessons = localLessons, openLocalImport = openLocalImport)
                        Page.TASKS -> RealAcademicTaskHub(localTasks, dao, cloud, openWebsite,
                            openLocalSchool = openLocalSchool)
                        Page.MESSAGES -> MessagesPage(openWebsite, cloud, openArticle)
                        Page.PROFILE -> ConnectionsPage(
                            openWebsite, cloud, openLocalSchool,
                            startSchoolAuthorization, localCaptureReturn
                        )
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
    localLessons: List<VerifiedLesson>, openLocalImport: () -> Unit
) {
    // 课表页自持整屏滚动（星期条 + 课程列表），因此不再外包一层 Content/LazyColumn。
    CloudTimetablePanel(cloud, openWebsite, openProfile, localLessons, openLocalImport)
}

@Composable
private fun MessagesPage(
    openWebsite: () -> Unit,
    cloud: CloudClient,
    openArticle: (String) -> Unit
) {
    // 消息中心自持整屏滚动（搜索 / 来源筛选 / 全文 sheet），因此不再外包一层 Content。
    MessageCenterPanel(cloud, openWebsite, openArticle)
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
