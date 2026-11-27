package io.github.x07xh.xiaoxu.ui

import io.github.x07xh.xiaoxu.MainDestination
import org.junit.Assert.*
import org.junit.Test

class AppPageContractTest {
    @Test fun everyPrimaryHasExactlyOneRoot() {
        for (destination in MainDestination.entries) {
            val roots = AppRoute.entries.filter { it.primary == destination && it.parent == null }
            assertEquals(listOf(AppRoute.root(destination)), roots)
        }
    }
    @Test fun childrenReturnToTheirOwnPrimary() {
        for (route in AppRoute.entries.filter { it.parent != null }) {
            assertEquals(AppRoute.root(route.primary), route.parent)
        }
    }
    @Test fun unreadIsNotEmptyAndFailureDoesNotDiscardLastRead() {
        assertEquals(PageSnapshot.Loading, pageSnapshot<List<String>>(null))
        assertEquals(PageSnapshot.Ready(emptyList<String>()), pageSnapshot(emptyList<String>()))
        assertEquals(PageSnapshot.Unavailable("error"), pageSnapshot<List<String>>(null, "error"))
        assertEquals(PageSnapshot.Ready(listOf("saved"), true), pageSnapshot(listOf("saved"), "error"))
    }
}
