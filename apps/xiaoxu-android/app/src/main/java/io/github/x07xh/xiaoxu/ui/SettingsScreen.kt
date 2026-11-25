package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/screen/SettingsScreen.kt
// Original authors and contributors; original scaffold/header/rows, real Xiaoxu business bindings.

import com.tyust.course.ui.system.WallpaperCaption


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPasteSearch
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.tyust.course.ui.theme.*
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.AppThemeMode
import com.tyust.course.manager.WallpaperPreset
import com.tyust.course.manager.WallpaperMode
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import io.github.x07xh.xiaoxu.ui.InsetGroupedRow
import io.github.x07xh.xiaoxu.ui.InsetGroupedSection

@Composable
internal fun SettingsScreen(data: AppWorkspaceState, actions: AppPageActions) {
    val context = LocalContext.current
    val currentVersion = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "未知" }
    var themeOpen by remember { mutableStateOf(false) }
    var wallpaperOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // 折叠进度随滚动偏移连续变化（约 96px 行程），全程跟手
    val headerCollapse by remember {
        derivedStateOf { (scrollState.value / 96f).coerceIn(0f, 1f) }
    }
    GlassPageScaffold(
        title = "我的",
        topBar = {
            Box(Modifier.moduleEntrance(0)) {
            SystemTopBar(
                title = "我的",
                subtitle = "账号、外观与应用偏好",
                collapseFraction = headerCollapse
            )
            }
        }
    ) { padding ->
        // 内容延伸到玻璃顶栏下方，滚动时从顶栏底下穿过（padding 施加在滚动内容内部）
        Column(
            modifier = Modifier
                .fillMaxSize().wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally).widthIn(max = AppSpacing.formMaxWidth)
                .verticalScroll(scrollState)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingsHeader(
                name = "校序账户",
                studentId = "",
                school = if (data.cloud.authenticated) "云端已连接" else "云端未连接",
                isSuper = false,
                quotaInfo = "",
                canRefreshCookie = false,
                isRefreshingCookie = false,
                onQuotaClick = {},
                onRefreshCookieClick = {}
            )

            InsetGroupedSection(Modifier.moduleEntrance(2), header = "账号与教务") {
                SettingsRow(Icons.Outlined.School, Color(0xFF0A84FF), "学校与教务", "数据连接",
                    onClick = { actions.navigate(AppRoute.CONNECTIONS) })
                SettingsRow(Icons.Outlined.ManageAccounts, Color(0xFF32ADE6), "账号", if (data.cloud.authenticated) "已连接" else "未连接",
                    showDivider = false, onClick = { actions.navigate(AppRoute.ACCOUNT) })
            }
            InsetGroupedSection(Modifier.moduleEntrance(3), header = "外观") {
                SettingsRow(Icons.Outlined.Palette, MaterialTheme.colorScheme.primary, "主题",
                    AppearanceSettingsManager.themeMode.label, onClick = { themeOpen = true })
                SettingsRow(Icons.Outlined.Palette, Color(0xFFBF5AF2), "背景",
                    "选择预设背景", onClick = { wallpaperOpen = true })
                InsetGroupedRow(icon = Icons.Outlined.AutoAwesome, iconTint = Color(0xFF5AC8FA),
                    title = "液态玻璃", subtitle = if (AppearanceSettingsManager.glassEffectEnabled) "折射、色散与跟手形变" else "已关闭",
                    showDivider = false, trailing = {
                        LiquidSwitch(checked = AppearanceSettingsManager.glassEffectEnabled,
                            onCheckedChange = AppearanceSettingsManager::updateGlassEffect)
                    })
            }
            InsetGroupedSection(Modifier.moduleEntrance(3), header = "学习与课程") {
                SettingsRow(Icons.Outlined.Assignment, Color(0xFF0A84FF), "课表设置",
                    onClick = { actions.navigate(AppRoute.SCHEDULE_SETTINGS) })
                SettingsRow(Icons.Outlined.VolumeOff, MaterialTheme.colorScheme.primary, "通知与提醒",
                    onClick = { actions.navigate(AppRoute.REMINDER_SETTINGS) })
                SettingsRow(Icons.Outlined.School, Color(0xFF18796B), "第二课堂同步",
                    showDivider = false, onClick = { actions.navigate(AppRoute.SECOND_CLASS_SETTINGS) })
            }
            InsetGroupedSection(Modifier.moduleEntrance(3), header = "数据与安全") {
                SettingsRow(Icons.Outlined.AssignmentInd, Color(0xFF5E5CE6), "任务备份",
                    showDivider = false, onClick = { actions.navigate(AppRoute.TASK_BACKUP) })
            }
            InsetGroupedSection(Modifier.moduleEntrance(3), header = "应用与支持") {
                SettingsRow(Icons.Outlined.Campaign, MaterialTheme.colorScheme.primary, "通知中心",
                    onClick = { actions.navigate(AppRoute.MESSAGES) })
                SettingsRow(Icons.Outlined.Info, Color(0xFF8E8E93), "关于校序", "当前版本 $currentVersion",
                    onClick = { actions.navigate(AppRoute.ABOUT) })
                SettingsRow(Icons.Outlined.FavoriteBorder, Color(0xFFFF375F), "致谢与许可",
                    showDivider = false, onClick = { actions.navigate(AppRoute.LICENSES) })
            }

            WallpaperCaption(
                text = "校序 · $currentVersion",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }

    if (themeOpen) {
        val modes = AppThemeMode.entries
        GlassOptionWheelDialog(modes.map { it.label }, modes.indexOf(AppearanceSettingsManager.themeMode),
            onConfirm = { AppearanceSettingsManager.updateThemeMode(modes[it]); themeOpen = false },
            onDismiss = { themeOpen = false }, title = "主题")
    }
    if (wallpaperOpen) {
        val presets = WallpaperPreset.entries
        GlassOptionWheelDialog(presets.map { it.displayName },
            presets.indexOfFirst { it.baseColor == AppearanceSettingsManager.style.baseColor }.coerceAtLeast(0),
            onConfirm = { AppearanceSettingsManager.updateWallpaper(presets[it]); wallpaperOpen = false },
            onDismiss = { wallpaperOpen = false }, title = "背景")
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
            .background(glassSurfaceColor())
            .border(0.5.dp, glassBorderColor(), heroShape)
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
internal fun SettingsRow(
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

enum class SystemTone {
    Info,
    Success,
    Warning,
    Danger,
    Neutral
}

@Composable
fun SystemStatusBadge(
    text: String,
    tone: SystemTone = SystemTone.Info,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (tone) {
        SystemTone.Info -> SemanticInfoContainer to SemanticInfo
        SystemTone.Success -> SemanticSuccessContainer to SemanticSuccess
        SystemTone.Warning -> SemanticWarningContainer to SemanticWarning
        SystemTone.Danger -> SemanticDangerContainer to SemanticDanger
        SystemTone.Neutral -> NeuInsetBackground to Neutral500
    }

    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

