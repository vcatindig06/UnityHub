package com.unityhub.app.ui

import android.content.Intent
import android.provider.CalendarContract
import com.unityhub.app.data.RehearsalCalendarRules
import com.unityhub.app.data.RehearsalDto
import java.time.ZoneId

internal object RehearsalCalendarIntent {
    fun create(rehearsal: RehearsalDto): Intent {
        val draft = RehearsalCalendarRules.buildEvent(
            rehearsal = rehearsal,
            zoneId = ZoneId.systemDefault()
        )
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, draft.title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, draft.location)
            putExtra(CalendarContract.Events.DESCRIPTION, draft.description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, draft.beginAtMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, draft.endAtMillis)
            putExtra(CalendarContract.Events.EVENT_TIMEZONE, draft.timeZoneId)
        }
    }
}
