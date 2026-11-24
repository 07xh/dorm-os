package io.github.x07xh.xiaoxu.data

import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CampusUtilitiesSyncCoordinatorTest {
    private val month = YearMonth.of(2026, 11)
    private val room = CampusUtilityRoom("05", "02", "01", "1", "3", "0301")
    private val binding = CampusUtilitiesSyncBinding("a".repeat(64), room)
    private val interval = CampusUtilitiesSyncCoordinator.DEFAULT_INTERVAL_MILLIS
    private val start = 100_000_000L

    private class MemoryStore : CampusUtilitiesSyncStore {
        val attempts = mutableMapOf<String, Long>()
        val snapshots = mutableMapOf<Pair<String, String>, CampusUtilityMonthResult>()
        override fun load(scope: String, month: YearMonth) =
            snapshots[scope to month.toString()]
        override fun reserveIfDue(scope: String, now: Long, interval: Long): Boolean {
            val last = attempts[scope]
            if (last != null && (now <= last || now - last < interval)) return false
            attempts[scope] = now
            return true
        }
        override fun save(scope: String, result: CampusUtilityMonthResult) {
            snapshots[scope to result.month] = result
        }
        override fun clear(scope: String) {
            snapshots.keys.removeAll { it.first == scope }
        }
    }

    private fun response(data: JSONObject) = CampusUtilitiesRawResponse(
        200, JSONObject().put("success", true).put("resultData", data).toString()
    )

    private fun readClient(onCall: (String) -> Unit = {}) =
        CampusUtilitiesReadOnlyClient(CampusUtilitiesTransport { path, _ ->
            onCall(path)
            when (path) {
                "/utilities/config" -> response(JSONObject().put("detailConfig",
                    JSONObject().put("version", "v1")))
                "/utilities/account" -> response(JSONObject().put("accountNum", "test"))
                "/utilities/usageDetails" -> response(JSONObject().put("totalUsed", "12.5"))
                else -> error("unexpected endpoint")
            }
        }, pace = {}, now = { start })

    @Test fun disabledOrUnboundDoesNotRead() = runBlocking {
        val store = MemoryStore()
        val c = CampusUtilitiesSyncCoordinator({ null },
            readClient { error("network forbidden") }, store)
        assertEquals(CampusUtilitiesSyncState.Disabled,
            c.refreshIfDue(false, month, start))
        assertEquals(CampusUtilitiesSyncState.NotAuthorized,
            c.refreshIfDue(true, month, start))
        assertTrue(store.attempts.isEmpty())
    }

    @Test fun sixHourBudgetSurvivesNewCoordinatorAndMonthChange() = runBlocking {
        val store = MemoryStore()
        var calls = 0
        fun coordinator() = CampusUtilitiesSyncCoordinator({ binding },
            readClient { calls++ }, store)
        val result = coordinator().refreshIfDue(true, month, start)
            as CampusUtilitiesSyncState.Current
        assertNull(result.result.accountNum)
        assertEquals("12.5", result.result.totalUsed)
        assertTrue(coordinator().refreshIfDue(true, month, start + 1)
            is CampusUtilitiesSyncState.Cached)
        assertTrue(coordinator().refreshIfDue(true, month.plusMonths(1), start + 1)
            is CampusUtilitiesSyncState.Cached)
        assertEquals(3, calls)
        assertTrue(coordinator().refreshIfDue(true, month, start + interval)
            is CampusUtilitiesSyncState.Current)
        assertEquals(6, calls)
    }

    @Test fun failureKeepsLastSuccessfulSnapshotAndDoesNotRetry() = runBlocking {
        val store = MemoryStore()
        CampusUtilitiesSyncCoordinator({ binding }, readClient(), store)
            .refreshIfDue(true, month, start)
        var calls = 0
        val failing = CampusUtilitiesReadOnlyClient(CampusUtilitiesTransport { _, _ ->
            calls++
            CampusUtilitiesRawResponse(503, "")
        }, pace = {})
        val c = CampusUtilitiesSyncCoordinator({ binding }, failing, store)
        val result = c.refreshIfDue(true, month, start + interval)
            as CampusUtilitiesSyncState.Failed
        assertEquals("12.5", result.cached?.totalUsed)
        assertTrue(c.refreshIfDue(true, month, start + interval + 1)
            is CampusUtilitiesSyncState.Cached)
        assertEquals(1, calls)
    }

    @Test fun revokedAuthorizationDoesNotReturnCachedPersonalData() = runBlocking {
        val store = MemoryStore()
        CampusUtilitiesSyncCoordinator({ binding }, readClient(), store)
            .refreshIfDue(true, month, start)
        val denied = CampusUtilitiesReadOnlyClient(CampusUtilitiesTransport { _, _ ->
            CampusUtilitiesRawResponse(401, "")
        }, pace = {})
        val c = CampusUtilitiesSyncCoordinator({ binding }, denied, store)
        assertEquals(CampusUtilitiesSyncState.NotAuthorized,
            c.refreshIfDue(true, month, start + interval))
        assertNull(store.load(binding.scope, month))
    }

    @Test fun changedBindingNeverPublishesOldResult() = runBlocking {
        val store = MemoryStore()
        var active: CampusUtilitiesSyncBinding? = binding
        val client = readClient { active = null }
        val c = CampusUtilitiesSyncCoordinator({ active }, client, store)
        assertEquals(CampusUtilitiesSyncState.NotAuthorized,
            c.refreshIfDue(true, month, start))
        assertTrue(store.snapshots.isEmpty())
    }
}
