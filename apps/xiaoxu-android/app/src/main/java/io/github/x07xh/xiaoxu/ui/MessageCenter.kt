package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.launch

/*
 * 校序消息中心（Issue #209 的"消息页"子任务）。
 *
 * 数据边界：
 * - 不发起新请求、不解析新接口、不写数据库。唯一数据源是 CloudClient.state 里已有的
 *   notices（华水官方教务公告，CloudClient 已做 https/host/路径白名单）与
 *   pendingWechatCount（微信待审核候选的真实计数）。
 * - 公告只有标题 / 分类 / 日期标签 / 原文链接，没有正文。因此"全文"sheet 不编造正文，
 *   只展示完整标题与来源信息，并提供跳转学校官网原文的入口。
 * - 微信候选经现有本人会话读取，App 原生审核只显示已接收的候选摘要，
 *   不伪造完整微信聊天记录；确认后由服务端创建普通云端任务。
 */

// ---------------------------------------------------------------------------
// 纯逻辑：筛选 / 计数 / 相位（可脱离 Compose 单独验证）
// ---------------------------------------------------------------------------

const val MSG_FILTER_ALL = "all"
const val MSG_FILTER_NOTICE = "notice"
const val MSG_FILTER_EXAM = "exam"
const val MSG_FILTER_WECHAT = "wechat"

data class MessageFilterOption(val key: String, val label: String)

/** 顺序对齐设计稿的 pill 行：全部 → 分类 → 待审核。 */
val MESSAGE_FILTERS = listOf(
    MessageFilterOption(MSG_FILTER_ALL, "全部"),
    MessageFilterOption(MSG_FILTER_NOTICE, "通知公告"),
    MessageFilterOption(MSG_FILTER_EXAM, "考试安排"),
    MessageFilterOption(MSG_FILTER_WECHAT, "微信待审核")
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
    if (!state.authenticated && state.pendingWechatCount == null &&
        state.noticeFetchedAt == null
    ) {
        return MessagePhase.NotConnected
    }
    return MessagePhase.Empty
}

/** 待审核数量的真实文案；null 表示本次读取失败，不能当成 0。 */
fun pendingWechatLabel(count: Int?, authenticated: Boolean): String = when {
    !authenticated -> "未连接校序云端，无法读取待审核数量"
    count == null -> "本次未成功读取待审核数量"
    count >= 30 -> "至少 30 条待审核候选（接口单次只返回前 30 条）"
    else -> "待审核候选 $count 条"
}

/** 分区徽标用的短文案；完整口径仍由 pendingWechatLabel 表达，这里只是入口提示。 */
fun pendingWechatShortLabel(count: Int?, authenticated: Boolean): String = when {
    !authenticated -> "未连接云端"
    count == null -> "数量未读取"
    count == 0 -> "无待审核"
    count >= 30 -> "≥30 条待审核"
    else -> "$count 条待审核"
}

// ---------------------------------------------------------------------------
// 视觉常量
// ---------------------------------------------------------------------------

private val ContentMaxWidth = 860.dp
private val CardShape = RoundedCornerShape(18.dp)
private val TileShape = RoundedCornerShape(13.dp)

private val ExamTile = Color(0xFFFFF1DE)
private val ExamInk = Color(0xFFB0741F)
private val NoticeTile = Color(0xFFE7EFFF)
private val NoticeInk = Color(0xFF2563EB)

private fun Modifier.pageWidth(max: androidx.compose.ui.unit.Dp): Modifier =
    this.widthIn(max = max).fillMaxWidth()

// ---------------------------------------------------------------------------
// 入口：MainActivity 消息页直接调用
// ---------------------------------------------------------------------------

