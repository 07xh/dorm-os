package io.github.x07xh.xiaoxu.data

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Explicit source withdrawal is neither submission nor a network failure. */
internal object ChaoxingWorkAvailability {
    fun isRemovedStatus(raw:String):Boolean = raw.trim().trimEnd('。','！','!') in setOf(
        "已删除","已撤回","作业已删除","作业不存在","作业已撤回")

    fun hidden(element:Element):Boolean = (listOf(element)+element.parents()).any {
        val style=it.attr("style").replace(Regex("\\s+"),"").lowercase()
        it.hasAttr("hidden") || it.attr("aria-hidden").equals("true",true) ||
            "display:none" in style || "visibility:hidden" in style
    }

    fun fromDetail(document:Document):String? {
        // A question, answer, editable form or hidden template cannot declare
        // that the whole assignment has disappeared.
        if(document.selectFirst("#submitForm,.questionLi,.question,.TiMu,.answerList")!=null) return null
        if(hidden(document.body())) return null
        val body=document.body().clone()
        body.select("script,style,noscript").remove()
        body.getAllElements().filter(::hidden).forEach {it.remove()}
        val text=body.text().replace(Regex("\\s+"),"")
            .removePrefix("提示：").removePrefix("提示:")
        if(text.length>160) return null
        val prefix="(?:您访问的|您查看的|你访问的|该|此|本|当前)?作业"
        val tail="[。！!]?(?:确定|返回|返回首页|返回课程|关闭)?"
        return when {
            Regex("^$prefix(?:不存在(?:或已(?:被)?删除)?|已不存在)$tail$").matches(text) -> "作业不存在"
            Regex("^(?:${prefix}已(?:被(?:老师|教师|任课老师)?)?删除|(?:老师|教师|任课老师)已删除该作业)$tail$").matches(text) -> "已删除"
            Regex("^${prefix}已撤回$tail$").matches(text) -> "已撤回"
            else -> null
        }
    }
}
