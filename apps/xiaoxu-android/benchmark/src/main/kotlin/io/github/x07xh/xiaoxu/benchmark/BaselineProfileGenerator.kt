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

        // 五个主入口各走一遍：让导航、各页首帧组合、Room 查询都进入 profile。
        // 不用文字定位（文案会变），用屏幕比例点底栏，5 等分。
        val w = device.displayWidth
        val h = device.displayHeight
        val barY = (h * 0.94f).toInt()
        listOf(0.14f, 0.32f, 0.5f, 0.68f, 0.86f).forEach { ratio ->
            device.click((w * ratio).toInt(), barY)
            Thread.sleep(600)
        }
        // 回到首页，让首屏成为 profile 的收尾路径。
        device.click((w * 0.14f).toInt(), barY)
        Thread.sleep(600)
    }

    companion object {
        const val TARGET_PACKAGE = "io.github.x07xh.xiaoxu"
    }
}
