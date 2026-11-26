package com.tyust.course.schedule

import android.content.Context
import android.widget.Toast

/** Xiaoxu integration adapter for the upstream settings page; widget hosting is not yet wired. */
enum class ScheduleWidgetStyle(val title: String, val description: String) {
    Single("简洁单课", "突出当前或下一节课，适合小块桌面空间"),
    Double("双课程", "并排查看当前与下一节的时间、教室"),
    Timeline("今日时间轴", "按时间排列今日课程，突出尚未结束的课程")
}

object ScheduleWidgetUpdater {
    fun requestPin(context: Context, style: ScheduleWidgetStyle = ScheduleWidgetStyle.Double) {
        Toast.makeText(context, "“${style.title}”桌面小组件尚未接入校序", Toast.LENGTH_SHORT).show()
    }
}
