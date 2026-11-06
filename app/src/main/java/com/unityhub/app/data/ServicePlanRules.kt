package com.unityhub.app.data

import java.time.LocalDate
import java.time.LocalTime

internal object ServicePlanRules {
    fun normalizeTitle(value: String): String {
        val normalized = value.trim()
        require(normalized.length in 1..160) {
            "Enter a service title between 1 and 160 characters."
        }
        return normalized
    }

    fun normalizeDate(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        require(runCatching { LocalDate.parse(normalized) }.isSuccess) {
            "Use a valid date in YYYY-MM-DD format."
        }
        return normalized
    }

    fun normalizeTime(value: String): String? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        val parsed = runCatching { LocalTime.parse(normalized) }.getOrElse {
            throw IllegalArgumentException("Use a valid 24-hour time, such as 09:30.")
        }
        return parsed.toString().let { if (it.length == 5) "$it:00" else it }
    }
}
