package io.github.x07xh.xiaoxu.data

/**
 * Read-only comparison of one verified school batch with the subsequently
 * refreshed owner assignment ledger. This does NOT link, complete or delete
 * old tasks. Course title is only a possible grouping for manual inspection.
 */
data class ChaoxingCourseLedgerCheck(
    val courseKey: String,
    val listed: Int,
    val stored: Int?,
    val missing: Int?,
    val possibleLegacy: Int?,
    val statuses: Map<String, Int>
)

data class ChaoxingLedgerReconciliation(
    val courses: Map<String, ChaoxingCourseLedgerCheck>,
    val unmatchedLegacy: Int?,
    val possibleLegacyTotal: Int?,
    val missingFromLedger: Int?,
    val ledgerFresh: Boolean
)

fun reconcileChaoxingLedger(
    audits: List<ChaoxingCourseAudit>,
    tasks: List<CloudTask>,
    ledgerFresh: Boolean
): ChaoxingLedgerReconciliation {
    val verifiedById = if (ledgerFresh) {
        tasks.filter {
            trustedChaoxingPlatformStatus(
                it.provider, it.sourceExternalId, it.officialStatus
            ) != null
        }.groupBy { it.sourceExternalId }
    } else emptyMap()

    val validTitles = audits.groupBy { it.courseTitle.trim() }
        .filterValues { it.size == 1 }
        .mapValues { it.value.single().courseKey }
    val unmatchedByCourse = mutableMapOf<String, Int>()
    var ungrouped = 0
    var possibleLegacyTotal = 0
    if (ledgerFresh) {
        for (task in tasks) {
            if (task.provider != "chaoxing" || trustedChaoxingPlatformStatus(
                    task.provider, task.sourceExternalId, task.officialStatus
                ) != null) continue
            possibleLegacyTotal++
            val key = task.sourceCourse?.trim()?.let { validTitles[it] }
            if (key == null) ungrouped++
            else unmatchedByCourse[key] = (unmatchedByCourse[key] ?: 0) + 1
        }
    }

    val reports = audits.associate { audit ->
        val parts = audit.courseKey.split(':')
        val canMatch = audit.coverage in setOf(
            ChaoxingCourseCoverage.COUNT_VERIFIED, ChaoxingCourseCoverage.LIST_ONLY
        ) && parts.size == 3
        // A unique exact source row is required: duplicate or conflicting
        // trusted rows cannot be counted as reconciled persistence.
        val stored = if (ledgerFresh && canMatch) audit.homework.count { work ->
            val id = "work:${parts[0]}:${parts[1]}:${work.workId}"
            verifiedById[id]?.singleOrNull()?.officialStatus == work.statusCode
        } else null
        val missing = stored?.let { audit.homework.size - it }
        audit.courseKey to ChaoxingCourseLedgerCheck(
            courseKey = audit.courseKey,
            listed = audit.homework.size,
            stored = stored,
            missing = missing,
            possibleLegacy = if (ledgerFresh) (unmatchedByCourse[audit.courseKey] ?: 0)
                else null,
            statuses = audit.homework.groupingBy { it.statusCode }.eachCount()
        )
    }
    return ChaoxingLedgerReconciliation(
        courses = reports,
        unmatchedLegacy = if (ledgerFresh) ungrouped else null,
        possibleLegacyTotal = if (ledgerFresh) possibleLegacyTotal else null,
        missingFromLedger = if (ledgerFresh) reports.values.mapNotNull { it.missing }.sum()
            else null,
        ledgerFresh = ledgerFresh
    )
}
