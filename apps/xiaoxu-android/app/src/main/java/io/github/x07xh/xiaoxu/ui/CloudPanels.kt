package io.github.x07xh.xiaoxu.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.data.CloudClient
import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.PlatformConnection
import kotlinx.coroutines.launch

/** Trusted owner cloud status is independent of the existing local official-page reader. */
private fun connectionStatusLabel(connection: PlatformConnection?): String = when {
    connection == null -> "尚未取得真实连接状态"
    connection.sessionState == "expired" -> "学校登录已过期"
    connection.sessionState == "unreadable" -> "学校会话无法验证，需重新授权"
    connection.sessionState == "review_pending" -> "本人课表等待核对"
    connection.sessionState == "active" && connection.verified ->
        "已建立本人授权和可信读取记录"
    connection.sessionState == "active" -> "已有学校会话，个人数据尚未验收"
    else -> "未连接本人学校账号"
}

/** Existing first-party API; school sessions are not transferred to Android. */
@Composable
fun CloudTasksPanel(client: CloudClient) {
    val state = client.state
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("校序云端任务", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        if (!state.authenticated) {
            Text("连接校序云端后显示已有任务。本机任务不受影响。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        label = { Text("新增云端任务") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = title.isNotBlank() && !state.busy,
                        onClick = {
                            val value = title
                            scope.launch { if (client.addCloudTask(value)) title = "" }
                        }
                    ) { Text("保存至云端") }
                }
            }
            if (!state.tasksFresh) {
                Text("本次任务列表未能核验最新结果；下方若有记录，仅为旧快照。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.tasks.isEmpty() && state.tasksFresh) {
                Text("当前云端任务列表为空（已完成本次读取）。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.tasks.take(50).forEach { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(task.title, fontWeight = FontWeight.Medium)
                        val source = when (task.provider) {
                            "chaoxing" -> "学习通来源（不代表已核验完成状态）"
                            "ncwu" -> "青果来源"
                            "second_class" -> "第二课堂来源"
                            else -> "校序个人任务"
                        }
                        Text(source + " · " + when (task.status) {
                            "done" -> "个人标记已完成"
                            "in_progress" -> "进行中"
                            else -> "待办"
                        }, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!task.due.isNullOrBlank()) {
                            Text("截止：" + task.due,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        if (state.privateError != null) {
            Text(state.privateError, color = MaterialTheme.colorScheme.error)
        }
        Text("本机任务与云端任务分开显示，不自动合并，避免重复或误覆盖。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConnectionsPanel(
    client: CloudClient, openWebsite: () -> Unit,
    openLocalSchool: (String) -> Unit, startSchoolAuthorization: (String) -> Unit,
    recentImport: LocalSchoolImportReceipt? = null
) {
    val state = client.state
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP)
                foreground = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Never save the passphrase across configuration changes.
    var passphrase by remember { mutableStateOf("") }
    var cloudAdvanced by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(XiaoxuSpacing.section)
                ) {
                    ProfileHeroCard(state = state)
                    SchoolReaderSection(
                        state = state,
                        openLocalSchool = openLocalSchool,
                        recentImport = recentImport
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(XiaoxuSpacing.section)
                ) {
                    CloudAuthorizationSection(
                        client = client,
                        state = state,
                        scope = scope,
                        foreground = foreground,
                        passphrase = passphrase,
                        onPassphraseChange = { passphrase = it.take(256) },
                        openLocalSchool = openLocalSchool,
                        startSchoolAuthorization = startSchoolAuthorization,
                        openWebsite = openWebsite,
                        cloudAdvanced = cloudAdvanced,
                        onToggleAdvanced = { cloudAdvanced = !cloudAdvanced }
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(XiaoxuSpacing.section)) {
                ProfileHeroCard(state = state)
                SchoolReaderSection(
                    state = state,
                    openLocalSchool = openLocalSchool,
                    recentImport = recentImport
                )
                HorizontalDivider(color = XiaoxuPalette.Line)
                CloudAuthorizationSection(
                    client = client,
                    state = state,
                    scope = scope,
                    foreground = foreground,
                    passphrase = passphrase,
                    onPassphraseChange = { passphrase = it.take(256) },
                    openLocalSchool = openLocalSchool,
                    startSchoolAuthorization = startSchoolAuthorization,
                    openWebsite = openWebsite,
                    cloudAdvanced = cloudAdvanced,
                    onToggleAdvanced = { cloudAdvanced = !cloudAdvanced }
                )
            }
        }
    }
}

/**
 * 账号头图：渐变蓝卡 + 通用头像 + 真实会话状态 + 三格真实指标。
 *
 * 没有真实头像来源（学校头像接口未接入、云端也不保存头像），因此只用通用人形图标，
 * 不画虚构头像、不编造学号；三格数字全部来自 CloudUiState 已有字段。
 */
@Composable
private fun ProfileHeroCard(state: CloudUiState) {
    val shape = RoundedCornerShape(XiaoxuRadius.hero)
    val connectedPlatforms = state.platforms.count { it.sessionState == "active" }
    val platformCaption = when {
        !state.authenticated -> "未连接"
        !state.connectionsFresh -> "待核验"
        else -> "$connectedPlatforms/3"
    }
    // 微信待审核数量：wechatError != null 表示本次候选读取失败，count 是上次保留的缓存值，
    // 必须标"（快照）"；count 为空时是"未读取"而不是 0。与消息中心口径保持一致。
    val reviewCaption = when {
        !state.authenticated -> "—"
        state.pendingWechatCount == null -> "未读取"
        state.wechatError != null -> state.pendingWechatCount.toString() + "（快照）"
        else -> state.pendingWechatCount.toString()
    }
    // 未连接 / 未成功读取且没有任何缓存时，数量是未知而不是 0。
    val taskCaption = when {
        !state.authenticated -> "—"
        state.tasksFresh -> state.tasks.size.toString()
        state.tasks.isEmpty() -> "—"
        else -> state.tasks.size.toString() + "（快照）"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(XiaoxuPalette.Brand, XiaoxuPalette.BrandDeep),
                    start = Offset.Zero,
                    end = Offset(1080f, 760f)
                )
            )
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.minDimension * 0.60f,
                    center = Offset(size.width * 0.96f, -size.height * 0.18f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.40f,
                    center = Offset(size.width * 0.72f, size.height * 1.12f)
                )
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = "通用头像（未接入真实头像）",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "我的学习空间",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = when {
                                state.authenticated && state.connectionsFresh -> "校序云端会话已核验"
                                state.authenticated -> "云端状态待重新核验"
                                else -> "云端未连接 · 可选"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
        Text(
            text = "学校账号各自授权；本机读取不依赖校序云端登录。",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.82f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileMetric(label = "学校平台", value = platformCaption, modifier = Modifier.weight(1f))
            ProfileMetric(label = "待审核候选", value = reviewCaption, modifier = Modifier.weight(1f))
            ProfileMetric(label = "云端任务", value = taskCaption, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProfileMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(XiaoxuRadius.tile))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 本机优先：三个学校平台的本机官方读取入口，始终可见，不需要任何云端授权。 */
@Composable
private fun SchoolReaderSection(
    state: CloudUiState,
    openLocalSchool: (String) -> Unit,
    recentImport: LocalSchoolImportReceipt?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "学校数据连接",
            caption = "在 App 内打开学校官方页面，登录本人账号后主动读取"
        )
        // Native-first path already implemented in the existing local school activities.
        // This is deliberately separate from verified Cloudflare sessions.
        LocalSchoolConnectionPanel(openLocalSchool, recentImport)
    }
}

/**
 * 云端可选授权：默认收起，展开后是登录 / 刷新 / 退出，三张平台状态卡与设置行。
 * 平台状态只陈述真实字段，不把"有会话"写成"已核验"。
 */
@Composable
private fun CloudAuthorizationSection(
    client: CloudClient,
    state: CloudUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    foreground: Boolean,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    openLocalSchool: (String) -> Unit,
    startSchoolAuthorization: (String) -> Unit,
    openWebsite: () -> Unit,
    cloudAdvanced: Boolean,
    onToggleAdvanced: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title = "云端授权与高级同步",
            caption = "可选配置，不影响本机读取、课表和待办",
            actionLabel = if (cloudAdvanced) "收起" else "展开",
            onAction = onToggleAdvanced
        )
        if (cloudAdvanced) {
            XiaoxuCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(XiaoxuPalette.BrandSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = XiaoxuPalette.BrandDeep
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "校序安全连接",
                            style = MaterialTheme.typography.titleSmall,
                            color = XiaoxuPalette.Ink
                        )
                        Text(
                            text = state.authMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink3
                        )
                    }
                    SourceBadge(
                        text = if (state.authenticated) "已连接" else "未连接",
                        tone = if (state.authenticated) XiaoxuTone.Success else XiaoxuTone.Neutral
                    )
                }
                if (!state.authenticated) {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = onPassphraseChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("校序个人口令（不是学校密码）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    PrimaryAction(
                        label = if (state.signingIn) "正在连接" else "连接校序云端",
                        enabled = passphrase.isNotBlank() && !state.signingIn
                    ) {
                        val submitted = passphrase
                        onPassphraseChange("")
                        scope.launch { client.signIn(submitted) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryAction(
                            label = "刷新云端数据",
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f)
                        ) { scope.launch { client.refresh() } }
                        SecondaryAction(
                            label = "退出云端",
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f)
                        ) { scope.launch { client.signOut() } }
                    }
                    if (state.lastSyncAt != null) {
                        Text(
                            text = "最近读取校序云端：" + state.lastSyncAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = XiaoxuPalette.Ink3
                        )
                    }
                }
            }

            Text(
                text = "云端学校授权",
                style = MaterialTheme.typography.titleSmall,
                color = XiaoxuPalette.Ink,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "仅用于云端可信同步。本机读取和云端授权是不同的数据来源。",
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3
            )

            val qrStartedAt = state.chaoxingQr.startedAt
            LaunchedEffect(qrStartedAt, foreground) {
                if (qrStartedAt == 0L || !foreground) return@LaunchedEffect
                // Bounded, foreground-only: no always-on polling or worker.
                repeat(24) {
                    delay(4500L)
                    if (client.state.chaoxingQr.status !in setOf("pending", "scanned"))
                        return@LaunchedEffect
                    client.pollChaoxingQr()
                }
            }
            val providerIcons = mapOf(
                "chaoxing" to Icons.AutoMirrored.Outlined.MenuBook,
                "ncwu" to Icons.Outlined.School,
                "second_class" to Icons.Outlined.EmojiEvents
            )
            for ((id, title) in listOf(
                "chaoxing" to "学习通", "ncwu" to "青果教务",
                "second_class" to "第二课堂")) {
                PlatformStatusCard(
                    id = id,
                    title = title,
                    icon = providerIcons[id] ?: Icons.Outlined.Cloud,
                    client = client,
                    state = state,
                    scope = scope,
                    openLocalSchool = openLocalSchool,
                    startSchoolAuthorization = startSchoolAuthorization
                )
            }
            if (state.privateError != null) {
                Text(
                    text = state.privateError,
                    color = XiaoxuPalette.Danger,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            SettingRow(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                title = "网页备用连接 / 诊断",
                subtitle = "在学校官网或校序网页端完成连接与诊断",
                onClick = openWebsite
            )
            Text(
                text = "本机官网读取仅保存当前可见记录，不等于校序云端授权或官方完整作业同步。",
                color = XiaoxuPalette.Ink3,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** 平台状态卡：图标 + 名称 + 真实状态 + 主次动作；次要动作收进设置行。 */
@Composable
private fun PlatformStatusCard(
    id: String,
    title: String,
    icon: ImageVector,
    client: CloudClient,
    state: CloudUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    openLocalSchool: (String) -> Unit,
    startSchoolAuthorization: (String) -> Unit
) {
    val platform = state.platforms.firstOrNull { it.id == id }
    val canRead = state.authenticated && state.connectionsFresh &&
        platform?.sessionState == "active"
    val refreshing = state.schoolSyncBusyProvider == id ||
        (id == "chaoxing" && state.chaoxingBusy)
    val statusTone = when {
        !state.authenticated -> XiaoxuTone.Neutral
        !state.connectionsFresh -> XiaoxuTone.Warning
        platform?.verified == true -> XiaoxuTone.Success
        else -> XiaoxuTone.Neutral
    }
    XiaoxuCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusTone.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = statusTone.contentColor
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = XiaoxuPalette.Ink,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        !state.authenticated -> "请先连接校序云端"
                        !state.connectionsFresh -> "本次无法核验连接状态；缓存仅供参考，请刷新重试"
                        else -> connectionStatusLabel(platform)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            SourceBadge(
                text = when {
                    !state.authenticated -> "云端未连接"
                    !state.connectionsFresh -> "状态待刷新"
                    platform?.verified == true && id == "ncwu" -> "教务会话有效"
                    platform?.verified == true -> "已核验读取"
                    platform?.sessionState == "active" -> "尚未核验数据"
                    else -> "未连接"
                },
                tone = statusTone
            )
        }
        if (state.connectionsFresh && platform?.verified == true &&
            platform.lastSuccessAt != null) {
            Text(
                text = (if (id == "ncwu") "上次连接记录：" else "上次可信读取：") + platform.lastSuccessAt,
                style = MaterialTheme.typography.labelSmall,
                color = XiaoxuPalette.Ink3
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryAction(
                label = when {
                    refreshing -> "正在读取…"
                    id == "chaoxing" -> "读取本人课程"
                    id == "ncwu" -> "成绩读取待接入"
                    else -> "刷新本人数据"
                },
                // The old ncwu sync path refreshes a server timetable, NOT grades.
                // Keep school authorization, but do not send the imported schedule to cloud.
                enabled = id != "ncwu" && canRead && !state.busy &&
                    !state.chaoxingBusy && state.schoolSyncBusyProvider == null,
                modifier = Modifier.weight(1f)
            ) {
                scope.launch {
                    if (id == "chaoxing") client.loadChaoxingCourses()
                    else client.syncAuthorizedSchool(id)
                }
            }
            PrimaryAction(
                label = when (platform?.sessionState) {
                    "expired", "unreadable" -> "重新授权"
                    "active" -> "管理授权"
                    else -> "连接账号"
                },
                enabled = client.ownerSessionBinding() != null,
                modifier = Modifier.weight(1f)
            ) { startSchoolAuthorization(id) }
        }
        // Reuse the native school reader for one-phone users.
        // Local visible records remain separate from verified cloud sync.
        if (id != "ncwu") {
            SettingRow(
                icon = Icons.Outlined.School,
                title = if (id == "chaoxing") "只有一台手机？打开学习通官网"
                    else "本机打开第二课堂官网",
                subtitle = "在本机浏览器读取当前可见记录，不经过云端",
                onClick = { openLocalSchool(id) }
            )
        }
        if (id == "chaoxing") {
            val qr = state.chaoxingQr
            SettingRow(
                icon = Icons.Outlined.Cloud,
                title = if (qr.status in setOf("pending", "scanned")) "重新生成扫码二维码"
                else "显示扫码二维码（需另一设备）",
                subtitle = if (qr.status in setOf("pending", "scanned"))
                    "二维码已生成，扫码后点「已扫码，立即检查」"
                else "用另一台设备扫码登录学习通，本机不保存密码",
                enabled = state.authenticated && !qr.busy && !state.busy,
                onClick = { scope.launch { client.startChaoxingQr() } }
            )
            if (qr.status != "idle") {
                qr.message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (qr.status == "failed") XiaoxuPalette.Danger
                        else XiaoxuPalette.Ink3
                    )
                }
                if (qr.status in setOf("pending", "scanned")) {
                    val qrBitmap = remember(qr.image) {
                        qr.image?.let { raw ->
                            val bounds = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                            if (bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048) {
                                BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
                            } else null
                        }
                    }
                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(XiaoxuRadius.tile))
                                .background(XiaoxuPalette.SurfaceAlt)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "学习通官方扫码登录二维码",
                                modifier = Modifier.size(208.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "二维码图片无法安全显示，请改用网页备用连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = XiaoxuPalette.Danger
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryAction(
                            label = "已扫码，立即检查",
                            enabled = !qr.busy,
                            modifier = Modifier.weight(1f)
                        ) { scope.launch { client.pollChaoxingQr() } }
                        SecondaryAction(
                            label = "取消扫码",
                            enabled = !qr.busy,
                            modifier = Modifier.weight(1f),
                            onClick = client::cancelChaoxingQr
                        )
                    }
                }
            }
            if (state.chaoxingCourses.isNotEmpty()) {
                Text(
                    text = "已读取本人 " + state.chaoxingCourses.size + " 门课程",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink2
                )
                SettingRow(
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = if (state.chaoxingBatchBusy) "正在逐门核验作业…" else "一键核验全部课程作业",
                    subtitle = "按已有可信只读接口逐门核验；失败或未读到的课程不标记成功",
                    enabled = state.authenticated && state.connectionsFresh &&
                        platform?.verified == true && !state.chaoxingBusy &&
                        !state.chaoxingDetailBusy && !state.busy &&
                        state.schoolSyncBusyProvider == null,
                    onClick = { scope.launch { client.syncChaoxingAllCourses() } }
                )
            }
            state.chaoxingBatchStatus?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3
                )
            }
            state.chaoxingError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = XiaoxuPalette.Danger
                )
            }
            if (platform?.autoSyncEnabled == true) {
                Text(
                    text = "每小时检查已登记；实际同步结果以任务页为准",
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3
                )
            }
        }
        if (state.schoolSyncResultProvider == id && state.schoolSyncMessage != null) {
            Text(
                text = state.schoolSyncMessage,
                style = MaterialTheme.typography.bodySmall,
                color = XiaoxuPalette.Ink3
            )
        }
    }
}

/** 分组设置行：图标 + 标题 + 说明 + 右侧箭头，整行 48dp 可点。 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(XiaoxuRadius.tile))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = title },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(XiaoxuPalette.SurfaceAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) XiaoxuPalette.Ink2 else XiaoxuPalette.Ink3
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) XiaoxuPalette.Ink else XiaoxuPalette.Ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = XiaoxuPalette.Ink3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = XiaoxuPalette.Ink3
        )
    }
}

/** 主操作：实心主色，48dp；不可用时降透明度但仍说明原因由上方文案承担。 */
@Composable
private fun PrimaryAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) XiaoxuPalette.Brand else XiaoxuPalette.Brand.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 次操作：描边，48dp；与主操作并排时不抢视觉。 */
@Composable
private fun SecondaryAction(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, XiaoxuPalette.Line, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) XiaoxuPalette.Ink2 else XiaoxuPalette.Ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
