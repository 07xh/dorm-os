package io.github.x07xh.xiaoxu.data

import java.time.LocalDateTime
import org.json.JSONObject

internal data class ChaoxingExamNoticeEvidence(
    val sourceKey:String, val title:String, val alias:String, val courseId:String?
)

/** Reproduce the existing inbox identity from this notice's own fields.
 * This class never requests an exam URL or claims an attempt was completed. */
internal object ChaoxingExamNoticeEvidenceReader {
    private fun text(value:String,max:Int)=value.replace(Regex("<[^>]*>"),"")
        .replace(Regex("[\\u0000-\\u0008\\u000B-\\u001F]")," ").trim().take(max)
    private fun fields(notice:JSONObject):Map<String,String> {
        val result=linkedMapOf<String,String>()
        for(line in notice.optString("content").split(Regex("\\r\\n|\\r|\\n"))) {
            val m=Regex("^\\s*([^：:\\s]{2,20})[：:]\\s*(.*?)\\s*$").matchEntire(line) ?: continue
            result.putIfAbsent(m.groupValues[1],text(m.groupValues[2],240))
        }
        return result
    }
    fun isExam(notice:JSONObject):Boolean =
        notice.optString("title").contains("考试") || fields(notice).containsKey("考试时间")

    fun fromNotice(notice:JSONObject):ChaoxingExamNoticeEvidence? {
        if(!isExam(notice)) return null
        val id=sequenceOf("idCode","uuid","id","noticeId","noticeid")
            .map {notice.optString(it)}.firstOrNull {it.isNotBlank()} ?: return null
        if(!id.matches(Regex("[A-Za-z0-9_$:-]{1,100}"))) return null
        val f=fields(notice)
        val courseId=Regex("courseId(\\d{1,24})").matchEntire(notice.optString("tag"))?.groupValues?.get(1)
        val course=courseId ?: text(f["课程名称"].orEmpty(),80)
        val name=text(f["试卷名称"] ?: f["考试名称"] ?: text(notice.optString("title"),140),100)
        if(name.isBlank()) return null
        // The legacy inbox hashes the explicitly dated START of 考试时间;
        // this is identity metadata, not a guessed deadline or an exam ID.
        val start=Regex("20\\d{2}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?")
            .find(f["考试时间"].orEmpty())?.value
        val day=start?.let {runCatching {LocalDateTime.parse(it.replace(' ','T')).toLocalDate().toString()}.getOrNull()}
            ?: "unknown"
        val alias="cxn:"+ChaoxingLocalReader.hash("exam|$course|考试：$name|$day").take(40)
        return ChaoxingExamNoticeEvidence("examnotice:"+ChaoxingLocalReader.hash("chaoxing-exam-notice:"+id),
            "考试：$name",alias,courseId)
    }
}
