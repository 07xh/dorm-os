package io.github.x07xh.xiaoxu

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests the academic transition actually called by NativeShell. */
class AcademicNavigationTest {
    @Test fun secondClassEntryWorksFromEveryMainPageAndAcademicSubsection() {
        for (main in MainDestination.entries) {
            for (section in AcademicDestination.entries) {
                val next = AcademicNavigation(main, section).openSecondClass()
                assertEquals(MainDestination.ACADEMIC, next.destination)
                assertEquals(AcademicDestination.SECOND_CLASS, next.academic)
            }
        }
    }

    @Test fun openingSecondClassAgainKeepsTheSameRoute() {
        val route = AcademicNavigation().openSecondClass()
        assertEquals(route, route.openSecondClass())
    }

    @Test fun defaultSectionAndAcademicSelectionStayReachable() {
        val state = AcademicNavigation()
        assertEquals(AcademicDestination.OVERVIEW, state.academic)
        for (section in AcademicDestination.entries) {
            val next = state.selectAcademic(section)
            assertEquals(MainDestination.ACADEMIC, next.destination)
            assertEquals(section, next.academic)
        }
    }
}
