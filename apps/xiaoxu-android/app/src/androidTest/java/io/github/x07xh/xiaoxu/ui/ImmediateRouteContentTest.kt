package io.github.x07xh.xiaoxu.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.x07xh.xiaoxu.MainActivity
import io.github.x07xh.xiaoxu.data.assertRuntimeIsolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ImmediateRouteContentTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun isolated() = assertRuntimeIsolation(compose.activity)

    @Test fun rapidRouteChangesDisposePreviousContentAndRestoreSavedState() {
        val route = mutableStateOf("home")
        val active = AtomicInteger()
        val disposed = AtomicInteger()
        val maximumActive = AtomicInteger()
        val rendered = AtomicReference<Pair<String, Int>>()
        val increment = AtomicReference<() -> Unit>()
        compose.runOnUiThread {
            compose.activity.setContent {
                ImmediateRouteContent(route.value) { page ->
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    DisposableEffect(page) {
                        val current = active.incrementAndGet()
                        maximumActive.updateAndGet { maxOf(it, current) }
                        onDispose { active.decrementAndGet(); disposed.incrementAndGet() }
                    }
                    SideEffect {
                        rendered.set(page to count)
                        increment.set { count++ }
                    }
                }
            }
        }
        compose.waitUntil { rendered.get() == ("home" to 0) }
        compose.runOnUiThread { increment.get().invoke() }
        compose.waitUntil { rendered.get() == ("home" to 1) }
        repeat(20) { index ->
            val next = if (index % 2 == 0) "detail" else "home"
            compose.runOnUiThread { route.value = next }
            compose.waitUntil { rendered.get()?.first == next }
            assertEquals(1, active.get())
            assertEquals(if (next == "home") 1 else 0, rendered.get().second)
        }
        assertEquals("the outgoing route is never retained for animation", 1, maximumActive.get())
        assertTrue("previous content is disposed on every change", disposed.get() >= 20)
    }
}
