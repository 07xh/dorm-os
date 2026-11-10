package io.github.x07xh.xiaoxu.data

import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.LocalDate

/** Public, immutable campus announcement snapshot. Never a school login/session record. */
internal data class PublicNoticeArchiveRead(
    val items: List<AcademicNotice>,
    val fetchedAt: String
)

/**
 * Read the *existing* verified public/data/notices.json archive into the same
 * AcademicNotice model already used by the Android message center.
 *
 * Fail closed on a corrupted source or origin. The caller may continue with the
 * independent /api/notices response if this optional archive is unavailable.
 */
internal object PublicNoticeArchive {
    private const val SCHEMA = "xiaoxu/notices-v1"
    private val allowedSources = setOf("jwc", "school", "student", "jwc-api",
        "dianqi", "gcxlzx", "qysy")
    private val articlePath = Regex(
        """^/(?:(?:xsc|jiaowu|dianqi|gcxlzx|qysy)/)?info/\d{3,6}/\d{1,8}\.htm$"""
    )
    private val archiveId = Regex("""^ncwu-[0-9a-f]{16}$""")
    private val dateFormat = Regex("""^20\d{2}-\d{2}-\d{2}$""")

    fun parse(json: JSONObject): PublicNoticeArchiveRead {
        require(json.optString("schema") == SCHEMA && json.optString("mode") == "auto") {
            "公开通知归档尚未经过真实采集验证"
        }
        val fetchedAt = json.optString("lastSuccessAt")
        val instant = runCatching { Instant.parse(fetchedAt) }.getOrNull()
        require(instant != null && !instant.isAfter(Instant.now().plusSeconds(300))) {
            "公开通知归档缺少有效采集时间"
        }
        val rawItems = json.optJSONArray("items")
            ?: throw IllegalArgumentException("公开通知归档缺少文章列表")
        require(rawItems.length() in 2..240) { "公开通知归档条数异常" }

        val distinct = LinkedHashMap<String, AcademicNotice>()
        for (index in 0 until rawItems.length()) {
            val row = rawItems.optJSONObject(index)
                ?: throw IllegalArgumentException("公告记录格式异常")
            val sourceId = row.optString("sourceId")
            val id = row.optString("id")
            // Unverified manual seed entries are never promoted merely because
            // the later archive was collected successfully.
            if (id.startsWith("seed-") || id.startsWith("seed:")) continue
            require(sourceId in allowedSources && archiveId.matches(id)) {
                "公告来源或记录标识不可信"
            }
            val url = trustedPublicArticleUrl(row.optString("url"))
                ?: throw IllegalArgumentException("公告文章链接不属于学校官方白名单")
            val title = row.optString("title").trim()
            require(title.length in 8..220) { "公告标题不完整" }
            val type = when (row.optString("category")) {
                "考试" -> "exam"
                "教学", "校园", "学生事务" -> "notice"
                else -> throw IllegalArgumentException("公告分类无效")
            }
            val dateRaw = row.optString("date")
            val dated = if (dateFormat.matches(dateRaw) &&
                runCatching { LocalDate.parse(dateRaw) }.isSuccess) dateRaw else ""
            distinct.putIfAbsent(url, AcademicNotice(
                id = url, title = title, category = type, dateLabel = dated, url = url
            ))
        }
        require(distinct.size >= 2) { "有效官方公告不足两条" }
        return PublicNoticeArchiveRead(distinct.values.toList(), fetchedAt)
    }

    private fun trustedPublicArticleUrl(candidate: String): String? = try {
        val url = URL(candidate)
        if (url.protocol != "https" ||
            url.host !in setOf("www.ncwu.edu.cn", "www2.ncwu.edu.cn") ||
            url.port != -1 || url.userInfo != null || url.query != null ||
            url.ref != null || !articlePath.matches(url.path)) null
        else (url.protocol + "://" + url.host + url.path).takeIf { it == candidate }
    } catch (_: Exception) {
        null
    }
}
