package io.github.x07xh.xiaoxu.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** All responses are synthetic; the isolated runtime APK cannot contact the school. */
@RunWith(AndroidJUnit4::class)
class SecondClassNativeHttpReadTest {
    private val salt = "b".repeat(32)
    private val student = "synthetic-student"
    private val hash = MessageDigest.getInstance("SHA-256")
        .digest((salt + ":" + student).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    @Before fun isolation() = assertRuntimeIsolation(InstrumentationRegistry.getInstrumentation().targetContext)

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
        assertEquals(2, parseLocalSecondClassScores(result, 1).size)
        assertEquals(1, parseLocalSecondClassActivities(result, 1).size)
        assertEquals(2, parseLocalSecondClassCatalogEmpty(result, 1).size)
        assertEquals(1, parseLocalSecondClassCatalogActivities(result, 1).size)
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
        assertTrue(parseLocalSecondClassCatalogEmpty(result, 1).isEmpty())
        assertTrue(parseLocalSecondClassCatalogActivities(result, 1).isEmpty())
    }

    @Test fun nativeSessionAdapterIsUnavailableInOfflineRuntimeAndNormalPackages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(SecondClassNativeQaSession.available(context))
        SecondClassNativeQaSession.configure(context, true)
        assertFalse(SecondClassNativeQaSession.enabled(context))
        assertNull(SecondClassNativeQaSession.acquire(context, hash))
    }

    @Test fun nativeSessionOnlyAcceptsUnambiguousScopedCookie() {
        val synthetic = "sid=00000000-0000-4000-8000-000000000000"
        assertEquals(synthetic, selectSecondClassSessionCookie("unrelated=fixture; " + synthetic))
        assertNull(selectSecondClassSessionCookie(synthetic + "; " + synthetic))
        assertNull(selectSecondClassSessionCookie("sid=invalid"))
        assertNull(selectSecondClassSessionCookie(null))
        assertTrue(isScopedSecondClassSessionUpdate(synthetic + "; Path=/; Secure; HttpOnly"))
        assertTrue(isScopedSecondClassSessionUpdate(synthetic + "; Domain=dekt.ncwu.edu.cn"))
        assertFalse(isScopedSecondClassSessionUpdate(synthetic + "; Domain=.ncwu.edu.cn"))
        assertFalse(isScopedSecondClassSessionUpdate("unrelated=fixture"))
        assertFalse(isScopedSecondClassSessionUpdate(synthetic + "; Domain=example.invalid"))
    }

    @Test fun onlyKnownSchoolLoginRedirectMeansReconnect() {
        assertTrue(isSecondClassLoginRedirect(302, "/login"))
        assertTrue(isSecondClassLoginRedirect(302, "https://dekt.ncwu.edu.cn/login"))
        assertFalse(isSecondClassLoginRedirect(302, "https://example.invalid/login"))
        assertFalse(isSecondClassLoginRedirect(302, "/sys/stu/score"))
        assertFalse(isSecondClassLoginRedirect(302, "http://dekt.ncwu.edu.cn/login"))
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
