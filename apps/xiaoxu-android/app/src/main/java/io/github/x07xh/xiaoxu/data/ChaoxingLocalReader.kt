package io.github.x07xh.xiaoxu.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal data class ChaoxingReadRequest(val url: String, val method: String = "GET", val form: Map<String, String> = emptyMap())
internal data class ChaoxingReadPage(val url: String, val text: String)
internal fun interface ChaoxingReadTransport { fun request(request: ChaoxingReadRequest): ChaoxingReadPage }
internal class ChaoxingReadFailure(val code: String) : Exception(code)
internal data class ChaoxingReadItem(val kind: String, val sourceKey: String, val title: String, val status: String, val sourceHost: String,
    val noticeAliases: Set<String> = emptySet(), val peerAliases: Set<String> = emptySet(),
    val deadline: ChaoxingDeadlineEvidence? = null)
internal data class ChaoxingLocalRead(val items: List<ChaoxingReadItem>, val accountHash: String,
    val courseCount: Int, val noticePages: Int, val failures: List<String>,
    val supersededKeys: Set<String> = emptySet()) {
    val complete: Boolean get() = failures.isEmpty()
}
internal data class ChaoxingNoticeAlias(val id: String, val course: String, val peer: Boolean)
internal data class ChaoxingDiscoveredLink(val title: String, val url: String,
    val aliases: Set<ChaoxingNoticeAlias> = emptySet())

/** One on-device, read-only flow: notices -> study plans -> actual course entries.
 * Interoperability follows the supplied reference client; HTML parsing is Jsoup.
 * Transport alone handles this app's WebView cookies. Signed links are transient.
 */
