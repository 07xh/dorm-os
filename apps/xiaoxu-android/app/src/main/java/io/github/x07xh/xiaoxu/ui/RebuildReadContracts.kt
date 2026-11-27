package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.data.*

// Recovered original pure data and status contracts; no old UI dependencies.
data class LocalImportedScheduleView(
    val lessons: List<VerifiedLesson>,
    val sourceLabel: String
)

fun localImportedScheduleView(lessons: List<VerifiedLesson>) =
    LocalImportedScheduleView(
        lessons = lessons,
        sourceLabel = "华水青果 · 本机导入（待本人核对）"
    )

internal fun <T> homeCoursePreview(records: List<T>): List<T> = records.take(4)

sealed interface MessagePhase {
    data object Loading : MessagePhase
    data object NotConnected : MessagePhase
    data class Failed(val message: String) : MessagePhase
    data object Empty : MessagePhase
    data class Content(val notices: List<AcademicNotice>) : MessagePhase
}

/** CloudUiState 没有"公告已读过"标志，busy 与 publicError 组合足以判定四态。 */
fun deriveMessagePhase(state: CloudUiState): MessagePhase {
    if (state.notices.isNotEmpty()) return MessagePhase.Content(state.notices)
    if (state.busy) return MessagePhase.Loading
    if (state.publicError != null) {
        return MessagePhase.Failed(state.publicError)
    }
    // An owner login or WeChat count is NOT evidence that the public notice feed
    // was fetched. Only the notice endpoint's fetchedAt can establish a verified empty.
    if (state.noticeFetchedAt.isNullOrBlank()) return MessagePhase.NotConnected
    return MessagePhase.Empty
}
