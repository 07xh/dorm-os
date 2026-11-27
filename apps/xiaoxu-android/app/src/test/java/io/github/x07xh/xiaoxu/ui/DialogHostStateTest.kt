package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, DialogHostStateTest.kt
// Original authors and contributors.

import org.junit.Assert.*
import org.junit.Test

class DialogHostStateTest {
    @Test fun disposingOldOwnerCannotDismissNewDialog() {
        val state = DialogHostState()
        var dismissed = 0
        val first = state.show(onDismiss = { dismissed++ }) {}
        state.dismiss(first)
        val second = state.show(onDismiss = { dismissed++ }) {}
        state.finishDismissal(first)
        state.dismiss(first, notify = false)
        assertTrue(state.isVisible)
        assertSame(second, state.dialogs.single().handle)
        assertEquals(1, dismissed)
        state.finishDismissal(first)
        assertEquals(1, dismissed)
    }

    @Test fun dismissingNestedDialogKeepsParentAndCallbackRunsOnce() {
        val state = DialogHostState()
        var dismissed = 0
        val parent = state.show(onDismiss = {}) {}
        val child = state.show(onDismiss = { dismissed++ }) {}
        state.dismiss(child)
        state.finishDismissal(child)
        state.finishDismissal(child)
        assertEquals(1, dismissed)
        assertSame(parent, state.dialogs.single().handle)
        assertTrue(state.isVisible)
    }
}
