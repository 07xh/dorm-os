package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Only existing, explicitly authorized same-app sessions may implement this boundary.
 * No credential capture, login automation, session export or persistent copies.
 * Production handoff remains disabled until independently verified. */
internal interface SecondClassAuthorizedHttpSession {
    fun authorize(connection: HttpsURLConnection)
    fun acceptResponse(connection: HttpsURLConnection)
}

internal enum class SecondClassReadEndpoint(val path: String) {
    SCORES("/sys/stu/score/list"),
    PERSONAL("/sys/stu/center/signupactivity/list"),
    CATALOG("/sys/stu/activity")
}

internal class SecondClassHttpFailure(val status: Int, val endpoint: SecondClassReadEndpoint) : Exception("school_read_failed")

/** Only a known login redirect is evidence of expiry. Never follow it or expose its URL. */
internal fun isSecondClassLoginRedirect(status: Int, location: String?): Boolean {
    if (status !in 300..399 || location.isNullOrBlank()) return false
    return runCatching {
        val u = URI("https://dekt.ncwu.edu.cn/").resolve(location)
        if (u.scheme != "https" || u.port !in setOf(-1, 443) ||
            u.rawUserInfo != null || u.rawFragment != null) return@runCatching false
        when (u.host?.lowercase()) {
            "dekt.ncwu.edu.cn" -> u.rawPath in setOf("/login", "/login/")
            "authserver.ncwu.edu.cn" -> {
                if (u.rawPath != "/authserver/login") false
                else {
                    val fields = u.rawQuery.orEmpty().split('&').map {
                        val pair = it.split('=', limit = 2)
                        URLDecoder.decode(pair[0], "UTF-8") to
                            URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
                    }
                    fields.size == 1 && fields.single().first == "service" &&
                        fields.single().second == "https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/"
                }
            }
            else -> false
        }
    }.getOrDefault(false)
}

