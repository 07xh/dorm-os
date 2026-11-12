package io.github.x07xh.xiaoxu.data

import java.time.LocalDate
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * A date copied from visible official homework/notice text, not a forecast.
 * Without an explicit year the result must never be used for overdue status,
 * WorkManager scheduling or a fabricated YYYY-MM-DD date.
 */
internal data class ChaoxingDeadlineEvidence(val displayed: String, val hasYear: Boolean)

/** Strict read-only extraction; no exam entry, submission or account operations. */
internal object ChaoxingDeadlineEvidenceReader {
    private val fullDate = Regex("""^(20\d{2})[-/年](\d{1,2})[-/月](\d{1,2})日?\s+(\d{1,2}):(\d{2})$""")
    private val shortDate = Regex("""^(\d{1,2})[-/月](\d{1,2})日?\s+(\d{1,2}):(\d{2})$""")
    private val dateValue = """(?:20\d{2}[-/年])?\d{1,2}[-/月]\d{1,2}日?\s+\d{1,2}:\d{2}"""
    private val workLabel = Regex("""(?:作业截止时间|提交截止时间|截止时间|结束时间)\s*[：:]\s*($dateValue)""")
    // Generic activity or registration deadlines in exam notices are not exam deadlines.
    private val examLabel = Regex("""(?:考试截止时间|考试结束时间|考试作答截止时间|考试作答结束时间|测验截止时间|测验结束时间|测验作答截止时间|测验作答结束时间)\s*[：:]\s*($dateValue)""")
    private val range = Regex("""作答时间\s*[：:]\s*\d{1,2}[-/]\d{1,2}\s+\d{1,2}:\d{2}\s*至\s*($dateValue)""")

    private fun pad(value: Int) = value.toString().padStart(2, '0')

    private fun checked(value: String): ChaoxingDeadlineEvidence? {
        val normalized = value.trim()
        val detailed = fullDate.matchEntire(normalized)
        if (detailed != null) {
            val (year, month, day, hour, minute) = detailed.destructured
            val date = runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
                ?: return null
            if (hour.toInt() !in 0..23 || minute.toInt() !in 0..59) return null
            return ChaoxingDeadlineEvidence("${date} ${pad(hour.toInt())}:${pad(minute.toInt())}", true)
        }
        val short = shortDate.matchEntire(normalized) ?: return null
        val (month, day, hour, minute) = short.destructured
        // The official page omits the year, so February 29 is accepted as a
        // possible calendar date, but it is never treated as this year's date.
        val possible = runCatching { LocalDate.of(2024, month.toInt(), day.toInt()) }.getOrNull()
            ?: return null
        if (hour.toInt() !in 0..23 || minute.toInt() !in 0..59) return null
        return ChaoxingDeadlineEvidence(
            "${pad(possible.monthValue)}-${pad(possible.dayOfMonth)} ${pad(hour.toInt())}:${pad(minute.toInt())}", false)
    }

    private fun extract(text: String, exam: Boolean): ChaoxingDeadlineEvidence? {
        if (text.length !in 1..100000) return null
        val found = linkedSetOf<ChaoxingDeadlineEvidence>()
        val labels = if (exam) examLabel else workLabel
        for (match in labels.findAll(text)) checked(match.groupValues[1])?.let(found::add)
        // A clearly labeled exam window can supply its explicit end.
        // Generic event and registration deadlines remain excluded.
        if (exam) {
            val examRange = Regex("""考试时间\s*[：:]\s*$dateValue\s*(?:至|~|～|—|到)\s*($dateValue)""")
            for (match in examRange.findAll(text)) checked(match.groupValues[1])?.let(found::add)
        }
        if (!exam) for (match in range.findAll(text)) checked(match.groupValues[1])?.let(found::add)
        // Conflicting official ranges/labels must never choose an arbitrary due.
        return found.singleOrNull()
    }

    private fun visibleText(html: String): String {
        val document = Jsoup.parse(html)
        document.select("script,style,noscript,iframe,input,textarea,[hidden],[type=hidden]").remove()
        return document.body().text()
    }

    /** Only the fetched, signed-in official work page or this work's list row. */
    fun fromHomeworkHtml(html: String): ChaoxingDeadlineEvidence? =
        if (html.length in 1..600000) extract(visibleText(html), false) else null

    /** Metadata on an authenticated official exam notice; never follow an exam URL. */
    fun fromExamNotice(notice: JSONObject): ChaoxingDeadlineEvidence? {
        val title = notice.optString("title")
        if (!Regex("考试|测验").containsMatchIn(title) &&
            !ChaoxingExamNoticeEvidenceReader.isExam(notice)) return null
        val content = notice.optString("content")
        if (content.length !in 1..50000) return null
        return extract(visibleText(content), true)
    }
}
