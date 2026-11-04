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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.LocalTaskDao
import io.github.x07xh.xiaoxu.data.LocalTaskDatabase
import kotlinx.coroutines.launch

/**
 * Android native entry: no WebView wrapper and no duplicate school credential collector.
 * Existing owner-auth and connector pages remain on the verified HTTPS website.
 */
class MainActivity : ComponentActivity() {
    private val dao by lazy { LocalTaskDatabase.open(applicationContext).tasks() }

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
                NativeShell(dao = dao, openWebsite = {
                    // Fixed origin: never forward app credentials or a school session.
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://dorm-os-dk9.pages.dev/"))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                    )
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
private fun NativeShell(dao: LocalTaskDao, openWebsite: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(Page.HOME) }
    val localTasks by dao.observe().collectAsState(initial = emptyList())

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
                        Page.HOME -> HomePage(localTasks, openTasks = { current = Page.TASKS }, openWebsite)
                        Page.SCHEDULE -> SchedulePage(openWebsite)
                        Page.TASKS -> TasksPage(localTasks, dao)
                        Page.MESSAGES -> MessagesPage(openWebsite)
                        Page.PROFILE -> ConnectionsPage(openWebsite)
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
private fun HomePage(tasks: List<LocalTask>, openTasks: () -> Unit, openWebsite: () -> Unit) {
    Content {
        item { Heading("今天", "在这里管理本机任务。课程数据必须来自经过验证的真实学校来源。") }
        item {
            InfoCard(
                title = "本机待办：" + tasks.count { !it.completed }.toString(),
                message = "仅统计在这台设备上创建的任务，不混入未经验证的学习通作业。",
                action = "管理任务",
                onClick = openTasks
            )
        }
        item {
            InfoCard(
                title = "课表暂未在 App 验收",
                message = "不会显示演示课程。后续从青果教务读取本人真实课表后才展示。",
                action = "前往现有校序网站",
                onClick = openWebsite
            )
        }
        item {
            InfoCard(
                title = "官方通知与微信待审核",
                message = "继续使用现有云端通知和人工审核流程，App 暂不声称已原生同步。",
                action = "打开现有功能",
                onClick = openWebsite
            )
        }
    }
}

@Composable
private fun SchedulePage(openWebsite: () -> Unit) {
    Content {
        item { Heading("课表", "手机与平板共用的原生页面") }
        item {
            InfoCard(
                "尚无已验证的本机课程",
                "正在保留青果官方登录与来源核验门禁。本版本不会凭空生成课表。",
                "在网站查看真实连接状态",
                openWebsite
            )
        }
    }
}

@Composable
private fun TasksPage(tasks: List<LocalTask>, dao: LocalTaskDao) {
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    fun addTask() {
        val title = draft.trim()
        if (title.isEmpty()) return
        draft = ""
        scope.launch { dao.insert(LocalTask(title = title)) }
    }
    Content {
        item { Heading("任务", "本机创建、离线保存；暂不与云端作业混为一谈。") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("添加本机任务") },
                        singleLine = true
                    )
                    Button(onClick = ::addTask, enabled = draft.isNotBlank()) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加任务")
                    }
                }
            }
        }
        if (tasks.isEmpty()) {
            item { InfoCard("还没有本机任务", "可以先记录自己的待办。学习通真实作业接入后将明确标识来源。") }
        }
        items(tasks, key = { it.id }) { task ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.completed,
                        onCheckedChange = { checked -> scope.launch { dao.setCompleted(task.id, checked) } }
                    )
                    Text(
                        text = task.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else null
                    )
                    IconButton(onClick = { scope.launch { dao.remove(task.id) } }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除任务")
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesPage(openWebsite: () -> Unit) {
    Content {
        item { Heading("消息", "只显示通过本人授权或公共渠道取得的信息") }
        item {
            InfoCard(
                "微信候选任务",
                "沿用现有电脑采集器与云端人工审核。手机不会在未确认时自动生成任务。",
                "进入审核页面",
                openWebsite
            )
        }
        item {
            InfoCard(
                "学校公开通知",
                "继续使用已部署的通知服务。后续接入原生消息列表前，请在网站查看。",
                "打开通知",
                openWebsite
            )
        }
    }
}

@Composable
private fun ConnectionsPage(openWebsite: () -> Unit) {
    Content {
        item { Heading("我的", "账号和数据源分别管理，不要求学校账号重复登录校序。") }
        item {
            InfoCard(
                "数据连接中心",
                "当前使用现有校序网站的授权与会话管理。App 还没有独立验证学校连接，因此不显示“已连接”。",
                "前往现有数据连接",
                openWebsite
            )
        }
        item { InfoCard("学习通", "待完成本人真实课程、作业及重启后刷新验收。") }
        item { InfoCard("青果教务", "待完成真实课表及来源一致性验收。") }
        item { InfoCard("第二课堂", "待完成活动、积分与会话恢复验收。") }
        item {
            InfoCard(
                "本地优先 · 云端辅助",
                "手动任务只保存在当前设备的 Room 数据库中。删除 App 可能删除这些数据；尚未启用跨设备同步。"
            )
        }
    }
}
