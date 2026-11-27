package io.github.x07xh.xiaoxu.ui

// SPDX-License-Identifier: GPL-3.0-only
// Source: znjhahaha/zhengfang-apk @ bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed, PageDataStateTest.kt

import org.junit.Assert.*
import org.junit.Test

class PageDataStateTest {
    @Test fun revisitingPageRetainsResultButAccountChangesDetachOldCallbacks() {
        val model = PageDataViewModel()
        val first = model.forAccount("a").state("grades") { listOf(1) }
        assertSame(first, model.forAccount("a").state("grades") { emptyList<Int>() })
        val second = model.forAccount("b").state("grades") { emptyList<Int>() }
        first.value = listOf(999)
        assertTrue(second.value.isEmpty())
        assertNotSame(first, model.forAccount("a").state("grades") { emptyList<Int>() })
    }
}
