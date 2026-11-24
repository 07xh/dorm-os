package io.github.x07xh.xiaoxu.ui.v2

import java.text.Normalizer
import java.util.Locale

internal data class UiV2Setting(
    val route: String,
    val title: String,
    val group: String,
    val keywords: List<String> = emptyList()
)

/** Only destinations implemented by the existing settings router are indexed. */
internal val uiV2SettingsIndex = listOf(
    UiV2Setting("timetable-settings", "课表设置", "学习与课程", listOf("学期", "教学周", "课程", "导入", "导出")),
    UiV2Setting("notifications", "通知与提醒", "学习与课程", listOf("消息", "免打扰", "通知权限")),
    UiV2Setting("connections", "数据连接", "账户与数据", listOf("登录", "云端", "教务", "青果", "学习通", "第二课堂")),
    UiV2Setting("backup", "备份与恢复", "账户与数据", listOf("本机", "任务备份", "恢复", "数据")),
    UiV2Setting("motion", "外观与动效", "其他", listOf("动画", "灵动", "减少动画", "性能", "流畅度")),
    UiV2Setting("about", "关于校序", "其他", listOf("版本", "应用", "开发者")),
    UiV2Setting("notifications-course", "课程提醒", "通知与提醒", listOf("上课", "课前", "通知")),
    UiV2Setting("notifications-task", "任务提醒", "通知与提醒", listOf("截止", "作业", "通知")),
    UiV2Setting("licenses", "开源许可", "关于校序", listOf("开源", "许可证", "license", "版权"))
)

internal fun uiV2NormalizeSettingQuery(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filterNot { it.isWhitespace() }

internal fun uiV2SearchSettings(query: String): List<UiV2Setting> {
    val q = uiV2NormalizeSettingQuery(query)
    if (q.isEmpty()) return emptyList()
    return uiV2SettingsIndex.mapNotNull { entry ->
        val title = uiV2NormalizeSettingQuery(entry.title)
        val score = when {
            title == q -> 100
            title.startsWith(q) -> 80
            q in title -> 60
            entry.keywords.any { uiV2NormalizeSettingQuery(it) == q } -> 50
            entry.keywords.any { q in uiV2NormalizeSettingQuery(it) } -> 40
            q in uiV2NormalizeSettingQuery(entry.group) -> 20
            else -> 0
        }
        if (score == 0) null else entry to score
    }.sortedByDescending { it.second }.map { it.first }
}
