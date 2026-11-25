package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** New presentation baseline. One theme, no old glass implementation. */
@Composable
fun XiaoxuTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFB5CBFF)
    ) else lightColorScheme(
        primary = Color(0xFF315FA9),
        background = Color(0xFFF7F8FA),
        surface = Color(0xFFFFFFFF)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun XiaoxuGroupedSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}
