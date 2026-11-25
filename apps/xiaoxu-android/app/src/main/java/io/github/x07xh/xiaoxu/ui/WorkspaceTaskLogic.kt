package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.*
import java.time.*

private val SchoolZone = ZoneId.of("Asia/Shanghai")

/** In-memory combination for DISPLAY ONLY. Room, D1 and school data remain separate. */
internal data class WorkspaceTask(
    val key: String,
    val title: String,
    val origin: String,
    val done: Boolean,
    val date: LocalDate?,
    val dateSource: String,
    val storage: String,
    val category: String = storage,
    val revision: Int = -1,
    val provider: String? = null,
    val links: List<String> = emptyList(),
    val sourceExternalId: String? = null,
    val officialStatus: String? = null,
    /** Exact status text on the signed-in local official page, if available. */
    val officialStatusRaw: String? = null,
    /** From a validated on-device official work or exam-notice identity only. */
    val officialDeadline: String? = null
)

internal fun parseWorkspaceDue(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
        ?: runCatching { Instant.parse(value).atZone(SchoolZone).toLocalDate() }.getOrNull()
}

/**
 * Render a canonical official homework record only once if historical imports
 * have the exact same Chaoxing course/class/work ID. Never deduplicate by
 * title or date, and never delete imported records from the cloud database.
 *
 * Without a trusted official row the historical entries stay visible:
 * unverified IDs cannot decide which personal task record is canonical.
 */
internal fun distinctVerifiedChaoxingHomeworkRows(cloud: List<CloudTask>): List<CloudTask> {
    // Only one independently verified source row is a safe canonical record.
    // If two trusted rows disagree or duplicate, keep ALL evidence visible
    // instead of guessing which row to hide.
    val verified = cloud.filter {
        trustedChaoxingPlatformStatus(it.provider, it.sourceExternalId, it.officialStatus) != null
    }.groupBy { it.sourceExternalId }
    val canonical = verified.filterValues { it.size == 1 }
        .mapNotNull { (key, rows) -> key?.let { it to rows.single().id } }.toMap()
    return cloud.filter { row ->
        val sourceId = row.sourceExternalId
        row.provider != "chaoxing" || sourceId == null ||
            canonical[sourceId] == null || canonical[sourceId] == row.id
    }
}

