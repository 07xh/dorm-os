package io.github.x07xh.xiaoxu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MailOutline
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
 * - 微信候选的正文只存在于云端审核页，客户端不缓存、不伪造，只给出真实计数与入口。
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
private val WechatTile = Color(0xFFE1F8EC)
private val WechatInk = Color(0xFF10805C)

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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().pageWidth(ContentMaxWidth),
                contentPadding = PaddingValues(
                    start = if (wide) 28.dp else 18.dp,
                    end = if (wide) 28.dp else 18.dp,
                    top = 10.dp,
                    bottom = 26.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { MessageHeader(count = filtered.size, onRefresh = refresh) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .semantics { contentDescription = "搜索消息" },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("搜索公告标题或日期") },
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
                        WechatSourceCard(
                            count = state.pendingWechatCount,
                            authenticated = state.authenticated,
                            openWebsite = openWebsite
                        )
                    }
                }

                if (filterKey != MSG_FILTER_WECHAT) {
                    items(count = filtered.size) { index ->
                        val notice = filtered[index]
                        NoticeCard(
                            notice = notice,
                            onClick = { openedId = notice.id.ifBlank { notice.url } }
                        )
                    }
                    if (filtered.isEmpty() &&
                        phase !is MessagePhase.Loading && phase !is MessagePhase.Failed
                    ) {
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
private fun MessageHeader(count: Int, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "消息中心",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.width(8.dp))
                SourceChip()
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "来源清楚 · 可搜索 · 原文可追溯（当前 $count 条匹配）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("刷新")
        }
    }
}

@Composable
private fun SourceChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "官方来源",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

@Composable
private fun FilterRow(selected: String, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(count = MESSAGE_FILTERS.size, key = { index -> MESSAGE_FILTERS[index].key }) { index ->
            val option = MESSAGE_FILTERS[index]
            MessagePill(
                label = option.label,
                isSelected = selected == option.key,
                a11yLabel = "按" + option.label + "筛选消息",
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
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                role = Role.Tab
                contentDescription = a11yLabel
                stateDescription = if (isSelected) "已选中" else "未选中"
            },
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
    val text = when {
        filterKey == MSG_FILTER_WECHAT -> "微信待审核不使用公告列表，计数见下方卡片"
        query.isNotBlank() -> "关键词“" + query.trim() + "”匹配 $matched 条"
        else -> "已读取的官方公告 $total 条，当前列出 $matched 条"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

// ---------------------------------------------------------------------------
// 消息卡片（设计稿的彩色图标 + 标题 + 元信息 + 查看全文）
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
                onClickLabel = "查看这条消息的全文",
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CardShape
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "华水教务官网 · " + noticeCategoryLabel(notice.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (notice.dateLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = notice.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "查看全文",
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

@Composable
private fun WechatSourceCard(count: Int?, authenticated: Boolean, openWebsite: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = CardShape
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(TileShape)
                        .background(WechatTile),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.MailOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = WechatInk
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "微信消息 · 人工审核",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = pendingWechatLabel(count, authenticated),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "微信原文只保存在云端候选记录里，客户端不缓存、不猜测内容；" +
                    "必须由本人核对原文后确认，确认前不会进入任务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = openWebsite,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("打开云端审核页")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 全文 sheet：只有官方标题与链接，不伪造正文
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
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "消息详情",
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = "校序只保存官方标题、分类与原文链接，正文不落本地缓存，" +
                    "因此这里不复制粘贴网页内容，也不做摘要改写。请打开学校官网阅读全文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { openArticle(notice.url) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("查看全文（学校官网）")
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
        text = if (query == null) "该来源下没有已成功读取的官方公告。"
        else "没有匹配“" + query.trim() + "”的公告，换个关键词或切回“全部”。",
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
        text = "只展示已成功读取的官方公告与真实待审核计数；未连接、读取失败或来源未验证时，" +
            "不填充任何演示消息。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}
