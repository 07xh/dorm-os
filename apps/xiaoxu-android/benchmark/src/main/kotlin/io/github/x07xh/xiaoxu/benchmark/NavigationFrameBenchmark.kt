package io.github.x07xh.xiaoxu.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measure actual main-tab interactions, independently of display size and startup. */
@RunWith(AndroidJUnit4::class)
class NavigationFrameBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun rapidMainTabSwitches() = measureTabs(220L)

    @Test fun settledMainTabSwitches() = measureTabs(600L)

    private fun measureTabs(dwellMs: Long) = rule.measureRepeated(
        packageName = BaselineProfileGenerator.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3
        ),
        iterations = 6,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.selectMainTab("HOME")
            Thread.sleep(600)
        }
    ) {
        repeat(2) {
            listOf("SCHEDULE", "TASKS", "ACADEMIC", "PROFILE", "HOME").forEach { route ->
                device.selectMainTab(route)
                Thread.sleep(dwellMs)
            }
        }
    }
}

/** Reject missed taps rather than reporting timing for the wrong destination. */
internal fun UiDevice.selectMainTab(route: String) {
    val tab = wait(Until.findObject(By.res("xiaoxu_tab_$route")), 3_000)
        ?: error("Navigation tab not found: $route")
    tab.click()
    check(wait(Until.hasObject(By.res("xiaoxu_page_$route")), 3_000)) {
        "Navigation destination did not appear: $route"
    }
}
