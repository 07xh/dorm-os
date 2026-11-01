package xyz.dormos.nfc

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native interpretation of the user-approved dorm_os_nfc_interactive_v2.html.
 * No mock scenes, no side-button features: all states/scene names are from the device.
 * The preview's simulated NFC button is replaced by Android's real NFC reader.
 */
internal data class V2Palette(
    val ink: Color,
    val blue: Color,
    val muted: Color,
    val line: Color,
    val background: Color,
    val surface: Color,
    val dark: Color,
    val accentLow: Color,
    val iconBackground: Color
)

internal fun v2Palette(theme: String): V2Palette = when (theme) {
    "night" -> V2Palette(
        ink = Color(0xFFE9F1FC), blue = Color(0xFF77A6FF),
        muted = Color(0xFF96ABC4), line = Color(0xFF2B3D53),
        background = Color(0xFF101B2A), surface = Color(0xFF19283C),
        dark = Color(0xFF244E95), accentLow = Color(0xFF273D60),
        iconBackground = Color(0xFF2B4160)
    )
    "editorial" -> V2Palette(
        ink = Color(0xFF222B22), blue = Color(0xFF426F5B),
        muted = Color(0xFF8A938A), line = Color(0xFFE8ECE5),
        background = Color(0xFFF7F7F2), surface = Color.White,
        dark = Color(0xFF244A3C), accentLow = Color(0xFFE7F1E8),
        iconBackground = Color(0xFFEAF3EB)
    )
    else -> V2Palette(
        ink = Color(0xFF17253B), blue = Color(0xFF2865D8),
        muted = Color(0xFF8490A2), line = Color(0xFFE6EAF1),
        background = Color(0xFFF7F8FC), surface = Color.White,
        dark = Color(0xFF142D53), accentLow = Color(0xFFE8EFFF),
        iconBackground = Color(0xFFF0F4FB)
    )
}

internal val LocalV2Palette = staticCompositionLocalOf { v2Palette("default") }

