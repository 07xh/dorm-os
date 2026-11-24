package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderQuietHoursTest {
    @Test fun disabledPolicyNeverSuppresses() {
        val settings = LocalQuietHours()
        assertFalse(isQuietMinute(23 * 60, settings))
        assertFalse(isQuietMinute(2 * 60, settings))
    }

    @Test fun overnightIntervalIncludesStartExcludesEnd() {
        val settings = LocalQuietHours(enabled = true, startMinutes = 23 * 60,
            endMinutes = 7 * 60)
        assertFalse(isQuietMinute(22 * 60 + 59, settings))
        assertTrue(isQuietMinute(23 * 60, settings))
        assertTrue(isQuietMinute(6 * 60 + 59, settings))
        assertFalse(isQuietMinute(7 * 60, settings))
    }

    @Test fun daytimeAndEqualEndpointsAreHandled() {
        val daytime = LocalQuietHours(true, 13 * 60, 15 * 60)
        assertFalse(isQuietMinute(12 * 60, daytime))
        assertTrue(isQuietMinute(14 * 60, daytime))
        assertFalse(isQuietMinute(15 * 60, daytime))
        assertFalse(isQuietMinute(14 * 60,
            LocalQuietHours(true, 14 * 60, 14 * 60)))
    }
}
