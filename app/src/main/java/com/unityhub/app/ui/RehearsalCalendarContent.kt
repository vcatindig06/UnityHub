package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.RehearsalCalendarRules
import com.unityhub.app.data.RehearsalDto
import com.unityhub.app.data.RehearsalTimeFilter
import com.unityhub.app.data.ServicePlanDto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RehearsalCalendarContent(
    rehearsals: List<RehearsalDto>,
    servicePlans: List<ServicePlanDto>,
    timeFilter: RehearsalTimeFilter,
    selectedDateText: String,
    displayedMonthText: String,
    onSelectedDateChange: (String) -> Unit,
    onDisplayedMonthChange: (String) -> Unit,
    onOpenRehearsal: (RehearsalDto) -> Unit
) {
    val selectedDate = runCatching { LocalDate.parse(selectedDateText) }.getOrElse { LocalDate.now() }
    val displayedMonth = runCatching { YearMonth.parse(displayedMonthText) }
        .getOrElse { YearMonth.from(selectedDate) }
    val days = remember(displayedMonth, rehearsals) {
        RehearsalCalendarRules.monthGrid(displayedMonth, rehearsals)
    }
    val selectedRehearsals = remember(selectedDate, rehearsals) {
        RehearsalCalendarRules.rehearsalsOn(selectedDate, rehearsals)
    }
    val monthRehearsalCount = days
        .filter { it.isInDisplayedMonth }
        .sumOf { it.rehearsals.size }
    val monthLabel = displayedMonth.format(
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    )
    val selectedDateLabel = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    )

    fun moveMonth(direction: Long) {
        val nextMonth = displayedMonth.plusMonths(direction)
        val nextDate = nextMonth.atDay(minOf(selectedDate.dayOfMonth, nextMonth.lengthOfMonth()))
        onDisplayedMonthChange(nextMonth.toString())
        onSelectedDateChange(nextDate.toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { moveMonth(-1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { moveMonth(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
            TextButton(
                onClick = {
                    val today = LocalDate.now()
                    onSelectedDateChange(today.toString())
                    onDisplayedMonthChange(YearMonth.from(today).toString())
                }
            ) {
                Text("Today")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { weekday ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        weekday,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    RehearsalCalendarDayCell(
                        day = day,
                        selected = day.date == selectedDate,
                        today = day.date == LocalDate.now(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .heightIn(min = 58.dp),
                        onClick = {
                            onSelectedDateChange(day.date.toString())
                            onDisplayedMonthChange(YearMonth.from(day.date).toString())
                        }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(selectedDateLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                when (selectedRehearsals.size) {
                    0 -> "No ${timeFilter.label.lowercase(Locale.getDefault())} rehearsals on this day"
                    1 -> "1 rehearsal"
                    else -> "${selectedRehearsals.size} rehearsals"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selectedRehearsals.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Text(
                    "No rehearsals match this date and filter. Choose another day or switch to All.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            selectedRehearsals.forEach { rehearsal ->
                RehearsalListCard(
                    rehearsal = rehearsal,
                    servicePlan = servicePlans.firstOrNull { it.id == rehearsal.servicePlanId },
                    onClick = { onOpenRehearsal(rehearsal) }
                )
            }
        }

        Text(
            "${monthRehearsalCount} rehearsals in ${monthLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RehearsalCalendarDayCell(
    day: com.unityhub.app.data.RehearsalCalendarDay,
    selected: Boolean,
    today: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        today -> MaterialTheme.colorScheme.secondaryContainer
        day.isInDisplayedMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        day.isInDisplayedMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = background
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = foreground)
            if (day.rehearsals.isNotEmpty()) {
                Text(
                    if (day.rehearsals.size > 9) "9+" else day.rehearsals.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            } else {
                Box(Modifier.size(12.dp))
            }
        }
    }
}
