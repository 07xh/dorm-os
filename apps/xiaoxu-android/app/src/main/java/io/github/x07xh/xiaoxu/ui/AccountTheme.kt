package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LocalAppearance = compositionLocalOf { "系统" }
private val LocalSelectAppearance = compositionLocalOf<(String) -> Unit> { {} }

@Composable
internal fun AccountTheme(enabled: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", 0) }
    var mode by remember { mutableStateOf(prefs.getString("mode", "系统") ?: "系统") }
    val dark = enabled && (mode == "深色" || mode == "系统" && isSystemInDarkTheme())
    val window = androidx.activity.compose.LocalActivity.current?.window
    SideEffect {
        window?.let {
            val bars = androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalAccountDark provides dark, LocalAppearance provides mode,
        LocalSelectAppearance provides { value -> mode = value; prefs.edit().putString("mode", value).apply() }) {
        val scheme = if (dark) darkColorScheme(primary = XiaoxuPalette.Brand,
            background = XiaoxuPalette.Bg, surface = XiaoxuPalette.Surface,
            onSurface = XiaoxuPalette.Ink, surfaceVariant = XiaoxuPalette.SurfaceAlt,
            onSurfaceVariant = XiaoxuPalette.Ink2, secondaryContainer = XiaoxuPalette.BrandSoft,
            onSecondaryContainer = XiaoxuPalette.Ink) else MaterialTheme.colorScheme
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
internal fun AccountAppearancePanel() {
    val mode = LocalAppearance.current
    val select = LocalSelectAppearance.current
    XiaoxuCard {
        Text("外观", style = MaterialTheme.typography.titleMedium, color = XiaoxuPalette.Ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("系统", "浅色", "深色").forEach { label ->
                FilterChip(selected = mode == label, onClick = { select(label) },
                    label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}
