package io.github.x07xh.xiaoxu.data

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ChaoxingLocalReaderTest {

    @Test fun oldPeerAliasCollisionCannotBorrowOnlyRemainingOfficialWork() {
        val course = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val current = "https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"
        fun peer(key: String) = JSONObject().put("title", "作业互评通知")
            .put("tag","courseId123").put("content","作业名称：实验报告一")
            .put("extendParam",JSONObject().put("cparams",
                """{"funConfig":{"content":{"key":"$key"}}}"""))
        val first = peer("901234")
        val removed = peer("901235")
        assertEquals(ChaoxingLocalReader.noticeAliases(first).single().id,
            ChaoxingLocalReader.noticeAliases(removed).single().id)
        var detailReads=0
        val read=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val body=when(java.net.URI(request.url).path) {
                "/base" -> "<script>var uid='42';</script>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true)
                    .put("notices",JSONObject().put("lastPage",1)
                        .put("list",org.json.JSONArray().put(first).put(removed))).toString()
                "/mooc2-ans/visit/courselistdata" ->
                    "<a href='$course'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc-ans/mooc2/work/list" ->
                    "<ul class='bottomList'><li data='$current'>" +
                    "<p class='overHidden2'>实验报告一</p><p class='status'>待批阅</p></li></ul>"
                "/work/dowork" -> { detailReads++; throw ChaoxingReadFailure("unexpected_peer_detail") }
                else -> error("unexpected request")
            }
            ChaoxingReadPage(request.url,body)
        }).scan(2026)
        assertTrue(read.failures.toString(),read.complete)
        assertEquals(0,detailReads)
        val work=read.items.single {it.kind=="homework"}
        assertEquals("work:123:456:901234",work.sourceKey)
        assertEquals("待批阅",work.status)
        assertTrue(work.peerAliases.isEmpty())
    }

    @Test fun ambiguousDetachedPeerNoticeNeverFetchesAnyCandidateDetails() {
        val one = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val two = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=457&cpi=789"
        val peer = JSONObject().put("title","作业互评通知")
            .put("tag","courseId123").put("content","作业名称：实验报告一")
            .put("extendParam",JSONObject().put("cparams",
                """{"funConfig":{"content":{"key":"901234"}}}"""))
        var workDetailRequests=0
        val read=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val clazz = if (request.url.contains("457")) "457" else "456"
            val body = when (java.net.URI(request.url).path) {
                "/base" -> "<script>var uid='42';</script>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true)
                    .put("notices",JSONObject().put("lastPage",1)
                        .put("list",org.json.JSONArray().put(peer))).toString()
                "/mooc2-ans/visit/courselistdata" ->
                    "<a href='$one'><span class='course-name'>一班</span></a>" +
                    "<a href='$two'><span class='course-name'>二班</span></a>"
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=$clazz'>作业</a>"
                "/mooc-ans/mooc2/work/list" -> {
                    val work="https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=$clazz&workId=901234"
                    "<ul class='bottomList'><li data='$work'><p class='overHidden2'>实验报告一</p>" +
                        "<p class='status'>待批阅</p></li></ul>"
                }
                "/work/dowork" -> {
                    workDetailRequests++
                    throw ChaoxingReadFailure("should_not_fetch_ambiguous_detail")
                }
                else -> error("unexpected request")
            }
            ChaoxingReadPage(request.url,body)
        }).scan(2026)
        assertTrue(read.failures.toString(),read.complete)
        assertEquals(0,workDetailRequests)
        val homework=read.items.filter {it.kind=="homework"}
        assertEquals(2,homework.size)
        assertTrue(homework.all {it.status=="待批阅" && it.peerAliases.isEmpty()})
    }

    @Test fun optionalPeerDetailFailureKeepsLaterOfficialHomeworkButScanIsPartial() {
        val workA="https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"
        val workB="https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901235"
        val courseUrl="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val peer=JSONObject().put("title","作业互评通知").put("tag","courseId123")
            .put("content","作业名称：实验报告一")
            .put("extendParam",JSONObject().put("cparams",
                """{"funConfig":{"content":{"key":"901234"}}}"""))
        val scan=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val content=when(java.net.URI(request.url).path) {
                "/base" -> "<script>var uid='42';</script>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true)
                    .put("notices",JSONObject().put("lastPage",1)
                        .put("list",org.json.JSONArray().put(peer))).toString()
                "/mooc2-ans/visit/courselistdata" ->
                    "<a href='$courseUrl'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc-ans/mooc2/work/list" ->
                    "<ul class='bottomList'>" +
                        "<li data='$workA'><p class='overHidden2'>实验报告一</p><p class='status'>待批阅</p></li>" +
                        "<li data='$workB'><p class='overHidden2'>实验报告二</p><p class='status'>已完成</p></li></ul>"
                "/work/dowork" -> throw ChaoxingReadFailure("work_detail_unavailable")
                else -> error("unexpected official path")
            }
            ChaoxingReadPage(request.url,content)
        }).scan(2026)
        assertFalse(scan.complete)
        assertTrue(scan.failures.toString(),scan.failures.any {it.contains("work_detail_unavailable")})
        val works=scan.items.filter {it.kind=="homework"}
        assertEquals(2,works.size)
        assertEquals("待批阅", works.single {it.sourceKey=="work:123:456:901234"}.status)
        assertEquals("已完成", works.single {it.sourceKey=="work:123:456:901235"}.status)
        assertTrue(works.all {it.peerAliases.isEmpty()})
    }

    @Test fun fallbackWorkIdentityNormalizesEquivalentParameterNames() {
        val first="https://mooc1.chaoxing.com/work/dowork?courseId=123&workId=901234"
        val second="https://mooc1.chaoxing.com/work/dowork?courseid=123&taskrefId=901234"
        val courseUrl="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val read=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val content=when(java.net.URI(request.url).path) {
                "/base" -> "<script>var uid='42';</script>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true)
                    .put("notices",JSONObject().put("lastPage",1).put("list",
                        org.json.JSONArray().put(JSONObject().put("title","任务")
                            .put("attachment",org.json.JSONArray().put(JSONObject().put("url",first)))))).toString()
                "/work/dowork" -> "<p class='status'>待批阅</p>"
                "/mooc2-ans/visit/courselistdata" ->
                    "<a href='$courseUrl'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc-ans/mooc2/work/list" ->
                    "<ul class='bottomList'><li data='$second'>" +
                        "<p class='overHidden2'>实验报告一</p><p class='status'>待批阅</p></li></ul>"
                else -> error("unexpected official path")
            }
            ChaoxingReadPage(request.url,content)
        }).scan(2026)
        assertTrue(read.failures.toString(),read.complete)
        val rows=read.items.filter {it.kind=="study_task"}
        assertEquals(1,rows.size)
        assertTrue(rows.single().sourceKey.startsWith("study:"))
    }

    @Test fun incompleteWorkUrlCannotOverwriteAnotherCoursesSameNumberedTask() {
        val course1="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val course2="https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=124&clazzid=457&cpi=789"
        val read=ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val course = if (Regex("(?i)courseid=124").containsMatchIn(request.url)) "124" else "123"
            val body=when(java.net.URI(request.url).path) {
                "/base" -> "<script>var uid='42';</script>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true).put("notices",
                    JSONObject().put("lastPage",1).put("list",org.json.JSONArray())).toString()
                "/mooc2-ans/visit/courselistdata" ->
                    "<a href='$course1'><span class='course-name'>课程一</span></a>" +
                    "<a href='$course2'><span class='course-name'>课程二</span></a>"
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=$course'>作业</a>"
                "/mooc-ans/mooc2/work/list" -> {
                    val url="https://mooc1.chaoxing.com/work/dowork?courseId=$course&workId=901234"
                    "<ul class='bottomList'><li data='$url'><p class='overHidden2'>同名实验</p>" +
                        "<p class='status'>状态未知</p></li></ul>"
                }
                else -> error("unexpected path")
            }
            ChaoxingReadPage(request.url,body)
        }).scan(2026)
        assertTrue(read.failures.toString(),read.complete)
        val fallback=read.items.filter { it.kind == "study_task" }
        assertEquals(2,fallback.size)
        assertEquals(2,fallback.map {it.sourceKey}.toSet().size)
        // Missing class identity is not promoted into an official work tuple.
        assertTrue(fallback.all {it.sourceKey.startsWith("study:")})
    }

    @Test fun equivalentCaseAliasesProduceOneOfficialWorkIdAndLinkNotification() {
        val workUrl = "https://mooc1.chaoxing.com/work/dowork?" +
            "courseId=123&courseid=123&classId=456&clazzid=456&workId=901234&taskrefId=901234"
        val courseUrl = "https://mooc1.chaoxing.com/visit/stucoursemiddle?" +
            "courseId=123&courseid=123&classId=456&clazzid=456&cpi=789"
        val notice = JSONObject().put("title", "作业通知").put("tag", "courseId123")
            .put("content", "作业名称：实验报告一")
            .put("extendParam",JSONObject().put("cparams",
                """{"funConfig":{"content":{"key":"901234"}}}"""))
        val alias = ChaoxingLocalReader.noticeAliases(notice).single().id
        val read = ChaoxingLocalReader(ChaoxingReadTransport { request ->
            val content = when(java.net.URI(request.url).path) {
                "/base" -> "<script>var uid='42';</script>"
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true)
                    .put("notices",JSONObject().put("lastPage",1).put("list",
                        org.json.JSONArray().put(notice))).toString()
                "/mooc2-ans/visit/courselistdata" ->
                    "<a href='$courseUrl'><span class='course-name'>Python</span></a>"
                "/visit/stucoursemiddle" ->
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc-ans/mooc2/work/list" ->
                    "<ul class='bottomList'><li data='$workUrl'>" +
                        "<p class='overHidden2'>实验报告一</p><p class='status'>已完成</p></li></ul>"
                else -> error("unexpected path")
            }
            ChaoxingReadPage(request.url, content)
        }).scan(2026)
        assertTrue(read.failures.toString(),read.complete)
        assertEquals(1,read.items.count { it.kind == "homework" })
        val work=read.items.single { it.kind == "homework" }
        assertEquals("work:123:456:901234", work.sourceKey)
        assertEquals(setOf(alias),work.noticeAliases)
    }

    @Test fun conflictingAliasesQuarantineWorkAndUppercaseCourseScopeIsEnforced() {
        fun fixture(work: String, course: String): ChaoxingLocalRead {
            val courseUrl="https://mooc1.chaoxing.com/visit/stucoursemiddle?" +
                "courseId=$course&classId=456&cpi=789"
            val workUrl="https://mooc1.chaoxing.com/work/dowork?" + work
            return ChaoxingLocalReader(ChaoxingReadTransport { r ->
                val body=when(java.net.URI(r.url).path) {
                    "/base" -> "<script>var uid='42';</script>"
                    "/pc/notice/getNoticeList" -> JSONObject().put("status",true)
                        .put("notices",JSONObject().put("lastPage",1)
                            .put("list",org.json.JSONArray())).toString()
                    "/mooc2-ans/visit/courselistdata" ->
                        "<a href='$courseUrl'><span class='course-name'>Python</span></a>"
                    "/visit/stucoursemiddle" ->
                        "<a data-url='/mooc-ans/mooc2/work/list?courseId=$course&amp;classId=456'>作业</a>"
                    "/mooc-ans/mooc2/work/list" ->
                        "<ul class='bottomList'><li data='$workUrl'><p class='overHidden2'>实验报告</p>" +
                            "<p class='status'>待批阅</p></li></ul>"
                    else -> error("unexpected path")
                }
                ChaoxingReadPage(r.url,body)
            }).scan(2026)
        }
        val conflict=fixture("courseId=123&classId=456&workId=901234&taskrefId=901235", "123")
        assertFalse(conflict.complete)
        assertTrue(conflict.failures.toString(),
            conflict.failures.any { it.contains("ambiguous_identity") })
        assertFalse(conflict.items.any { it.kind == "homework" })

        val scope=fixture("courseId=124&classId=456&workId=901234", "123")
        assertFalse(scope.complete)
        assertTrue(scope.failures.toString(), scope.failures.any {it.contains("course_identity")})
        assertFalse(scope.items.any {it.kind == "homework"})
    }

    @Test fun detachedPeerNoticeMustHaveOfficialPeerCompletionEvidence() {
        val workUrl = "https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"
        val courseUrl = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
        val peerNotice = JSONObject().put("title", "作业互评通知")
            .put("tag", "courseId123").put("content", "作业名称：实验报告一")
            .put("extendParam", JSONObject().put("cparams",
                """{"funConfig":{"content":{"key":"901234"}}}"""))
        val peerId = ChaoxingLocalReader.noticeAliases(peerNotice).single().id
        assertTrue(ChaoxingLocalReader.noticeLinks(peerNotice).isEmpty())
        fun read(detail: String): Pair<ChaoxingLocalRead, Int> {
            var detailReads=0
            val result=ChaoxingLocalReader(ChaoxingReadTransport { request ->
                val body=when(java.net.URI(request.url).path) {
                    "/base" -> "<script>var uid='42';</script>"
                    "/pc/notice/getNoticeList" -> JSONObject().put("status", true).put("notices",
                        JSONObject().put("lastPage", 1).put("list",
                            org.json.JSONArray().put(peerNotice))).toString()
                    "/mooc2-ans/visit/courselistdata" ->
                        "<a href='$courseUrl'><span class='course-name'>Python</span></a>"
                    "/visit/stucoursemiddle" ->
                        "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                    "/mooc-ans/mooc2/work/list" ->
                        "<ul class='bottomList'><li data='$workUrl'>" +
                            "<p class='overHidden2'>实验报告一</p><p class='status'>待批阅</p></li></ul>"
                    "/work/dowork" -> { detailReads++; "<p class='status'>$detail</p>" }
                    else -> error("unexpected official path")
                }
                ChaoxingReadPage(request.url, body)
            }).scan(2026)
            return result to detailReads
        }
        val (complete, reads)=read("已互评")
        assertTrue(complete.complete)
        assertEquals(1, reads)
        val completedWork=complete.items.single { it.kind == "homework" }
        assertEquals(setOf(peerId), completedWork.peerAliases)
        assertEquals("已互评", completedWork.status)

        val (generic, genericReads)=read("待批阅")
        assertTrue(generic.complete)
        assertEquals(1, genericReads)
        val genericWork=generic.items.single { it.kind == "homework" }
        assertEquals(setOf(peerId), genericWork.peerAliases)
        assertEquals("待批阅", genericWork.status)
        // UI merge separately requires explicit peer completion; submitted
        // coursework alone cannot remove the independent review notice.
    }

    @Test fun detachedNoticeLinksOnlyOneVerifiedCourseWorkTuple() {
        val official = "https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"
        val notice = JSONObject().put("title", "作业通知").put("tag", "courseId123")
            .put("content", "作业名称：实验报告一")
            .put("extendParam", JSONObject().put("cparams",
                """{"funConfig":{"content":{"key":"901234"}}}"""))
        // There is deliberately no notification attachment: the cloud cxn
        // identity exists but cannot be connected by following a link.
        assertTrue(ChaoxingLocalReader.noticeLinks(notice).isEmpty())
        val expectedAlias = ChaoxingLocalReader.noticeAliases(notice).single().id
        fun read(twoClasses: Boolean, failSecond: Boolean = false): ChaoxingLocalRead {
            val first = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
            val second = first.replace("clazzid=456", "clazzid=457")
            return ChaoxingLocalReader(ChaoxingReadTransport { r ->
                val url = java.net.URI(r.url)
                val clazz = if (r.url.contains("457")) "457" else "456"
                val content = when (url.path) {
                    "/base" -> "<script>var uid='42';</script>"
                    "/pc/notice/getNoticeList" -> JSONObject().put("status", true)
                        .put("notices", JSONObject().put("lastPage", 1)
                            .put("list", org.json.JSONArray().put(notice))).toString()
                    "/mooc2-ans/visit/courselistdata" -> {
                        "<a href='$first'><span class='course-name'>Python 1</span></a>" +
                            if (twoClasses) "<a href='$second'><span class='course-name'>Python 2</span></a>" else ""
                    }
                    "/visit/stucoursemiddle" -> {
                        if (clazz == "457" && failSecond) throw ChaoxingReadFailure("course_http")
                        "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=$clazz'>作业</a>"
                    }
                    "/mooc-ans/mooc2/work/list" -> {
                        val workUrl = official.replace("classId=456", "classId=$clazz")
                        "<ul class='bottomList'><li data='$workUrl'>" +
                            "<p class='overHidden2'>实验报告一</p><p class='status'>已完成</p></li></ul>"
                    }
                    else -> error("unexpected request: " + url.path)
                }
                ChaoxingReadPage(r.url, content)
            }).scan(2026)
        }
        val unique = read(twoClasses = false)
        assertTrue(unique.complete)
        assertEquals(setOf(expectedAlias), unique.items.single { it.kind == "homework" }.noticeAliases)
        val ambiguous = read(twoClasses = true)
        assertTrue(ambiguous.complete)
        assertTrue(ambiguous.items.filter { it.kind == "homework" }.all { it.noticeAliases.isEmpty() })
        val partial = read(twoClasses = true, failSecond = true)
        assertFalse(partial.complete)
        assertTrue(partial.items.filter { it.kind == "homework" }.all { it.noticeAliases.isEmpty() })
    }
    @Test fun detailStatusSurvivesGenericListButReopenedWorkStaysPending() {
        val reader = ChaoxingLocalReader(ChaoxingReadTransport { error("no network needed") })
        assertEquals("已互评", reader.reconcileWorkStatus("待批阅", "已互评"))
        assertEquals("已批阅", reader.reconcileWorkStatus("已提交", "已批阅"))
        assertEquals("已完成", reader.reconcileWorkStatus("", "已完成"))
        assertEquals("未提交", reader.reconcileWorkStatus("未提交", "已互评"))
        assertEquals("退回重做", reader.reconcileWorkStatus("退回重做", "已批阅"))
        assertEquals("已退回", reader.reconcileWorkStatus("待批阅", "已退回"))
        assertEquals("状态未知", reader.reconcileWorkStatus("", null))
    }
    @Test fun noticeAliasesUseCloudIdentityAndOnlyBindResolvedSameCourseWork() {
        val work = "https://mooc1.chaoxing.com/work/dowork?courseId=123&classId=456&workId=901234"
        fun notice(peer: Boolean) = JSONObject().put("title", if(peer) "作业互评通知" else "作业通知")
            .put("tag","courseId123").put("content","作业名称：实验报告二")
            .put("extendParam",JSONObject().put("cparams", """{"funConfig":{"content":{"key":"901234"}}}"""))
            .put("attachment",org.json.JSONArray().put(JSONObject().put("url",work)))
        val assignment = ChaoxingLocalReader.noticeLinks(notice(false)).single().aliases.single()
        val peer = ChaoxingLocalReader.noticeLinks(notice(true)).single().aliases.single()
        assertFalse(assignment.peer)
        assertTrue(peer.peer)
        assertNotEquals(assignment.id,peer.id)
        assertEquals("cxn:d5c660bedb337eb6315542b90377d719ce794df6",assignment.id)
        assertEquals("cxn:b11bca381bf9b8af16aca73f1e35ebf1af22c866",peer.id)
        var ownerCalls=0
        val read = ChaoxingLocalReader(ChaoxingReadTransport { r ->
            val body=when(java.net.URI(r.url).path) {
                "/base" -> {ownerCalls++; "<script>var uid='42';</script>"}
                "/pc/notice/getNoticeList" -> JSONObject().put("status",true).put("notices",
                    JSONObject().put("lastPage",1).put("list",org.json.JSONArray().put(notice(false)).put(notice(true))
                        .put(notice(false).put("tag","courseId999")))).toString()
                "/work/dowork" -> "<p class='status'>已互评</p>"
                "/mooc2-ans/visit/courselistdata" -> "<div class='learnCourse'><a href='$courseUrl'><span class='course-name'>Python</span></a></div>"
                "/visit/stucoursemiddle" -> "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456'>作业</a>"
                "/mooc-ans/mooc2/work/list" -> "<ul class='bottomList'><li data='$work'><p class='overHidden2'>实验报告二</p><p class='status'>待批阅</p></li></ul>"
                else -> error("unexpected URL")
            }
            ChaoxingReadPage(r.url,body)
        }).scan(2026)
        assertTrue(read.complete)
        assertEquals(2,ownerCalls)
        val row=read.items.single {it.kind=="homework"}
        assertEquals(setOf(assignment.id),row.noticeAliases)
        assertEquals(setOf(peer.id),row.peerAliases)
        assertEquals("已互评",row.status)
    }
    private val profile = "<script>var uid='42';</script><span id='siteName'>华北水利水电大学</span>"
    private val courseUrl = "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=123&clazzid=456&cpi=789"
    private val workUrl = "https://mooc1.chaoxing.com/mooc-ans/mooc2/work/task?courseId=123&classId=456&workId=901&enc=privateWork"
    private fun fixture(changeOwner: Boolean = false, failCourse: Boolean = false): Pair<ChaoxingLocalRead, List<String>> {
        val calls=mutableListOf<String>()
        var profiles=0
        val transport=ChaoxingReadTransport { request ->
            val u=java.net.URI(request.url)
            calls.add(request.method+" "+u.path)
            val body=when(u.path) {
                "/base" -> if(++profiles>1&&changeOwner) profile.replace("uid='42'", "uid='43'") else profile
                "/pc/notice/getNoticeList" -> {
                    assertEquals("POST",request.method)
                    assertEquals("2",request.form["type"])
                    if(request.form["lastValue"].isNullOrEmpty())
                        """{"status":true,"notices":{"list":[{"title":"Python任务","attachment":"[{\"url\":\"https://task.chaoxing.com/userStudyPlan/studyPlanSubPage?encryTaskId=privateTask\"}]"}],"lastPage":0,"lastGetId":"nextPage"}}"""
                    else """{"status":true,"notices":{"list":[],"lastPage":1}}"""
                }
                "/userStudyPlan/studyPlanSubPage" -> "<script>const iframeUrl = \"https://task.chaoxing.com/userStudyPlan/detail\";</script>"
                "/userStudyPlan/detail" -> "<script>const eTaskUserId = \"privateUser\";</script>"
                "/userStudyPlan/getGroupData" -> """{"result":true,"data":[{"encryptGroupId":"privateGroup","groupAllowStudy":true},{"encryptGroupId":"lockedGroup","groupAllowStudy":false}]}"""
                "/userStudyPlan/getPlanDataByGroupId" -> """{"result":true,"data":[{"planId":700,"name":"Python已完成","planType":4,"isFinish":1,"planAllowStudy":true},{"planId":701,"name":"Python未交","planType":4,"isFinish":0,"planAllowStudy":true,"encryptPlanId":"privatePlan"}]}"""
                "/userStudyPlan/getToStudyUrl" -> """{"result":true,"data":{"url":"$workUrl"}}"""
                "/mooc2-ans/visit/courselistdata" -> {
                    assertEquals("POST",request.method)
                    assertEquals("0",request.form["courseFolderId"])
                    "<div class='learnCourse'><a href='$courseUrl'><span class='course-name'>Python</span></a></div>"
                }
                "/visit/stucoursemiddle" -> if(failCourse) throw ChaoxingReadFailure("course_http") else
                    "<a data-url='/mooc-ans/mooc2/work/list?courseId=123&amp;classId=456&amp;cpi=789&amp;enc=privateEntry&amp;t=1700000000123'>作业</a>"
                "/mooc-ans/mooc2/work/list" -> {
                    assertTrue(request.url.contains("t=1700000000123"))
                    "<ul class='bottomList'><li data='$workUrl'><p class='overHidden2'>Python未交</p><p class='status'>待批阅</p></li></ul>"
                }
                "/mooc-ans/mooc2/work/task" -> "<p class='status'>待批阅</p>"
                else -> error("unexpected first-party read: "+u.path)
            }
            ChaoxingReadPage(request.url,body)
        }
        return ChaoxingLocalReader(transport).scan(2026) to calls
    }
    @Test fun noticeAttachmentPlansAndActualCourseEntryShareOneReader() {
        val (read,calls)=fixture()
        assertTrue(calls.indexOf("POST /pc/notice/getNoticeList")<calls.indexOf("POST /userStudyPlan/getGroupData"))
        assertTrue(calls.indexOf("POST /userStudyPlan/getGroupData")<calls.indexOf("POST /mooc2-ans/visit/courselistdata"))
        assertEquals(2,calls.count {it=="POST /pc/notice/getNoticeList"})
        assertEquals(1,read.courseCount)
        assertTrue(read.items.any {it.sourceKey=="plan:700"&&it.status=="已完成"})
        assertTrue(read.items.any {it.sourceKey=="work:123:456:901"&&it.status=="待批阅"})
        assertTrue(read.items.any {it.status=="未解锁"})
        assertEquals(64,read.accountHash.length)
        for(secret in listOf("privateWork","privateTask","privateUser","privateGroup","privatePlan","privateEntry"))
            assertFalse(secret,read.toString().contains(secret))
    }
    @Test fun failedCourseDoesNotEraseSuccessfullyExpandedNoticePlans() {
        val (read,_)=fixture(failCourse=true)
        assertTrue(read.failures.isNotEmpty())
        assertTrue(read.items.any {it.sourceKey=="plan:700"})
        assertFalse(read.complete)
    }
    @Test fun accountChangeDuringReadRejectsEntireBatch() {
        try {fixture(changeOwner=true);fail("account change must fail before Room write")}
        catch(e:ChaoxingReadFailure){assertEquals("account_changed",e.code)}
    }
    @Test fun externalAttachmentsAndExamActionsAreNotRead() {
        val links=ChaoxingLocalReader.noticeLinks(JSONObject("""{"attachment":[{"url":"https://evil.example/work/task"},{"url":"https://mooc1.chaoxing.com/examapi/intoexamorwork?workorexam=exam"},{"url":"$workUrl"}]}"""))
        assertEquals(1,links.size)
        assertEquals(workUrl,links.single().url)
    }
}
