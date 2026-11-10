package io.github.x07xh.xiaoxu.data

import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChaoxingDeadlineEvidenceTest {
    @Test fun onlyMonthDayFromOfficialWorkWindowCannotBecomeAFullDate() {
        val parsed = ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(
            "<html><body>作答时间：11-08 08:00 至 11-15 23:59</body></html>")
        assertEquals("11-15 23:59", parsed?.displayed)
        assertFalse(parsed!!.hasYear)
    }

    @Test fun aCompleteOfficialTimeIsPreservedWithoutGuessing() {
        val parsed = ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(
            "<div>提交截止时间：2026-11-15 23:59</div>")
        assertEquals("2026-11-15 23:59", parsed?.displayed)
        assertTrue(parsed!!.hasYear)
    }

    @Test fun invalidConflictingOrHiddenValuesDoNotProduceADeadline() {
        assertNull(ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(
            "<div>截止时间：2026-02-29 10:00</div>"))
        assertNull(ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(
            "<div>截止时间：2026-11-15 23:59</div><p>结束时间：2026-11-16 00:00</p>"))
        assertNull(ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(
            "<script>const text='截止时间：2026-11-15 23:59'</script><p>未提交</p>"))
        assertNull(ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(
            "<div>截止时间：2026-11-15 25:59</div>"))
    }

    @Test fun examNoticeIsMetadataOnlyAndMustHaveAnExplicitLabeledTime() {
        val notice = JSONObject().put("id","1001")
            .put("title","Python期末考试通知")
            .put("content","<p>考试结束时间：2026-11-15 23:59</p>")
        assertEquals("2026-11-15 23:59",
            ChaoxingDeadlineEvidenceReader.fromExamNotice(notice)?.displayed)
        assertNull(ChaoxingDeadlineEvidenceReader.fromExamNotice(
            JSONObject(notice.toString()).put("title","普通作业通知")))
        assertNull(ChaoxingDeadlineEvidenceReader.fromExamNotice(
            JSONObject(notice.toString()).put("content","请于本周完成考试")))
    }

    private fun fingerprint(kind: String, key: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("chaoxing|" + kind + "|" + key).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test fun onlyIdentityBoundHomeworkAndExamNoticeCanExposeAFullDueDate() {
        val workId = "work:123:456:901"
        val homework = NativeSchoolRecord("chaoxing","homework", fingerprint("homework",workId),
            "实验报告","当前网页显示的状态：未提交 · 官网截止时间：2026-11-15 23:59 · 官方作业身份："+workId,
            "mooc1.chaoxing.com",1000)
        assertEquals("2026-11-15 23:59",nativeChaoxingDeadline(homework)?.label)
        assertEquals("2026-11-15",nativeChaoxingDeadline(homework)?.date.toString())
        assertNull(nativeChaoxingDeadline(homework.copy(fingerprint="forged")))
        assertNull(nativeChaoxingDeadline(homework.copy(sourceHost="example.com")))
        val partial=homework.copy(details=homework.details.replace(
            "官网截止时间：2026-11-15 23:59","官网截止片段：11-15 23:59"))
        assertNull(nativeChaoxingDeadline(partial)?.date)
        assertEquals("11-15 23:59（年份未提供）",nativeChaoxingDeadline(partial)?.label)

        val noticeId="examnotice:"+"a".repeat(64)
        val exam=NativeSchoolRecord("chaoxing","exam_notice",fingerprint("exam_notice",noticeId),
            "Python期末考试","官网考试通知状态：官网考试通知 · 官网截止时间：2026-11-20 10:00 · 考试通知身份："+noticeId,
            "notice.chaoxing.com",1000)
        assertEquals("2026-11-20 10:00",nativeChaoxingDeadline(exam)?.label)
        assertNull(nativeChaoxingDeadline(exam.copy(sourceHost="mooc1.chaoxing.com")))
    }

    @Test fun theSingleOnDeviceReaderNeverFollowsExamActions() {
        val examUrl="https://mooc1.chaoxing.com/examapi/intoexamorwork?workorexam=exam"
        val sent=mutableListOf<String>()
        val transport=ChaoxingReadTransport { request ->
            sent.add(request.url)
            val path=java.net.URI(request.url).path
            val text=when(path) {
                "/base" -> "<html><script>var uid='314159';</script></html>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true).put("notices",
                    JSONObject().put("lastPage",1).put("list",
                        org.json.JSONArray().put(JSONObject().put("id","1001")
                            .put("title","Python期末考试通知")
                            .put("content","考试结束时间：2026-11-15 23:59")
                            .put("attachment",org.json.JSONArray().put(JSONObject().put("url",examUrl)))))).toString()
                "/mooc2-ans/visit/courselistdata" -> "<p>暂无课程</p>"
                else -> error("Unexpected exam or work navigation: "+path)
            }
            ChaoxingReadPage(request.url,text)
        }
        val result=ChaoxingLocalReader(transport).scan(2026)
        val exam=result.items.single {it.kind=="exam_notice"}
        assertEquals("2026-11-15 23:59",exam.deadline?.displayed)
        assertTrue(result.complete)
        assertFalse(sent.contains(examUrl))
        assertFalse(result.items.any {it.kind=="homework"})
    }
}
