package io.github.x07xh.xiaoxu.ui

// Reused original MessageCenter.kt filtering, source labels and reminder summary.
// Shared page frame, snapshot state and Material components replace old shell.
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.*
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

const val MSG_FILTER_ALL = "all"
const val MSG_FILTER_NOTICE = "notice"
const val MSG_FILTER_EXAM = "exam"
const val MSG_FILTER_WECHAT = "wechat"

data class MessageFilterOption(val key: String, val label: String)

/** Official notice subfilters; WeChat is a separate source tab, not a notice category. */
val MESSAGE_FILTERS = listOf(
    MessageFilterOption(MSG_FILTER_ALL, "全部公告"),
    MessageFilterOption(MSG_FILTER_NOTICE, "通知公告"),
    MessageFilterOption(MSG_FILTER_EXAM, "考试安排")
)


fun noticeCategoryLabel(category: String): String =
    if (category == "exam") "考试安排" else "通知公告"

/**
 * 搜索 + 来源筛选。
 * 微信待审核走独立数据源（只有计数），因此在该筛选项下公告列表恒为空，
 * 由界面单独渲染待审核卡片，而不是拿公告冒充微信消息。
 */
fun filterNotices(
    notices: List<AcademicNotice>,
    query: String,
    filterKey: String
): List<AcademicNotice> {
    val keyword = query.trim()
    if (filterKey == MSG_FILTER_WECHAT) return emptyList()
    return notices.filter { notice ->
        val bySource = when (filterKey) {
            MSG_FILTER_NOTICE -> notice.category != "exam"
            MSG_FILTER_EXAM -> notice.category == "exam"
            else -> true
        }
        if (!bySource) return@filter false
        if (keyword.isEmpty()) return@filter true
        notice.title.contains(keyword, ignoreCase = true) ||
            notice.dateLabel.contains(keyword, ignoreCase = true) ||
            noticeCategoryLabel(notice.category).contains(keyword)
    }
}


fun pendingWechatLabel(count: Int?, authenticated: Boolean, wechatError: String? = null): String = when {
    !authenticated -> "未连接校序云端，无法读取待审核数量"
    count == null -> "本次未成功读取待审核数量"
    wechatError != null -> "待审核候选 $count 条（快照，本次读取失败）"
    count >= 30 -> "至少 30 条待审核候选（接口单次只返回前 30 条）"
    else -> "待审核候选 $count 条"
}

/** 分区徽标用的短文案；完整口径仍由 pendingWechatLabel 表达，这里只是入口提示。 */
fun pendingWechatShortLabel(count: Int?, authenticated: Boolean, wechatError: String? = null): String = when {
    !authenticated -> "未连接云端"
    count == null -> "数量未读取"
    wechatError != null -> "$count（快照）"
    count == 0 -> "无待审核"
    count >= 30 -> "≥30 条待审核"
    else -> "$count 条待审核"
}