/** Fixed read-only routes; redirect and TLS failures never trigger a login attempt. */
internal class SecondClassNativeHttpTransport(private val session: SecondClassAuthorizedHttpSession) {
    private val requests = Mutex()
    private var attempted = false
    suspend fun post(endpoint: SecondClassReadEndpoint, form: Map<String, String>): JSONObject =
        requests.withLock {
        withContext(Dispatchers.IO) {
            val validForm = when (endpoint) {
                SecondClassReadEndpoint.SCORES -> form.isEmpty()
                SecondClassReadEndpoint.PERSONAL -> form.keys == setOf("page", "pagesize") &&
                    (form["page"]?.toIntOrNull() ?: 0) in 1..25 && form["pagesize"] == "50"
                SecondClassReadEndpoint.CATALOG -> form.keys == setOf("title") &&
                    form["title"] in listOf("报名中", "待验证", "即将开始报名")
            }
            require(validForm)
            // 2026-11-21 observed school session: requests must be serial.
            // Wait after every completed/failed attempt, including catalog filters.
            if (attempted) delay(300)
            attempted = true
            val url = URL("https://dekt.ncwu.edu.cn" + endpoint.path)
            val connection = url.openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.useCaches = false
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json, text/plain, */*")
                connection.setRequestProperty("Origin", "https://dekt.ncwu.edu.cn")
                connection.setRequestProperty("Referer", "https://dekt.ncwu.edu.cn/sys/stu/score")
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
                session.authorize(connection)
                val body = form.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                if (isSecondClassLoginRedirect(status, connection.getHeaderField("Location")))
                    throw AutoReadFailure("needs_reconnect")
                if (status == 401 && endpoint != SecondClassReadEndpoint.PERSONAL)
                    throw AutoReadFailure("needs_reconnect")
                if (status != 200) throw SecondClassHttpFailure(status, endpoint)
                session.acceptResponse(connection)
                val limit = if (endpoint == SecondClassReadEndpoint.CATALOG) 100_000 else 300_000
                val bytes = java.io.ByteArrayOutputStream()
                connection.inputStream.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= limit)
                        bytes.write(buffer, 0, count)
                    }
                }
                JSONObject(bytes.toString("UTF-8"))
            } finally {
                connection.disconnect()
            }
        }
        }
}

private val studentPattern = Regex("^[A-Za-z0-9_-]{1,50}$")
private fun hashIdentity(salt: String, student: String): String =
    MessageDigest.getInstance("SHA-256").digest((salt + ":" + student).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

private fun JSONObject.strictInt(key: String): Int {
    val raw = get(key)
    require(raw is Number && raw.toDouble() == raw.toInt().toDouble())
    return raw.toInt()
}
private fun JSONObject.strictNumber(key: String): Number {
    val raw = get(key)
    require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() in 0.0..100000.0)
    return raw
}
private fun JSONObject.strictString(key: String): String {
    val raw = get(key)
    require(raw is String)
    return raw
}
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private data class ScoreProjection(val student: String, val rows: JSONArray)
private fun projectScores(raw: JSONObject, salt: String, expected: String?): ScoreProjection {
    val data = raw.getJSONArray("data").objects()
    require(data.size in 1..50 && raw.strictInt("totalSize") == data.size &&
        raw.strictInt("totalPage") in 0..1 && raw.strictInt("startIndex") == 0)
    val students = data.filter { it.strictInt("school_term_index") != 100 }
        .map { it.strictString("student_code") }.toSet()
    if (students.size != 1) throw AutoReadFailure("needs_confirmation")
    val student = students.single()
    if (!student.matches(studentPattern) ||
        expected != null && hashIdentity(salt, student) != expected)
        throw AutoReadFailure("needs_confirmation")
    val seen = mutableSetOf<Int>()
    val rows = JSONArray()
    for (row in data) {
        val index = row.strictInt("school_term_index")
        require(index in 1..100 && seen.add(index))
        if (index != 100 || !row.isNull("student_code")) {
            if (row.opt("student_code") != student) throw AutoReadFailure("needs_confirmation")
        }
        val categories = JSONArray()
        for (i in 1..6) categories.put(row.strictNumber("activity_type" + i + "_score"))
        rows.put(JSONObject().put("termIndex", index).put("qualityScore", row.strictNumber("score_value"))
            .put("volunteerHours", row.strictNumber("score_plus")).put("categories", categories))
    }
    return ScoreProjection(student, rows)
}

/** Project into the existing native snapshot contract. Tests inject synthetic JSON;
 * manual callers may omit the previous binding; the returned salted hash must
 * then be confirmed by the owner before any local snapshot is replaced. */
internal suspend fun readSecondClassNativeHttp(
    salt: String, expected: String?,
    post: suspend (SecondClassReadEndpoint, Map<String, String>) -> JSONObject
): JSONObject {
    require(salt.matches(Regex("^[a-f0-9]{32}$")) &&
        (expected == null || expected.matches(Regex("^[a-f0-9]{64}$"))))
    val score = projectScores(post(SecondClassReadEndpoint.SCORES, emptyMap()), salt, expected)
    val activities = JSONArray()
    val ids = mutableSetOf<String>()
    var total = -1
    var pages = -1
    for (page in 1..25) {
        currentCoroutineContext().ensureActive()
        val raw = post(SecondClassReadEndpoint.PERSONAL, mapOf("page" to page.toString(), "pagesize" to "50"))
        val items = raw.getJSONArray("data").objects()
        val pageTotal = raw.strictInt("totalSize")
        val pageCount = raw.strictInt("totalPage")
        require(pageTotal in 0..250 && pageCount in 0..25 && items.size <= 50 &&
            raw.strictInt("startIndex") >= 0)
        if (page == 1) { total = pageTotal; pages = pageCount }
        require(total == pageTotal && pages == pageCount)
        if (page == 1 && total == 0 && pages == 0 && items.isEmpty()) break
        require(page <= pages && items.isNotEmpty())
        for (item in items) {
            if (item.opt("student_code") != score.student) throw AutoReadFailure("needs_confirmation")
            val id = item.strictString("id")
            val title = item.strictString("activity_name").trim()
            require(id.matches(Regex("^[A-Za-z0-9:_-]{1,120}$")) && ids.add(id) && title.length in 1..120)
            val start = if (item.isNull("activity_start") || item.opt("activity_start") == "") null
                else item.strictString("activity_start").also {
                    require(it.matches(Regex("^20\\d{2}-\\d{2}-\\d{2}(?: \\d{2}:\\d{2}:\\d{2})?$")))
                    val format = java.text.SimpleDateFormat(if (it.length == 10) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                    format.isLenient = false
                    require(format.parse(it) != null)
                }
            val verify = item.opt("verify_state") as? String
            val activity = item.opt("activity_state")
            val settlement = when {
                verify == "2" || verify == "1" && activity == "5" -> "settled"
                verify == "1" && activity == "4" -> "unsettled"
                verify == "1" && activity == "6" -> "awaiting_settlement"
                else -> "unknown"
            }
            val projected = JSONObject().put("id", id).put("title", title)
                .put("startTime", start ?: JSONObject.NULL)
                .put("signupStatus", if (item.opt("signup_state") == "1") "registered" else "unknown")
                .put("reviewStatus", "unknown").put("settlementStatus", settlement)
                .put("verificationCode", verify?.takeIf { it.matches(Regex("^[0-9]$")) } ?: JSONObject.NULL)
            if (!item.isNull("score_value")) {
                val points = item.strictNumber("score_value")
                if (settlement == "settled") projected.put("points", points)
            }
            activities.put(projected)
        }
        require(activities.length() <= total)
        if (page == pages) break
    }
    require(activities.length() == total)
    // Unknown or partial catalog never replaces a previous verified snapshot.
    var catalogStatus = "verified_empty"
    var empty = JSONArray()
    var catalog = JSONArray()
    try {
        for (filter in listOf("报名中", "待验证", "即将开始报名")) {
            val raw = post(SecondClassReadEndpoint.CATALOG, mapOf("title" to filter))
            val data = raw.getJSONArray("data").objects()
            val count = raw.strictInt("totalSize")
            val pageCount = raw.strictInt("totalPage")
            require(count >= 0 && pageCount >= 0 && raw.strictInt("startIndex") == 0)
            if (count == 0 && pageCount == 0 && data.isEmpty()) { empty.put(filter); continue }
            if (count != data.size || count !in 1..50 || pageCount != 1) {
                catalogStatus = "nonempty_unverified"; break
            }
            data.forEachIndexed { ordinal, item ->
                val title = item.strictString("activity_name").replace(Regex("\\s+"), " ").trim()
                require(title.length in 2..120 && title.none { it == '<' || it == '>' || it.code < 32 })
                catalog.put(JSONObject().put("filter", filter).put("title", title).put("ordinal", ordinal))
            }
            require(catalog.length() <= 120)
        }
        if (catalogStatus == "verified_empty" && catalog.length() > 0) catalogStatus = "verified_nonempty"
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (failure: AutoReadFailure) { throw failure }
    catch (_: Exception) { catalogStatus = "unavailable" }
    if (catalogStatus !in setOf("verified_empty", "verified_nonempty")) {
        empty = JSONArray(); catalog = JSONArray()
    }
    // Re-read identity after all requests so a session change cannot publish a mixed batch.
    val finalScore = projectScores(post(SecondClassReadEndpoint.SCORES, emptyMap()), salt, expected)
    if (score.student != finalScore.student) throw AutoReadFailure("needs_confirmation")
    return JSONObject().put("schema", "xiaoxu/local-secondclass-scores-v1")
        .put("ok", true).put("identityMatched", true).put("httpStatus", 200)
        .put("accountHash", hashIdentity(salt, finalScore.student)).put("rows", finalScore.rows)
        .put("activitiesComplete", true).put("activities", activities)
        .put("catalogStatus", catalogStatus).put("catalogEmptyFilters", empty)
        .put("catalogActivities", catalog)
}