internal class ChaoxingLocalReader(
    private val transport: ChaoxingReadTransport,
    private val progress: (String) -> Unit = {},
    private val checkCancelled: () -> Unit = {}
) {
    companion object {
        const val HOME = "https://i.chaoxing.com/base"
        const val COURSES = "https://mooc2-ans.chaoxing.com/mooc2-ans/visit/courselistdata"
        private val numeric = Regex("[0-9]{1,24}")
        private fun clean(value: String, max: Int = 100) = value.replace(Regex("[\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ").trim().take(max)
        internal fun hash(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        internal fun safeUrl(raw: String, base: String = HOME): String? = runCatching {
            if(raw.isBlank()) return null
            val absolute = URI(base).resolve(raw.trim()).toString()
            ChaoxingWebNavigation.target(absolute)
        }.getOrNull()
        private fun params(url: String): Map<String, String> {
            val result = linkedMapOf<String, String>()
            for (part in URI(url).rawQuery.orEmpty().split('&').filter {it.isNotEmpty()}) {
                val pair = part.split('=', limit = 2)
                val key = URLDecoder.decode(pair[0], "UTF-8")
                if (result.containsKey(key)) throw ChaoxingReadFailure("ambiguous_identity")
                result[key] = URLDecoder.decode(pair.getOrElse(1) {""}, "UTF-8")
            }
            return result
        }
        private fun query(url: String, fields: Map<String, String>): String {
            val u = URI(url)
            val values = params(url).toMutableMap().apply {putAll(fields)}
            val query = values.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            return "https://" + u.rawAuthority + u.rawPath + "?" + query
        }
        private fun supported(url: String): Boolean {
            val uri=URI(url);val path=uri.path.lowercase();val p=params(url)
            if (path.contains("/exam/") || p["workorexam"].equals("exam",true)) return false
            return path.contains("/work/") || path.contains("/examapi/intoexamorwork") ||
                path.contains("/userstudyplan/") || path.contains("/documentstudy/")
        }
        internal fun noticeLinks(notice: JSONObject): List<ChaoxingDiscoveredLink> {
            val result=linkedMapOf<String,ChaoxingDiscoveredLink>()
            fun add(title: String, raw: String) {
                val url=safeUrl(raw,"https://notice.chaoxing.com/") ?: return
                if (runCatching {supported(url)}.getOrDefault(false))
                    result[url]=ChaoxingDiscoveredLink(clean(title).ifBlank {"学习任务"},url)
            }
            fun walk(value: Any?, title: String, depth: Int) {
                if (depth>8 || result.size>=1000) return
                when(value) {
                    is String -> if(value.length<=500000 && value.trim().let {it.startsWith("{")||it.startsWith("[")})
                        runCatching {JSONTokener(value).nextValue()}.getOrNull()?.let {walk(it,title,depth+1)}
                    is JSONArray -> for(i in 0 until minOf(value.length(),1000)) walk(value.opt(i),title,depth+1)
                    is JSONObject -> {
                        val label=value.optString("title",value.optString("name",title))
                        for(key in listOf("url","webUrl","href")) if(value.opt(key) is String) add(label,value.optString(key))
                        for(key in value.keys()) walk(value.opt(key),label,depth+1)
                    }
                }
            }
            val title=notice.optString("title","学习任务")
            walk(notice.opt("attachment"),title,0);walk(notice.opt("attachments"),title,0)
            for(key in listOf("content","rtf_content")) for(a in Jsoup.parse(notice.optString(key)).select("a[href]"))
                add(a.text().ifBlank {title},a.attr("href"))
            val aliases = noticeAliases(notice)
            return result.values.map { it.copy(aliases = aliases) }
        }
        /** Reproduce the existing cloud notice identity contract, using the
         * notification's own metadata, never comparing titles across tasks. */
        /** Follow the existing cloud cxn identity contract, not an assignment title. */
        private fun noticeSourceTaskKey(notice: JSONObject): String? {
            val encoded = notice.optJSONObject("extendParam")?.optString("cparams").orEmpty()
            if (encoded.length > 4000) return null
            val key = runCatching {
                JSONObject(encoded).optJSONObject("funConfig")?.optJSONObject("content")?.optString("key")
            }.getOrNull()
            return key?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,100}")) }
        }

        internal fun noticeAliases(notice: JSONObject): Set<ChaoxingNoticeAlias> {
            val course = Regex("courseId(\\d{1,24})").matchEntire(notice.optString("tag"))
                ?.groupValues?.get(1) ?: return emptySet()
            val fields = linkedMapOf<String,String>()
            for (line in notice.optString("content").split(Regex("\\r\\n|\\r|\\n"))) {
                val m = Regex("^\\s*([^：:\\s]{2,20})[：:]\\s*(.*?)\\s*$").matchEntire(line) ?: continue
                fields.putIfAbsent(m.groupValues[1], m.groupValues[2].replace(Regex("<[^>]*>"), "").trim())
            }
            val name = fields["作业名称"]?.take(100)?.takeIf {it.isNotBlank()} ?: return emptySet()
            val title = notice.optString("title")
            val peer = title.contains("互评")
            val identity = if (peer) "peer_review|$course|互评：$name|" else {
                if (!title.contains("作业") && !fields.containsKey("结束时间")) return emptySet()
                val key = noticeSourceTaskKey(notice) ?: return emptySet()
                "assignment|$course|work:$key"
            }
            return setOf(ChaoxingNoticeAlias("cxn:"+hash(identity).take(40), course, peer))
        }
        private fun stringConstant(doc: Document, key: String): String {
            val pattern=Regex("(?:const|let|var)\\s+"+Regex.escape(key)+"\\s*=\\s*(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')")
            for(script in doc.select("script")) {
                val value=pattern.find(script.data())?.groupValues?.get(1) ?: continue
                return runCatching {JSONTokener(value).nextValue() as String}.getOrDefault("")
            }
            return ""
        }
        private fun account(page: ChaoxingReadPage): String {
            if(URI(page.url).host!="i.chaoxing.com") throw ChaoxingReadFailure("login_required")
            val doc=Jsoup.parse(page.text,page.url)
            val uid=stringConstant(doc,"uid").ifBlank {doc.selectFirst("input#uid")?.`val`().orEmpty()}
            val numericUid = uid.ifBlank {
                Regex("""(?:\b(?:var|let|const)\s+uid\s*=\s*|["']uid["']\s*:\s*)["']?(\d{1,24})(?!\d)""")
                    .find(doc.select("script").joinToString("\n") {it.data()})?.groupValues?.get(1).orEmpty()
            }
            if(!numeric.matches(numericUid) || numericUid.all {it == '0'}) throw ChaoxingReadFailure("login_required")
            return hash("chaoxing-owner:"+numericUid)
        }
        /** The official page may include both camelCase and lower-case
         * spellings of the SAME ID. Accept equivalent values but reject
         * conflicting aliases instead of demoting an actual homework item
         * to an unrelated study-task key.
         */
        private fun consistentIdentity(p: Map<String, String>, vararg keys: String): String? {
            val values=keys.mapNotNull { p[it] }.distinct()
            if (values.size > 1) throw ChaoxingReadFailure("ambiguous_identity")
            return values.singleOrNull()
        }
        private fun numericIdentity(p: Map<String, String>, vararg keys: String): String? =
            consistentIdentity(p, *keys)?.takeIf { numeric.matches(it) }
        private fun workKey(url: String): String? {
            val p=params(url)
            val course=numericIdentity(p,"courseId","courseid") ?: return null
            val clazz=numericIdentity(p,"classId","clazzid","classid") ?: return null
            val work=numericIdentity(p,"workId","workid","taskrefId") ?: return null
            return "work:$course:$clazz:$work"
        }
        private fun stableStudyKey(url: String): String {
            val p=params(url)
            // Fallback work links without a complete course/class/work triple
            // must still include their course and class namespace in the
            // fingerprint. Exclude time-limited enc/t tokens for stability.
            val identity=listOfNotNull(
                consistentIdentity(p,"courseId","courseid")?.let {"course=$it"},
                consistentIdentity(p,"classId","clazzid","classid")?.let {"class=$it"},
                p["cpi"]?.let {"cpi=$it"},
                p["encryTaskId"]?.let {"encryTaskId=$it"},
                p["taskId"]?.let {"taskId=$it"},
                consistentIdentity(p,"workId","workid","taskrefId")?.let {"work=$it"},
                p["planId"]?.let {"planId=$it"}
            ).joinToString("&")
            if(identity.isBlank()) throw ChaoxingReadFailure("task_identity")
            return "study:"+hash(URI(url).host+URI(url).path+"?"+identity)
        }
    }
    /**
     * Work detail and course list belong to the same on-device official scan.
     * Preserve a more specific completed/reviewed detail when the list only
     * reports the generic submission state. A later negative/reopened list
     * state always wins; do not turn a reopened work into a completed task.
     */
    internal fun reconcileWorkStatus(listed: String, detailed: String?): String {
        val list = listed.trim()
        val detail = detailed?.trim().orEmpty().takeUnless { it.isBlank() || it == "状态未知" }
        if (list.isBlank()) return detail ?: "状态未知"
        if (detail == null) return list
        if (statusClass(list) == 0) return detail
        val negative = Regex("未提交|未完成|待提交|待完成|未交|退回|重做|重新提交|驳回")
        if (negative.containsMatchIn(list)) return list
        if (negative.containsMatchIn(detail)) return detail
        val positive = Regex("已完成|已提交|已交|提交成功|待批阅|待批改|待评阅|已批阅|已评分|已评阅|已互评|已互評|互评完成|互評完成")
        return if (positive.containsMatchIn(list) && positive.containsMatchIn(detail)) detail else list
    }

    private fun statusClass(value: String): Int = when {
        Regex("未提交|未完成|待提交|待完成|未交|退回|重做|重新提交|驳回").containsMatchIn(value) -> -1
        Regex("已完成|已提交|已交|提交成功|待批阅|待批改|待评阅|已批阅|已批改|已评分|已评阅|已互评|已互評|互评完成|互評完成|已达标").containsMatchIn(value) -> 1
        else -> 0
    }

    private val examGatewayKeys=mutableSetOf<String>()
    private val examGatewayUrls=mutableSetOf<String>()
    private fun isKnownExamGateway(url:String):Boolean =
        URI(url).path.contains("/examapi/intoexamorwork") &&
            (url in examGatewayUrls || workKey(url)?.let {it in examGatewayKeys}==true)
    private fun isExamUrl(url:String):Boolean =
        URI(url).path.lowercase().contains("/exam/") || params(url)["workorexam"].equals("exam",true)
    private val items=linkedMapOf<String,ChaoxingReadItem>()
    private val failures=mutableListOf<String>()
    private val superseded=mutableSetOf<String>()
    private val noticeBindings=linkedMapOf<String,MutableSet<ChaoxingNoticeAlias>>()
    // Notices without an actionable attachment may still contain the exact
    // official work key. Resolve them against a COMPLETE course-list scan only.
    private val detachedNoticeCandidates=linkedMapOf<String,MutableSet<ChaoxingNoticeAlias>>()
    private val courseWorkKeys=linkedSetOf<String>()
    // Transient first-party URLs are only used during this invocation. They
    // must never be written into Room, D1, logs, or the return value.
    private val courseWorkUrls=linkedMapOf<String,String>()
    private fun bindNotice(link: ChaoxingDiscoveredLink, work: String?) {
        if (work == null || !work.matches(Regex("work:\\d{1,24}:\\d{1,24}:\\d{1,24}"))) return
        val aliases = link.aliases.filter {it.course == work.split(':')[1]}
        noticeBindings.getOrPut(work) {linkedSetOf()}.addAll(aliases)
    }
    private fun fetch(url: String, method: String="GET", form: Map<String,String> = emptyMap()):ChaoxingReadPage {
        checkCancelled()
        val safe=safeUrl(url) ?: throw ChaoxingReadFailure("non_official_url")
        val page=transport.request(ChaoxingReadRequest(safe,method,form))
        if(safeUrl(page.url)!=page.url || page.text.length>6000000) throw ChaoxingReadFailure("invalid_response")
        if(URI(page.url).host.orEmpty().startsWith("passport") ||
            (Jsoup.parse(page.text).selectFirst("input[type=password]")!=null)) throw ChaoxingReadFailure("login_required")
        checkCancelled()
        return page
    }
    private fun record(kind:String,key:String,title:String,status:String,host:String,
        deadline: ChaoxingDeadlineEvidence? = null) {
        if(items.size>=5000 && !items.containsKey(key)) throw ChaoxingReadFailure("record_limit")
        if(clean(title).isBlank()) throw ChaoxingReadFailure("record_title")
        // A generic list row cannot downgrade a full-year date from the
        // same work's detail to a month/day-only fragment.
        val previous = items[key]?.deadline
        val bestDeadline = when {
            deadline == null -> previous
            previous?.hasYear == true && !deadline.hasYear -> previous
            else -> deadline
        }
        items[key]=ChaoxingReadItem(kind,key,clean(title),clean(status,60).ifBlank {"状态未知"},host,
            deadline=bestDeadline)
    }
    private fun reportFailure(label:String,code:String) {
        val reason=when(code) {
            "work_row_conflict" -> "同一作业有不同状态，详情仍未能核实"
            "work_page_overlap" -> "分页有重叠，已继续保留新作业"
            "work_page_mismatch" -> "返回页码与请求不符"
            "work_page_stalled" -> "翻页仍返回同一批作业"
            "work_page_empty" -> "后续分页未返回作业"
            "work_page_limit" -> "分页数量超过读取上限"
            "plan_destination_rejected" -> "学习计划未提供作业入口"
            "plan_destination_url" -> "学习计划返回的入口地址无效"
            "plan_destination_type" -> "学习计划入口不是支持的任务类型"
            "work_redirect_exam" -> "作业入口跳到了考试页面"
            "work_redirect_course" -> "作业入口跳回了课程页面"
            "work_redirect_login" -> "作业入口跳到了登录页面"
            "work_redirect_other" -> "作业入口跳到了无法识别的页面"
            else -> null
        }
        failures.add(clean(label,60)+"："+(reason?.let {"$it（$code）"} ?: code))
    }
    private fun recover(label:String, block:()->Unit) {
        try {block()} catch(e:ChaoxingReadFailure) {
            if(e.code in setOf("account_changed","login_required")) throw e
            reportFailure(label,e.code)
        } catch(_:org.json.JSONException) {
            failures.add(clean(label,60)+"：invalid_response")
        }
    }
    fun scan(year:Int):ChaoxingLocalRead {
        items.clear();failures.clear();superseded.clear();noticeBindings.clear()
        examGatewayKeys.clear();examGatewayUrls.clear()
        detachedNoticeCandidates.clear();courseWorkKeys.clear();courseWorkUrls.clear()
        progress("核对本机学习通登录会话…")
        val owner=account(fetch(HOME))
        val links=linkedMapOf<String,ChaoxingDiscoveredLink>()
        var noticePages=0
        var noticesComplete=false
        recover("通知读取") {
            val fields=listOf("notice_type","lastValue","sort","folderUUID","kw","startTime","endTime",
                "gKw","gName","tag","fidsCode","filterSenderPuids","filterTags").associateWith {""}.toMutableMap()
            fields.putAll(mapOf("type" to "2","year" to year.toString(),"queryFolderNoticePrevYear" to "0"))
            val cursors=mutableSetOf<String>()
            for(pageNumber in 1..100) {
                progress("读取通知第 $pageNumber 页…")
                val body=JSONObject(fetch("https://notice.chaoxing.com/pc/notice/getNoticeList","POST",fields.toMap()).text)
                if(!body.optBoolean("status")) throw ChaoxingReadFailure("notice_response")
                val notices=body.optJSONObject("notices")
                val list=notices?.optJSONArray("list") ?: if(notices==null) JSONArray() else throw ChaoxingReadFailure("notice_response")
                noticePages++
                for(i in 0 until list.length()) {
                    val notice = list.getJSONObject(i)
                    val discovered=noticeLinks(notice)
                    if(ChaoxingExamNoticeEvidenceReader.isExam(notice)) {
                        val exam=ChaoxingExamNoticeEvidenceReader.fromNotice(notice)
                        if(exam==null) reportFailure("考试通知","exam_notice_identity")
                        else {
                            record("exam_notice",exam.sourceKey,exam.title,"官网考试通知，提交状态未核实",
                                "notice.chaoxing.com",ChaoxingDeadlineEvidenceReader.fromExamNotice(notice))
                            items[exam.sourceKey]=items.getValue(exam.sourceKey).copy(noticeAliases=setOf(exam.alias))
                            for(entry in discovered) {
                                if(!URI(entry.url).path.contains("/examapi/intoexamorwork")) continue
                                examGatewayUrls.add(entry.url)
                                workKey(entry.url)?.takeIf {exam.courseId!=null && it.split(':')[1]==exam.courseId}
                                    ?.let(examGatewayKeys::add)
                            }
                        }
                        continue
                    }
                    val aliases = noticeAliases(notice)
                    // Plain assignment and peer-review notifications have
                    // distinct legacy cxn IDs. Retain their distinct peer flag.
                    // The server-side cxn hash uses this sourceTaskKey, too.
                    // No title-only match: require a numeric work ID and course.
                    val workKey = noticeSourceTaskKey(notice)
                        ?.takeIf { it.matches(Regex("[0-9]{6,24}")) }
                    if (workKey != null) for (alias in aliases) {
                        detachedNoticeCandidates.getOrPut(alias.course + ":" + workKey) {
                            linkedSetOf()
                        }.add(alias)
                    }
                    for(link in discovered) {
                        if(links.size>=1000) throw ChaoxingReadFailure("notice_limit")
                        links[link.url]=link.copy(aliases = link.aliases + links[link.url]?.aliases.orEmpty())
                    }
                }
                if(list.length()==0 || notices?.optInt("lastPage",1)==1) break
                val cursor=notices?.optString("lastGetId").orEmpty()
                if(cursor.isBlank()||!cursors.add(cursor)||pageNumber==100) throw ChaoxingReadFailure("notice_pagination")
                fields["lastValue"]=cursor
            }
            noticesComplete=true
        }
        for(link in links.values.filter {URI(it.url).path.contains("/userStudyPlan/")}) recover(link.title) {readStudyPlan(link)}
        // Direct assignment links in notices supplement plan children; they are
        // not assumed submitted merely because a notification exists.
        for(link in links.values.filterNot {URI(it.url).path.contains("/userStudyPlan/")}) recover(link.title) {readWorkLink(link)}
        var courseCount=0
        var catalogComplete=false
        val listedCourses=mutableSetOf<String>()
        val failedCourses=mutableSetOf<String>()
        val verifiedScopes=linkedMapOf<String,String>()
        val failedScopes=mutableSetOf<String>()
        recover("课程目录") {
            progress("补查本人课程作业…")
            val form=mapOf("courseType" to "1","courseFolderId" to "0","query" to "","pageHeader" to "0",
                "single" to "","superstarClass" to "","isFirefly" to "0","courseLevelLabelId" to "0","fid" to "","from" to "")
            val page=fetch(COURSES,"POST",form)
            val doc=Jsoup.parse(page.text,page.url)
            val courses=linkedMapOf<String,ChaoxingDiscoveredLink>()
            for(name in doc.select(".course-name")) {
                val a=name.closest("a[href]") ?: name.parent()?.selectFirst("a[href]") ?: continue
                val url=safeUrl(a.attr("href"),page.url) ?: continue
                if(courses.size>=120) throw ChaoxingReadFailure("course_limit")
                courses[url]=ChaoxingDiscoveredLink(name.attr("title").ifBlank {name.text()},url)
            }
            if(courses.isEmpty()&&!doc.text().contains("暂无课程")) throw ChaoxingReadFailure("course_response")
            courseCount=courses.size
            for((index,course) in courses.values.withIndex()) {
                progress("读取课程 ${index+1}/$courseCount："+clean(course.title))
                val courseId=hash(URI(course.url).path+"?"+params(course.url).filterKeys {it in setOf("courseid","clazzid","cpi","enc")}.toString())
                record("course","course:$courseId",course.title,"课程列表","mooc2-ans.chaoxing.com")
                val numericCourse=numericIdentity(params(course.url),"courseId","courseid")
                if(numericCourse!=null) listedCourses.add(numericCourse)
                val numericClass=numericIdentity(params(course.url),"classId","clazzid","classid")
                val scope=if(numericCourse!=null && numericClass!=null) "scope:$numericCourse:$numericClass" else null
                val before=failures.size
                var identitiesComplete=false
                recover(course.title) {identitiesComplete=readCourse(course)}
                if(failures.size!=before) {
                    if(numericCourse!=null) failedCourses.add(numericCourse)
                    if(scope!=null) failedScopes.add(scope)
                } else if(scope!=null && identitiesComplete) verifiedScopes[scope]=course.title
                if(numericCourse==null) throw ChaoxingReadFailure("course_identity")
            }
            catalogComplete=true
        }
        if(catalogComplete) for((scope,title) in verifiedScopes) {
            if(scope !in failedScopes)
                record("course",scope,title,"作业列表已完整核对","mooc1.chaoxing.com")
        }
        progress("确认读取前后为同一账号…")
        if(account(fetch(HOME))!=owner) throw ChaoxingReadFailure("account_changed")
        if (catalogComplete && noticesComplete) {
            // Completeness is scoped to a course, including every listed class.
            // An unrelated failed course must not block proven notice aliases.
            // Finish scanning all courses BEFORE resolving any linkless peer
            // notice. A same-course work ID can occur in multiple classes.
            // Reading either candidate prematurely would produce misleading
            // evidence or turn an ambiguous notification into a scan error.
            val byCourseWork = courseWorkKeys.groupBy { key ->
                val parts = key.split(':')
                parts[1] + ":" + parts[3]
            }
            // Peer-review cxn IDs in the existing cloud contract are based
            // on course + title, not the precise work ID. Two same-title
            // notices can therefore share one alias while naming different
            // official work keys. This must remain ambiguous even if only
            // one of the historical works is still in the live course list.
            val candidatesByAlias = detachedNoticeCandidates.flatMap { (key, aliases) ->
                aliases.map { alias -> alias.id to key }
            }.groupBy({it.first},{it.second}).mapValues { (_, keys) -> keys.toSet() }
            val unique = detachedNoticeCandidates.mapNotNull { (key, aliases) ->
                val unambiguous = aliases.filterTo(linkedSetOf()) {
                    candidatesByAlias[it.id]?.size == 1
                }
                if (unambiguous.isEmpty()) null
                else byCourseWork[key].orEmpty().singleOrNull()
                    ?.takeIf {it.split(':')[1] in (listedCourses - failedCourses)}
                    ?.let { it to unambiguous }
            }
            val peerCompleted = Regex(
                "已互评|已互評|互评已完成|互評已完成|已完成互评|已完成互評|互评完成|互評完成"
            )
            for ((work, aliases) in unique) {
                val before=failures.size
                val original = items[work] ?: continue
                if (aliases.any { it.peer } && !peerCompleted.containsMatchIn(original.status)) {
                    val url = courseWorkUrls[work] ?: continue
                    recover("互评详情") {
                        readWorkLink(ChaoxingDiscoveredLink(original.title,url),forceDetail=true)
                        val detailed = items[work]?.status
                        record("homework",work,original.title,
                            reconcileWorkStatus(original.status,detailed),original.sourceHost)
                    }
                }
                if(failures.size==before)
                    noticeBindings.getOrPut(work) {linkedSetOf()}.addAll(aliases)
            }
        }
        val rows = items.values.map { item ->
            if(item.kind=="exam_notice") return@map item
            val bound = noticeBindings[item.sourceKey].orEmpty()
            item.copy(noticeAliases = bound.filterNot {it.peer}.map {it.id}.toSet(),
                peerAliases = bound.filter {it.peer}.map {it.id}.toSet())
        }
        return ChaoxingLocalRead(rows,owner,courseCount,noticePages,failures.toList(),superseded.toSet())
    }
    private fun readStudyPlan(link:ChaoxingDiscoveredLink) {
        progress("展开学习计划："+clean(link.title))
        var page=fetch(link.url);var doc=Jsoup.parse(page.text,page.url)
        var user=stringConstant(doc,"eTaskUserId")
        if(user.isBlank()) {
            val iframe=stringConstant(doc,"iframeUrl").ifBlank {doc.selectFirst("iframe[src]")?.attr("src").orEmpty()}
            val target=safeUrl(iframe,page.url) ?: throw ChaoxingReadFailure("plan_identity")
            page=fetch(target);doc=Jsoup.parse(page.text,page.url);user=stringConstant(doc,"eTaskUserId")
        }
        if(user.isBlank()||user.length>500) throw ChaoxingReadFailure("plan_identity")
        val origin="https://"+URI(page.url).rawAuthority
        fun data(path:String,values:Map<String,String>):JSONArray {
            val body=JSONObject(fetch(query(origin+path,values),"POST").text)
            if(!body.optBoolean("result")) throw ChaoxingReadFailure("plan_response")
            return body.optJSONArray("data") ?: throw ChaoxingReadFailure("plan_response")
        }
        val base=mapOf("encryTaskUserId" to user)
        val groups=data("/userStudyPlan/getGroupData",base)
        val failureCountBeforePlan=failures.size
        val resolvedWorks=mutableSetOf<String>()
        var assignmentCount=0
        var lockedGroup=false
        if(groups.length()>200) throw ChaoxingReadFailure("group_limit")
        for(i in 0 until groups.length()) {
            val group=groups.getJSONObject(i);val encrypted=group.optString("encryptGroupId")
            if(!group.optBoolean("groupAllowStudy")) {
                lockedGroup=true
                record("study_task","study:"+hash(link.url.substringBefore('?')+user+encrypted),
                    link.title+" · "+group.optString("name","未解锁分组"),"未解锁",URI(page.url).host)
                continue
            }
            recover("学习计划分组 ${i+1}") {
                if(encrypted.isBlank()) throw ChaoxingReadFailure("group_identity")
                val plans=data("/userStudyPlan/getPlanDataByGroupId",base+mapOf("encryGroupId" to encrypted))
                if(plans.length()>2000) throw ChaoxingReadFailure("plan_limit")
                for(j in 0 until plans.length()) {
                    recover("学习计划项目 ${i+1}/${j+1}") {
                        val plan=plans.getJSONObject(j)
                        if (plan.optInt("planType",-1)==4) assignmentCount++
                        val id=plan.optString("planId")
                        val key=if(numeric.matches(id)) "plan:$id" else "study:"+hash(user+encrypted+plan.optString("encryptPlanId"))
                        val accessible=plan.optBoolean("planAllowStudy")||plan.optBoolean("chooseStudy")
                        val finished=when(plan.opt("isFinish")?.toString()?.lowercase()) {
                            "1", "true" -> true
                            "0", "false" -> false
                            else -> null
                        }
                        val status=sequenceOf("statusText","stateText","statusName").map {plan.optString(it)}.firstOrNull {it.isNotBlank()}
                            ?: if(!accessible) "未解锁" else when(finished) {true -> "已完成";false -> "未完成";null -> "状态未知"}
                        val title=plan.optString("name",link.title)
                        record("study_task",key,title,status,URI(page.url).host)
                        // getToStudyUrl resolves the official assignment destination;
                        // it does not start reading progress or submit an assignment.
                        if(plan.optInt("planType",-1)==4&&accessible&&plan.optString("encryptPlanId").isNotBlank()) {
                            val args=base+mapOf("encryptPlanId" to plan.optString("encryptPlanId"),"studyJumpType" to "0","isInterface" to "false")
                            val reply=JSONObject(fetch(query(origin+"/userStudyPlan/getToStudyUrl",args),"POST").text)
                            if(!reply.optBoolean("result")) throw ChaoxingReadFailure("plan_destination_rejected")
                            val url=safeUrl(reply.optJSONObject("data")?.optString("url").orEmpty(),page.url)
                                ?: throw ChaoxingReadFailure("plan_destination_url")
                            if(!supported(url)) throw ChaoxingReadFailure("plan_destination_type")
                            val resolved=readWorkLink(ChaoxingDiscoveredLink(title,url),status)
                            resolved?.let { resolvedWorks.add(it) }
                            // An actual work identity replaces the plan child in the
                            // task center, preventing two copies of the same assignment.
                            if(resolved!=null) {items.remove(key);superseded.add(key)}
                        }
                    }
                }
            }
        }
        // A notification can wrap a whole plan. Bind its legacy identity only
        // if the complete response proves it contains exactly one assignment.
        if (failures.size == failureCountBeforePlan && !lockedGroup && assignmentCount == 1 && resolvedWorks.size == 1)
            bindNotice(link, resolvedWorks.single())
    }
    /** Reconcile every observed navigation identity before accepting status.
     * A gateway may omit IDs, but a redirect must never change a known ID.
     */
    private fun resolvedWorkKey(urls: List<String>): String? {
        val fields=listOf(arrayOf("courseId","courseid"), arrayOf("classId","clazzid","classid"),
            arrayOf("workId","workid","taskrefId"))
        val values=fields.map { names ->
            val ids=urls.mapNotNull {numericIdentity(params(it),*names)}.distinct()
            if(ids.size>1) throw ChaoxingReadFailure("work_identity_conflict")
            ids.singleOrNull()
        }
        return if(values.all {it!=null}) "work:"+values.joinToString(":") else null
    }
    private fun workStatus(doc: Document): String {
        val explicit=doc.select("p.status,.workStatus,.work-status")
            .firstOrNull {it.closest("#submitForm")==null}?.text().orEmpty()
        if(explicit.isNotBlank()) return explicit
        // An editable question/form can contain any status words. They are not
        // evidence of submission. Follow the reference client's form boundary.
        if(doc.selectFirst("#submitForm")!=null) return ""
        return Regex("已互评|已互評|互评已完成|互評已完成|已完成互评|已完成互評|互评完成|互評完成|已批阅|已批閱|已批改|已评分|已完成此作业|已提交[，,、 ]*待批阅|已提交|已完成|待批阅|提交成功|已达标|已退回|未提交")
            .find(doc.text())?.value.orEmpty()
    }
    private fun unsupportedWorkDestination(url:String):String {
        // Only emit fixed categories: never expose signed URLs, query values,
        // response bodies, or cookies in the user-copyable read report.
        val path=URI(url).path.lowercase()
        return when {
            path.contains("/exam/") || params(url)["workorexam"].equals("exam",true) -> "work_redirect_exam"
            path.contains("/mycourse/") || path.contains("/visit/") -> "work_redirect_course"
            path.contains("login") -> "work_redirect_login"
            else -> "work_redirect_other"
        }
    }
    private fun readWorkLink(link:ChaoxingDiscoveredLink,knownStatus:String="",forceDetail:Boolean=false): String? {
        if(isKnownExamGateway(link.url)) return null
        val initialKey=workKey(link.url)
        if(!forceDetail&&initialKey!=null&&items[initialKey]?.status?.let {it!="状态未知"}==true &&
            items[initialKey]?.deadline!=null) {
            bindNotice(link,initialKey)
            return initialKey
        }
        val visited=mutableListOf(link.url)
        var page=fetch(link.url)
        if(!supported(page.url)) throw ChaoxingReadFailure(unsupportedWorkDestination(page.url))
        visited.add(page.url)
        resolvedWorkKey(visited)
        var doc=Jsoup.parse(page.text,page.url)
        var state=workStatus(doc)
        // The official gateway can render an entry anchor instead of issuing
        // an HTTP redirect. Follow only one unambiguous, read-only work entry.
        if(state.isBlank()&&doc.selectFirst("#submitForm")==null) {
            val targets=doc.select("a[href*=/work/dowork],a[href*=intoexamorwork]")
                .mapNotNull {safeUrl(it.attr("href"),page.url)}
                .filter {supported(it)&&it !in visited}.distinct()
            if(targets.size>1) throw ChaoxingReadFailure("ambiguous_work_entry")
            targets.singleOrNull()?.let { target ->
                visited.add(target)
                resolvedWorkKey(visited)
                page=fetch(target)
                if(!supported(page.url)) throw ChaoxingReadFailure(unsupportedWorkDestination(page.url))
                visited.add(page.url)
                resolvedWorkKey(visited)
                doc=Jsoup.parse(page.text,page.url)
                state=workStatus(doc)
            }
        }
        val key=resolvedWorkKey(visited)
        val status=state.ifBlank {knownStatus}.ifBlank {"状态未知"}
        val deadline=ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(page.text)
        if(key!=null) {
            record("homework",key,link.title,status,URI(page.url).host,deadline)
            bindNotice(link,key)
            if(initialKey==null) {
                // Remove only the exact former gateway fingerprint, never a
                // same-title task or an unrelated historic notification.
                runCatching {stableStudyKey(link.url)}.getOrNull()?.let {
                    items.remove(it);superseded.add(it)
                }
            }
        } else record("study_task",stableStudyKey(link.url),link.title,status,URI(page.url).host,deadline)
        return key
    }
    private fun readCourse(course:ChaoxingDiscoveredLink):Boolean {
        var identitiesComplete=true
        var page=fetch(course.url);var doc=Jsoup.parse(page.text,page.url)
        var paginationUrl=course.url
        val entry=doc.selectFirst("a[data-url*=work/list],iframe[src*=work/list],a[href*=work/list]")
        val hidden=listOf("courseid","clazzid","cpi","workEnc","openc","enc","t")
            .associateWith {doc.getElementById(it)?.`val`().orEmpty()}
        // A validated first-party reader (xuexitong-cli, MIT) obtains the
        // work-list signature from the course page, not the course-entry URL.
        // The menu item can be absent while a signed work-list is available.
        val signedWorkList=listOf("courseid","clazzid","cpi").all {
            numeric.matches(hidden[it].orEmpty())
        } && !hidden["workEnc"].isNullOrBlank()
        if(entry!=null || signedWorkList) {
            val raw=entry?.let {it.attr("data-url").ifBlank {it.attr("src")}.ifBlank {it.attr("href")}}
            var url=raw?.let {safeUrl(it,page.url) ?: throw ChaoxingReadFailure("course_entry")}
                ?: "https://mooc1.chaoxing.com/mooc2/work/list"
            if(URI(url).path !in setOf("/mooc2/work/list","/mooc-ans/mooc2/work/list"))
                throw ChaoxingReadFailure("course_entry")
            if(signedWorkList) {
                val old=params(url)
                val ids=mapOf("courseId" to hidden.getValue("courseid"),
                    "classId" to hidden.getValue("clazzid"),"cpi" to hidden.getValue("cpi"))
                if(ids.any {(k,v)->old[k]!=null&&old[k]!=v})
                    throw ChaoxingReadFailure("course_identity")
                // Preserve the first-party entry route and query parameters.
                // A page-provided t belongs with its workEnc; only synthesize
                // a timestamp when the page did not supply one.
                val fields=ids+mapOf("ut" to "s","enc" to hidden.getValue("workEnc"),
                    "t" to hidden.getValue("t").ifBlank {System.currentTimeMillis().toString()},"isdisplaytable" to "2")+
                    mapOf("openc" to hidden.getValue("openc"),
                        "stuenc" to hidden.getValue("enc")).filterValues {it.isNotBlank()}
                url=query(url,fields)
            }
            paginationUrl=query(url,mapOf("pageNum" to "1"))
            page=fetch(paginationUrl);doc=Jsoup.parse(page.text,page.url)
        }
        var pages=1;var current=1;val seen=mutableSetOf<String>()
        val expected=params(course.url)
        val expectedCourse=numericIdentity(expected,"courseId","courseid")
        val expectedClass=numericIdentity(expected,"classId","clazzid","classid")
        while(true) {
            val active=doc.select("#page li.xl-active")
            if(active.size>1 || (active.size==1 && active.first()!!.text().trim().toIntOrNull()!=current))
                throw ChaoxingReadFailure("work_page_mismatch")
            // Prefer the actual homework panel, as the reference client does;
            // alternate hidden layouts are not an additional page of work.
            val selector=if(doc.selectFirst(".bottomList")!=null) ".bottomList li[data]" else "li[data]"
            val rows=doc.select(selector).filter {it.selectFirst("p.overHidden2")!=null}
            val priorKeys=seen.toSet()
            val pageRows=linkedMapOf<String,Pair<String,String>>()
            var overlap=false
            var excludedExams=0
            if(rows.isEmpty()&&!doc.text().contains("暂无作业")&&!doc.text().contains("没有作业")) throw ChaoxingReadFailure("work_list_response")
            for(row in rows) {
                val url=safeUrl(row.attr("data"),page.url) ?: throw ChaoxingReadFailure("work_identity")
                if(isExamUrl(url) || isKnownExamGateway(url)) {excludedExams++;continue}
                if(!supported(url)) throw ChaoxingReadFailure("work_identity")
                val key=workKey(url)
                if(key!=null) {
                    val triple=key.split(':')
                    if(expectedCourse?.let {it!=triple[1]}==true || expectedClass?.let {it!=triple[2]}==true)
                        throw ChaoxingReadFailure("course_identity")
                }
                if(key==null) identitiesComplete=false
                val stable=key?:stableStudyKey(url)
                val signature=clean(row.selectFirst("p.overHidden2")!!.text()) to
                    clean(row.selectFirst("p.status")?.text().orEmpty(),60)
                val duplicate=pageRows.putIfAbsent(stable,signature)
                if(duplicate!=null) {
                    val listedClass=statusClass(duplicate.second)
                    val priorClass=if(listedClass!=0) listedClass else statusClass(items[stable]?.status.orEmpty())
                    val nextClass=statusClass(signature.second)
                    if(listedClass==0 && nextClass!=0)
                        pageRows[stable]=duplicate.first to signature.second
                    if(priorClass!=0 && nextClass!=0 && priorClass!=nextClass) {
                        // Different labels (or titles) are not conflicting
                        // evidence. Opposite submission states need the actual
                        // official detail; never erase a known status by fiat.
                        record(if(key==null) "study_task" else "homework",stable,duplicate.first,"状态未知",URI(url).host)
                        recover(course.title) {
                            readWorkLink(ChaoxingDiscoveredLink(duplicate.first,url),forceDetail=true)
                        }
                        if(statusClass(items[stable]?.status.orEmpty())==0)
                            reportFailure(course.title,"work_row_conflict")
                    } else if(nextClass!=0 && priorClass==0) {
                        record(if(key==null) "study_task" else "homework",stable,duplicate.first,
                            reconcileWorkStatus(signature.second,items[stable]?.status),URI(url).host)
                    }
                    continue
                }
                if(!seen.add(stable)) {overlap=true;continue}
                if (key != null) {
                    courseWorkKeys.add(key)
                    courseWorkUrls[key]=url
                }
                val listedStatus=row.selectFirst("p.status")?.text().orEmpty()
                // Preserve any earlier official detail evidence; only after
                // the complete course scan may a unique peer notice trigger a
                // further detail lookup.
                val detailedStatus=items[stable]?.status
                // The course list may say merely "submitted" while the work
                // detail already proves graded/peer-reviewed/completed.
                // A reopened or returned work in the current list still wins.
                val status=reconcileWorkStatus(listedStatus,detailedStatus)
                record(if(key==null) "study_task" else "homework",stable,
                    row.selectFirst("p.overHidden2")!!.text(),status,URI(url).host,
                    ChaoxingDeadlineEvidenceReader.fromHomeworkHtml(row.outerHtml()))
                if(statusClass(status) == 0) recover("作业详情") {
                    readWorkLink(ChaoxingDiscoveredLink(row.selectFirst("p.overHidden2")!!.text(),url),forceDetail=true)
                }
            }
            if(current>1 && pageRows.isEmpty() && excludedExams==0) throw ChaoxingReadFailure("work_page_empty")
            if(overlap) {
                if(pageRows.keys.all {it in priorKeys}) throw ChaoxingReadFailure("work_page_stalled")
                // Retain newly discovered rows after an overlap, but do not
                // claim a complete snapshot or delete unmatched older records.
                reportFailure(course.title,"work_page_overlap")
            }
            val buttons=doc.select("#page li:not(.xl-prevPage):not(.xl-nextPage)").mapNotNull {it.text().toIntOrNull()}
            pages=maxOf(pages,buttons.maxOrNull()?:1)
            if(pages>100) throw ChaoxingReadFailure("work_page_limit")
            if(current>=pages) break
            current++
            page=fetch(query(paginationUrl,mapOf("pageNum" to current.toString())))
            doc=Jsoup.parse(page.text,page.url)
        }
        return identitiesComplete
    }
}
