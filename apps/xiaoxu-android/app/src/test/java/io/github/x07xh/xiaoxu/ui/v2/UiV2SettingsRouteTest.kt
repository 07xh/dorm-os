package io.github.x07xh.xiaoxu.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Test

class UiV2SettingsRouteTest {
    @Test fun nestedRoutesReturnToTheirActualParent() {
        assertEquals("about", uiV2SettingsParent("licenses"))
        assertEquals("notifications", uiV2SettingsParent("notifications-course"))
        assertEquals("notifications", uiV2SettingsParent("notifications-task"))
    }
    @Test fun primarySettingsChildrenReturnHome() {
        listOf("connections", "notifications", "backup", "about",
            "timetable-settings", "timetable-editor", "home").forEach {
            assertEquals("home", uiV2SettingsParent(it))
        }
    }
}
