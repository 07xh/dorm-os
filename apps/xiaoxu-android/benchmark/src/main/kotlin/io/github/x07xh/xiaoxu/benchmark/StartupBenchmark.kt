package io.github.x07xh.xiaoxu.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动基准（Issue #334）。四组 CompilationMode 就是"profile 有没有用"的对照实验。
 *
 * 运行：`gradle :benchmark:connectedAndroidTest`
 * 前提：设备/模拟器已连接，且装的是 `release`（profileable、debuggable=false）包。
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun coldStartupPartialNoProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 3)
    )

    @Test
    fun coldStartupPartialWithProfile() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
    )

    @Test
    fun coldStartupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = BaselineProfileGenerator.TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    companion object {
        // 官方样例用 1（跑得快），但 1 个样本无法判断差异；这里给 10。
        private const val ITERATIONS = 10
    }
}