internal fun combineWorkspaceTasks(
    local: List<LocalTask>, cloud: List<CloudTask>,
    native: List<NativeSchoolRecord> = emptyList()
): List<WorkspaceTask> =
    buildList {
        // An official work tuple, not a title, allows local official-page evidence
        // to appear in the SAME task list without duplicating cloud records.
        val coverage=nativeChaoxingWorkCoverage(native)
        val removedWorkIds=nativeChaoxingRemovedWorkIds(native)
        val examAliases=native.flatMap(::nativeChaoxingExamNoticeAliases).toSet()
        val localEvidence = native.mapNotNull { record ->
            val id = nativeChaoxingWorkSourceId(record)
            val status = nativeChaoxingVisibleStatus(record)
            if (id != null && !coverage.excludes(id) &&
                status in setOf("todo", "returned", "submitted", "graded", "unknown"))
                id to record else null
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, records) -> records.maxByOrNull { it.capturedAt }!! }
        val planEvidence = native.mapNotNull { record ->
            val id = nativeChaoxingPlanSourceId(record)
            val status = nativeChaoxingVisibleStatus(record)
            if (id != null && status in setOf("todo", "returned", "submitted", "graded", "unknown"))
                id to record else null
        }.groupBy({ it.first }, { it.second })
            .mapValues { (_, records) -> records.maxByOrNull { it.capturedAt }!! }
        val cloudIds = cloud.filter { it.provider == "chaoxing" }
            .mapNotNull { it.sourceExternalId }.toSet()
        val aliases = native.flatMap { record ->
            val work = nativeChaoxingWorkSourceId(record) ?: return@flatMap emptyList()
            nativeChaoxingNoticeAliases(record).map { it to (work to false) } +
                nativeChaoxingNoticeAliases(record, peer = true).map {it to (work to true)}
        }.groupBy({it.first}, {it.second}).mapNotNull { (alias, bindings) ->
            val binding = bindings.distinct().singleOrNull() ?: return@mapNotNull null
            val (work, peer) = binding
            val raw = localEvidence[work]?.let(::nativeChaoxingVisibleStatusText).orEmpty()
            if (peer && !Regex("已互评|已互評|互评已完成|互評已完成|已完成互评|已完成互評|互评完成|互評完成").containsMatchIn(raw)) null else alias to work
        }.toMap()
        fun workId(task: CloudTask): String? = if (task.provider == "chaoxing")
            aliases[task.sourceExternalId] ?: task.sourceExternalId else null
        // One display row per proved work identity. Prefer the original official
        // cloud row; preserve every underlying D1 record and personal flag.
        val dedupedCloud = distinctVerifiedChaoxingHomeworkRows(cloud)
        val linkedCloud = dedupedCloud.filter { aliases.containsKey(it.sourceExternalId) && it.provider == "chaoxing" }
        val matchingOfficialRows = dedupedCloud.filter { it.provider == "chaoxing" &&
            it.sourceExternalId in aliases.values }
        // If multiple trusted cloud rows claim the SAME official work ID,
        // choose none arbitrarily. A legacy notice alias must not make one
        // contradictory submitted/returned record disappear.
        val ambiguousOfficialIds = matchingOfficialRows.groupBy { it.sourceExternalId }
            .filterValues { it.size > 1 }.keys
        val canonical = (linkedCloud + matchingOfficialRows).groupBy {workId(it)}
            .mapNotNull { (id, rows) ->
                id?.takeIf { it !in ambiguousOfficialIds }?.let { sourceId ->
                    sourceId to rows.sortedWith(compareBy<CloudTask> {
                        if (it.sourceExternalId in aliases.values) 0 else 1
                    }.thenBy {it.id}).first().id
                }
            }.toMap()
        val represented = cloudIds + linkedCloud.mapNotNull { workId(it) }
        local.forEach { task ->
            val source = when (task.source) {
                "chaoxing" -> "本人标注学习通"
                "second_class" -> "本人标注第二课堂"
                "course" -> "本人标注课程"
                else -> "本机待办"
            }
            add(WorkspaceTask("local:" + task.id, task.title, source, task.completed,
                parseWorkspaceDue(task.due), "本人填写", "local"))
        }
        dedupedCloud.forEach { task ->
            val effectiveId = workId(task)
            if(task.provider=="chaoxing" && task.status!="done" && task.sourceExternalId in examAliases)
                return@forEach
            // Preserve every D1 record and personal completion flag. A complete
            // current class list can withdraw an absent pending work from this
            // display without asserting that the teacher deleted it.
            if(task.provider=="chaoxing" && task.status!="done" &&
                (coverage.excludes(effectiveId) || effectiveId in removedWorkIds))
                return@forEach
            if (task.provider == "chaoxing" && canonical[effectiveId]?.let {it != task.id} == true)
                return@forEach
            val source = when (task.provider) {
                "wechat" -> "微信任务"
                "chaoxing" -> task.sourceCourse?.takeIf { it.isNotBlank() }
                    ?.let { "学习通 · $it" } ?: "学习通"
                "ncwu" -> "青果记录"
                "second_class" -> "第二课堂记录"
                else -> "校序云端任务"
            }
            val trustedStatus = trustedChaoxingPlatformStatus(
                task.provider, task.sourceExternalId, task.officialStatus)
            val localRecord = if (task.provider == "chaoxing")
                effectiveId?.let { localEvidence[it] } else null
            val planRecord = if (task.provider == "chaoxing")
                effectiveId?.let { planEvidence[it] } else null
            val localStatus = localRecord?.let(::nativeChaoxingVisibleStatus)
            val planStatus = planRecord?.let(::nativeChaoxingVisibleStatus)
            // Exact official IDs can identify BOTH homework and study plans.
            // Unknown local observations cannot erase a trusted work status.
            val verifiedStatuses = setOf("todo", "returned", "submitted", "graded")
            val fromLocal = localStatus in verifiedStatuses
            val fromPlan = !fromLocal && planStatus in verifiedStatuses
            val visibleStatus = when {
                fromLocal -> localStatus
                fromPlan -> planStatus
                else -> trustedStatus
            }
            val evidence = when {
                fromLocal -> localRecord
                fromPlan -> planRecord
                else -> null
            }
            val observedDeadline=localRecord?.let(::nativeChaoxingDeadline)
            add(WorkspaceTask("cloud:" + task.id, task.title,
                if (evidence != null) "$source · 本机官网状态" else source,
                task.status == "done", observedDeadline?.date ?: parseWorkspaceDue(task.due),
                if(observedDeadline?.date != null) "学习通本机官方截止"
                else if (evidence != null) "本机官方页面观察" else "云端记录，需核对来源", "cloud",
                when (task.provider) {
                    "wechat" -> "wechat"
                    "chaoxing" -> "chaoxing"
                    else -> "cloud"
                },
                task.revision, task.provider, task.links,
                effectiveId ?: task.sourceExternalId, visibleStatus,
                evidence?.let(::nativeChaoxingVisibleStatusText),
                officialDeadline=observedDeadline?.label))
        }
        localEvidence.filterKeys { it !in represented }.forEach { (id, record) ->
            val status = nativeChaoxingVisibleStatus(record)
            add(WorkspaceTask(
                "native:" + record.fingerprint, record.title,
                "学习通 · 本机官网", false, nativeChaoxingDeadline(record)?.date,
                "本机官方页面观察",
                "native", "chaoxing", provider = "chaoxing",
                sourceExternalId = id, officialStatus = status,
                officialStatusRaw = nativeChaoxingVisibleStatusText(record),
                officialDeadline = nativeChaoxingDeadline(record)?.label
            ))
        }
        // A source-ID matched cloud row already represents this official plan;
        // do not append an extra native row solely because its kind is study_task.
        planEvidence.filterKeys { it !in cloudIds }.forEach { (id, record) ->
            val status = nativeChaoxingVisibleStatus(record)
            add(WorkspaceTask(
                "native:" + record.fingerprint, record.title,
                "学习通 · 学习计划（本人页面）", false, null, "本机官方页面观察",
                "native", "chaoxing", provider = "chaoxing",
                sourceExternalId = id, officialStatus = status,
                officialStatusRaw = nativeChaoxingVisibleStatusText(record)
            ))
        }
    }.sortedWith(compareBy<WorkspaceTask> { considerCompleteInTaskList(it) }
        .thenBy { it.date ?: LocalDate.MAX }.thenBy { it.key })

