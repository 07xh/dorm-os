package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, ui/system/GlassPageScaffold.kt
// Original authors and contributors; root shell owns system insets.

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.tyust.course.ui.system.LocalAppOverlayBottomInset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Shared page shell for forms, history and standalone browser activities. */
@Composable
fun GlassPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val page: @Composable () -> Unit = {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = bottomBar,
                topBar = {
                    if (topBar != null) topBar() else SystemTopBar(
                        title = title,
                        subtitle = subtitle,
                        navigationIcon = {
                            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                        },
                        actions = actions
                    )
                }
            ) { padding ->
                content(PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = maxOf(padding.calculateBottomPadding(), LocalAppOverlayBottomInset.current)
                ))
            }
    }
    GlassWindowHost(modifier) { page() }
}
