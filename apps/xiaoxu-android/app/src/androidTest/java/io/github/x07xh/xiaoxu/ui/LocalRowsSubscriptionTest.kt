package io.github.x07xh.xiaoxu.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.x07xh.xiaoxu.MainActivity
import io.github.x07xh.xiaoxu.data.assertRuntimeIsolation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Offline runtime check: opening UI controls must not restart Room-style cold queries. */
@RunWith(AndroidJUnit4::class)
class LocalRowsSubscriptionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun isolated() = assertRuntimeIsolation(compose.activity)

    private class Query {
        val starts = AtomicInteger()
        val active = AtomicInteger()
        val rows = MutableStateFlow(listOf("saved"))
        fun observe() = flow {
            starts.incrementAndGet()
            active.incrementAndGet()
            try { emitAll(rows) } finally { active.decrementAndGet() }
        }
    }

    @Test fun unrelatedUiUpdatesKeepOneSubscriptionAndNewRowsStillArrive() {
        val query = Query()
        val control = mutableIntStateOf(0)
        val rendered = AtomicInteger(-1)
        val shown = AtomicReference<List<String>>(emptyList())
        compose.runOnUiThread {
            compose.activity.setContent {
                val rows by rememberLocalRows(query) { query.observe() }
                val value = control.intValue
                SideEffect { rendered.set(value); shown.set(rows) }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000L) { shown.get() == listOf("saved") }
        repeat(20) { index ->
            compose.runOnUiThread { control.intValue = index + 1 }
            compose.waitUntil(timeoutMillis = 10_000L) { rendered.get() == index + 1 }
        }
        assertEquals("one query despite 20 filter/dialog recompositions", 1, query.starts.get())
        compose.runOnUiThread { query.rows.value = listOf("saved", "new") }
        compose.waitUntil(timeoutMillis = 10_000L) { shown.get().size == 2 }
        assertEquals(1, query.starts.get())
    }

    @Test fun stoppedActivityReleasesQueryAndResumeReadsLatestRows() {
        val query = Query()
        val shown = AtomicReference<List<String>>(emptyList())
        compose.runOnUiThread {
            compose.activity.setContent {
                val rows by rememberLocalRows(query) { query.observe() }
                SideEffect { shown.set(rows) }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000L) { query.active.get() == 1 && shown.get().isNotEmpty() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(timeoutMillis = 10_000L) { query.active.get() == 0 }
        query.rows.value = listOf("updated while stopped")
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(timeoutMillis = 10_000L) { shown.get() == listOf("updated while stopped") }
        assertEquals(2, query.starts.get())
    }

    @Test fun changingQueryOwnerCancelsTheOldQuery() {
        val first = Query()
        val second = Query().apply { rows.value = listOf("second owner") }
        val owner = mutableStateOf(first)
        val shown = AtomicReference<List<String>>(emptyList())
        val leaked = AtomicBoolean(false)
        compose.runOnUiThread {
            compose.activity.setContent {
                val current = owner.value
                val rows by rememberLocalRows(current) { current.observe() }
                SideEffect {
                    if (current === second && "saved" in rows) leaked.set(true)
                    shown.set(rows)
                }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000L) { first.active.get() == 1 }
        compose.runOnUiThread { owner.value = second }
        compose.waitUntil(timeoutMillis = 10_000L) { shown.get() == listOf("second owner") && first.active.get() == 0 }
        assertEquals(0, first.active.get())
        assertEquals(1, second.active.get())
        assertFalse("the new owner must never render the old owner's rows", leaked.get())
    }
}
