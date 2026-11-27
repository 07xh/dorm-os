package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, NavScrollIntentTest.kt

import org.junit.Assert.*
import org.junit.Test

class NavScrollIntentTest {
    @Test fun smallScrollsAccumulateAndReverseRequiresFreshTravel() {
        val scroll = NavScrollIntent()
        repeat(9) { assertNull(scroll.scroll(-4f)) }
        assertEquals(true, scroll.scroll(-4f))
        assertNull(scroll.scroll(12f))
        assertNull(scroll.scroll(-2f))
        assertNull(scroll.scroll(12f))
        assertEquals(false, scroll.scroll(8f))
    }

    @Test fun pageChangeDiscardsTravel() {
        val scroll = NavScrollIntent()
        scroll.scroll(-39f)
        scroll.reset()
        assertNull(scroll.scroll(-1f))
    }
}
