package com.unityhub.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal object RehearsalRules {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun normalizeTitle(value: String): String {
        val normalized = value.trim().replace(Regex("\\s+"), " ")
        require(normalized.length in 2..120) {
            "A rehearsal title must contain 2–120 characters."
        }
        return normalized
    }

    fun normalizeDate(value: String): String {
        val normalized = value.trim()
        require(runCatching { LocalDate.parse(normalized) }.isSuccess) {
            "Enter the rehearsal date as YYYY-MM-DD."
        }
        return normalized
    }

    fun normalizeTime(value: String, label: String = "Start time"): String {
        val parsed = runCatching { LocalTime.parse(value.trim()) }.getOrElse {
            throw IllegalArgumentException("$label must use 24-hour time, such as 18:30.")
        }
        return parsed.format(timeFormatter)
    }

    fun normalizeEndTime(startTime: String, endTime: String): String? {
        val normalized = endTime.trim()
        if (normalized.isBlank()) return null
        val start = LocalTime.parse(normalizeTime(startTime))
        val end = LocalTime.parse(normalizeTime(normalized, "End time"))
        require(end > start) { "End time must be later than start time." }
        return end.format(timeFormatter)
    }

    fun normalizeLocation(value: String): String {
        val normalized = value.trim()
        require(normalized.length <= 160) { "Location must be 160 characters or fewer." }
        return normalized
    }

    fun normalizeNotes(value: String): String {
        val normalized = value.trim()
        require(normalized.length <= 1000) { "Notes must be 1,000 characters or fewer." }
        return normalized
    }

    fun normalizeStatus(value: String): String {
        val normalized = value.trim().lowercase()
        require(normalized in setOf("scheduled", "completed", "cancelled")) {
            "Choose a valid rehearsal status."
        }
        return normalized
    }

    fun canRespond(status: String): Boolean = status == "scheduled"

    fun responseLabel(status: String): String = when (status) {
        "available" -> "Available"
        "unavailable" -> "Can't attend"
        "attended" -> "Attended"
        "absent" -> "Absent"
        else -> "Awaiting response"
    }

    fun isAvailabilityResponse(status: String): Boolean =
        status == "available" || status == "unavailable"

    fun isAttendanceResult(status: String): Boolean =
        status == "attended" || status == "absent"

    fun formatTime(value: String): String =
        runCatching {
            LocalTime.parse(value).format(DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrElse { value.take(5) }

    fun formatDate(value: String): String =
        runCatching {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        }.getOrElse { value }
}
