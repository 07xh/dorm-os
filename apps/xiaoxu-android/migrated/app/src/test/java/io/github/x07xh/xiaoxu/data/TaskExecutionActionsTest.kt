package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskExecutionActionsTest {
    @Test fun remoteParentWriteSerializesStepMutationUntilConfirmed() = runBlocking {
        val root=ExecutionRoot("cloud:test-owner","cloud:1")
        val events=mutableListOf<String>()
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val first=launch {withExecutionAction(root) {events.add("saving");started.complete(Unit);release.await();events.add("confirmed")}}
        started.await()
        val second=launch(start=CoroutineStart.UNDISPATCHED) {withExecutionAction(root) {events.add("edit")}}
        assertEquals(listOf("saving"),events)
        release.complete(Unit);first.join();second.join()
        assertEquals(listOf("saving","confirmed","edit"),events)
    }
    @Test fun queuedOwnerWriteIsRejectedAfterAccountSwitch() = runBlocking {
        val root=ExecutionRoot("wechat:owner-a","cloud:1")
        var currentOwner="owner-a";var writes=0
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val held=launch {withExecutionAction(root) {started.complete(Unit);release.await()}}
        started.await()
        val queued=async(start=CoroutineStart.UNDISPATCHED) {
            withOwnerExecutionAction(root,{currentOwner}) {writes++;true}
        }
        currentOwner="owner-b";release.complete(Unit);held.join()
        org.junit.Assert.assertFalse(queued.await())
        assertEquals(0,writes)
    }
}
