package io.github.x07xh.xiaoxu

import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicNavigationTest {
    @Test fun homeShortcutSelectsSecondClassAndBackFromMessagesReturnsHome() {
        val start = AcademicNavigation()
        val secondClass = start.openSecondClass()
        assertEquals(MainDestination.ACADEMIC, secondClass.destination)
        assertEquals(AcademicDestination.SECOND_CLASS, secondClass.academic)
        val home = secondClass.openMessages().closeMessages()
        assertEquals(MainDestination.HOME, home.destination)
        assertEquals(AcademicDestination.SECOND_CLASS, home.academic)
    }
    @Test fun switchingMainTabsRetainsAcademicSelection() {
        val state = AcademicNavigation().openSecondClass().selectMain(MainDestination.TASKS)
            .selectMain(MainDestination.ACADEMIC)
        assertEquals(AcademicDestination.SECOND_CLASS, state.academic)
    }
}
