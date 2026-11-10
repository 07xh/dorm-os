package io.github.x07xh.xiaoxu.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

/**
 * Local-only snapshots obtained by the user from allowed official pages.
 * They are NOT automatically verified against the school's authoritative
 * academic record. Never include passwords, raw Cookies, HTML, URLs or CAS tickets.
 * Source-aware table shares the existing local task Room via additive v2->v3 migration.
 */
@Entity(tableName = "native_school_records", primaryKeys = ["provider", "kind", "fingerprint"])
data class NativeSchoolRecord(
    val provider: String,
    val kind: String,
    val fingerprint: String,
    val title: String,
    val details: String,
    val sourceHost: String,
    val capturedAt: Long
)

/**
 * Official text visible in the owner's on-device Chaoxing page.
 * This is a UI completion hint, not a cloud submission acknowledgement:
 * never match a separate historical row by title, and never write this state
 * back to the owner's personal checkbox or an official submission endpoint.
 */
private fun nativeChaoxingOfficialHost(host: String): Boolean {
    val url = "https://$host/"
    return runCatching {
        java.net.URI(url).host.equals(host, ignoreCase = true) &&
            ChaoxingWebNavigation.target(url) != null
    }.getOrDefault(false)
}

fun nativeChaoxingVisibleStatusText(record: NativeSchoolRecord): String? {
    if (record.provider != "chaoxing" ||
        record.kind !in setOf("homework", "study_task") ||
        !nativeChaoxingOfficialHost(record.sourceHost)) return null
    return Regex(
        """(?:^|[·\n])\s*(?:当前网页显示的状态|学习计划状态|提交状态|审核状态|任务状态|状态)\s*[：:]\s*([^·\n]{1,60})"""
    ).find(record.details)?.groupValues?.get(1)?.trim()
}

fun nativeChaoxingVisibleStatus(record: NativeSchoolRecord): String? {
    val raw = nativeChaoxingVisibleStatusText(record) ?: return null
    return when {
        Regex("""未提交|未完成|待提交|未交|未达标|未开始|待完成""").containsMatchIn(raw) -> "todo"
        Regex("""退回|重做|重新提交|驳回""").containsMatchIn(raw) -> "returned"
        Regex("""已批阅|已批閱|已评分|已評分|已评阅|已評閱|已批改""").containsMatchIn(raw) -> "graded"
        Regex("""已完成|已提交|已交|待批阅|待批閱|待评阅|待評閱|待批改|已达标|已作答|已互评|已互評|互评完成|互評完成|提交成功""").containsMatchIn(raw) ->
            "submitted"
        else -> "unknown"
    }
}