@Composable
fun MessageCenterPanel(
    client: CloudClient,
    openWebsite: () -> Unit,
    openArticle: (String) -> Unit
) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val phase = remember(state) { deriveMessagePhase(state) }

    var query by rememberSaveable { mutableStateOf("") }
    var filterKey by rememberSaveable { mutableStateOf(MSG_FILTER_ALL) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }

    val refresh: () -> Unit = { scope.launch { client.refresh() } }
    val filtered = remember(state.notices, query, filterKey) {
        filterNotices(state.notices, query, filterKey)
    }
    val showWechat = filterKey == MSG_FILTER_ALL || filterKey == MSG_FILTER_WECHAT

    // 官方公告列表只在真有可展示的公告相位时出现，加载/失败由各自状态卡承担。
    val showOfficialSection = filterKey != MSG_FILTER_WECHAT &&
        (phase is MessagePhase.Content || phase is MessagePhase.Empty)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val sidePadding = when {
            wide -> 28.dp
            maxWidth < 380.dp -> 14.dp
            else -> 18.dp
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().pageWidth(ContentMaxWidth),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    top = 10.dp,
                    bottom = 26.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    MessageHeader(
                        noticeTotal = state.notices.size,
                        matched = filtered.size,
                        filterKey = filterKey,
                        pendingCount = state.pendingWechatCount,
                        authenticated = state.authenticated,
                        onRefresh = refresh
                    )
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .semantics { contentDescription = "搜索官方公告：标题或日期" },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("搜索官方公告标题或日期") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "清空搜索",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )
                }
                item {
                    FilterRow(selected = filterKey, onSelect = { filterKey = it })
                }
                item {
                    MessageCountLine(
                        filterKey = filterKey,
                        matched = filtered.size,
                        query = query,
                        total = state.notices.size
                    )
                }
                if (state.publicError != null && state.notices.isNotEmpty()) {
                    item {
                        MessageBanner(
                            icon = Icons.Outlined.Warning,
                            text = "刷新失败，下面显示的是上一次成功读取的公告，可能已经过期。",
                            container = MaterialTheme.colorScheme.errorContainer,
                            ink = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                if (state.noticesPartial && state.notices.isNotEmpty()) {
                    item {
                        MessageBanner(
                            icon = Icons.Outlined.Info,
                            text = "部分官方栏目读取失败，列表可能不完整。",
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            ink = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (phase) {
                    MessagePhase.Loading -> items(count = 3) { MessageSkeleton() }
                    is MessagePhase.Failed -> item {
                        MessageErrorCard(phase.message, refresh, openWebsite)
                    }
                    else -> Unit
                }

                if (showWechat) {
                    item {
                        SourceSectionHeader(
                            icon = Icons.Outlined.PersonOutline,
                            title = "微信候选",
                            badge = "本人云端 · 未经学校核验",
                            detail = pendingWechatShortLabel(
                                state.pendingWechatCount,
                                state.authenticated
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

                if (showOfficialSection) {
                    item {
                        SourceSectionHeader(
                            icon = Icons.Outlined.MailOutline,
                            title = "华水官方公告",
                            badge = "官方网站 · 只读",
                            detail = if (query.isBlank()) "共 ${filtered.size} 条"
                            else "匹配 ${filtered.size} 条"
                        )
                    }
                    items(count = filtered.size) { index ->
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
// 头部 / 搜索 / 筛选
// ---------------------------------------------------------------------------

@Composable
private fun MessageHeader(
    noticeTotal: Int,
    matched: Int,
    filterKey: String,
    pendingCount: Int?,
    authenticated: Boolean,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "消息中心",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("刷新")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "官方公告与微信候选分区展示，来源各自标注，不互相冒充。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        // 窄屏可横向滚动，避免两块来源徽标被挤压换行。
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SourceChip(
                label = "华水官方公告",
                detail = when {
                    noticeTotal == 0 -> "暂无已保存公告"
                    filterKey == MSG_FILTER_NOTICE || filterKey == MSG_FILTER_EXAM ->
                        "其中 $matched 条"
                    else -> "共 $noticeTotal 条"
                },
                icon = Icons.Outlined.MailOutline,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                ink = MaterialTheme.colorScheme.onTertiaryContainer
            )
            SourceChip(
                label = "微信候选",
                detail = pendingWechatShortLabel(pendingCount, authenticated),
                icon = Icons.Outlined.PersonOutline,
                container = MaterialTheme.colorScheme.surfaceVariant,
                ink = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SourceChip(
    label: String,
    detail: String,
    icon: ImageVector,
    container: Color,
    ink: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label：$detail" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = ink
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 1
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = ink.copy(alpha = 0.78f),
            maxLines = 1
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
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.width(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
                isSelected = selected == option.key,
                a11yLabel = when (option.key) {
                    MSG_FILTER_ALL -> "全部来源筛选"
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
    isSelected: Boolean,
    a11yLabel: String,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "message-pill-bg"
    )
    val foreground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "message-pill-fg"
    )
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            // selected 由 selectable 提供，与 selectableGroup 一起被读屏当作单选组播报。
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = a11yLabel },
        contentAlignment = Alignment.Center
    ) {
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
            "本视图只列出本人云端候选，可逐条审核；官方公告不在本视图内，也不参与这里的计数。"
        query.isNotBlank() ->
            "关键词“" + query.trim() + "”只用于官方公告：命中 $matched 条；微信候选不参与搜索。"
        total == 0 ->
            "当前没有已保存的官方公告；是否成功读取请以刷新状态为准。微信候选另行展示。"
        filterKey == MSG_FILTER_ALL ->
            "官方公告已读取 $total 条，当前可展示 $matched 条；微信候选在上方单独分区。"
        else ->
            "“" + filterLabel + "”共 $matched 条（已读取官方公告 $total 条）。"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append(noticeCategoryLabel(notice.category))
                    append("：")
                    append(notice.title)
                    if (notice.dateLabel.isNotBlank()) {
                        append("，官网日期 ")
                        append(notice.dateLabel)
                    }
                    append("。详情不含正文，正文需到学校官网查看")
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CardShape
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(TileShape)
                    .background(if (isExam) ExamTile else NoticeTile),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExam) Icons.Outlined.DateRange else Icons.Outlined.MailOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isExam) ExamInk else NoticeInk
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                // 来源与日期允许各占一行内两段，窄屏时日期不会被标题挤掉。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "华水教务官网 · " + noticeCategoryLabel(notice.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (notice.dateLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = notice.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "查看详情",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 详情 sheet：只有官方标题、分类、日期标签与链接；不伪造正文 / 附件
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeDetailSheet(
    notice: AcademicNotice,
    openArticle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "公告详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )
                Text(
                    text = noticeCategoryLabel(notice.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(16.dp)
            ) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleMedium,
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "校序只保存官方标题、分类、日期标签与原文链接；这里不复制网页正文、" +
                    "不生成摘要，也没有附件下载入口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { openArticle(notice.url) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("在官网查看原文")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun SheetMetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// 空态 / 错误 / 加载 / 横幅
// ---------------------------------------------------------------------------

@Composable
private fun MessageEmptyHint(query: String?) {
    MessageBanner(
        icon = Icons.Outlined.Search,
        text = if (query == null) "该筛选下没有已成功读取的官方公告。微信候选不在官方公告里，请切到“微信待审核”查看。"
        else "没有匹配“" + query.trim() + "”的官方公告；搜索只覆盖官方公告，不搜索微信候选。",
        container = MaterialTheme.colorScheme.surface,
        ink = MaterialTheme.colorScheme.onSurfaceVariant
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = CardShape
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "官方公告读取失败",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = message + "。失败时不展示示例消息，也不保留无法确认的新内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("重试")
                }
                OutlinedButton(onClick = openWebsite, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("查看连接状态")
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
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}
