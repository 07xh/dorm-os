package io.github.x07xh.xiaoxu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicNavigationTest {
    @Test fun messageBackRestoresSecondClassOrigin() {
        val origin = AcademicNavigation().openSecondClass()
        val opened = origin.openMessages()
        assertTrue(opened.messagesOpen)
        val restored = opened.closeMessages()
        assertFalse(restored.messagesOpen)
        assertEquals(MainDestination.ACADEMIC, restored.destination)
        assertEquals(AcademicDestination.SECOND_CLASS, restored.academic)
    }

    @Test fun messageBackRestoresTaskOrScheduleOrigin() {
        for (destination in listOf(MainDestination.TASKS, MainDestination.SCHEDULE)) {
            val origin = AcademicNavigation().selectMain(destination)
            assertEquals(origin, origin.openMessages().closeMessages())
        }
    }

    @Test fun switchingMainTabsRetainsAcademicSelection() {
        val state = AcademicNavigation().openSecondClass().selectMain(MainDestination.TASKS)
            .selectMain(MainDestination.ACADEMIC)
        assertEquals(AcademicDestination.SECOND_CLASS, state.academic)
    }

    @Test fun selectingMainTabClosesMessagesAndPreservesAcademicSubsection() {
        val state = AcademicNavigation().openSecondClass().openMessages()
            .selectMain(MainDestination.HOME)
        assertFalse(state.messagesOpen)
        assertEquals(MainDestination.HOME, state.destination)
        assertEquals(AcademicDestination.SECOND_CLASS, state.academic)
    }

    @Test fun defaultAcademicSectionIsOverviewAndServiceRouteIsReachable() {
        val state = AcademicNavigation()
        assertEquals(AcademicDestination.OVERVIEW, state.academic)
        assertEquals(AcademicDestination.SERVICES,
            state.selectAcademic(AcademicDestination.SERVICES).academic)
    }
}
