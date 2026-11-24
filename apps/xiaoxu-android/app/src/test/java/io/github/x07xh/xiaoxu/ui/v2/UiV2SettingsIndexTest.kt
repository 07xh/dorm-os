package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiV2SettingsIndexTest {
    @Test fun exactTitleWinsAndSynonymsFindRealRoutes() {
        assertEquals("notifications-course", uiV2SearchSettings("课程提醒").first().route)
        assertEquals("connections", uiV2SearchSettings("青果").first().route)
        assertEquals("utilities", uiV2SearchSettings("电费").first().route)
        assertEquals("utilities", uiV2SearchSettings("水费").first().route)
        assertEquals("licenses", uiV2SearchSettings("ＬＩＣＥＮＳＥ").first().route)
    }
    @Test fun emptyOrUnknownQueriesDoNotInventResults() {
        assertTrue(uiV2SearchSettings("　 ").isEmpty())
        assertTrue(uiV2SearchSettings("不存在的入口").isEmpty())
        assertEquals(uiV2SettingsIndex.size, uiV2SettingsIndex.map { it.route }.distinct().size)
    }
}
