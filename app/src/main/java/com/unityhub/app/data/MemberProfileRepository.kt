package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.LocalDate

data class MemberServiceActivity(
    val assignmentId: String,
    val serviceTitle: String,
    val serviceDate: String?,
    val startTime: String?,
    val roleLabel: String,
    val serviceStatus: String,
    val responseStatus: String
)

data class MemberRehearsalActivity(
    val assignmentId: String,
    val rehearsalTitle: String,
    val rehearsalDate: String,
    val startTime: String,
    val location: String,
    val rehearsalStatus: String,
    val responseStatus: String
)

data class MemberProfileSummary(
    val upcomingServices: Int,
    val confirmedServices: Int,
    val declinedServices: Int,
    val awaitingServiceResponses: Int,
    val upcomingRehearsals: Int,
    val attendedRehearsals: Int,
    val absentRehearsals: Int,
    val unrecordedRehearsals: Int,
    val songsCreated: Int = 0,
    val servicesServed: Int = 0
)

data class MemberProfileData(
    val joinedAt: String,
    val ministryPositions: List<String>,
    val summary: MemberProfileSummary,
    val recentServices: List<MemberServiceActivity>,
    val recentRehearsals: List<MemberRehearsalActivity>
)

internal object MemberProfileRules {
    const val RECENT_ACTIVITY_LIMIT = 8

    fun summarize(
        services: List<MemberServiceActivity>,
        rehearsals: List<MemberRehearsalActivity>,
        today: LocalDate,
        songsCreated: Int = 0
    ): MemberProfileSummary {
        val upcomingServices = services.count { activity ->
            activity.serviceStatus !in setOf("completed", "cancelled") &&
                isOnOrAfter(activity.serviceDate, today)
        }
        val upcomingRehearsals = rehearsals.count { activity ->
            activity.rehearsalStatus == "scheduled" &&
                isOnOrAfter(activity.rehearsalDate, today)
        }
        val completedRehearsals = rehearsals.filter { it.rehearsalStatus == "completed" }

        return MemberProfileSummary(
            upcomingServices = upcomingServices,
            confirmedServices = services.count { it.responseStatus == "confirmed" },
            declinedServices = services.count { it.responseStatus == "declined" },
            awaitingServiceResponses = services.count { it.responseStatus == "pending" },
            upcomingRehearsals = upcomingRehearsals,
            attendedRehearsals = completedRehearsals.count { it.responseStatus == "attended" },
            absentRehearsals = completedRehearsals.count { it.responseStatus == "absent" },
            unrecordedRehearsals = completedRehearsals.count {
                it.responseStatus !in setOf("attended", "absent")
            },
            songsCreated = songsCreated.coerceAtLeast(0),
            servicesServed = services.count {
                it.serviceStatus == "completed" && it.responseStatus == "confirmed"
            }
        )
    }

    fun recentServices(
        activities: List<MemberServiceActivity>,
        limit: Int = RECENT_ACTIVITY_LIMIT
    ): List<MemberServiceActivity> =
        activities.sortedWith(
            compareByDescending<MemberServiceActivity> { it.serviceDate.orEmpty() }
                .thenByDescending { it.startTime.orEmpty() }
                .thenBy { it.serviceTitle.lowercase() }
        ).take(limit.coerceAtLeast(0))

    fun recentRehearsals(
        activities: List<MemberRehearsalActivity>,
        limit: Int = RECENT_ACTIVITY_LIMIT
    ): List<MemberRehearsalActivity> =
        activities.sortedWith(
            compareByDescending<MemberRehearsalActivity> { it.rehearsalDate }
                .thenByDescending { it.startTime }
                .thenBy { it.rehearsalTitle.lowercase() }
        ).take(limit.coerceAtLeast(0))

    fun serviceResponseLabel(status: String): String = when (status) {
        "confirmed" -> "Confirmed"
        "declined" -> "Unable to serve"
        else -> "Awaiting response"
    }

    fun serviceStatusLabel(status: String): String = when (status) {
        "published" -> "Published"
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        else -> "Draft"
    }

