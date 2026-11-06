package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.ServiceCalendarDay
import com.unityhub.app.data.ServiceCalendarRules
import com.unityhub.app.data.ServicePlanDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ServiceCalendarContent(
    plans: List<ServicePlanDto>,
    mode: String,
    selectedDateText: String,
    displayedMonthText: String,
    onSelectedDateChange: (String) -> Unit,
    onDisplayedMonthChange: (String) -> Unit,
    onOpenServicePlan: (ServicePlanDto) -> Unit
) {
    val selectedDate = runCatching { LocalDate.parse(selectedDateText) }.getOrElse { LocalDate.now() }
    val displayedMonth = runCatching { YearMonth.parse(displayedMonthText) }
        .getOrElse { YearMonth.from(selectedDate) }
    val visibleDays = if (mode == "week") {
        ServiceCalendarRules.weekDates(selectedDate).map { date ->
            ServiceCalendarDay(
                date = date,
                isInDisplayedMonth = YearMonth.from(date) == YearMonth.from(selectedDate),
                services = ServiceCalendarRules.servicesOn(date, plans)
            )
        }
    } else {
        ServiceCalendarRules.monthGrid(displayedMonth, plans)
    }
    val selectedServices = ServiceCalendarRules.servicesOn(selectedDate, plans)
    val selectedDateLabel = selectedDate.format(
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault())
    )
    val periodLabel = if (mode == "week") {
        val week = ServiceCalendarRules.weekDates(selectedDate)
        week.first().format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())) + " – " +
            week.last().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    } else {
        displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

    fun movePeriod(direction: Long) {
        if (mode == "week") {
            val nextDate = selectedDate.plusWeeks(direction)
            onSelectedDateChange(nextDate.toString())
            onDisplayedMonthChange(YearMonth.from(nextDate).toString())
        } else {
            val nextMonth = displayedMonth.plusMonths(direction)
            val nextDate = nextMonth.atDay(minOf(selectedDate.dayOfMonth, nextMonth.lengthOfMonth()))
            onDisplayedMonthChange(nextMonth.toString())
            onSelectedDateChange(nextDate.toString())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { movePeriod(-1) }) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = if (mode == "week") "Previous week" else "Previous month"
                )
            }
            Text(periodLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { movePeriod(1) }) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = if (mode == "week") "Next week" else "Next month"
                )
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

        visibleDays.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        selected = day.date == selectedDate,
                        today = day.date == LocalDate.now(),
                        modifier = Modifier.weight(1f).padding(2.dp).heightIn(min = 58.dp),
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
                if (selectedServices.size == 1) "1 planned service" else "${selectedServices.size} planned services",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (selectedServices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Text(
                    "No services planned for this day.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            selectedServices.forEach { plan ->
                CalendarServiceCard(plan = plan, onClick = { onOpenServicePlan(plan) })
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: ServiceCalendarDay,
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
            if (day.services.isNotEmpty()) {
                Text(
                    day.services.size.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CalendarServiceCard(plan: ServicePlanDto, onClick: () -> Unit) {
    val statusLabel = when (plan.status) {
        "published" -> "Published"
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        else -> "Draft"
    }
    val statusColor = when (plan.status) {
        "published" -> MaterialTheme.colorScheme.primary
        "completed" -> MaterialTheme.colorScheme.secondary
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val timeLabel = plan.startTime?.let { value ->
        runCatching {
            LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        }.getOrDefault(value.take(5))
    } ?: "Time not set"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(plan.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            Text(
                timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (plan.notes.isNotBlank()) {
                Text(
                    plan.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Text("Open service plan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
