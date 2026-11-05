package io.github.x07xh.xiaoxu.ui

import androidx.compose.ui.graphics.Color

/**
 * 海报版演示数据（Issue #284 视觉对齐）。
 *
 * 这**不是**学校真实数据：仅当本机没有任何可信课表 / 任务 / 公告时，用来把
 * 版式骨架填满，让页面结构与设计海报一致。所有使用它的页面必须同时显示
 * [PosterDemoBadge]，绝不允许把演示内容冒充成"已核验"的官方数据。
 *
 * 一旦用户连接学校、导入课表或读到云端任务，页面就改用真实记录渲染，
 * 演示数据不参与任何统计、不写入 Room、不上传云端。
 */

data class PosterLesson(
    val title: String,
    val room: String,
    val weekday: Int,      // 1 = 周一 … 7 = 周日
    val section: Int,      // 起始节次：1 / 3 / 5 / 7 / 9
    val span: Int = 2,
    val tint: PosterCourseTint,
    val teacher: String = "",
    val weeks: String = "第 1–16 周（共 16 周）"
)

enum class PosterCourseTint(val background: Color, val foreground: Color) {
    Blue(Color(0xFFDCE8FF), Color(0xFF2159D8)),
    Amber(Color(0xFFFFE9CC), Color(0xFFB96A00)),
    Green(Color(0xFFD8F3E4), Color(0xFF167A52)),
    Violet(Color(0xFFE8DEFF), Color(0xFF6E45C6)),
    Rose(Color(0xFFFFDCE2), Color(0xFFC93850)),
    Teal(Color(0xFFD6F0F5), Color(0xFF0E7280))
}

data class PosterTask(
    val title: String,
    val source: String,
    val deadline: String,
    val urgent: Boolean = false,
    val tint: PosterCourseTint
)

data class PosterMessage(
    val title: String,
    val time: String,
    val summary: String,
    val tag: String,
    val tint: PosterCourseTint,
    val detail: String = ""
)

data class PosterCandidate(
    val group: String,
    val members: Int,
    val time: String,
    val summary: String,
    val extracted: String
)

object PosterDemo {
    const val term = "2026 秋季学期"
    const val week = "第 10 周"
    const val date = "2026 年 11 月 4 日 · 星期三"

    val hero = PosterLesson(
        title = "电路基础",
        room = "实验楼 E103",
        weekday = 3,
        section = 5,
        tint = PosterCourseTint.Rose,
        teacher = "李明航 教授"
    )

    val lessons = listOf(
        PosterLesson("高等数学", "A203", 1, 1, tint = PosterCourseTint.Blue),
        PosterLesson("高等数学", "A203", 5, 1, tint = PosterCourseTint.Blue),
        PosterLesson("大学英语", "C102", 3, 1, tint = PosterCourseTint.Amber),
        PosterLesson("高等数学", "A203", 4, 3, tint = PosterCourseTint.Blue),
        PosterLesson("Python程序设计", "B402", 2, 3, tint = PosterCourseTint.Green),
        PosterLesson("Python程序设计", "B402", 6, 7, tint = PosterCourseTint.Green),
        PosterLesson("工程制图", "B201", 3, 5, tint = PosterCourseTint.Violet),
        PosterLesson("电路基础", "E103", 4, 5, tint = PosterCourseTint.Rose),
        PosterLesson("思想道德与法治", "B207", 5, 7, tint = PosterCourseTint.Teal)
    )

    val tasks = listOf(
        PosterTask("电路基础 · 课后作业 3", "课程系统 · 实验楼 E103", "3 天后截止", tint = PosterCourseTint.Blue),
        PosterTask("高等数学 · 期中复习提纲", "教师发布 · 教学楼 A203", "本周五截止", urgent = true, tint = PosterCourseTint.Violet),
        PosterTask("工程制图 · 实验报告", "课程系统 · 工程楼 B201", "11 月 12 日截止", tint = PosterCourseTint.Green)
    )

    val messages = listOf(
        PosterMessage(
            title = "26电气学习交流", time = "今天 18:24 · 微信群",
            summary = "请同学们周五提交高效课堂练习，并上传至课程平台。",
            tag = "待提取", tint = PosterCourseTint.Green,
            detail = "请同学们周五提交高效课堂练习，并上传至课程平台。具体要求：包含第 3–5 章习题，文件命名为「学号_高数练习.pdf」。"
        ),
        PosterMessage(
            title = "教务通知", time = "今天 14:06 · 官网通知",
            summary = "期中教学安排已发布，请以学校官方教务通知为准。",
            tag = "官方通知", tint = PosterCourseTint.Blue,
            detail = "期中教学安排已发布，请以学校官方教务通知为准。"
        ),
        PosterMessage(
            title = "班级通知群", time = "今天 16:42 · 微信群",
            summary = "本周二课堂活动报名截止时间为周日晚 20:00。",
            tag = "待提取", tint = PosterCourseTint.Green,
            detail = "本周二课堂活动报名截止时间为周日晚 20:00，请及时在班级群接龙。"
        ),
        PosterMessage(
            title = "校序 · 本地提醒", time = "今天 09:20 · 本地通知",
            summary = "距工程制图练习截止还有 3 天，可提前安排时间。",
            tag = "本地提示", tint = PosterCourseTint.Violet,
            detail = "距工程制图练习截止还有 3 天，可提前安排时间。"
        )
    )

    val candidates = listOf(
        PosterCandidate("电路基础学习交流群", 128, "今天 18:24", "本周六前提交实验报告初稿，请大家注意截止时间。", "提交实验报告（截止：本周六）"),
        PosterCandidate("高数 A 班学习群", 206, "今天 16:42", "期中复习资料已上传网盘，建议本周完成自测。", "复习与自测（本周内）"),
        PosterCandidate("工程制图讨论群", 84, "今天 09:20", "下周二课堂展示，请提前准备 PPT 并在群内提交初版。", "课堂展示准备（截止：下周二）")
    )
}
