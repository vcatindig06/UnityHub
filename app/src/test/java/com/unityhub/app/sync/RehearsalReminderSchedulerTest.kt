package com.unityhub.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class RehearsalReminderSchedulerTest {

    @Test
    fun exposesSupportedReminderLabels() {
        assertEquals(
            "1 day before",
            RehearsalReminderScheduler.labelFor(
                RehearsalReminderScheduler.ONE_DAY_BEFORE_MINUTES
            )
        )
        assertEquals(
            "1 hour before",
            RehearsalReminderScheduler.labelFor(
                RehearsalReminderScheduler.ONE_HOUR_BEFORE_MINUTES
            )
        )
        assertEquals(
            "15 minutes before",
            RehearsalReminderScheduler.labelFor(
                RehearsalReminderScheduler.FIFTEEN_MINUTES_BEFORE_MINUTES
            )
        )
    }
}