    fun rehearsalResponseLabel(rehearsalStatus: String, responseStatus: String): String =
        when {
            rehearsalStatus == "completed" && responseStatus == "attended" -> "Attended"
            rehearsalStatus == "completed" && responseStatus == "absent" -> "Absent"
            rehearsalStatus == "completed" -> "Not recorded"
            rehearsalStatus == "cancelled" -> "Cancelled"
            responseStatus == "available" -> "Available"
            responseStatus == "unavailable" -> "Can't attend"
            else -> "Awaiting response"
        }

    private fun isOnOrAfter(value: String?, today: LocalDate): Boolean {
        val date = value?.let { raw ->
            runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
        } ?: return false
        return !date.isBefore(today)
    }
}

class MemberProfileRepository(private val client: SupabaseClient) {

    suspend fun loadProfile(
        organizationId: String,
        memberId: String
    ): MemberProfileData {
        require(organizationId.isNotBlank() && memberId.isNotBlank()) {
            "Choose a valid member in the active worship team."
        }

        // Resolve the selected membership inside the active organization first.
        val membership = client.from("organization_members").select {
            filter {
                eq("id", memberId)
                eq("organization_id", organizationId)
            }
        }.decodeList<OrganizationMemberDto>().firstOrNull()
            ?: error("This member is no longer part of the active worship team.")

        // Ministry positions are read only for this membership and organization.
        val memberPositions = client.from("member_positions").select {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", memberId)
            }
        }.decodeList<MemberMinistryPositionDto>()
        val positionIds = memberPositions.map { it.positionId }.toSet()
        val ministryPositions = if (positionIds.isEmpty()) {
            emptyList()
        } else {
            client.from("ministry_positions").select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<MinistryPositionDto>()
                .filter { it.id in positionIds }
                .map { it.name }
                .sortedBy { it.lowercase() }
        }

        // Every activity query is constrained by both the active organization and
        // the selected membership ID. Existing Supabase RLS remains authoritative.
        val serviceAssignments = client.from("service_plan_assignments").select {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", memberId)
            }
        }.decodeList<ServiceAssignmentDto>()

        val servicePlansById = if (serviceAssignments.isEmpty()) {
            emptyMap()
        } else {
            client.from("service_plans").select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<ServicePlanDto>().associateBy { it.id }
        }

        val serviceActivities = serviceAssignments.mapNotNull { assignment ->
            val plan = servicePlansById[assignment.servicePlanId] ?: return@mapNotNull null
            MemberServiceActivity(
                assignmentId = assignment.id,
                serviceTitle = plan.title,
                serviceDate = plan.serviceDate,
                startTime = plan.startTime,
                roleLabel = assignment.roleLabel,
                serviceStatus = plan.status,
                responseStatus = assignment.responseStatus
            )
        }

        val songsCreated = client.from("songs").select {
            filter {
                eq("organization_id", organizationId)
                eq("created_by", membership.userId)
            }
        }.decodeList<SongDto>().size

        val rehearsalAssignments = client.from("rehearsal_assignments").select {
            filter {
                eq("organization_id", organizationId)
                eq("member_id", memberId)
            }
        }.decodeList<RehearsalAssignmentDto>()

        val rehearsalsById = if (rehearsalAssignments.isEmpty()) {
            emptyMap()
        } else {
            client.from("rehearsals").select {
                filter { eq("organization_id", organizationId) }
            }.decodeList<RehearsalDto>().associateBy { it.id }
        }

        val rehearsalActivities = rehearsalAssignments.mapNotNull { assignment ->
            val rehearsal = rehearsalsById[assignment.rehearsalId] ?: return@mapNotNull null
            MemberRehearsalActivity(
                assignmentId = assignment.id,
                rehearsalTitle = rehearsal.title,
                rehearsalDate = rehearsal.rehearsalDate,
                startTime = rehearsal.startTime,
                location = rehearsal.location,
                rehearsalStatus = rehearsal.status,
                responseStatus = assignment.responseStatus
            )
        }

        return MemberProfileData(
            joinedAt = membership.joinedAt,
            ministryPositions = ministryPositions,
            summary = MemberProfileRules.summarize(
                services = serviceActivities,
                rehearsals = rehearsalActivities,
                today = LocalDate.now(),
                songsCreated = songsCreated
            ),
            recentServices = MemberProfileRules.recentServices(serviceActivities),
            recentRehearsals = MemberProfileRules.recentRehearsals(rehearsalActivities)
        )
    }
}
