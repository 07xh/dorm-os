package io.github.x07xh.xiaoxu.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize a remote parent write with its on-device step edits. Never hold a Room transaction over HTTPS. */
private val executionActionLocks = ConcurrentHashMap<ExecutionRoot, Mutex>()
internal suspend fun <T> withExecutionAction(root: ExecutionRoot, action: suspend () -> T): T =
    executionActionLocks.getOrPut(root) { Mutex() }.withLock { action() }

/** Check after acquiring the queue lock; captured UI ownership may already be obsolete. */
internal suspend fun withOwnerExecutionAction(
    root: ExecutionRoot, currentOwner: () -> String?, action: suspend () -> Boolean
): Boolean = withExecutionAction(root) {
    val owner=currentOwner()
    if(owner.isNullOrBlank() || root.scope != "wechat:$owner") false else action()
}
