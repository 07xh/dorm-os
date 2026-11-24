package io.github.x07xh.xiaoxu.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成校序的 Baseline Profile（Issue #334）。
 *
 * `includeInStartupProfile = true` 让这份 profile 同时进入 startup profile，
 * 冷启动时 ART 会优先 AOT 编译这些方法，减少解释执行与 JIT。
 *
 * 运行：`gradle :app:generateBaselineProfile`（需连接设备或模拟器）
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndMainTabs() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Use the same verified locators as the frame benchmark.
        listOf("HOME", "SCHEDULE", "TASKS", "ACADEMIC", "PROFILE", "HOME").forEach { route ->
            device.selectMainTab(route)
            Thread.sleep(600)
        }
    }

    companion object {
        const val TARGET_PACKAGE = "io.github.x07xh.xiaoxu"
    }
}
