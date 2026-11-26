package com.unityhub.app.data

import java.time.LocalDate

enum class AttendancePeriod(val label: String, val dayCount: Int?) {
    LAST_30_DAYS("30 days", 30),
    LAST_90_DAYS("90 days", 90),
    ALL_TIME("All time", null)
}

enum class RehearsalRosterFilter(val label: String) {
    ALL("All"),
    AWAITING("Awaiting"),
    AVAILABLE("Available"),
    UNAVAILABLE("Can't attend"),
    ATTENDED("Attended"),
    ABSENT("Absent"),
    NOT_RECORDED("Not recorded")
}

data class RehearsalRosterSummary(
    val invited: Int,
    val available: Int,
    val unavailable: Int,
    val awaiting: Int,
    val attended: Int,
    val absent: Int,
    val notRecorded: Int
)

internal object RehearsalRosterRules {
    fun filterByPeriod(
        snapshots: List<RehearsalAttendanceSnapshot>,
        period: AttendancePeriod,
        today: LocalDate
    ): List<RehearsalAttendanceSnapshot> =
        snapshots.filter { snapshot ->
            includesDate(snapshot.rehearsal.rehearsalDate, period, today)
        }

    fun includesDate(
        dateValue: String,
        period: AttendancePeriod,
        today: LocalDate
    ): Boolean {
        val date = runCatching { LocalDate.parse(dateValue) }.getOrNull() ?: return false
        val days = period.dayCount ?: return true
        val firstDay = today.minusDays((days - 1).toLong())
        return !date.isBefore(firstDay) && !date.isAfter(today)
    }

    fun attendanceRate(attended: Int, absent: Int): Int? {
        val recordedOutcomes = attended + absent
        return if (recordedOutcomes > 0) attended * 100 / recordedOutcomes else null
    }

    fun summary(assignments: List<RehearsalAssignmentDto>): RehearsalRosterSummary {
        val attended = assignments.count { it.responseStatus == "attended" }
        val absent = assignments.count { it.responseStatus == "absent" }
        return RehearsalRosterSummary(
            invited = assignments.size,
            available = assignments.count { it.responseStatus == "available" },
            unavailable = assignments.count { it.responseStatus == "unavailable" },
            awaiting = assignments.count { it.responseStatus == "pending" },
            attended = attended,
            absent = absent,
            notRecorded = assignments.size - attended - absent
        )
    }

    fun filtersFor(rehearsalStatus: String): List<RehearsalRosterFilter> =
        if (rehearsalStatus == "completed") {
            listOf(
                RehearsalRosterFilter.ALL,
                RehearsalRosterFilter.ATTENDED,
                RehearsalRosterFilter.ABSENT,
                RehearsalRosterFilter.NOT_RECORDED
            )
        } else {
            listOf(
                RehearsalRosterFilter.ALL,
                RehearsalRosterFilter.AWAITING,
                RehearsalRosterFilter.AVAILABLE,
                RehearsalRosterFilter.UNAVAILABLE
            )
        }

    fun filter(
        assignments: List<RehearsalAssignmentDto>,
        memberNames: Map<String, String>,
        selectedFilter: RehearsalRosterFilter,
        query: String
    ): List<RehearsalAssignmentDto> {
        val normalizedQuery = query.trim().lowercase()
        return assignments.filter { assignment ->
            val matchesStatus = when (selectedFilter) {
                RehearsalRosterFilter.ALL -> true
                RehearsalRosterFilter.AWAITING -> assignment.responseStatus == "pending"
                RehearsalRosterFilter.AVAILABLE -> assignment.responseStatus == "available"
                RehearsalRosterFilter.UNAVAILABLE -> assignment.responseStatus == "unavailable"
                RehearsalRosterFilter.ATTENDED -> assignment.responseStatus == "attended"
                RehearsalRosterFilter.ABSENT -> assignment.responseStatus == "absent"
                RehearsalRosterFilter.NOT_RECORDED ->
                    assignment.responseStatus != "attended" && assignment.responseStatus != "absent"
            }
            val memberName = memberNames[assignment.memberId].orEmpty()
            val matchesQuery = normalizedQuery.isBlank() ||
                memberName.lowercase().contains(normalizedQuery)
            matchesStatus && matchesQuery
        }.sortedWith(
            compareBy<RehearsalAssignmentDto> {
                memberNames[it.memberId].orEmpty().lowercase()
            }.thenBy { it.memberId }
        )
    }
}