@Composable
private fun LocalReminderSummary(
    previews: List<LocalReminderPreview>?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenCourse: (String, Int) -> Unit,
    onSettings: () -> Unit
) {
    InsetGroupedSection {
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = "查看本机提醒计划",
                onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DateRange, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("本机提醒" + (previews?.let { " · ${it.size}" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(if (previews == null) "正在读取本机计划"
                     else "未来七天计划 · 非已送达记录",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            if (previews.isNullOrEmpty()) {
                Text(if (previews == null) "正在检查提醒配置"
                     else "未来七天暂无已启用且未被免打扰跳过的本机提醒",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                previews.forEach { preview ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(
                            role = Role.Button, onClickLabel = "查看" + preview.title,
                            onClick = {
                                if (preview.origin == LocalReminderOrigin.TASK) {
                                    preview.targetId.toLongOrNull()?.let(onOpenTask)
                                } else {
                                    onOpenCourse(preview.targetId, preview.weekday)
                                }
                            }
                        ).padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(preview.title, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(preview.detail, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "打开提醒设置",
                    onClick = onSettings),
                verticalAlignment = Alignment.CenterVertically) {
                Text("管理提醒设置", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}



@Composable
internal fun ColumnScope.MessageCenterFeature(data: AppWorkspaceState, actions: AppPageActions) {
    val state = data.cloud
    val phase = deriveMessagePhase(state)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val tasks = (data.localTasks as? PageSnapshot.Ready)?.value
    val lessons = (data.lessons as? PageSnapshot.Ready)?.value
    val upcomingLocal by produceState<List<LocalReminderPreview>?>(null, tasks, lessons, data.semesterCalendar) {
        if (tasks == null || lessons == null) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            plannedLocalReminderInbox(tasks, lessons, data.semesterCalendar, ZonedDateTime.now(),
                LocalDeadlineReminders.enabled(context),
                lessons.filter { LocalCourseReminders.enabledForCourse(context, it.id) }.map { it.id }.toSet(),
                LocalDeadlineReminders.schedule(context), LocalCourseReminders.leadMinutes(context),
                LocalReminderQuietHours.load(context))
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf(MSG_FILTER_NOTICE) }
    var filter by rememberSaveable { mutableStateOf(MSG_FILTER_ALL) }
    var openedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val officialList = rememberLazyListState()
    val reviewList = rememberLazyListState()
    val filtered = remember(state.notices, query, filter) { filterNotices(state.notices, query, filter).distinctBy { it.url } }
    val showWechat = source == MSG_FILTER_WECHAT
    val opened = state.notices.firstOrNull { it.url == openedUrl }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        FilterChip(selected = !showWechat, onClick = { source = MSG_FILTER_NOTICE }, label = { Text("官方通知") })
        FilterChip(selected = showWechat, onClick = { source = MSG_FILTER_WECHAT }, label = { Text("微信审核") })
        AcademicReadAction("刷新", state.busy) { scope.launch { actions.refreshCloud() } }
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val wide = maxWidth >= 840.dp && !showWechat
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = if (showWechat) reviewList else officialList,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
                item { LocalReminderSummary(upcomingLocal, expanded, { expanded = !expanded },
                    actions.openLocalTask, actions.openLocalCourse,
                    { actions.navigate(AppRoute.REMINDER_SETTINGS) }) }
                if (showWechat) {
                    item {
                        InsetGroupedSection(header = "微信候选") {
                            InsetGroupedRow(title = pendingWechatLabel(state.pendingWechatCount, state.authenticated, state.wechatError),
                                subtitle = "通过现有云端入口审核", onClick = actions.openWebsite, showDivider = false)
                        }
                    }
                } else {
                    item { OutlinedTextField(query, { query = it.take(60) }, Modifier.fillMaxWidth(),
                        label = { Text("搜索公告") }, singleLine = true) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MESSAGE_FILTERS.forEach { option ->
                                FilterChip(filter == option.key, { filter = option.key }, label = { Text(option.label) })
                            }
                        }
                    }
                    if (state.publicError != null && state.notices.isNotEmpty()) item { AppNotice("刷新失败，显示上次读取的公告。", error = true) }
                    if (state.noticesPartial) item { AppNotice("部分栏目读取失败。") }
                    when (phase) {
                        MessagePhase.Loading -> item { CircularProgressIndicator() }
                        MessagePhase.NotConnected -> item { SystemEmptyState("尚未读取公告", "点击刷新读取学校公开公告。") }
                        is MessagePhase.Failed -> item { AppNotice(phase.message, error = true) }
                        else -> {
                            if (filtered.isEmpty()) item { SystemEmptyState("暂无匹配公告", "请检查搜索条件或刷新数据。") }
                            items(filtered, key = { it.url }) { notice ->
                                InsetGroupedSection {
                                    InsetGroupedRow(title = notice.title,
                                        subtitle = noticeCategoryLabel(notice.category) + " · " + notice.dateLabel,
                                        showDivider = false, onClick = { openedUrl = notice.url })
                                }
                            }
                        }
                    }
                }
            }
            if (wide) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
                    if (opened == null) SystemEmptyState("公告详情", "选择左侧公告查看来源和原文。")
                    else NoticeDetails(opened, actions.openArticle)
                }
            }
        }
        if (!wide && !showWechat && opened != null) {
            AlertDialog(onDismissRequest = { openedUrl = null }, title = { Text("公告详情") },
                text = { NoticeDetails(opened, actions.openArticle) },
                confirmButton = { TextButton(onClick = { openedUrl = null }) { Text("关闭") } })
        }
    }
}

@Composable
private fun NoticeDetails(notice: AcademicNotice, openArticle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.item)) {
        Text(notice.title, style = MaterialTheme.typography.titleMedium)
        AppNotice(noticeCategoryLabel(notice.category) + " · 华水教务官网")
        AppNotice("发布：" + notice.dateLabel.ifBlank { "未标注" })
        Text("正文请在学校官网查看", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { openArticle(notice.url) }) { Text("在官网查看原文") }
    }
}
