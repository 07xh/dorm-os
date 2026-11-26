package com.tyust.course.ui.screen

// Adapted from the pinned upstream SettingsScreen.kt (GPL-3.0).
// Original retained under pending-business-adapters; changes limited to
// the business-facing parameter list, labels, actions and settings rows.
// The original scaffold, scroll collapse, grouped presentation, header and row implementations are retained.

import com.tyust.course.ui.theme.moduleEntrance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import com.tyust.course.ui.system.SystemIconButton
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPasteSearch
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.InsetGroupedRow
import com.tyust.course.ui.system.InsetGroupedSection
import com.tyust.course.ui.system.LiquidSwitch
import com.tyust.course.ui.system.PagePadding
import com.tyust.course.ui.system.SectionSpacing
import com.tyust.course.ui.system.SystemCard
import com.tyust.course.ui.system.SystemStatusBadge
import com.tyust.course.ui.system.SystemTone
import com.tyust.course.ui.system.SystemTopBar
import com.tyust.course.ui.theme.SemanticDanger

@Composable
fun XiaoxuSettingsScreen(
    connected: Boolean,
    refreshing: Boolean,
    onScheduleSettings: () -> Unit,
    onNotifications: () -> Unit,
    onConnections: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onRefresh: () -> Unit,
    onMessages: () -> Unit
) {
    val scrollState = rememberScrollState()
    // 折叠进度随滚动偏移连续变化（约 96px 行程），全程跟手
    val headerCollapse by remember {
        derivedStateOf { (scrollState.value / 96f).coerceIn(0f, 1f) }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.moduleEntrance(0)) {
            SystemTopBar(
                title = "设置",
                subtitle = "账号、学习与应用偏好",
                collapseFraction = headerCollapse,
                actions = {
                    SystemIconButton(Icons.Outlined.Refresh, "刷新", onRefresh, enabled = !refreshing)
                    SystemIconButton(Icons.Outlined.NotificationsNone, "消息中心", onMessages)
                }
            )
            }
        }
    ) { padding ->
        // 内容延伸到玻璃顶栏下方，滚动时从顶栏底下穿过（padding 施加在滚动内容内部）
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = PagePadding,
                    end = PagePadding,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = com.tyust.course.ui.system.LocalAppOverlayBottomInset.current + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            SettingsHeader(
                name = "校序",
                studentId = "",
                school = if (connected) "云端已连接" else "云端未连接",
                isSuper = false,
                quotaInfo = "",
                canRefreshCookie = false,
                isRefreshingCookie = false,
                onQuotaClick = onConnections,
                onRefreshCookieClick = onRefresh
            )

            InsetGroupedSection(Modifier.moduleEntrance(2), header = "学习与课程") {
                SettingsRow(icon = Icons.Outlined.DateRange,
                    iconTint = Color(0xFF0A84FF), title = "课表设置",
                    onClick = onScheduleSettings)
                SettingsRow(icon = Icons.Outlined.NotificationsNone,
                    iconTint = Color(0xFFFF9F0A), title = "通知与提醒",
                    showDivider = false, onClick = onNotifications)
            }

            InsetGroupedSection(Modifier.moduleEntrance(3), header = "账户与数据") {
                SettingsRow(icon = Icons.Outlined.School,
                    iconTint = Color(0xFF18796B), title = "数据连接",
                    onClick = onConnections)
                SettingsRow(icon = Icons.Outlined.CheckCircle,
                    iconTint = Color(0xFF34C759), title = "备份与恢复",
                    showDivider = false, onClick = onBackup)
            }

            InsetGroupedSection(Modifier.moduleEntrance(3), header = "其他") {
                SettingsRow(icon = Icons.Outlined.Info,
                    iconTint = Color(0xFF8E8E93), title = "关于校序",
                    showDivider = false, onClick = onAbout)
            }

        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SettingsHeader(
    name: String,
    studentId: String,
    school: String,
    isSuper: Boolean,
    quotaInfo: String,
    canRefreshCookie: Boolean,
    isRefreshingCookie: Boolean,
    onQuotaClick: () -> Unit,
    onRefreshCookieClick: () -> Unit
) {
    // 裸玻璃容器（不走 Surface，避免 elevation 阴影在半透色下泛白）
    val heroShape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .moduleEntrance(1)
            .clip(heroShape)
            .background(com.tyust.course.ui.system.glassSurfaceColor())
            .border(0.5.dp, com.tyust.course.ui.system.glassBorderColor(), heroShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).ifBlank { "同" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = name.ifBlank { "同学" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = school.ifBlank { "未选择学校" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSuper) {
                SystemStatusBadge(
                    text = "超级用户",
                    tone = SystemTone.Success
                )
            }
            if (quotaInfo.isNotBlank()) {
                Box(modifier = Modifier.clickable(onClick = onQuotaClick)) {
                    SystemStatusBadge(
                        text = if (isSuper) "无限制" else "配额 $quotaInfo",
                        tone = if (isSuper) SystemTone.Info else SystemTone.Neutral
                    )
                }
            }
            if (canRefreshCookie) {
                Box(
                    modifier = Modifier.clickable(
                        enabled = !isRefreshingCookie,
                        onClick = onRefreshCookieClick
                    )
                ) {
                    SystemStatusBadge(
                        text = if (isRefreshingCookie) "更新中" else "更新 Cookie",
                        tone = SystemTone.Info
                    )
                }
            }
        }
    }
}

/** 设置行：InsetGroupedRow + 彩色图标 chip + chevron。 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    InsetGroupedRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}
