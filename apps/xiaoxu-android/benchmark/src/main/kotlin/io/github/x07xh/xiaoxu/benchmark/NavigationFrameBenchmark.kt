package io.github.x07xh.xiaoxu.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproducible main-tab frame-time sample on the optimized, profileable app.
 * Matches the five equal-width bottom-tab hit regions already exercised by
 * BaselineProfileGenerator. This measures frames; it does not assert visual
 * fidelity, true task completion, school data, or successful route clicks.
 */
@RunWith(AndroidJUnit4::class)
class NavigationFrameBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun rapidMainTabSwitches() = rule.measureRepeated(
        packageName = BaselineProfileGenerator.TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(
            baselineProfileMode = BaselineProfileMode.Disable,
            warmupIterations = 3
        ),
        startupMode = StartupMode.WARM,
        iterations = 6,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        val w = device.displayWidth
        val y = (device.displayHeight * 0.94f).toInt()
        // Schedule, profile, home, tasks, academic, then home.
        listOf(0.32f, 0.86f, 0.14f, 0.68f, 0.50f, 0.14f).forEach { tab ->
            device.click((w * tab).toInt(), y)
            Thread.sleep(220)
        }
    }
}
