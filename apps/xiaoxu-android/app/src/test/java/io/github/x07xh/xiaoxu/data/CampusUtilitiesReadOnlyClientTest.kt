package io.github.x07xh.xiaoxu.data

import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CampusUtilitiesReadOnlyClientTest {
    private val room = CampusUtilityRoom("05", "02", "01", "1", "3", "0301")

    private fun success(value: JSONObject): CampusUtilitiesRawResponse =
        CampusUtilitiesRawResponse(200, JSONObject().put("success", true)
            .put("resultData", value).toString())

    @Test fun directReadUsesOnlyDocumentedPostSequenceAndV2() = runBlocking {
        val calls = ArrayList<Pair<String, JSONObject>>()
        val transport = CampusUtilitiesTransport { path, payload ->
            calls.add(path to JSONObject(payload.toString()))
            when (path) {
                "/utilities/config" -> success(JSONObject().put("detailConfig",
                    JSONObject().put("enabled", true).put("version", "v2")))
                "/utilities/account" -> success(JSONObject().put("accountNum", "A1234"))
                "/utilities/usageDetailsV2" -> success(JSONObject().put("data",
                    JSONObject().put("totalCount", 2).put("list", org.json.JSONArray())))
                else -> fail("Undocumented endpoint: $path").let { CampusUtilitiesRawResponse(500, "") }
            }
        }
        val client = CampusUtilitiesReadOnlyClient(transport, pace = {})
        val result = client.readMonth(CampusUtilityKind.ELECTRIC,
            room, YearMonth.of(2026, 11))
        assertEquals(
            listOf("/utilities/config", "/utilities/account", "/utilities/usageDetailsV2"),
            calls.map { it.first }
        )
        assertEquals("electric", calls[0].second.getString("utilityType"))
        assertEquals("electric", calls[1].second.getString("utilityType"))
        assertEquals("0301", calls[1].second.getString("room"))
        assertEquals("2026-11-01", calls[2].second.getString("fromDate"))
        assertEquals("2026-11-30", calls[2].second.getString("toDate"))
        assertEquals(1000, calls[2].second.getInt("pageSize"))
        assertEquals(0, calls[2].second.getInt("pageNo"))
        assertEquals("A1234", result.accountNum)
        assertEquals(2, result.recordCount)
        assertNull(result.totalUsed)
    }

    @Test fun v1UsesDetailsAndSummaryWithoutInventingBalance() = runBlocking {
        val transport = CampusUtilitiesTransport { path, _ ->
            when (path) {
                "/utilities/config" -> success(JSONObject().put("detailConfig",
                    JSONObject().put("enabled", true).put("version", "v1")))
                "/utilities/account" -> success(JSONObject().put("accountNum", "W042"))
                "/utilities/usageDetails" -> success(JSONObject().put("totalUsed", "12.50")
                    .put("details", JSONObject().put("totalCount", 3)))
                else -> throw AssertionError(path)
            }
        }
        val result = CampusUtilitiesReadOnlyClient(transport, pace = {})
            .readMonth(CampusUtilityKind.WATER, room, YearMonth.of(2026, 10))
        assertEquals("12.50", result.totalUsed)
        assertEquals(3, result.recordCount)
        assertEquals("v1", result.detailVersion)
    }

    @Test fun anonymousOrExpiredSessionStopsWithoutRetry() = runBlocking {
        val paths = mutableListOf<String>()
        val transport = CampusUtilitiesTransport { path, _ ->
            paths.add(path)
            CampusUtilitiesRawResponse(401, "")
        }
        try {
            CampusUtilitiesReadOnlyClient(transport, pace = {})
                .readMonth(CampusUtilityKind.WATER, room, YearMonth.of(2026, 11))
            fail("Expected server 401 to reject the read")
        } catch (e: CampusUtilitiesException) {
            assertEquals(CampusUtilitiesFailure.LOGIN_REQUIRED, e.failure)
        }
        assertEquals(listOf("/utilities/config"), paths)
    }

    @Test fun networkFailureIsNotMisreportedAsLoginFailure() = runBlocking {
        var calls = 0
        val transport = CampusUtilitiesTransport { _, _ ->
            calls++
            CampusUtilitiesRawResponse(0, "")
        }
        try {
            CampusUtilitiesReadOnlyClient(transport, pace = {})
                .readMonth(CampusUtilityKind.ELECTRIC, room, YearMonth.of(2026, 11))
            fail("Expected a failed first read")
        } catch (e: CampusUtilitiesException) {
            assertEquals(CampusUtilitiesFailure.NETWORK_FAILURE, e.failure)
        }
        assertEquals(1, calls)
    }

    @Test fun malformedRoomDoesNotIssueRequests() {
        assertNull(CampusUtilityRoom.parse("05-02-01"))
        assertNull(CampusUtilityRoom.parse("05-02-01-1-3-\n0301"))
        assertTrue(CampusUtilityRoom.parse("05-02-01-1-3-0301") != null)
    }

    @Test fun sm4StandardKnownBlockVectorDecrypts() {
        fun hex(value: String): ByteArray =
            value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val key = hex("0123456789abcdeffedcba9876543210")
        val cipher = hex("681edf34d206965e86b3e94f536e4246")
        val plain = CampusUtilitiesSm4.decryptBlock(cipher, key)
        assertEquals("0123456789abcdeffedcba9876543210",
            plain.joinToString("") { "%02x".format(it.toInt() and 0xff) })
    }
}