/** Stable, non-secret official work tuple validated by the fixed on-device reader. */
fun nativeChaoxingWorkSourceId(record: NativeSchoolRecord): String? {
    if (record.provider != "chaoxing" || record.kind != "homework" ||
        !nativeChaoxingOfficialHost(record.sourceHost)) return null
    val identity = Regex("""(?:^| · )官方作业身份：(work:\d{1,24}:\d{1,24}:\d{1,24})$""")
        .find(record.details)?.groupValues?.get(1) ?: return null
    val expected = MessageDigest.getInstance("SHA-256")
        .digest(("chaoxing|homework|" + identity).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return identity.takeIf { record.fingerprint == expected }
}

/** Stable official plan identity; never derive it from an assignment title. */
fun nativeChaoxingPlanSourceId(record: NativeSchoolRecord): String? {
    if (record.provider != "chaoxing" || record.kind != "study_task" ||
        !nativeChaoxingOfficialHost(record.sourceHost)) return null
    val identity = Regex("""(?:^| · )学习任务身份：((?:plan:\d{1,24}|study:[a-f0-9]{64}))$""")
        .find(record.details)?.groupValues?.get(1) ?: return null
    val expected = MessageDigest.getInstance("SHA-256")
        .digest(("chaoxing|study_task|" + identity).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return identity.takeIf { record.fingerprint == expected }
}

/** Non-secret notice aliases rebuilt from the same official notification and
 * resolved work destination. These are identity evidence, never status proof. */
fun nativeChaoxingNoticeAliases(record: NativeSchoolRecord, peer: Boolean = false): Set<String> {
    if (nativeChaoxingWorkSourceId(record) == null) return emptySet()
    val label = if (peer) "互评通知身份" else "旧通知身份"
    val value = Regex("(?:^| · )" + label + "：([^·]+) · ")
        .find(record.details)?.groupValues?.get(1)?.trim() ?: return emptySet()
    val ids = value.split(',')
    return if (ids.size <= 100 && ids.all {it.matches(Regex("cxn:[a-f0-9]{40}"))})
        ids.toSet() else emptySet()
}

/** A complete class list proves absence only for the latest saved owner scan.
 * Older proofs expire after any new (including partial) scan. Presence uses
 * exact official IDs captured in that same scan, never names or dates. */
internal data class NativeChaoxingWorkCoverage(val scopes:Set<String>, val present:Set<String>) {
    fun excludes(id:String?):Boolean {
        if(id==null || !id.matches(Regex("""work:\d{1,24}:\d{1,24}:\d{1,24}"""))) return false
        return id.substringAfter("work:").substringBeforeLast(':') in scopes && id !in present
    }
}
internal fun nativeChaoxingWorkCoverage(records:List<NativeSchoolRecord>):NativeChaoxingWorkCoverage {
    val binding=records.singleOrNull {it.provider=="chaoxing" && it.kind=="account_binding" &&
        it.fingerprint=="current" && it.sourceHost=="i.chaoxing.com" &&
        it.details.matches(Regex("[a-f0-9]{64}"))}
        ?: return NativeChaoxingWorkCoverage(emptySet(),emptySet())
    val scopes=records.mapNotNull { row ->
        if(row.provider!="chaoxing" || row.kind!="course" || row.capturedAt!=binding.capturedAt ||
            !nativeChaoxingOfficialHost(row.sourceHost)) return@mapNotNull null
        val identity=Regex("""完整作业列表身份：(scope:\d{1,24}:\d{1,24})""")
            .matchEntire(row.details)?.groupValues?.get(1) ?: return@mapNotNull null
        val expected=MessageDigest.getInstance("SHA-256")
            .digest(("chaoxing|course|"+identity).toByteArray(Charsets.UTF_8))
            .joinToString("") {"%02x".format(it)}
        identity.removePrefix("scope:").takeIf {row.fingerprint==expected}
    }.toSet()
    val present=records.filter {it.capturedAt==binding.capturedAt}
        .mapNotNull(::nativeChaoxingWorkSourceId).toSet()
    return NativeChaoxingWorkCoverage(scopes,present)
}

@Dao
interface NativeSchoolRecordDao {
    /** Atomically replace a complete score+activity read, including verified zero activities. */
    @Transaction
    suspend fun replaceSecondClassSnapshot(
        scores: List<NativeSchoolRecord>, activities: List<NativeSchoolRecord>,
        catalogEmpty: List<NativeSchoolRecord> = emptyList(),
        catalogActivities: List<NativeSchoolRecord> = emptyList(),
        accountHash: String? = null, expectedBinding: String? = null,
        catalogVerified: Boolean = true
    ) {
        require(scores.isNotEmpty() && scores.size <= 50)
        require(scores.all { it.provider == "second_class" && it.kind == "credits" })
        require(activities.size in 1..251)
        require(activities.count { it.kind == "activity_summary" } == 1)
        require(activities.all { it.provider == "second_class" && it.kind in setOf("activity", "activity_summary") })
        // One reader produces one capture time across its verified personal
        // responses. Reject stale/foreign rows before deleting any saved data.
        // This supplements, rather than replaces, the reader's identity checks.
        val capturedAt = scores.first().capturedAt
        require(capturedAt > 0)
        require((scores + activities).all {
            it.sourceHost == "dekt.ncwu.edu.cn" && it.capturedAt == capturedAt
        })
        require(accountHash == null || accountHash.matches(Regex("^[a-f0-9]{64}$")))
        require(expectedBinding == null || expectedBinding == accountHash)
        val saved = snapshotKind("second_class", "credits").maxOfOrNull { it.capturedAt } ?: 0
        require(capturedAt >= saved) { "stale_secondclass_batch" }
        val bindings = snapshotKind("second_class", "account_binding")
        if (expectedBinding != null && bindings.isNotEmpty()) {
            require(bindings.size == 1 && bindings.single().details == expectedBinding &&
                bindings.single().capturedAt == saved && bindings.single().sourceHost == "dekt.ncwu.edu.cn")
        }
        // The validated catalog is tied to this account's verified score read.
        // Clear an earlier owner's empty snapshot on successful account switch,
        // even when the current catalog is unavailable or nonempty/unverified.
        require(catalogEmpty.size in 0..3 && catalogActivities.size <= 120)
        // Failed/unknown catalog responses may update verified personal scores,
        // but cannot carry newly inferred catalog records.
        require(catalogVerified || (catalogEmpty.isEmpty() && catalogActivities.isEmpty()))
        require(catalogEmpty.all {
            it.provider == "second_class" && it.kind == "catalog_empty" &&
                it.sourceHost == "dekt.ncwu.edu.cn" && it.capturedAt == scores.first().capturedAt
        })
        val knownFilters = setOf("报名中", "待验证", "即将开始报名")
        require(catalogEmpty.map { it.title }.distinct().size == catalogEmpty.size &&
            catalogEmpty.all { it.title in knownFilters })
        require(catalogActivities.all {
            it.provider == "second_class" && it.kind == "catalog_activity" &&
                it.sourceHost == "dekt.ncwu.edu.cn" && it.capturedAt == capturedAt &&
                knownFilters.any { filter -> it.details.startsWith("筛选：$filter\n") }
        })
        require(catalogActivities.map { it.fingerprint }.distinct().size == catalogActivities.size)
        require(catalogActivities.none { row ->
            catalogEmpty.any { zero -> row.details.startsWith("筛选：${zero.title}\n") }
        })
        // Keep the prior catalog only when its persisted binding proves it
        // belongs to this same owner. A new/changed account always clears it.
        // Retained catalog rows keep their original capture timestamps, so UI
        // cannot misrepresent this attempt as a freshly verified catalog read.
        val preserveCatalog = !catalogVerified && accountHash != null &&
            bindings.size == 1 && bindings.single().details == accountHash &&
            bindings.single().sourceHost == "dekt.ncwu.edu.cn" &&
            bindings.single().capturedAt == saved
        if (!preserveCatalog) {
            deleteKind("second_class", "catalog_empty")
            deleteKind("second_class", "catalog_activity")
        }
        deleteKind("second_class", "credits")
        deleteKind("second_class", "activity")
        deleteKind("second_class", "activity_summary")
        deleteKind("second_class", "account_binding")
        val catalogSnapshot = if (catalogVerified) catalogEmpty + catalogActivities else emptyList()
        insertAll(scores + activities + catalogSnapshot)
        if (accountHash != null) insertAll(listOf(NativeSchoolRecord(
            "second_class", "account_binding", "current", "", accountHash,
            "dekt.ncwu.edu.cn", capturedAt)))
    }
    @Query("SELECT * FROM native_school_records ORDER BY capturedAt DESC, provider, kind, title")
    fun observe(): Flow<List<NativeSchoolRecord>>

    @Query("SELECT * FROM native_school_records ORDER BY capturedAt DESC, provider, kind, title")
    suspend fun snapshot(): List<NativeSchoolRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<NativeSchoolRecord>)

    @Query("DELETE FROM native_school_records WHERE provider = :provider AND kind = :kind")
    suspend fun deleteKind(provider: String, kind: String)

    @Query("SELECT * FROM native_school_records WHERE provider = :provider AND kind = :kind")
    suspend fun snapshotKind(provider: String, kind: String): List<NativeSchoolRecord>

    /**
     * Owner explicitly requests another visible official page. This remains a
     * local, unverified multi-page snapshot, not complete school synchronization.
     * Duplicate fingerprints update in-place. The size bound is checked within
     * the Room transaction before any write, preserving all old rows on error.
     */
    /** Single local reader commit. Changed accounts never inherit old snapshots. */
    @Transaction
    suspend fun mergeChaoxingRead(rows: List<NativeSchoolRecord>, accountHash: String,
        complete: Boolean = false, supersededKeys: Set<String> = emptySet()): Int {
        require(accountHash.matches(Regex("^[a-f0-9]{64}$")))
        require(rows.isNotEmpty() || complete) { "failed_empty_scan" }
        require(rows.size <= 5000 && rows.all {
            it.provider == "chaoxing" && it.kind in setOf("course", "homework", "study_task") &&
                it.sourceHost.endsWith(".chaoxing.com") && it.title.isNotBlank()
        })
        val old = snapshotKind("chaoxing", "account_binding").singleOrNull()?.details
        if (old != accountHash || complete) {
            for (kind in listOf("course", "homework", "study_task")) deleteKind("chaoxing", kind)
        } else if (supersededKeys.isNotEmpty()) {
            require(supersededKeys.size <= 5000)
            val fingerprints = supersededKeys.map { key ->
                require(key.matches(Regex("""plan:\d{1,24}|study:[a-f0-9]{64}""")))
                MessageDigest.getInstance("SHA-256").digest(("chaoxing|study_task|" + key).toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            }.toSet()
            val retained = snapshotKind("chaoxing", "study_task").filterNot {it.fingerprint in fingerprints}
            deleteKind("chaoxing", "study_task")
            insertAll(retained)
        }
        insertAll(rows)
        deleteKind("chaoxing", "account_binding")
        insertAll(listOf(NativeSchoolRecord("chaoxing", "account_binding", "current", "", accountHash,
            "i.chaoxing.com", rows.firstOrNull()?.capturedAt ?: System.currentTimeMillis())))
        return rows.count {it.kind != "course"}
    }

}

