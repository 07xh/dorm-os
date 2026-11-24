package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.ui.v2.UiV2SearchField
import io.github.x07xh.xiaoxu.ui.v2.uiV2DetailWidth
import io.github.x07xh.xiaoxu.ui.v2.LocalUiV2Motion
import io.github.x07xh.xiaoxu.ui.v2.UiV2MotionMode

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.AcademicNotice
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.LocalTask
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import io.github.x07xh.xiaoxu.data.LocalCourseReminders
import io.github.x07xh.xiaoxu.data.LocalDeadlineReminders
import io.github.x07xh.xiaoxu.data.LocalReminderQuietHours
import io.github.x07xh.xiaoxu.data.LocalSemesterConfigStore
import io.github.x07xh.xiaoxu.data.LocalReminderPreview
import io.github.x07xh.xiaoxu.data.LocalReminderOrigin
import io.github.x07xh.xiaoxu.data.plannedLocalReminderInbox
import java.time.ZonedDateTime
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/*
 * 校序消息中心（Issue #209 消息页 → Issue #275 海报级复刻）。
 *
 * 数据边界：
 * - 不发起新请求、不解析新接口、不写数据库。唯一数据源是 CloudClient.state 里已有的
 *   notices（华水官方教务公告，CloudClient 已做 https/host/路径白名单）与
 *   pendingWechatCount（微信待审核候选的真实计数）。
 * - 公告只有标题 / 分类 / 日期标签 / 原文链接，没有正文。因此"全文"sheet 不编造正文，
 *   只展示完整标题与来源信息，并提供跳转学校官网原文的入口。
 * - 微信候选经现有本人会话读取，App 原生审核只显示已接收的候选摘要，
 *   不伪造完整微信聊天记录；确认后由服务端创建普通云端任务。
 * - 使用统一原生蓝白视觉：紧凑标题、来源 Tab、搜索、四枚 pill、
 *   图标 + 短句 + 右侧箭头；海报里的演示消息、角标与智能总结一律不落地。
 */

// ---------------------------------------------------------------------------
// 纯逻辑：筛选 / 计数 / 相位（可脱离 Compose 单独验证）
// ---------------------------------------------------------------------------

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

/** pill 图标：只表达筛选维度，不表达查看状态或数量。 */
private fun messageFilterIcon(key: String): ImageVector = when (key) {
    MSG_FILTER_NOTICE -> Icons.Outlined.MailOutline
    MSG_FILTER_EXAM -> Icons.Outlined.DateRange
    MSG_FILTER_WECHAT -> Icons.Outlined.PersonOutline
    else -> Icons.Outlined.Inbox
}

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

sealed interface MessagePhase {
    data object Loading : MessagePhase
    data object NotConnected : MessagePhase
    data class Failed(val message: String) : MessagePhase
    data object Empty : MessagePhase
    data class Content(val notices: List<AcademicNotice>) : MessagePhase
}

/** CloudUiState 没有"公告已读过"标志，busy 与 publicError 组合足以判定四态。 */
fun deriveMessagePhase(state: CloudUiState): MessagePhase {
    if (state.notices.isNotEmpty()) return MessagePhase.Content(state.notices)
    if (state.busy) return MessagePhase.Loading
    if (state.publicError != null) {
        return MessagePhase.Failed(state.publicError)
    }
    // An owner login or WeChat count is NOT evidence that the public notice feed
    // was fetched. Only the notice endpoint's fetchedAt can establish a verified empty.
    if (state.noticeFetchedAt.isNullOrBlank()) return MessagePhase.NotConnected
    return MessagePhase.Empty
}

/**
 * 待审核数量的真实文案；null 表示本次读取失败，不能当成 0。
 * wechatError != null 说明本次微信候选读取失败，count 是上次保留的缓存值，
 * 必须标注"（快照）"，不能冒充本次成功读取的数量。
 */
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

// ---------------------------------------------------------------------------
// 视觉常量
// ---------------------------------------------------------------------------