@Composable
internal fun V2NfcMark(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF2865D8)
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (w * .085f).coerceAtLeast(1f)
        drawArc(
            color, 225f, 90f, false,
            Offset(w * .06f, h * .08f), Size(w * .88f, h * .82f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color, 225f, 90f, false,
            Offset(w * .27f, h * .31f), Size(w * .46f, h * .44f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawCircle(color, radius = stroke * .85f, center = Offset(w * .5f, h * .76f))
    }
}

/** Compact Canvas-only tag art, placed beside rather than behind the copy. */
@Composable
private fun V2HeroTagArt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val blue = Color(0xFFBFD9FF)
        val stroke = 2.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.06f),
            radius = w * 0.46f, center = Offset(w * 0.5f, h * 0.53f))
        rotate(degrees = 12f, pivot = Offset(w * 0.50f, h * 0.52f)) {
            drawRoundRect(
                color = blue.copy(alpha = 0.12f),
                topLeft = Offset(w * 0.20f, h * 0.22f),
                size = Size(w * 0.64f, h * 0.60f),
                cornerRadius = CornerRadius(15.dp.toPx())
            )
            drawRoundRect(
                color = blue, topLeft = Offset(w * 0.20f, h * 0.22f),
                size = Size(w * 0.64f, h * 0.60f),
                cornerRadius = CornerRadius(15.dp.toPx()),
                style = Stroke(width = stroke)
            )
            drawCircle(blue, radius = 3.dp.toPx(),
                center = Offset(w * 0.31f, h * 0.31f))
            drawLine(blue.copy(alpha = 0.62f),
                start = Offset(w * 0.33f, h * 0.70f),
                end = Offset(w * 0.66f, h * 0.70f),
                strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
        }
        drawArc(
            color = blue, startAngle = 225f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(w * 0.37f, h * 0.04f),
            size = Size(w * 0.44f, w * 0.44f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = blue, startAngle = 225f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(w * 0.47f, h * 0.13f),
            size = Size(w * 0.24f, w * 0.24f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(blue, radius = 2.dp.toPx(),
            center = Offset(w * 0.59f, h * 0.22f))
    }
}

@Composable
internal fun V2Home(
    nfcOn: Boolean,
    rootReady: Boolean,
    rootHint: String,
    scenes: List<Triple<String, String, String>>,
    onScan: () -> Unit,
    onScenes: () -> Unit,
    onScene: (String, String) -> Unit
) {
    val p = LocalV2Palette.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = p.dark,
        shape = RoundedCornerShape(25.dp)
    ) {
        Box(Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(p.dark, p.dark.copy(alpha = 0.93f))))) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    V2NfcMark(Modifier.size(16.dp), Color(0xFFBFD9FF))
                    Spacer(Modifier.width(7.dp))
                    Text("NFC 场景", color = Color(0xFFC9D9F2), fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("轻触标签\n即刻执行", color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 27.sp, lineHeight = 35.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("一碰触发已绑定脚本", color = Color(0xFFD1DFF3),
                            fontSize = 12.sp, lineHeight = 18.sp, maxLines = 2)
                    }
                    Spacer(Modifier.width(6.dp))
                    V2HeroTagArt(Modifier.size(width = 93.dp, height = 111.dp))
                }
                Spacer(Modifier.height(15.dp))
                Button(
                    onClick = onScan,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF8FBFF),
                        contentColor = Color(0xFF194179)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    V2NfcMark(Modifier.size(18.dp), Color(0xFF194179))
                    Spacer(Modifier.width(10.dp))
                    Text("扫描 NFC 标签", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    V2SectionHeading("设备状态")
    Spacer(Modifier.height(11.dp))
    Surface(shape = RoundedCornerShape(19.dp), color = p.surface,
        border = BorderStroke(1.dp, p.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
            V2StatusRow(
                isNfc = true, title = "NFC",
                detail = if (nfcOn) "系统 NFC 已开启" else "请在系统设置中开启 NFC",
                good = nfcOn, state = if (nfcOn) "已开启" else "未开启"
            )
            HorizontalDivider(color = p.line)
            V2StatusRow(
                isNfc = false, title = "KernelSU",
                detail = if (rootReady) "已连接 TapFlow SH 执行模块"
                    else rootHint.take(48).ifBlank { "未检测到 Root 模块" },
                good = rootReady, state = if (rootReady) "已连接" else "未连接"
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("常用场景", color = p.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onScenes, contentPadding = PaddingValues(0.dp)) {
            Text("查看全部  ›", color = p.blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    Surface(shape = RoundedCornerShape(19.dp), color = p.surface,
        border = BorderStroke(1.dp, p.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
            if (scenes.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    Text("暂无已绑定的 NFC 场景", color = p.ink,
                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("到「工具」创建 SH，再在脚本卡片中选择「绑 NFC」。",
                        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp)
                    TextButton(onClick = onScenes, contentPadding = PaddingValues(0.dp)) {
                        Text("查看场景  ›", color = p.blue, fontSize = 12.sp)
                    }
                }
            } else {
                scenes.take(3).forEachIndexed { index, scene ->
                    if (index != 0) HorizontalDivider(color = p.line)
                    V2SceneRow(scene.second, "NFC 标签 · ${scene.third}", onClick = {
                        onScene(scene.first, scene.third)
                    })
                }
            }
        }
    }
    Spacer(Modifier.height(25.dp))
}

@Composable
private fun V2StatusRow(isNfc: Boolean, title: String, detail: String, good: Boolean, state: String) {
    val p = LocalV2Palette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(39.dp).clip(RoundedCornerShape(13.dp))
                .background(p.iconBackground),
            contentAlignment = Alignment.Center
        ) {
            if (isNfc) V2NfcMark(Modifier.size(23.dp), p.blue)
            else V2NavIcon(which = 2, color = p.blue, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = p.ink, fontSize = 14.sp)
            Text(detail, color = p.muted, fontSize = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(7.dp))
        Surface(
            color = if (good) if (p.surface == Color.White) Color(0xFFE7F6F0) else Color(0xFF194C45) else p.accentLow,
            shape = RoundedCornerShape(11.dp)
        ) {
            Text(state, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                color = if (good) if (p.surface == Color.White) Color(0xFF168966) else Color(0xFFA3E1CB) else p.muted,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun V2SectionHeading(title: String) {
    val p = LocalV2Palette.current
    Text(title, color = p.ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
}

@Composable
internal fun V2SceneRow(title: String, detail: String, onClick: () -> Unit) {
    val p = LocalV2Palette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 70.dp)
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
            .background(p.iconBackground), contentAlignment = Alignment.Center) {
            V2NfcMark(Modifier.size(25.dp), p.blue)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = p.muted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = p.muted, fontSize = 24.sp)
    }
}

@Composable
internal fun V2Scenes(
    scenes: List<PersonalScene>,
    scriptNames: Map<String, String>,
    filter: String,
    onFilter: (String) -> Unit,
    toggle: NfcTriggerToggleState,
    rootReady: Boolean,
    busy: Boolean,
    onEnable: (Boolean) -> Unit,
    onNew: () -> Unit,
    onOpen: (PersonalScene, String) -> Unit
) {
    val p = LocalV2Palette.current
    Text("已创建 ${scenes.size} 个场景 · 启用 ${scenes.count { it.enabled }} 个",
        color = p.muted, fontSize = 13.sp)
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("全部", "已启用", "已停用").forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onFilter(option) },
                label = { Text(option, fontSize = 12.sp) }
            )
        }
    }
    Spacer(Modifier.height(13.dp))
    Surface(color = p.surface, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, p.line)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NFC 自动触发", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = p.ink)
                Text(
                    when {
                        toggle.pending -> if (toggle.displayed) "正在开启 · 等待 KernelSU 确认"
                            else "正在关闭 · 等待 KernelSU 确认"
                        !rootReady -> "KernelSU 未连接 · 请到工具页检查授权"
                        toggle.confirmed -> "已开启 · 仅已绑定且启用的标签执行 SH"
                        else -> "已关闭 · 碰卡不会执行任何 SH"
                    },
                    color = p.muted, fontSize = 11.sp
                )
            }
            TapFlowNfcTriggerSwitch(
                checked = toggle.displayed,
                interactive = rootReady && !busy && !toggle.pending,
                onCheckedChange = onEnable,
                p = p
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    val visible = scenes.filter {
        when (filter) {
            "已启用" -> it.enabled
            "已停用" -> !it.enabled
            else -> true
        }
    }
    Surface(color = p.surface, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, p.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (visible.isEmpty()) {
                Text(if (scenes.isEmpty()) "还没有绑定场景" else "没有符合条件的场景",
                    color = p.ink, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 19.dp))
                Text("创建场景后，选择一个本机 SH 脚本并写入自己的 NFC 标签。",
                    color = p.muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 17.dp))
            } else {
                visible.forEachIndexed { index, scene ->
                    if (index > 0) HorizontalDivider(color = p.line)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onOpen(scene, scriptNames[scene.scriptId] ?: "未知脚本")
                        }.heightIn(min = 70.dp).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                            .background(p.iconBackground), contentAlignment = Alignment.Center) {
                            V2NfcMark(Modifier.size(25.dp), p.blue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(scene.name, color = p.ink, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(scriptNames[scene.scriptId] ?: "未知脚本",
                                color = p.muted, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            color = if (scene.enabled) if (p.surface == Color.White) Color(0xFFE7F6F0) else Color(0xFF194C45) else p.accentLow,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (scene.enabled) "启用" else "停用",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                color = if (scene.enabled) if (p.surface == Color.White) Color(0xFF168966) else Color(0xFFA3E1CB) else p.muted,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(5.dp))
                        Text("›", color = p.muted, fontSize = 23.sp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = onNew,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, p.line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.accentLow, contentColor = p.blue
        )
    ) {
        Text("＋  新建 NFC 场景", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(20.dp))
    Text("场景由 NFC 标签与本机 SH 脚本组成。新增自动化只需编辑脚本，无须修改 APK。",
        color = p.muted, fontSize = 12.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(22.dp))
}

/** Compact local-only toggle: pending Root work does not make the track flash gray. */
@Composable
internal fun TapFlowNfcTriggerSwitch(
    checked: Boolean,
    interactive: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    p: V2Palette
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) p.blue else p.line,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "NFC trigger track"
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 26.dp else 4.dp,
        animationSpec = tween(205, easing = FastOutSlowInEasing),
        label = "NFC trigger thumb"
    )
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 34.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .toggleable(
                value = checked,
                enabled = interactive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbX.roundToPx(), 0) }
                .size(26.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
        )
    }
}

@Composable
internal fun V2ToolsHeading(onScan: () -> Unit, onWrite: () -> Unit) {
    val p = LocalV2Palette.current
    V2SectionHeading("标签工具")
    Spacer(Modifier.height(12.dp))
    Surface(color = p.surface, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, p.line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            V2SceneRow("读取 NFC 标签", "查看文本、链接、容量与技术类型", onClick = onScan)
            HorizontalDivider(color = p.line)
            V2SceneRow("写入普通标签", "仅写文本或网址，不触发 Root", onClick = onWrite)
        }
    }
    Spacer(Modifier.height(22.dp))
}

