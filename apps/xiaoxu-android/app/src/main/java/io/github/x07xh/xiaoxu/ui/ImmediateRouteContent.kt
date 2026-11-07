package io.github.x07xh.xiaoxu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds

/** One active native child, with scroll/filter state saved separately for each route. */
@Composable
internal fun <T : Any> ImmediateRouteContent(
    route: T,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable (T) -> Unit
) {
    val stateHolder = rememberSaveableStateHolder()
    Box(modifier.clipToBounds()) {
        stateHolder.SaveableStateProvider(route) { content(route) }
    }
}
