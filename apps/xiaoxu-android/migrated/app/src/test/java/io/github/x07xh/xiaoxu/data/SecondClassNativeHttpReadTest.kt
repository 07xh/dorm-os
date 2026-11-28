package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Offline JVM contract tests: every response is supplied by a synthetic callback.
 * Legacy device-only session and persistence integration tests remain in the
 * existing isolated runtime suite; this suite never instantiates HTTP transport.
 */
class SecondClassNativeHttpReadTest {
    private val salt = "b".repeat(32)
    private val student = "synthetic-student"
    private val hash = MessageDigest.getInstance("SHA-256")
        .digest((salt + ":" + student).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun envelope(data: JSONArray = JSONArray(), total: Int = data.length(),
        pages: Int = if (data.length() == 0) 0 else 1) = JSONObject()
        .put("data", data).put("totalSize", total).put("totalPage", pages).put("startIndex", 0)

    private fun score(code: String? = student, index: Int = 1): JSONObject {
        val row = JSONObject().put("school_term_index", index).put("score_value", 3).put("score_plus", 2)
        if (code != null) row.put("student_code", code)
        for (i in 1..6) row.put("activity_type" + i + "_score", 0)
        return row
    }

    private fun scores(code: String = student) =
        envelope(JSONArray().put(score(code)).put(score(null, 100)))

    @Test fun completeBatchUsesExistingSnapshotContract() = runBlocking {
        val calls = mutableListOf<SecondClassReadEndpoint>()
        val result = readSecondClassNativeHttp(salt, hash) { endpoint, form ->
            calls.add(endpoint)
            when (endpoint) {
                SecondClassReadEndpoint.SCORES -> scores()
                SecondClassReadEndpoint.PERSONAL -> envelope()
                SecondClassReadEndpoint.CATALOG -> if (form["title"] == "报名中")
                    envelope(JSONArray().put(JSONObject().put("activity_name", "合成活动"))) else envelope()
            }
        }
        assertEquals(2, calls.count { it == SecondClassReadEndpoint.SCORES })
        assertEquals("xiaoxu/local-secondclass-scores-v1", result.getString("schema"))
        assertTrue(result.getBoolean("identityMatched"))
        assertEquals(hash, result.getString("accountHash"))
        assertEquals(2, result.getJSONArray("rows").length())
        assertTrue(result.getBoolean("activitiesComplete"))
        assertEquals(0, result.getJSONArray("activities").length())
        assertEquals("verified_nonempty", result.getString("catalogStatus"))
        assertEquals(2, result.getJSONArray("catalogEmptyFilters").length())
        assertEquals(1, result.getJSONArray("catalogActivities").length())
        assertFalse(result.toString().contains(student))
    }

    @Test fun firstManualReadReturnsOnlySaltedBindingAndNoStudentCode() = runBlocking {
        val result = readSecondClassNativeHttp(salt, null) { endpoint, _ ->
            when (endpoint) {
                SecondClassReadEndpoint.SCORES -> scores()
                SecondClassReadEndpoint.PERSONAL -> envelope()
                SecondClassReadEndpoint.CATALOG -> envelope()
            }
        }
        assertEquals(hash, result.getString("accountHash"))
        assertTrue(result.getBoolean("identityMatched"))
        assertFalse(result.toString().contains(student))
    }

    @Test fun wrongOwnerStopsBeforeOtherBusinessReads() = runBlocking {
        var calls = 0
        val failure = runCatching {
            readSecondClassNativeHttp(salt, hash) { endpoint, _ ->
                calls++
                assertEquals(SecondClassReadEndpoint.SCORES, endpoint)
                scores("different-student")
            }
        }.exceptionOrNull()
        assertTrue(failure is AutoReadFailure)
        assertEquals("needs_confirmation", (failure as AutoReadFailure).status)
        assertEquals(1, calls)
    }

    @Test fun accountChangeDuringBatchRejectsResult() = runBlocking {
        var reads = 0
        val failure = runCatching {
            readSecondClassNativeHttp(salt, hash) { endpoint, _ ->
                when (endpoint) {
                    SecondClassReadEndpoint.SCORES -> if (++reads == 1) scores() else scores("different-student")
                    else -> envelope()
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is AutoReadFailure)
    }

    @Test fun partialCatalogCannotBecomeVerifiedEmpty() = runBlocking {
        val result = readSecondClassNativeHttp(salt, hash) { endpoint, _ ->
            when (endpoint) {
                SecondClassReadEndpoint.SCORES -> scores()
                SecondClassReadEndpoint.PERSONAL -> envelope()
                SecondClassReadEndpoint.CATALOG -> envelope(JSONArray().put(JSONObject().put("activity_name", "合成活动")), 60, 2)
            }
        }
        assertEquals("nonempty_unverified", result.getString("catalogStatus"))
        assertEquals(0, result.getJSONArray("catalogEmptyFilters").length())
        assertEquals(0, result.getJSONArray("catalogActivities").length())
    }

    @Test fun onlyKnownSchoolLoginRedirectMeansReconnect() {
        assertTrue(isSecondClassLoginRedirect(302, "/login"))
        assertTrue(isSecondClassLoginRedirect(302, "https://dekt.ncwu.edu.cn/login"))
        assertFalse(isSecondClassLoginRedirect(302, "https://example.invalid/login"))
        assertFalse(isSecondClassLoginRedirect(302, "/sys/stu/score"))
        assertFalse(isSecondClassLoginRedirect(302, "http://dekt.ncwu.edu.cn/login"))
        assertTrue(isSecondClassLoginRedirect(302,
            "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fsec.ncwu.edu.cn%2Frump_frontend%2FloginFromCas%2F"))
        assertFalse(isSecondClassLoginRedirect(302,
            "https://authserver.ncwu.edu.cn/authserver/login?service=https%3A%2F%2Fevil.example%2F"))
        assertFalse(isSecondClassLoginRedirect(200, "/login"))
    }

    @Test fun explicitExpiryDuringCatalogRejectsWholeBatch() = runBlocking {
        val failure = runCatching {
            readSecondClassNativeHttp(salt, hash) { endpoint, _ ->
                when (endpoint) {
                    SecondClassReadEndpoint.SCORES -> scores()
                    SecondClassReadEndpoint.PERSONAL -> envelope()
                    SecondClassReadEndpoint.CATALOG -> throw AutoReadFailure("needs_reconnect")
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is AutoReadFailure)
        assertEquals("needs_reconnect", (failure as AutoReadFailure).status)
    }

    @Test fun incompletePersonalPaginationRejectsEntireBatch() = runBlocking {
        val failure = runCatching {
            readSecondClassNativeHttp(salt, hash) { endpoint, _ ->
                when (endpoint) {
                    SecondClassReadEndpoint.SCORES -> scores()
                    SecondClassReadEndpoint.PERSONAL -> envelope(JSONArray(), 5, 1)
                    SecondClassReadEndpoint.CATALOG -> error("must not read catalog")
                }
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }
}
