package io.github.x07xh.xiaoxu.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Room returns a new cold Flow on each observe() call. Keep the query identity
 * across filters, dialogs and checkbox feedback, and release it while stopped.
 * A changed owner gets a fresh state so records cannot leak between queries.
 */
@Composable
internal fun <T> rememberLocalRows(owner: Any, query: () -> Flow<List<T>>): State<List<T>> {
    val rows = remember(owner) { query().distinctUntilChanged() }
    return androidx.compose.runtime.key(owner) {
        rows.collectAsStateWithLifecycle(initialValue = emptyList())
    }
}