/** Verified source-ID-linked submissions are complete in the task LIST only.
 * Never write that inferred state back as a personal checkbox or school action.
 */
internal fun considerCompleteInTaskList(task: WorkspaceTask): Boolean =
    task.done || (task.provider == "chaoxing" &&
        task.officialStatus in setOf("submitted", "graded"))

/** Unknown school records are neither verified complete nor actionable.
 * Retain them for user review without changing any stored status. */
internal fun requiresChaoxingVerification(task: WorkspaceTask): Boolean =
    task.provider == "chaoxing" && !considerCompleteInTaskList(task) &&
        task.officialStatus !in setOf("todo", "returned")

internal fun isActionableWorkspaceTask(task: WorkspaceTask): Boolean =
    !considerCompleteInTaskList(task) && !requiresChaoxingVerification(task)

/** Distinguish live homework identity from a legacy inbox identity in details.
 * Notice identity alone cannot prove an assignment still exists or was deleted.
 */
internal fun chaoxingTaskIdentityHint(task: WorkspaceTask): String? {
    if (task.provider != "chaoxing") return null
    val id = task.sourceExternalId.orEmpty()
    return when {
        Regex("""work:\d{1,24}:\d{1,24}:\d{1,24}""").matches(id) ->
            "官方作业身份：$id"
        id.matches(Regex("""cxn:[a-f0-9]{40}""")) ->
            "历史通知身份：${id.take(16)}… · 尚未关联当前官方作业"
        id.startsWith("plan:") || id.startsWith("study:") ->
            "学习计划身份：$id"
        else -> "未获得可核验的官方作业身份"
    }
}

internal fun visibleWorkspaceTasks(
    tasks: List<WorkspaceTask>, archivedKeys: Set<String>
): List<WorkspaceTask> = tasks.filterNot { it.key in archivedKeys }

/** Official status never overrides a user's personal checked state or
 * becomes a verified due date. It is only a last-synced source status.
 */
internal fun officialTaskStatusLabel(status: String?): String? = when (status) {
    "todo" -> "官方未提交"
    "submitted" -> "官方已提交"
    "graded" -> "官方已批阅"
    "returned" -> "官方已退回"
    "removed" -> "官网显示作业已删除或失效"
    "unknown" -> "官方状态未知"
    else -> null
}

/** Only self-entered dates are reliable as personal task deadlines.
 * Some CloudTask records have source work IDs, but no verified due date.
 */
internal fun countUpcomingLocalDeadlines(tasks: List<WorkspaceTask>, today: LocalDate): Int =
    tasks.count { task ->
        task.storage == "local" && !task.done &&
            task.date?.let { !it.isBefore(today) && !it.isAfter(today.plusDays(3)) } == true
    }

/** Seven-day histogram from user-entered dates only; never treat cloud snapshots as deadlines. */
internal fun personalDeadlineHistogram(tasks: List<WorkspaceTask>, today: LocalDate): List<Int> {
    val totals = tasks.asSequence()
        .filter { it.storage == "local" && !it.done }
        .mapNotNull { it.date }
        .groupingBy { it }
        .eachCount()
    return List(7) { day -> totals[today.plusDays(day.toLong())] ?: 0 }
}

