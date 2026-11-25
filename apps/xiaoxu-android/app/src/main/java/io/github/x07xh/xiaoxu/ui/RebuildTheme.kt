package io.github.x07xh.xiaoxu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.ui.theme.CourseSelectorTheme

/** Bridge only: visual theme now uses the upstream implementation. */
@Composable
fun XiaoxuTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    remember(context) { AppearanceSettingsManager.initialize(context) }
    CourseSelectorTheme(content = content)
}

@Composable
internal fun XiaoxuGroupedSection(title: String, content: @Composable () -> Unit) {
    InsetGroupedSection(header = title) { content() }
}
