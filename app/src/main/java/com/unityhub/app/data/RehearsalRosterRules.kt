package com.unityhub.app.data

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