private fun thisWeek(date: LocalDate?, today: LocalDate): Boolean {
    if (date == null) return false
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return !date.isBefore(monday) && date.isBefore(monday.plusDays(7))
}

private fun selectTasks(list: List<WorkspaceTask>, filter: String, today: LocalDate) =
    list.filter { task ->
        when (filter) {
            "pending", "now" -> isActionableWorkspaceTask(task)
            "verify" -> requiresChaoxingVerification(task)
            "done" -> considerCompleteInTaskList(task)
            "week" -> task.storage == "local" && !task.done && thisWeek(task.date, today)
            "local" -> task.storage == "local"
            "cloud" -> task.storage == "cloud"
            else -> true
        }
    }

/** Independent source/deadline facets over already-read tasks. */
internal fun facetWorkspaceTasks(
    tasks: List<WorkspaceTask>, source: String, thisWeekOnly: Boolean, today: LocalDate
): List<WorkspaceTask> = tasks.filter { task ->
    (source == "all" || task.category == source) &&
        (!thisWeekOnly || thisWeek(trustedExecutionDate(task), today))
}

/** The search only filters already-read Room/D1 records; it never guesses school tasks. */
internal fun searchWorkspaceTasks(
    tasks: List<WorkspaceTask>, filter: String, query: String, today: LocalDate
): List<WorkspaceTask> {
    val needle = query.trim()
    return selectTasks(tasks, filter, today).filter { task ->
        needle.isEmpty() || task.title.contains(needle, ignoreCase = true) ||
            task.origin.contains(needle, ignoreCase = true)
    }
}

/**
 * NextTodo-style ready view, restricted to dependencies that the user explicitly
 * added between local Room tasks. Remote/official states are not overwritten.
 */
internal fun readyWorkspaceTasks(
    tasks: List<WorkspaceTask>,
    local: List<LocalTask>,
    dependencies: List<LocalTaskDependency>
): List<WorkspaceTask> {
    val readyLocalIds = readyLocalTaskIds(local, dependencies)
    // Only personally entered dates or verified official-page deadlines may
    // determine priority; imported/cloud snapshot dates never pose as deadlines.
    fun trustedDue(row: WorkspaceTask): LocalDate? = when {
        row.storage == "local" -> row.date
        row.provider == "chaoxing" &&
            (row.storage == "native" && row.officialDeadline != null ||
                row.storage == "cloud" && row.dateSource == "学习通本机官方截止") ->
            row.date
        else -> null
    }
    return tasks.asSequence()
        .filter(::isActionableWorkspaceTask)
        .filter { it.storage != "local" ||
            it.key.removePrefix("local:").toLongOrNull() in readyLocalIds }
        .sortedWith(compareBy<WorkspaceTask>(
            { trustedDue(it) ?: LocalDate.MAX },
            { it.key }
        ))
        .toList()
}

/** Unknown school records remain visible for review instead of resurfacing as to-dos. */
/** Prioritize only actionable tasks with a real, attributable due date.
 * Unknown school dates and untrusted cloud snapshots never become "today".
 */
internal fun isTodayPriorityWorkspaceTask(task: WorkspaceTask, today: LocalDate): Boolean {
    val due = task.date ?: return false
    if (!isActionableWorkspaceTask(task) || due.isAfter(today)) return false
    return task.storage == "local" ||
        (task.provider == "chaoxing" && task.officialDeadline != null &&
            (task.storage == "native" ||
                (task.storage == "cloud" &&
                    task.dateSource == "学习通本机官方截止")))
}

internal fun unverifiedChaoxingWorkspaceTasks(tasks: List<WorkspaceTask>): List<WorkspaceTask> =
    tasks.filter(::requiresChaoxingVerification)

/** Waiting items remain discoverable without a second task store or status model. */
internal fun waitingWorkspaceTasks(
    tasks: List<WorkspaceTask>,
    local: List<LocalTask>,
    dependencies: List<LocalTaskDependency>
): List<WorkspaceTask> {
    val waitingIds = waitingLocalTaskIds(local, dependencies)
    return tasks.asSequence()
        .filter { it.storage == "local" && !considerCompleteInTaskList(it) }
        .filter { it.key.removePrefix("local:").toLongOrNull() in waitingIds }
        .sortedWith(compareBy<WorkspaceTask>({ it.date ?: LocalDate.MAX }, { it.key }))
        .toList()
}

