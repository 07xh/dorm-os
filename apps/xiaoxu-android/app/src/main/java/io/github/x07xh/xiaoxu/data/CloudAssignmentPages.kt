package io.github.x07xh.xiaoxu.data

/**
 * Collects the existing owner-scoped /assignments offset API without silently
 * truncating school homework at one page. A complete short (or empty) final
 * page is required. A failure leaves CloudClient's prior task snapshot intact.
 *
 * The bound is deliberately independent of a provider's per-course limit.
 * A larger account must not be mistaken for a completely refreshed ledger.
 */
internal class CloudAssignmentPages(
    private val pageSize: Int = 100,
    private val maxRecords: Int = 2000
) {
    init {
        require(pageSize in 1..100 && maxRecords >= pageSize)
    }

    private val records = mutableListOf<CloudTask>()
    private val seenIds = mutableSetOf<String>()

    var complete: Boolean = false
        private set

    val nextOffset: Int get() = records.size

    fun append(page: List<CloudTask>, responseLimit: Int, responseOffset: Int) {
        check(!complete) { "cannot append after final page" }
        require(responseLimit == pageSize && responseOffset == records.size) {
            "untrusted pagination metadata"
        }
        require(page.size <= pageSize && records.size + page.size <= maxRecords) {
            "incomplete or oversized task ledger"
        }
        require(page.all { it.id.isNotBlank() }) { "missing task id" }
        val pageIds = page.map { it.id }
        require(pageIds.toSet().size == pageIds.size && pageIds.none { it in seenIds }) {
            "duplicate task id across pages"
        }
        records.addAll(page)
        seenIds.addAll(pageIds)
        complete = page.size < pageSize
    }

    fun verifiedRows(): List<CloudTask> {
        check(complete) { "task ledger may have more pages" }
        return records.toList()
    }
}
