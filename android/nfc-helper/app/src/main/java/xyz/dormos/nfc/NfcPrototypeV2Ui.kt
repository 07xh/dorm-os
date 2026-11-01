package xyz.dormos.nfc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Native interpretation of the user-approved dorm_os_nfc_interactive_v2.html.
 * No mock scenes, no side-button features: all states/scene names are from the device.
 * The preview's simulated NFC button is replaced by Android's real NFC reader.
 */
private val v2Ink = Color(0xFF17253B)
private val v2Blue = Color(0xFF2865D8)
private val v2Muted = Color(0xFF8490A2)
private val v2Line = Color(0xFFE6EAF1)
private val v2Background = Color(0xFFF7F8FC)
private val v2Dark = Color(0xFF142D53)

@Composable
internal fun V2NfcMark(
    modifier: Modifier = Modifier,
    color: Color = v2Blue
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
    Box(
        Modifier.fillMaxWidth().heightIn(min = 245.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Brush.horizontalGradient(listOf(v2Dark, Color(0xFF1A3A69))))
    ) {
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 25.dp, bottom = 64.dp)
                .rotate(13f).size(width = 72.dp, height = 83.dp)
                .border(2.dp, Color(0xFF9AB9EE), RoundedCornerShape(17.dp))
        ) {
            V2NfcMark(
                Modifier.align(Alignment.TopStart).offset(x = (-15).dp, y = (-19).dp)
                    .size(45.dp),
                Color(0xFFD6E5FF)
            )
        }
        Column(Modifier.fillMaxWidth().padding(21.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V2NfcMark(Modifier.size(17.dp), Color(0xFFBCD5FF))
                Spacer(Modifier.width(7.dp))
                Text("NFC 场景控制", color = Color(0xFFBED1EF), fontSize = 12.sp)
            }
            Spacer(Modifier.height(15.dp))
            Text("轻触标签，\n立即执行。", color = Color.White,
                fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp)
            Spacer(Modifier.height(10.dp))
            Text("为常用操作创建快捷入口，减少手动步骤。",
                modifier = Modifier.widthIn(max = 205.dp),
                color = Color(0xFFD1DBE9), fontSize = 12.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF8FBFF), contentColor = Color(0xFF194179)
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 17.dp, vertical = 10.dp),
                modifier = Modifier.heightIn(min = 47.dp)
            ) {
                V2NfcMark(Modifier.size(18.dp), Color(0xFF194179))
                Spacer(Modifier.width(10.dp))
                Text("扫描 NFC 标签", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    V2SectionHeading("设备状态")
    Spacer(Modifier.height(11.dp))
    Surface(shape = RoundedCornerShape(19.dp), color = Color.White,
        border = BorderStroke(1.dp, v2Line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
            V2StatusRow(
                symbol = "⌁", title = "NFC",
                detail = if (nfcOn) "系统 NFC 已开启" else "请在系统设置中开启 NFC",
                good = nfcOn, state = if (nfcOn) "已开启" else "未开启"
            )
            HorizontalDivider(color = v2Line)
            V2StatusRow(
                symbol = "◇", title = "KernelSU",
                detail = if (rootReady) "已连接 DORM SH 执行模块"
                    else rootHint.take(48).ifBlank { "未检测到 Root 模块" },
                good = rootReady, state = if (rootReady) "已连接" else "未连接"
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("常用场景", color = v2Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onScenes, contentPadding = PaddingValues(0.dp)) {
            Text("查看全部  ›", color = v2Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    Surface(shape = RoundedCornerShape(19.dp), color = Color.White,
        border = BorderStroke(1.dp, v2Line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
            if (scenes.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    Text("暂无已绑定的 NFC 场景", color = v2Ink,
                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("到「工具」创建 SH，再在脚本卡片中选择「绑 NFC」。",
                        color = v2Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    TextButton(onClick = onScenes, contentPadding = PaddingValues(0.dp)) {
                        Text("查看场景  ›", color = v2Blue, fontSize = 12.sp)
                    }
                }
            } else {
                scenes.take(3).forEachIndexed { index, scene ->
                    if (index != 0) HorizontalDivider(color = v2Line)
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
private fun V2StatusRow(symbol: String, title: String, detail: String, good: Boolean, state: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(39.dp).clip(RoundedCornerShape(13.dp))
                .background(Color(0xFFF0F5FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, color = v2Blue, fontSize = 22.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = v2Ink, fontSize = 14.sp)
            Text(detail, color = v2Muted, fontSize = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(7.dp))
        Surface(
            color = if (good) Color(0xFFE7F6F0) else Color(0xFFF0F2F6),
            shape = RoundedCornerShape(11.dp)
        ) {
            Text(state, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                color = if (good) Color(0xFF168966) else Color(0xFF8A95A5),
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun V2SectionHeading(title: String) {
    Text(title, color = v2Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
}

@Composable
internal fun V2SceneRow(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 70.dp)
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF0F4FB)), contentAlignment = Alignment.Center) {
            V2NfcMark(Modifier.size(25.dp), v2Blue)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = v2Ink, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = v2Muted, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("›", color = v2Muted, fontSize = 24.sp)
    }
}

@Composable
internal fun V2Scenes(
    scenes: List<PersonalScene>,
    scriptNames: Map<String, String>,
    filter: String,
    onFilter: (String) -> Unit,
    enabled: Boolean,
    rootReady: Boolean,
    busy: Boolean,
    onEnable: (Boolean) -> Unit,
    onNew: () -> Unit,
    onOpen: (PersonalScene, String) -> Unit
) {
    Text("已创建 ${scenes.size} 个场景 · 启用 ${scenes.count { it.enabled }} 个",
        color = v2Muted, fontSize = 13.sp)
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
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, v2Line)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("NFC 自动触发", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = v2Ink)
                Text(if (enabled) "总开关已开启 · 仍受单个场景状态控制"
                    else "总开关已关闭 · 所有标签均不会执行脚本",
                    color = v2Muted, fontSize = 11.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnable,
                enabled = rootReady && !busy)
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
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, v2Line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (visible.isEmpty()) {
                Text(if (scenes.isEmpty()) "还没有绑定场景" else "没有符合条件的场景",
                    color = v2Ink, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 19.dp))
                Text("创建场景后，选择一个本机 SH 脚本并写入自己的 NFC 标签。",
                    color = v2Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 17.dp))
            } else {
                visible.forEachIndexed { index, scene ->
                    if (index > 0) HorizontalDivider(color = v2Line)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onOpen(scene, scriptNames[scene.scriptId] ?: "未知脚本")
                        }.heightIn(min = 70.dp).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF0F4FB)), contentAlignment = Alignment.Center) {
                            V2NfcMark(Modifier.size(25.dp), v2Blue)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(scene.name, color = v2Ink, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(scriptNames[scene.scriptId] ?: "未知脚本",
                                color = v2Muted, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            color = if (scene.enabled) Color(0xFFE7F6F0) else Color(0xFFF0F2F6),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (scene.enabled) "启用" else "停用",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                color = if (scene.enabled) Color(0xFF168966) else v2Muted,
                                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(5.dp))
                        Text("›", color = v2Muted, fontSize = 23.sp)
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
        border = BorderStroke(1.dp, Color(0xFFBAD0F0)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFFF0F5FF), contentColor = v2Blue
        )
    ) {
        Text("＋  新建 NFC 场景", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(20.dp))
    Text("场景由 NFC 标签与本机 SH 脚本组成。新增自动化只需编辑脚本，无须修改 APK。",
        color = v2Muted, fontSize = 12.sp, lineHeight = 19.sp)
    Spacer(Modifier.height(22.dp))
}

@Composable
internal fun V2ToolsHeading(onScan: () -> Unit, onWrite: () -> Unit) {
    V2SectionHeading("标签工具")
    Spacer(Modifier.height(12.dp))
    Surface(color = Color.White, shape = RoundedCornerShape(19.dp),
        border = BorderStroke(1.dp, v2Line)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            V2SceneRow("读取 NFC 标签", "查看文本、链接、容量与技术类型", onClick = onScan)
            HorizontalDivider(color = v2Line)
            V2SceneRow("写入普通标签", "仅写文本或网址，不触发 Root", onClick = onWrite)
        }
    }
    Spacer(Modifier.height(22.dp))
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
