package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.CloudUiState
import io.github.x07xh.xiaoxu.data.VerifiedLesson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表新鲜度的回归测试（PR #269 第二轮 review）。
 *
 * 唯一判定依据是 CloudUiState 里的真实字段：
 * - scheduleFresh 只在本次 /api/v1/schedule 成功返回后为 true；
 * - scheduleStatus 是服务端给出的来源状态（ready / stale / not_connected …）；
 * - busy 表示当前有一次刷新在进行。
 *
 * 关键不变量：**上次保留的课程，绝不能冒充本次官方读取成功。**
 */
class ScheduleFreshnessTest {

    private fun lesson(title: String = "高等数学") = VerifiedLesson(
        id = "L1", term = "2026-2027-1", title = title, teacher = "示例教师",
        location = "教三楼302", weekday = 4, startSection = 1, endSection = 2,
        weeks = listOf(9, 10)
    )

    @Test
    fun readyWithPreviousLessonsAndFailedScheduleReadIsStale() {
        // 原状态 ready + 有旧课程，本次课表请求失败（scheduleFresh 保持 false）。
        val state = CloudUiState(
            authenticated = true,
            lessons = listOf(lesson()),
            scheduleStatus = "ready",
            scheduleFetchedAt = "2026-11-04T12:00:00Z",
            scheduleFresh = false,
            busy = false,
            privateError = "云端任务、微信候选刷新失败，部分结果可能过期"
        )
        val ui = deriveScheduleUiState(state) as ScheduleUiState.Verified
        assertTrue("上次保留的课程必须标记 stale", ui.stale)
        assertFalse("刷新已结束，不应是 refreshing", ui.refreshing)
        assertEquals("上次成功读取时间必须原样带出", "2026-11-04T12:00:00Z", ui.fetchedAt)
        assertEquals("课程不能被清空", 1, ui.lessons.size)
        // 课表专属提示不得直接搬运聚合 privateError。
        assertEquals("本次课表请求未成功", ui.refreshWarning)
        assertFalse(
            "聚合错误文本不得出现在课表提示里",
            ui.refreshWarning.orEmpty().contains("微信")
        )
    }

    @Test
    fun readyWithPreviousLessonsWhileRefreshingShowsRefreshing() {
        // 原状态 ready + 有旧课程，刷新进行中（CloudClient 开始时把 scheduleFresh 置 false）。
        val state = CloudUiState(
            authenticated = true,
            lessons = listOf(lesson()),
            scheduleStatus = "ready",
            scheduleFetchedAt = "2026-11-04T12:00:00Z",
            scheduleFresh = false,
            busy = true,
            privateError = null
        )
        val ui = deriveScheduleUiState(state) as ScheduleUiState.Verified
        assertTrue("刷新中也只能算上次数据", ui.stale)
        assertTrue("必须能区分出刷新中", ui.refreshing)
        assertNull("刷新中还没有失败提示", ui.refreshWarning)
        assertEquals(1, ui.lessons.size)
    }

    @Test
    fun serverMarkedStaleStaysStaleEvenWhenReadSucceeded() {
        // 本次请求成功，但服务端自己说这份数据是 stale。
        val state = CloudUiState(
            authenticated = true,
            lessons = listOf(lesson()),
            scheduleStatus = "stale",
            scheduleFresh = true,
            busy = false
        )
        val ui = deriveScheduleUiState(state) as ScheduleUiState.Verified
        assertTrue("服务端 stale 必须保留", ui.stale)
        assertFalse(ui.refreshing)
        assertNull(ui.refreshWarning)
    }

    @Test
    fun freshSuccessfulReadIsVerifiedForThisRead() {
        // 本次课表成功读取、状态 ready、没有错误。
        val state = CloudUiState(
            authenticated = true,
            lessons = listOf(lesson()),
            scheduleStatus = "ready",
            scheduleFetchedAt = "2026-11-05T09:00:00Z",
            scheduleFresh = true,
            busy = false,
            privateError = null
        )
        val ui = deriveScheduleUiState(state) as ScheduleUiState.Verified
        assertFalse("本次读取成功才允许显示已核验", ui.stale)
        assertFalse(ui.refreshing)
        assertNull(ui.refreshWarning)
        assertEquals("2026-11-05T09:00:00Z", ui.fetchedAt)
    }

    @Test
    fun taskOrWechatFailureDoesNotTaintSuccessfulScheduleRead() {
        // 课表成功、任务 / 微信候选失败：课表仍是本次已核验，错误只属于别的接口。
        val state = CloudUiState(
            authenticated = true,
            lessons = listOf(lesson()),
            scheduleStatus = "ready",
            scheduleFresh = true,
            tasksFresh = false,
            busy = false,
            privateError = "云端任务、微信候选刷新失败，部分结果可能过期"
        )
        val ui = deriveScheduleUiState(state) as ScheduleUiState.Verified
        assertFalse("任务 / 微信失败不得连带课表", ui.stale)
        assertNull("不得把聚合错误当成课表刷新提示", ui.refreshWarning)
    }

    @Test
    fun aggregateErrorWithoutLessonsIsNotReportedAsScheduleError() {
        // 没有任何课程，且只有聚合错误：只能说"这次云端读取失败"，不能点名课表请求。
        val state = CloudUiState(
            authenticated = true,
            lessons = emptyList(),
            scheduleStatus = "ready",
            scheduleFresh = false,
            busy = false,
            privateError = "云端任务、连接状态刷新失败，部分结果可能过期"
        )
        val ui = deriveScheduleUiState(state) as ScheduleUiState.Failed
        assertFalse("不得直接搬运聚合错误", ui.message.contains("任务"))
        assertFalse("不得直接搬运聚合错误", ui.message.contains("微信"))
        assertTrue("应说明是本次云端读取失败", ui.message.contains("云端读取失败"))
    }

    @Test
    fun localImportedTimetableIsIndependentFromCloudFreshness() {
        // 本机导入课表：不受 scheduleFresh / 刷新失败影响，但永远标注待本人核对。
        val local = localImportedScheduleState(listOf(lesson("课程导入验证（示例）")))
        assertFalse(local.stale)
        assertFalse(local.refreshing)
        assertNull(local.refreshWarning)
        assertTrue(local.sourceLabel.contains("本机导入"))
        assertEquals(1, local.lessons.size)

        // 云端未连接时，课表状态仍然是"未连接"，本机导入不会把云端状态变绿。
        val offline = deriveScheduleUiState(
            CloudUiState(authenticated = false, authMessage = "云端未连接")
        )
        assertTrue(offline is ScheduleUiState.NotConnected)
    }
}
