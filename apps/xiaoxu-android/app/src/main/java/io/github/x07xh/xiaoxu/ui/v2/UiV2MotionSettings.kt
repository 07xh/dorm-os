package io.github.x07xh.xiaoxu.ui.v2

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Column
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
private data class MotionPreference(val selected: UiV2MotionMode, val change: (UiV2MotionMode) -> Unit)
private val LocalMotionPreference = staticCompositionLocalOf {
    MotionPreference(UiV2MotionMode.Standard) {}
}

@Composable
internal fun UiV2MotionProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { context.getSharedPreferences("ui_v2_motion", Context.MODE_PRIVATE) }
    var mode by remember(prefs) { mutableStateOf(UiV2MotionMode.parse(prefs.getString("mode", null))) }
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
        LocalMotionPreference provides MotionPreference(mode) { next ->
            mode = next
            prefs.edit().putString("mode", next.name).apply()
        },
        content = content
    )
}

@Composable
internal fun UiV2MotionSettingsPage() {
    val preference = LocalMotionPreference.current
    val effective = LocalUiV2Motion.current
    UiV2SettingsGroup("交互动效") {
        Column {
            XiaoxuSegmentedControl(
                options = UiV2MotionMode.entries.map { XiaoxuSegmentOption(it.name, it.title) },
                selectedKey = preference.selected.name,
                onSelected = { preference.change(UiV2MotionMode.parse(it)) }
            )
            Text(preference.selected.description)
            if (effective != preference.selected) Text("系统已关闭动画，当前使用减少动画模式。")
            Text("目前应用于 V2 按压、分段滑块、弹层回位和返回预览。")
        }
    }
}