/** Fine-stroke management glyphs; each row uses the same 24dp optical grid. */
@Composable
internal fun V2ManagementIcon(which: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val c = Offset(w * .5f, h * .5f)
        val stroke = (w * .075f).coerceAtLeast(1.25f)
        val outline = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (which) {
            // Run history: restrained clock face and two precise hands.
            0 -> {
                drawCircle(color, w * .34f, c, style = outline)
                drawLine(color, Offset(c.x, h * .28f), Offset(c.x, c.y),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, c, Offset(w * .69f, h * .61f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawArc(color, 205f, 55f, false,
                    Offset(w * .07f, h * .07f), Size(w * .86f, h * .86f),
                    style = Stroke(stroke * .7f, cap = StrokeCap.Round))
            }
            // Backup: file outline with a clear export arrow.
            1 -> {
                val page = Path().apply {
                    moveTo(w * .27f, h * .12f)
                    lineTo(w * .62f, h * .12f)
                    lineTo(w * .77f, h * .27f)
                    lineTo(w * .77f, h * .87f)
                    lineTo(w * .27f, h * .87f)
                    close()
                }
                drawPath(page, color, style = outline)
                drawLine(color, Offset(w * .62f, h * .13f), Offset(w * .62f, h * .28f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .62f, h * .28f), Offset(w * .77f, h * .28f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .52f, h * .72f), Offset(w * .52f, h * .45f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .39f, h * .57f), Offset(w * .52f, h * .43f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .65f, h * .57f), Offset(w * .52f, h * .43f),
                    strokeWidth = stroke, cap = StrokeCap.Round)
            }
            // Appearance: an even gear, not a text glyph.
            2 -> {
                val outer = w * .29f
                drawCircle(color, outer, c, style = outline)
                drawCircle(color, w * .105f, c, style = outline)
                for (i in 0 until 8) {
                    val angle = i * Math.PI / 4.0
                    val dx = kotlin.math.cos(angle).toFloat()
                    val dy = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        color,
                        Offset(c.x + dx * outer * 1.08f, c.y + dy * outer * 1.08f),
                        Offset(c.x + dx * outer * 1.42f, c.y + dy * outer * 1.42f),
                        strokeWidth = stroke, cap = StrokeCap.Round
                    )
                }
            }
            // Root status: shield with a small verification tick.
            3 -> {
                val shield = Path().apply {
                    moveTo(w * .5f, h * .09f)
                    lineTo(w * .82f, h * .22f)
                    lineTo(w * .78f, h * .57f)
                    cubicTo(w * .74f, h * .73f, w * .61f, h * .84f, w * .5f, h * .91f)
                    cubicTo(w * .39f, h * .84f, w * .26f, h * .73f, w * .22f, h * .57f)
                    lineTo(w * .18f, h * .22f)
                    close()
                }
                drawPath(shield, color, style = outline)
                val check = Path().apply {
                    moveTo(w * .35f, h * .5f)
                    lineTo(w * .46f, h * .61f)
                    lineTo(w * .67f, h * .39f)
                }
                drawPath(check, color, style = outline)
            }
            // NFC settings: two balanced radio arcs and a contact point.
            else -> {
                drawArc(color, 222f, 96f, false,
                    Offset(w * .12f, h * .12f), Size(w * .76f, h * .70f),
                    style = outline)
                drawArc(color, 222f, 96f, false,
                    Offset(w * .29f, h * .30f), Size(w * .42f, h * .39f),
                    style = outline)
                drawCircle(color, w * .055f, Offset(c.x, h * .79f))
            }
        }
    }
}

