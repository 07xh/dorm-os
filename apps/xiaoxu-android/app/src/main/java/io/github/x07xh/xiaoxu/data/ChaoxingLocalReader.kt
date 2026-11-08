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
internal data class ChaoxingReadItem(val kind: String, val sourceKey: String, val title: String, val status: String, val sourceHost: String)
internal data class ChaoxingLocalRead(val items: List<ChaoxingReadItem>, val accountHash: String,
    val courseCount: Int, val noticePages: Int, val failures: List<String>,
    val supersededKeys: Set<String> = emptySet()) {
    val complete: Boolean get() = failures.isEmpty()
}
internal data class ChaoxingDiscoveredLink(val title: String, val url: String)

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
            return result.values.toList()
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
        private fun workKey(url: String): String? {
            val p=params(url)
            fun value(vararg keys:String):String? {
                val found=keys.mapNotNull {p[it]}
                return found.singleOrNull()?.takeIf {numeric.matches(it)}
            }
            val course=value("courseId","courseid") ?: return null
            val clazz=value("classId","clazzid","classid") ?: return null
            val work=value("workId","workid","taskrefId") ?: return null
            return "work:$course:$clazz:$work"
        }
        private fun stableStudyKey(url: String): String {
            val p=params(url)
            val identity=listOf("encryTaskId","taskId","workId","workid","taskrefId","planId")
                .mapNotNull {k->p[k]?.let {"$k=$it"}}.joinToString("&")
            if(identity.isBlank()) throw ChaoxingReadFailure("task_identity")
            return "study:"+hash(URI(url).host+URI(url).path+"?"+identity)
        }
    }
    private val items=linkedMapOf<String,ChaoxingReadItem>()
    private val failures=mutableListOf<String>()
    private val superseded=mutableSetOf<String>()
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
    private fun record(kind:String,key:String,title:String,status:String,host:String) {
        if(items.size>=5000 && !items.containsKey(key)) throw ChaoxingReadFailure("record_limit")
        if(clean(title).isBlank()) throw ChaoxingReadFailure("record_title")
        items[key]=ChaoxingReadItem(kind,key,clean(title),clean(status,60).ifBlank {"状态未知"},host)
    }
    private fun recover(label:String, block:()->Unit) {
        try {block()} catch(e:ChaoxingReadFailure) {
            if(e.code in setOf("account_changed","login_required")) throw e
            failures.add(clean(label,60)+"："+e.code)
        } catch(_:org.json.JSONException) {
            failures.add(clean(label,60)+"：invalid_response")
        }
    }
    fun scan(year:Int):ChaoxingLocalRead {
        items.clear();failures.clear();superseded.clear()
        progress("核对本机学习通登录会话…")
        val owner=account(fetch(HOME))
        val links=linkedMapOf<String,ChaoxingDiscoveredLink>()
        var noticePages=0
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
                for(i in 0 until list.length()) for(link in noticeLinks(list.getJSONObject(i))) {
                    if(links.size>=1000) throw ChaoxingReadFailure("notice_limit")
                    links[link.url]=link
                }
                if(list.length()==0 || notices?.optInt("lastPage",1)==1) break
                val cursor=notices?.optString("lastGetId").orEmpty()
                if(cursor.isBlank()||!cursors.add(cursor)||pageNumber==100) throw ChaoxingReadFailure("notice_pagination")
                fields["lastValue"]=cursor
            }
        }
        for(link in links.values.filter {URI(it.url).path.contains("/userStudyPlan/")}) recover(link.title) {readStudyPlan(link)}
        // Direct assignment links in notices supplement plan children; they are
        // not assumed submitted merely because a notification exists.
        for(link in links.values.filterNot {URI(it.url).path.contains("/userStudyPlan/")}) recover(link.title) {readWorkLink(link)}
        var courseCount=0
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
                recover(course.title) {readCourse(course)}
            }
        }
        progress("确认读取前后为同一账号…")
        if(account(fetch(HOME))!=owner) throw ChaoxingReadFailure("account_changed")
        return ChaoxingLocalRead(items.values.toList(),owner,courseCount,noticePages,failures.toList(),superseded.toSet())
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
        if(groups.length()>200) throw ChaoxingReadFailure("group_limit")
        for(i in 0 until groups.length()) {
            val group=groups.getJSONObject(i);val encrypted=group.optString("encryptGroupId")
            if(!group.optBoolean("groupAllowStudy")) {
                record("study_task","study:"+hash(link.url.substringBefore('?')+user+encrypted),
                    link.title+" · "+group.optString("name","未解锁分组"),"未解锁",URI(page.url).host)
                continue
            }
            if(encrypted.isBlank()) throw ChaoxingReadFailure("group_identity")
            val plans=data("/userStudyPlan/getPlanDataByGroupId",base+mapOf("encryGroupId" to encrypted))
            if(plans.length()>2000) throw ChaoxingReadFailure("plan_limit")
            for(j in 0 until plans.length()) {
                val plan=plans.getJSONObject(j)
                val id=plan.optString("planId")
                val key=if(numeric.matches(id)) "plan:$id" else "study:"+hash(user+encrypted+plan.optString("encryptPlanId"))
                val accessible=plan.optBoolean("planAllowStudy")||plan.optBoolean("chooseStudy")
                val finished=plan.opt("isFinish").let {it==true||it.toString() in setOf("1","true")}
                val status=sequenceOf("statusText","stateText","statusName").map {plan.optString(it)}.firstOrNull {it.isNotBlank()}
                    ?: if(!accessible) "未解锁" else if(finished) "已完成" else "未完成"
                val title=plan.optString("name",link.title)
                record("study_task",key,title,status,URI(page.url).host)
                // getToStudyUrl resolves the official assignment destination;
                // it does not start reading progress or submit an assignment.
                if(plan.optInt("planType",-1)==4&&accessible&&plan.optString("encryptPlanId").isNotBlank()) {
                    val args=base+mapOf("encryptPlanId" to plan.optString("encryptPlanId"),"studyJumpType" to "0","isInterface" to "false")
                    val reply=JSONObject(fetch(query(origin+"/userStudyPlan/getToStudyUrl",args),"POST").text)
                    if(!reply.optBoolean("result")) throw ChaoxingReadFailure("work_destination")
                    val url=safeUrl(reply.optJSONObject("data")?.optString("url").orEmpty(),page.url)
                        ?: throw ChaoxingReadFailure("work_destination")
                    if(!supported(url)) throw ChaoxingReadFailure("work_destination")
                    readWorkLink(ChaoxingDiscoveredLink(title,url),status)
                    // An actual work identity replaces the plan child in the
                    // task center, preventing two copies of the same assignment.
                    if(workKey(url)!=null) {items.remove(key);superseded.add(key)}
                }
            }
        }
    }
    private fun readWorkLink(link:ChaoxingDiscoveredLink,knownStatus:String="") {
        val key=workKey(link.url)
        if(key!=null&&items[key]?.status?.let {it!="状态未知"}==true) return
        val page=fetch(link.url);val doc=Jsoup.parse(page.text,page.url)
        val state=doc.selectFirst("p.status,.workStatus,.work-status")?.text().orEmpty().ifBlank {
            Regex("已完成此作业|已提交[，,、 ]*待批阅|待批阅|提交成功|已达标|已退回|未提交").find(doc.text())?.value.orEmpty()
        }.ifBlank {knownStatus}.ifBlank {"状态未知"}
        if(key!=null) record("homework",key,link.title,state,URI(link.url).host)
        else record("study_task",stableStudyKey(link.url),link.title,state,URI(link.url).host)
    }
    private fun readCourse(course:ChaoxingDiscoveredLink) {
        var page=fetch(course.url);var doc=Jsoup.parse(page.text,page.url)
        val entry=doc.selectFirst("a[data-url*=work/list],iframe[src*=work/list],a[href*=work/list]")
        if(entry!=null) {
            val raw=entry.attr("data-url").ifBlank {entry.attr("src")}.ifBlank {entry.attr("href")}
            var url=safeUrl(raw,page.url) ?: throw ChaoxingReadFailure("course_entry")
            if(URI(url).path !in setOf("/mooc2/work/list","/mooc-ans/mooc2/work/list")) throw ChaoxingReadFailure("course_entry")
            val hidden=listOf("courseid","clazzid","cpi","workEnc","enc","t").associateWith {doc.getElementById(it)?.`val`().orEmpty()}
            if(listOf("courseid","clazzid","cpi").all {numeric.matches(hidden[it].orEmpty())}&&!hidden["workEnc"].isNullOrBlank()) {
                val old=params(url)
                val ids=mapOf("courseId" to hidden.getValue("courseid"),"classId" to hidden.getValue("clazzid"),"cpi" to hidden.getValue("cpi"))
                if(ids.any {(k,v)->old[k]!=null&&old[k]!=v}) throw ChaoxingReadFailure("course_identity")
                val fields=ids+mapOf("ut" to "s","enc" to hidden.getValue("workEnc"))+
                    mapOf("stuenc" to hidden.getValue("enc"),"t" to hidden.getValue("t")).filterValues {it.isNotBlank()}
                url=query(url,fields)
            }
            page=fetch(url);doc=Jsoup.parse(page.text,page.url)
        }
        var pages=1;var current=1;val seen=mutableSetOf<String>()
        val expected=params(course.url)
        while(true) {
            val rows=doc.select(".bottomList li[data],li[data]").filter {it.selectFirst("p.overHidden2")!=null}
            if(rows.isEmpty()&&!doc.text().contains("暂无作业")&&!doc.text().contains("没有作业")) throw ChaoxingReadFailure("work_list_response")
            for(row in rows) {
                val url=safeUrl(row.attr("data"),page.url) ?: throw ChaoxingReadFailure("work_identity")
                if(!supported(url)) throw ChaoxingReadFailure("work_identity")
                val key=workKey(url)
                if(key!=null) {
                    val triple=key.split(':')
                    if(expected["courseid"]?.let {it!=triple[1]}==true || expected["clazzid"]?.let {it!=triple[2]}==true)
                        throw ChaoxingReadFailure("course_identity")
                }
                val stable=key?:stableStudyKey(url)
                if(!seen.add(stable)) throw ChaoxingReadFailure("work_pagination")
                record(if(key==null) "study_task" else "homework",stable,
                    row.selectFirst("p.overHidden2")!!.text(),row.selectFirst("p.status")?.text().orEmpty(),URI(url).host)
            }
            val buttons=doc.select("#page li").mapNotNull {it.text().toIntOrNull()}
            pages=maxOf(pages,buttons.maxOrNull()?:1)
            if(pages>100) throw ChaoxingReadFailure("work_pagination")
            if(current>=pages) break
            current++
            page=fetch(query(page.url,mapOf("pageNum" to current.toString())))
            doc=Jsoup.parse(page.text,page.url)
        }
    }
}