private val CardShape = RoundedCornerShape(XiaoxuRadius.list)


// ---------------------------------------------------------------------------
// 入口：MainActivity 消息页直接调用
// ---------------------------------------------------------------------------

@Composable
fun MessageCenterPanel(
    client: CloudClient,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit,
    useStatusBarInset: Boolean = false,
    onBack: (() -> Unit)? = null,
    localTasks: List<LocalTask>,
    localLessons: List<VerifiedLesson>,
    onOpenLocalTasks: (Long) -> Unit,
    onOpenLocalSchedule: (String, Int) -> Unit,
    onOpenReminderSettings: () -> Unit
) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val phase = remember(state) { deriveMessagePhase(state) }
    val context = LocalContext.current
    val upcomingLocal by produceState<List<LocalReminderPreview>?>(
        initialValue = null, key1 = localTasks, key2 = localLessons
    ) {
        value = withContext(Dispatchers.IO) {
            val now = ZonedDateTime.now()
            plannedLocalReminderInbox(
                tasks = localTasks, lessons = localLessons,
                calendar = LocalSemesterConfigStore.load(context),
                now = now,
                taskEnabled = LocalDeadlineReminders.enabled(context),
                selectedCourseIds = localLessons.asSequence().filter {
                    LocalCourseReminders.enabledForCourse(context, it.id)
                }.map { it.id }.toSet(),
                taskSchedule = LocalDeadlineReminders.schedule(context),
                leadMinutes = LocalCourseReminders.leadMinutes(context),
                quiet = LocalReminderQuietHours.load(context)
            )
        }
    }

    var query by rememberSaveable { mutableStateOf("") }
    var sourceTab by rememberSaveable { mutableStateOf(MSG_FILTER_NOTICE) }
    var filterKey by rememberSaveable { mutableStateOf(MSG_FILTER_ALL) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var localSummaryExpanded by rememberSaveable { mutableStateOf(false) }
    // Each source retains its own scroll position; switching from a long official list
    // must never land mid-way through the much shorter WeChat review page.
    val officialListState = rememberLazyListState()
    val wechatListState = rememberLazyListState()

    val refresh: () -> Unit = { scope.launch { client.refresh() } }
    val filtered = remember(state.notices, query, filterKey) {
        filterNotices(state.notices, query, filterKey)
    }
    // 稳定 key：筛选 / 搜索会重排 filtered，没有 key 时 LazyColumn 按 index 复用 slot，
    // NoticeCard 内部的 MutableInteractionSource 会串到别的公告上。
    // 公告 id 可能为空或重复，这里先去重，避免 "Key was already used" 崩溃。
    val noticeKeys = remember(filtered) {
        val seen = HashSet<String>(filtered.size)
        filtered.map { notice ->
            val base = notice.id.ifBlank { notice.url }.ifBlank { "notice" }
            var candidate = base
            var suffix = 1
            while (!seen.add(candidate)) {
                suffix++
                candidate = "$base#$suffix"
            }
            candidate
        }
    }
    val showWechat = sourceTab == MSG_FILTER_WECHAT
    val opened = state.notices.firstOrNull { (it.id.ifBlank { it.url }) == openedId }

    // The two sources never masquerade as one another in the visible list.
    val showOfficialSection = !showWechat &&
        (phase is MessagePhase.Content || phase is MessagePhase.Empty)

    BoxWithConstraints(
        if (useStatusBarInset) Modifier.fillMaxSize().statusBarsPadding()
        else Modifier.fillMaxSize()
    ) {
        val detailWidth = uiV2DetailWidth(maxWidth.value, maxHeight.value,
            mainMin = 400f, detailMin = 320f, detailMax = 420f, gap = 12f,
            horizontalPadding = 78f)
        val wide = detailWidth != null
        val sidePadding = when {
            wide -> 28.dp
            maxWidth < 380.dp -> 14.dp
            else -> 18.dp
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(Modifier.fillMaxHeight().xiaoxuPageWidth(XiaoxuPageLayout.ContentMaxWidth)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = sidePadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MessageHeader(onRefresh = refresh, onBack = onBack)
                    // Keep source switching available even deep in the message list.
                    val noticeTitle = "官方通知" +
                        if (state.notices.isNotEmpty()) " (${state.notices.size})" else ""
                    val wechatTitle = "微信审核" +
                        if (state.authenticated && state.wechatError == null &&
                            state.pendingWechatCount != null)
                            " (${state.pendingWechatCount})" else ""
                    XiaoxuSegmentedControl(
                        options = listOf(
                            XiaoxuSegmentOption(MSG_FILTER_NOTICE, noticeTitle),
                            XiaoxuSegmentOption(MSG_FILTER_WECHAT, wechatTitle)
                        ),
                        selectedKey = sourceTab,
                        onSelected = { sourceTab = it }
                    )
                }
                LazyColumn(
                    state = if (showWechat) wechatListState else officialListState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = sidePadding, end = sidePadding,
                        top = 10.dp, bottom = 26.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!showWechat) item(key = "local-reminders") {
                        LocalReminderSummary(
                            previews = upcomingLocal,
                            expanded = localSummaryExpanded,
                            onExpand = { localSummaryExpanded = !localSummaryExpanded },
                            onOpenTask = onOpenLocalTasks,
                            onOpenCourse = onOpenLocalSchedule,
                            onSettings = onOpenReminderSettings
                        )
                    }
                if (!showWechat) {
                    item {
                        UiV2SearchField(
                            query = query,
                            onQuery = { query = it.take(60) },
                            placeholder = "搜索官方公告标题或日期"
                        )
                    }
                    item { FilterRow(selected = filterKey, onSelect = { filterKey = it }) }
                    // 默认数量已经在来源选择器显示；仅筛选/搜索时补充命中数。
                    if (query.isNotBlank() || filterKey != MSG_FILTER_ALL) item {
                        MessageCountLine(
                            filterKey = filterKey,
                            matched = filtered.size,
                            query = query,
                            total = state.notices.size
                        )
                    }
                }
                if (!showWechat && state.publicError != null && state.notices.isNotEmpty()) {
                    item {
                        MessageBanner(
                            icon = Icons.Outlined.Warning,
                            text = "刷新失败，显示上次读取的公告。",
                            container = MaterialTheme.colorScheme.errorContainer,
                            ink = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                if (!showWechat && state.noticesPartial && state.notices.isNotEmpty()) {
                    item {
                        MessageBanner(
                            icon = Icons.Outlined.Info,
                            text = "部分栏目读取失败。",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            ink = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!showWechat) {
                    when (phase) {
                        MessagePhase.Loading -> items(count = 3) { MessageSkeleton() }
                        MessagePhase.NotConnected -> item {
                            MessageBanner(
                                icon = Icons.Outlined.Info,
                                text = "尚未成功读取官方公告。可以点击右上角“刷新”；学校公开公告无需登录校序云端。",
                                container = XiaoxuPalette.GlassCard,
                                ink = XiaoxuPalette.Ink2
                            )
                        }
                        is MessagePhase.Failed -> item {
                            MessageErrorCard(phase.message, refresh, openWebsite)
                        }
                        else -> Unit
                    }
                }

                if (showOfficialSection) {
                    // Source tab shows the total; each notice retains its official-source
                    // identity. Avoid a second full-width header before the first row.
                    items(count = filtered.size, key = { index -> noticeKeys[index] }) { index ->
                        val notice = filtered[index]
                        NoticeCard(
                            notice = notice,
                            onClick = { openedId = notice.id.ifBlank { notice.url } }
                        )
                    }
                    if (filtered.isEmpty()) {
                        item { MessageEmptyHint(query = query.takeIf { it.isNotBlank() }) }
                    }
                }

                if (showWechat) {
                    item {
                        SourceSectionHeader(
                            icon = Icons.Outlined.PersonOutline,
                            title = "微信候选",
                            badge = "本人云端",
                            detail = pendingWechatShortLabel(
                                state.pendingWechatCount,
                                state.authenticated,
                                state.wechatError
                            )
                        )
                    }
                    item {
                        WechatReviewPanel(
                            client = client,
                            pendingCount = state.pendingWechatCount,
                            openWebsite = openWebsite
                        )
                    }
                }

                }
            }
        }
        if (detailWidth != null && !showWechat) {
            Column(Modifier.width((detailWidth + 22f).dp).fillMaxHeight()
                .padding(top = 10.dp, end = 22.dp)) {
                NoticeTabletDetailPane(
                    notice = opened,
                    openArticle = openArticle
                )
            }
        }
        }
        if (!wide && !showWechat && opened != null) {
            NoticeDetailSheet(
                notice = opened,
                openArticle = openArticle,
                onDismiss = { openedId = null }
            )
        }
    }
}

/** Real local WorkManager plans, not a delivered-notification history. */
@Composable
private fun LocalReminderSummary(
    previews: List<LocalReminderPreview>?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onOpenCourse: (String, Int) -> Unit,
    onSettings: () -> Unit
) {
    XiaoxuCard(contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .clip(RoundedCornerShape(XiaoxuRadius.tile))
            .clickable(role = Role.Button, onClickLabel = "查看本机提醒计划",
                onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.DateRange, contentDescription = null,
                tint = XiaoxuPalette.Ink2, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("本机提醒" + (previews?.let { " · ${it.size}" } ?: ""),
                    style = MaterialTheme.typography.titleSmall, color = XiaoxuPalette.Ink)
                Text(if (previews == null) "正在读取本机计划"
                     else "未来七天计划 · 非已送达记录",
                    style = MaterialTheme.typography.labelSmall, color = XiaoxuPalette.Ink2)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                tint = XiaoxuPalette.Ink2)
        }
        if (expanded) {
            if (previews.isNullOrEmpty()) {
                Text(if (previews == null) "正在检查提醒配置"
                     else "未来七天暂无已启用且未被免打扰跳过的本机提醒",
                    style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
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
                                color = XiaoxuPalette.Ink, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(preview.detail, style = MaterialTheme.typography.labelSmall,
                                color = XiaoxuPalette.Ink2, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                            tint = XiaoxuPalette.Ink3, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClickLabel = "打开提醒设置",
                    onClick = onSettings),
                verticalAlignment = Alignment.CenterVertically) {
                Text("管理提醒设置", style = MaterialTheme.typography.labelMedium,
                    color = XiaoxuPalette.Brand)
            }
        }
    }
}

/** Wide screens retain the source list and detail at the same time, without extra dialogs. */
@Composable
private fun NoticeTabletDetailPane(
    notice: AcademicNotice?,
    openArticle: (String) -> Unit
) {
    XiaoxuCard {
        if (notice == null) {
            Text("公告详情", style = MaterialTheme.typography.titleLarge,
                color = XiaoxuPalette.Ink)
            Text("选择左侧公告查看来源、发布日期和官网链接。",
                style = MaterialTheme.typography.bodyMedium, color = XiaoxuPalette.Ink2)
        } else {
            Text("公告详情", style = MaterialTheme.typography.titleLarge,
                color = XiaoxuPalette.Ink)
            SourceBadge(text = noticeCategoryLabel(notice.category),
                tone = if (notice.category == "exam") XiaoxuTone.Warning else XiaoxuTone.Brand)
            Text(notice.title, style = MaterialTheme.typography.titleMedium,
                color = XiaoxuPalette.Ink, fontWeight = FontWeight.SemiBold)
            SheetMetaRow("来源", "华水教务官网")
            SheetMetaRow("发布", notice.dateLabel.ifBlank { "未标注" })
            Text("正文请在学校官网查看",
                style = MaterialTheme.typography.bodySmall, color = XiaoxuPalette.Ink2)
            XiaoxuDialogPrimaryButton(onClick = { openArticle(notice.url) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("在官网查看原文")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 头部：统一紧凑标题；来源计数由 Tab 呈现，不再堆叠摘要卡
// ---------------------------------------------------------------------------

/** 紧凑统一页头；来源计数归入来源标签，不重复堆叠摘要卡片。 */
@Composable
private fun MessageHeader(onRefresh: () -> Unit, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            XiaoxuToolbarAction(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                label = "返回原页面",
                onClick = onBack
            )
        }
        Text(
            text = "消息中心",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = XiaoxuPalette.Ink,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        XiaoxuToolbarAction(
            icon = Icons.Outlined.Refresh,
            label = "刷新官方公告与待审核数量",
            onClick = onRefresh
        )
    }
}

/**
 * 来源分区标题：把"哪一份数据"写在列表前面，避免混合页面被当成同一个来源。
 */
@Composable
private fun SourceSectionHeader(
    icon: ImageVector,
    title: String,
    badge: String,
    detail: String
) {
    // 只用一行呈现来源、数量和身份：不在分段控件下重复堆标题+徽标两层。
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        SourceBadge(
            text = badge,
            tone = if (title == "微信候选") XiaoxuTone.Warning else XiaoxuTone.Brand
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = XiaoxuPalette.Ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// 搜索 / 筛选
// ---------------------------------------------------------------------------

@Composable
private fun FilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(count = MESSAGE_FILTERS.size, key = { index -> MESSAGE_FILTERS[index].key }) { index ->
            val option = MESSAGE_FILTERS[index]
            MessagePill(
                label = option.label,
                icon = messageFilterIcon(option.key),
                isSelected = selected == option.key,
                a11yLabel = when (option.key) {
                    MSG_FILTER_ALL -> "全部官方公告筛选"
                    MSG_FILTER_WECHAT -> "微信候选筛选"
                    else -> option.label + "筛选"
                },
                onClick = { onSelect(option.key) }
            )
        }
    }
}

@Composable
private fun MessagePill(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    a11yLabel: String,
    onClick: () -> Unit
) {
    val reduced = LocalUiV2Motion.current == UiV2MotionMode.Reduced
    val background by animateColorAsState(
        targetValue = if (isSelected) XiaoxuPalette.GlassIndicator
        else XiaoxuPalette.GlassTrack,
        animationSpec = tween(if (reduced) 0 else XiaoxuMotion.SelectionMs),
        label = "message-pill-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) XiaoxuPalette.BrandDeep
        else XiaoxuPalette.Ink2,
        animationSpec = tween(if (reduced) 0 else XiaoxuMotion.SelectionMs),
        label = "message-pill-fg"
    )
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(
                width = XiaoxuSpacing.glassStroke,
                color = XiaoxuPalette.GlassBorder,
                shape = RoundedCornerShape(50)
            )
            // selected 由 selectable 提供，与 selectableGroup 一起被读屏当作单选组播报。
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { contentDescription = a11yLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = foreground
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

@Composable
private fun MessageCountLine(
    filterKey: String,
    matched: Int,
    query: String,
    total: Int
) {
    val filterLabel = MESSAGE_FILTERS.firstOrNull { it.key == filterKey }?.label ?: "全部"
    val text = when {
        filterKey == MSG_FILTER_WECHAT ->
            "本人云端候选 · 逐条审核"
        query.isNotBlank() ->
            "命中 $matched 条"
        total == 0 ->
            "暂无已保存的官方公告"
        filterKey == MSG_FILTER_ALL ->
            "官方公告 $total 条"
        else ->
            "“" + filterLabel + "” $matched 条"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = XiaoxuPalette.Ink3,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

// ---------------------------------------------------------------------------
// 公告卡片：只有标题 / 分类 / 官方日期标签 / 链接，因此入口叫「查看详情」而非「全文」
// ---------------------------------------------------------------------------

@Composable
private fun NoticeCard(notice: AcademicNotice, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val isExam = notice.category == "exam"
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(XiaoxuRadius.list))
            .background(XiaoxuPalette.GlassCard)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.Line,
                RoundedCornerShape(XiaoxuRadius.list))
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true, color = XiaoxuPalette.PressTint),
                onClickLabel = "查看公告详情与官网链接", role = Role.Button,
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
            )
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = noticeCategoryLabel(notice.category) + "：" +
                    notice.title + "，" + notice.dateLabel + "；查看详情"
            },
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(
            if (isExam) XiaoxuPalette.Warning else XiaoxuPalette.Brand))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(notice.title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, color = XiaoxuPalette.Ink,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOf(noticeCategoryLabel(notice.category), notice.dateLabel)
                .filter { it.isNotBlank() }.joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink2, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------
// 详情 sheet：只有官方标题、分类、日期标签与链接；不伪造正文 / 附件
// ---------------------------------------------------------------------------

@Composable
internal fun NoticeDetailSheet(
    notice: AcademicNotice,
    openArticle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    XiaoxuSheet(onDismiss = onDismiss, title = "公告详情") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceBadge(
                text = noticeCategoryLabel(notice.category),
                tone = if (notice.category == "exam") XiaoxuTone.Warning else XiaoxuTone.Brand
            )
        }
        // The sheet already owns the glass material; don't nest a solid title tile.
        Text(
            text = notice.title,
            style = MaterialTheme.typography.titleLarge,
            color = XiaoxuPalette.Ink,
            fontWeight = FontWeight.SemiBold
        )
        SheetMetaRow(label = "来源", value = "华水教务官网")
        SheetMetaRow(
            label = "发布",
            value = notice.dateLabel.ifBlank { "未标注" }
        )
        Text(
            text = "正文请在学校官网查看",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        XiaoxuDialogPrimaryButton(
            onClick = { openArticle(notice.url) },
            modifier = Modifier.semantics { contentDescription = "在官网查看原文" }
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("在官网查看原文")
        }
    }
}

@Composable
private fun SheetMetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = XiaoxuPalette.Ink3,
            modifier = Modifier.width(52.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2,
            modifier = Modifier.weight(1f)
        )
    }
}

// ---------------------------------------------------------------------------
// 空态 / 错误 / 加载 / 横幅
// ---------------------------------------------------------------------------

@Composable
private fun MessageEmptyHint(query: String?) {
    MessageBanner(
        icon = Icons.Outlined.Search,
        text = if (query == null) "该筛选下没有已成功读取的官方公告。微信候选不在官方公告里，请切到“微信审核”查看。"
        else "没有匹配“" + query.trim() + "”的官方公告；搜索只覆盖官方公告，不搜索微信候选。",
        container = XiaoxuPalette.GlassCard,
        ink = XiaoxuPalette.Ink2
    )
}

@Composable
private fun MessageBanner(
    icon: ImageVector,
    text: String,
    container: Color,
    ink: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .border(XiaoxuSpacing.glassStroke, XiaoxuPalette.GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = ink)
    }
}

@Composable
private fun MessageErrorCard(message: String, onRefresh: () -> Unit, openWebsite: () -> Unit) {
    XiaoxuCard(container = XiaoxuPalette.DangerSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "官方公告读取失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = XiaoxuPalette.Danger
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Danger
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(XiaoxuPalette.Danger)
                        .clickable(onClick = onRefresh, role = Role.Button)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, XiaoxuPalette.Danger.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable(onClick = openWebsite, role = Role.Button)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "查看连接状态",
                        style = MaterialTheme.typography.labelLarge,
                        color = XiaoxuPalette.Danger
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(CardShape)
            .background(XiaoxuPalette.GlassTrack)
            .semantics { contentDescription = "正在读取官方公告" }
    )
}

