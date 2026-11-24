package io.github.x07xh.xiaoxu.ui.v2

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentOption
import io.github.x07xh.xiaoxu.ui.XiaoxuSegmentedControl

internal val LocalUiV2Motion = staticCompositionLocalOf { UiV2MotionMode.Standard }
/** Independent render quality: lightweight does not capture the scrolling page. */
internal val LocalUiV2LiveGlass = staticCompositionLocalOf { false }
private data class MotionPreference(
    val selected: UiV2MotionMode,
    val change: (UiV2MotionMode) -> Unit,
    val liveGlass: Boolean,
    val changeLiveGlass: (Boolean) -> Unit
)
private val LocalMotionPreference = staticCompositionLocalOf {
    MotionPreference(UiV2MotionMode.Standard, {}, false, {})
}

@Composable
internal fun UiV2MotionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("ui_v2_motion", Context.MODE_PRIVATE) }
    var mode by remember(prefs) { mutableStateOf(UiV2MotionMode.parse(prefs.getString("mode", null))) }
    var liveGlass by remember(prefs) { mutableStateOf(prefs.getBoolean("live_glass", false)) }
    fun readScale(): Float = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    var systemScale by remember(context) { mutableFloatStateOf(readScale()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemScale = readScale() }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
            )
        }.isSuccess
        systemScale = readScale()
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(
        LocalUiV2Motion provides uiV2EffectiveMotion(mode, systemScale),
        LocalUiV2LiveGlass provides liveGlass,
        LocalMotionPreference provides MotionPreference(
            selected = mode,
            change = { next ->
                mode = next
                prefs.edit().putString("mode", next.name).apply()
            },
            liveGlass = liveGlass,
            changeLiveGlass = { next ->
                liveGlass = next
                prefs.edit().putBoolean("live_glass", next).apply()
            }
        ),
        content = content
    )
}

@Composable
internal fun UiV2MotionSettingsPage() {
    val preference = LocalMotionPreference.current
    val effective = LocalUiV2Motion.current
    UiV2SettingsGroup("交互动效") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            XiaoxuSegmentedControl(
                options = UiV2MotionMode.entries.map { XiaoxuSegmentOption(it.name, it.title) },
                selectedKey = preference.selected.name,
                onSelected = { preference.change(UiV2MotionMode.parse(it)) }
            )
            Text(preference.selected.description, style = MaterialTheme.typography.bodyMedium)
            if (effective != preference.selected) Text("系统已关闭动画，当前使用减少动画模式。")
            Text("减少动画时，底栏停用图标回放和惯性回弹。", style = MaterialTheme.typography.bodySmall)
        }
    }
    UiV2SettingsGroup("底栏渲染") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            XiaoxuSegmentedControl(
                options = listOf(
                    XiaoxuSegmentOption("lightweight", "流畅"),
                    XiaoxuSegmentOption("live", "实时玻璃")
                ),
                selectedKey = if (preference.liveGlass) "live" else "lightweight",
                onSelected = { preference.changeLiveGlass(it == "live") }
            )
            Text(
                if (preference.liveGlass) "实时采样、模糊与折射，可能增加 GPU 负担。"
                else "保留悬浮胶囊、选中滑块及拖动，关闭实时背景采样。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
