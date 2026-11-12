package com.unityhub.app.data

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ServiceRunSheetSummary(
    val totalMinutes: Int,
    val itemsWithoutDuration: Int,
    val estimatedFinishTime: String?
)

internal object ServiceRunSheetRules {
    private val finishTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun summarize(
        itemDurations: List<Int?>,
        serviceStartTime: String?
    ): ServiceRunSheetSummary {
        val totalMinutes = itemDurations.filterNotNull().sumOf { it.coerceAtLeast(0) }
        val missingDurations = itemDurations.count { it == null }
        val start = serviceStartTime?.let { value ->
            runCatching { LocalTime.parse(value) }.getOrNull()
        }

        val finishTime = if (itemDurations.isNotEmpty() && missingDurations == 0 && start != null) {
            val finishMinuteOfDay = start.toSecondOfDay() / 60 + totalMinutes
            val daysLater = finishMinuteOfDay / (24 * 60)
            val finishTimeOfDay = LocalTime.ofSecondOfDay(
                (finishMinuteOfDay % (24 * 60)) * 60L
            )
            val daySuffix = when (daysLater) {
                0 -> ""
                1 -> " (+1 day)"
                else -> " (+$daysLater days)"
            }
            finishTimeOfDay.format(finishTimeFormatter) + daySuffix
        } else {
            null
        }

        return ServiceRunSheetSummary(
            totalMinutes = totalMinutes,
            itemsWithoutDuration = missingDurations,
            estimatedFinishTime = finishTime
        )
    }

    fun formatDuration(totalMinutes: Int): String {
        val safeMinutes = totalMinutes.coerceAtLeast(0)
        val hours = safeMinutes / 60
        val minutes = safeMinutes % 60
        return when {
            hours == 0 -> "$minutes min"
            minutes == 0 -> "$hours hr"
            else -> "$hours hr $minutes min"
        }
    }
}
