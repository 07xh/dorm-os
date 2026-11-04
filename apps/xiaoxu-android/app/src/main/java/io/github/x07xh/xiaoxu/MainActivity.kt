package io.github.x07xh.xiaoxu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
import io.github.x07xh.xiaoxu.ui.CloudTimetablePanel
import io.github.x07xh.xiaoxu.ui.ChaoxingHomeworkPanel
import io.github.x07xh.xiaoxu.ui.CloudTasksPanel
import io.github.x07xh.xiaoxu.ui.ConnectionsPanel
import io.github.x07xh.xiaoxu.ui.LocalTaskBackupPanel
import io.github.x07xh.xiaoxu.ui.LocalTaskBoard
import io.github.x07xh.xiaoxu.ui.MessageCenterPanel
import io.github.x07xh.xiaoxu.ui.SecondClassPanel
import io.github.x07xh.xiaoxu.data.LocalTask
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2563EB),
                    background = Color(0xFFF5F8FD),
                    surface = Color.White
                )
            ) {
                NativeShell(dao = dao, cloud = cloud, appLifecycle = lifecycle, openWebsite = {
                    // Fixed origin: never forward app credentials or a school session.
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://dorm-os-dk9.pages.dev/"))
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
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Page.HOME) }
    val localTasks by dao.observe().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    DisposableEffect(appLifecycle, cloud) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { cloud.refreshOnForeground() }
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
                        Column {
                            Text("校序", fontWeight = FontWeight.SemiBold)
                            Text("Academic OS · Android", style = MaterialTheme.typography.labelSmall)
                        }
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
                        Page.HOME -> HomePage(localTasks, openTasks = { current = Page.TASKS },
                            openMessages = { current = Page.MESSAGES }, openWebsite = openWebsite, cloud = cloud)
                        Page.SCHEDULE -> SchedulePage(openWebsite, cloud,
                            openProfile = { current = Page.PROFILE })
                        Page.TASKS -> TasksPage(localTasks, dao, cloud, openWebsite)
                        Page.MESSAGES -> MessagesPage(openWebsite, cloud, openArticle)
                        Page.PROFILE -> ConnectionsPage(openWebsite, cloud)
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
private fun InfoCard(title: String, message: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null && onClick != null) {
                TextButton(onClick = onClick) {
                    Text(action)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    tasks: List<LocalTask>, openTasks: () -> Unit,
    openMessages: () -> Unit, openWebsite: () -> Unit, cloud: CloudClient
) {
    val state = cloud.state
    Content {
        item { Heading("今天", "课程与作业仅展示已成功同步的本人数据") }
        item {
            InfoCard(
                title = "本机待办：" + tasks.count { !it.completed },
                message = "本地独立保存，断网也能查看和编辑。",
                action = "查看任务", onClick = openTasks
            )
        }
        item {
            InfoCard(
                title = if (state.authenticated) "云端任务：" + state.tasks.size
                    else "云端任务未连接",
                message = if (state.authenticated)
                    "来自现有校序 API；学校作业的完成状态仍需与平台逐项核验。"
                    else "连接校序云端后可查看已保存的任务。",
                action = "管理任务", onClick = openTasks
            )
        }
        item {
            InfoCard(
                title = "真实课表尚未在 Android 验收",
                message = "沿用现有青果连接器，未获取可验证课程前不显示示例课程。",
                action = "查看网站连接状态", onClick = openWebsite
            )
        }
        item {
            InfoCard(
                title = "官方通知：" + state.notices.size + " 条",
                message = if (state.publicError == null) "来自华水官方教务公告接口。"
                    else "刷新失败，当前结果可能过期。",
                action = "查看消息", onClick = openMessages
            )
        }
    }
}

@Composable
private fun SchedulePage(openWebsite: () -> Unit, cloud: CloudClient, openProfile: () -> Unit) {
    // 课表页自持整屏滚动（星期条 + 课程列表），因此不再外包一层 Content/LazyColumn。
    CloudTimetablePanel(cloud, openWebsite, openProfile)
}

@Composable
private fun TasksPage(
    tasks: List<LocalTask>,
    dao: LocalTaskDao,
    cloud: CloudClient,
    openWebsite: () -> Unit
) {
    Content {
        item { Heading("任务", "本机待办离线保存，来源与截止由本人填写；云端作业单独显示。") }
        item { LocalTaskBoard(tasks, dao) }
        // 学习通真实课程/作业读取（PR #210）保持原样，不与本机待办合并。
        item { ChaoxingHomeworkPanel(cloud, openWebsite) }
        item { CloudTasksPanel(cloud) }
    }
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
private fun ConnectionsPage(openWebsite: () -> Unit, cloud: CloudClient) {
    Content {
        item { Heading("我的", "本机优先、云端可选，学校登录保持独立") }
        item { ConnectionsPanel(cloud, openWebsite) }
        // 第二课堂原生展示（PR #211）保持原样。
        item { SecondClassPanel(cloud, openWebsite) }
        // 本机任务 JSON 备份与合并恢复（PR #213）保持原样。
        item { LocalTaskBackupPanel() }
    }
}
