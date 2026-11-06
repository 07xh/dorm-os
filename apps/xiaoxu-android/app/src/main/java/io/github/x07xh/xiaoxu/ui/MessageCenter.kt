package io.github.x07xh.xiaoxu.ui

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.unit.sp
import io.github.x07xh.xiaoxu.data.AcademicNotice
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudUiState
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

private val CardShape = RoundedCornerShape(XiaoxuRadius.card)
private val TileShape = RoundedCornerShape(XiaoxuRadius.tile)

private val ExamTile = XiaoxuPalette.WarningSoft
private val ExamInk = XiaoxuPalette.Warning
private val NoticeTile = XiaoxuPalette.BrandSoft
private val NoticeInk = XiaoxuPalette.Brand

// ---------------------------------------------------------------------------
// 入口：MainActivity 消息页直接调用
// ---------------------------------------------------------------------------

@Composable
fun MessageCenterPanel(
    client: CloudClient,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit,
    useStatusBarInset: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val phase = remember(state) { deriveMessagePhase(state) }

    var query by rememberSaveable { mutableStateOf("") }
    var sourceTab by rememberSaveable { mutableStateOf(MSG_FILTER_NOTICE) }
    var filterKey by rememberSaveable { mutableStateOf(MSG_FILTER_ALL) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
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

    // The two sources never masquerade as one another in the visible list.
    val showOfficialSection = !showWechat &&
        (phase is MessagePhase.Content || phase is MessagePhase.Empty)

    BoxWithConstraints(
        if (useStatusBarInset) Modifier.fillMaxSize().statusBarsPadding()
        else Modifier.fillMaxSize()
    ) {
        val wide = maxWidth >= 840.dp
        val sidePadding = when {
            wide -> 28.dp
            maxWidth < 380.dp -> 14.dp
            else -> 18.dp
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = if (showWechat) wechatListState else officialListState,
                modifier = Modifier.fillMaxHeight().xiaoxuPageWidth(XiaoxuPageLayout.ContentMaxWidth),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    top = 10.dp,
                    bottom = 26.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    MessageHeader(onRefresh = refresh, onBack = onBack)
                }
                item {
                    // One shared glass selector for real message sources, not a
                    // Material TabRow with another visual system inside the page.
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
                if (!showWechat) {
                    item {
                        MessageSearchField(
                            query = query,
                            onQueryChange = { query = it.take(60) }
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
                                container = XiaoxuPalette.Surface,
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
                    item {
                        SourceSectionHeader(
                            icon = Icons.Outlined.MailOutline,
                            title = "华水官方公告",
                            badge = "官方网站",
                            detail = if (query.isBlank()) "共 ${filtered.size} 条"
                            else "匹配 ${filtered.size} 条"
                        )
                    }
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

                item { MessageFooterNote() }
            }
        }
    }

    val opened = state.notices.firstOrNull { (it.id.ifBlank { it.url }) == openedId }
    if (opened != null) {
        NoticeDetailSheet(
            notice = opened,
            openArticle = openArticle,
            onDismiss = { openedId = null }
        )
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
            style = MaterialTheme.typography.headlineSmall,
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

/** 白底圆角搜索条：与海报一致的填充样式，触控高度 ≥52dp。 */
@Composable
private fun MessageSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = "搜索官方公告：标题或日期" },
        singleLine = true,
        placeholder = {
            Text(
                text = "搜索官方公告标题或日期",
                style = MaterialTheme.typography.bodyMedium,
                color = XiaoxuPalette.Ink3
            )
        },
        leadingIcon = {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = XiaoxuPalette.Ink3
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = { onQueryChange("") }, role = Role.Button)
                        .semantics { contentDescription = "清空搜索" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = XiaoxuPalette.Ink2
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = XiaoxuPalette.Surface,
            unfocusedContainerColor = XiaoxuPalette.Surface,
            disabledContainerColor = XiaoxuPalette.Surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = XiaoxuPalette.Ink,
            unfocusedTextColor = XiaoxuPalette.Ink,
            cursorColor = XiaoxuPalette.Brand
        )
    )
}

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
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else XiaoxuPalette.Surface,
        animationSpec = tween(180),
        label = "message-pill-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else XiaoxuPalette.Ink2,
        animationSpec = tween(180),
        label = "message-pill-fg"
    )
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(
                width = 1.dp,
                color = if (isSelected) background else XiaoxuPalette.Line,
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
    // 海报式横排公告卡：真实数据只有标题、分类、日期和官网链接，绝不假造摘要。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(0.5.dp, CardShape, clip = false)
            .clip(CardShape)
            .background(XiaoxuPalette.GlassCard)
            .border(0.75.dp, XiaoxuPalette.GlassBorder, CardShape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClickLabel = "查看这条公告的详情与官网链接",
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .heightIn(min = 84.dp)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(noticeCategoryLabel(notice.category))
                    append("：")
                    append(notice.title)
                    if (notice.dateLabel.isNotBlank()) {
                        append("，官网日期 ")
                        append(notice.dateLabel)
                    }
                    append("。点击查看详情与原文链接")
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (isExam) ExamTile else NoticeTile),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isExam) Icons.Outlined.DateRange else Icons.Outlined.MailOutline,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (isExam) ExamInk else NoticeInk
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = XiaoxuPalette.Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "华水教务官网",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SourceBadge(
                    text = noticeCategoryLabel(notice.category),
                    tone = if (isExam) XiaoxuTone.Warning else XiaoxuTone.Brand
                )
            }
            if (notice.dateLabel.isNotBlank()) {
                Text(
                    text = notice.dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = XiaoxuPalette.Ink3
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(XiaoxuPalette.BrandSoft)
                .padding(16.dp)
        ) {
            Text(
                text = notice.title,
                style = MaterialTheme.typography.titleMedium,
                color = XiaoxuPalette.Ink,
                fontWeight = FontWeight.SemiBold
            )
        }
        SheetMetaRow(label = "来源", value = "华水教务官网（www2.ncwu.edu.cn）")
        SheetMetaRow(
            label = "发布",
            value = if (notice.dateLabel.isBlank()) "官方页面未提供日期标签"
            else notice.dateLabel + "（原文站点给出，年份未二次核验）"
        )
        SheetMetaRow(label = "分类", value = noticeCategoryLabel(notice.category))
        SheetMetaRow(label = "正文", value = "App 内不保存正文与附件，需通过学校官网查看")
        HorizontalDivider(color = XiaoxuPalette.Line)
        Text(
            text = "校序只保存官方标题、分类、日期标签与原文链接；这里不复制网页正文、" +
                "不生成摘要，也没有附件下载入口。",
            style = MaterialTheme.typography.bodySmall,
            color = XiaoxuPalette.Ink2
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(XiaoxuPalette.Brand)
                .clickable(onClick = { openArticle(notice.url) }, role = Role.Button)
                .semantics { contentDescription = "在官网查看原文" },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "在官网查看原文",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDismiss, role = Role.Button),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "关闭",
                style = MaterialTheme.typography.labelLarge,
                color = XiaoxuPalette.Ink2
            )
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
        container = XiaoxuPalette.Surface,
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
            .border(1.dp, XiaoxuPalette.Line, RoundedCornerShape(14.dp))
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
            .height(96.dp)
            .clip(CardShape)
            .background(XiaoxuPalette.SurfaceAlt)
            .semantics { contentDescription = "正在读取官方公告" }
    )
}

@Composable
private fun MessageFooterNote() {
    Text(
        text = "官方公告来自学校官网，只有标题、分类、日期标签与原文链接；" +
            "微信候选来自本人已绑定的电脑，未经学校核验，需逐条确认。" +
            "未连接、读取失败或来源未验证时，不填充任何演示消息。",
        style = MaterialTheme.typography.labelSmall,
        color = XiaoxuPalette.Ink3,
        modifier = Modifier.padding(top = 6.dp)
    )
}