/** Lightweight vector icons so the real APK matches the prototype's outlined navigation. */
@Composable
internal fun V2NavIcon(which: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val line = w * .075f
        when (which) {
            0 -> {
                val outline = Path().apply {
                    moveTo(.13f * w, .46f * h)
                    lineTo(.50f * w, .13f * h)
                    lineTo(.87f * w, .46f * h)
                    lineTo(.87f * w, .87f * h)
                    lineTo(.60f * w, .87f * h)
                    lineTo(.60f * w, .61f * h)
                    lineTo(.40f * w, .61f * h)
                    lineTo(.40f * w, .87f * h)
                    lineTo(.13f * w, .87f * h)
                    close()
                }
                drawPath(outline, color, style = Stroke(line, join = StrokeJoin.Round,
                    cap = StrokeCap.Round))
            }
            1 -> {
                val d = w * .29f
                for (row in 0..1) for (col in 0..1) {
                    drawRoundRect(
                        color,
                        topLeft = Offset(w * (.13f + col * .44f), h * (.13f + row * .44f)),
                        size = Size(d, d),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .065f),
                        style = Stroke(line * .9f)
                    )
                }
            }
            else -> {
                val outline = Path().apply {
                    moveTo(w * .67f, h * .12f)
                    cubicTo(w * .52f, h * .12f, w * .47f, h * .28f, w * .52f, h * .41f)
                    lineTo(w * .18f, h * .72f)
                    cubicTo(w * .07f, h * .87f, w * .24f, h * .98f, w * .37f, h * .83f)
                    lineTo(w * .64f, h * .51f)
                    cubicTo(w * .81f, h * .56f, w * .96f, h * .39f, w * .86f, h * .22f)
                    lineTo(w * .71f, h * .39f)
                    lineTo(w * .60f, h * .32f)
                    close()
                }
                drawPath(outline, color, style = Stroke(line * .9f,
                    join = StrokeJoin.Round, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
internal fun V2SettingsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(23.dp)) {
        val c = Offset(size.width * .5f, size.height * .5f)
        val radius = size.width * .30f
        val thickness = size.width * .082f
        drawCircle(color, radius, c, style = Stroke(thickness))
        drawCircle(color, size.width * .10f, c, style = Stroke(thickness))
        for (index in 0..7) {
            val angle = index * kotlin.math.PI / 4
            val dx = kotlin.math.cos(angle).toFloat()
            val dy = kotlin.math.sin(angle).toFloat()
            drawLine(color, c + Offset(dx * radius * 1.05f, dy * radius * 1.05f),
                c + Offset(dx * radius * 1.40f, dy * radius * 1.40f),
                strokeWidth = thickness, cap = StrokeCap.Round)
        }
    }
}
