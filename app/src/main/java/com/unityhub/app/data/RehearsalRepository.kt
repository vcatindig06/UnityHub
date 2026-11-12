package com.unityhub.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class RehearsalDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    val title: String,
    @SerialName("rehearsal_date") val rehearsalDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    val location: String = "",
    val notes: String = "",
    val status: String = "scheduled",
    @SerialName("service_plan_id") val servicePlanId: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class RehearsalAssignmentDto(
    val id: String,
    @SerialName("organization_id") val organizationId: String,
    @SerialName("rehearsal_id") val rehearsalId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("response_status") val responseStatus: String,
    @SerialName("response_note") val responseNote: String = "",
    @SerialName("assigned_by") val assignedBy: String,
    @SerialName("responded_at") val respondedAt: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
private data class NewRehearsal(
    @SerialName("organization_id") val organizationId: String,
    val title: String,
    @SerialName("rehearsal_date") val rehearsalDate: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String?,
    val location: String,
    val notes: String,
    @SerialName("service_plan_id") val servicePlanId: String?,
    @SerialName("created_by") val createdBy: String
)

@Serializable
private data class NewRehearsalAssignment(
    @SerialName("organization_id") val organizationId: String,
    @SerialName("rehearsal_id") val rehearsalId: String,
    @SerialName("member_id") val memberId: String,
    @SerialName("assigned_by") val assignedBy: String
)

class RehearsalRepository(private val client: SupabaseClient) {

    private val teamRepository = ServiceAssignmentRepository(client)

    suspend fun loadRehearsals(organizationId: String): List<RehearsalDto> {
        require(organizationId.isNotBlank())
        return client.from("rehearsals").select {
            filter { eq("organization_id", organizationId) }
        }.decodeList<RehearsalDto>()
            .sortedWith(compareBy<RehearsalDto> { it.rehearsalDate }
                .thenBy { it.startTime }
                .thenBy { it.title.lowercase() })
    }

    suspend fun loadAssignments(
        rehearsalId: String,
        organizationId: String
    ): List<RehearsalAssignmentDto> =
        client.from("rehearsal_assignments").select {
            filter {
                eq("rehearsal_id", rehearsalId)
                eq("organization_id", organizationId)
            }
        }.decodeList<RehearsalAssignmentDto>()
            .sortedBy { it.createdAt }

    suspend fun loadTeamMembers(organizationId: String): List<ServiceMemberOption> =
        teamRepository.loadTeamMembers(organizationId)

    suspend fun loadServicePlans(organizationId: String): List<ServicePlanDto> =
        ServicePlanRepository(client).loadPlans(organizationId)

    suspend fun createRehearsal(
        organizationId: String,
        userId: String,
        title: String,
        date: String,
        startTime: String,
        endTime: String,
        location: String,
        notes: String,
        servicePlanId: String?,
        memberIds: Set<String>
    ): RehearsalDto {
        require(organizationId.isNotBlank() && userId.isNotBlank())
        val normalizedStart = RehearsalRules.normalizeTime(startTime)
        val created = client.from("rehearsals").insert(
            NewRehearsal(
                organizationId = organizationId,
                title = RehearsalRules.normalizeTitle(title),
                rehearsalDate = RehearsalRules.normalizeDate(date),
                startTime = normalizedStart,
                endTime = RehearsalRules.normalizeEndTime(normalizedStart, endTime),
                location = RehearsalRules.normalizeLocation(location),
                notes = RehearsalRules.normalizeNotes(notes),
                servicePlanId = servicePlanId?.takeIf(String::isNotBlank),
                createdBy = userId
            )
        ).decodeList<RehearsalDto>().single()

        try {
            insertAssignments(created.id, organizationId, userId, memberIds)
        } catch (exception: Exception) {
            runCatching { deleteRehearsal(created.id, organizationId) }
            throw exception
        }
        return created
    }

    suspend fun updateRehearsal(
        rehearsalId: String,
        organizationId: String,
        userId: String,
        title: String,
        date: String,
        startTime: String,
        endTime: String,
        location: String,
        notes: String,
        servicePlanId: String?,
        memberIds: Set<String>
    ) {
        require(rehearsalId.isNotBlank() && organizationId.isNotBlank() && userId.isNotBlank())
        val normalizedStart = RehearsalRules.normalizeTime(startTime)
        client.from("rehearsals").update({
            set("title", RehearsalRules.normalizeTitle(title))
            set("rehearsal_date", RehearsalRules.normalizeDate(date))
            set("start_time", normalizedStart)
            set("end_time", RehearsalRules.normalizeEndTime(normalizedStart, endTime))
            set("location", RehearsalRules.normalizeLocation(location))
            set("notes", RehearsalRules.normalizeNotes(notes))
            set("service_plan_id", servicePlanId?.takeIf(String::isNotBlank))
            set("updated_at", Instant.now().toString())
        }) {
            filter {
                eq("id", rehearsalId)
                eq("organization_id", organizationId)
            }
        }
        syncAssignments(rehearsalId, organizationId, userId, memberIds)
    }

    suspend fun updateStatus(rehearsalId: String, organizationId: String, status: String) {
        client.from("rehearsals").update({
            set("status", RehearsalRules.normalizeStatus(status))
            set("updated_at", Instant.now().toString())
        }) {
            filter {
                eq("id", rehearsalId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun deleteRehearsal(rehearsalId: String, organizationId: String) {
        client.from("rehearsals").delete {
            filter {
                eq("id", rehearsalId)
                eq("organization_id", organizationId)
            }
        }
    }

    suspend fun respond(
        assignmentId: String,
        memberId: String,
        response: String,
        note: String = ""
    ) {
        require(RehearsalRules.isAvailabilityResponse(response)) {
            "Choose whether you are available or unable to attend."
        }
        require(note.trim().length <= 500) { "Response notes must be 500 characters or fewer." }
        client.from("rehearsal_assignments").update({
            set("response_status", response)
            set("response_note", note.trim())
            set("responded_at", Instant.now().toString())
        }) {
            filter {
                eq("id", assignmentId)
                eq("member_id", memberId)
            }
        }
    }

    suspend fun markAttendance(
        assignmentId: String,
        organizationId: String,
        status: String,
        note: String = ""
    ) {
        require(RehearsalRules.isAttendanceResult(status))
        require(note.trim().length <= 500)
        client.from("rehearsal_assignments").update({
            set("response_status", status)
            set("response_note", note.trim())
            set("responded_at", Instant.now().toString())
        }) {
            filter {
                eq("id", assignmentId)
                eq("organization_id", organizationId)
            }
        }
    }

    private suspend fun insertAssignments(
        rehearsalId: String,
        organizationId: String,
        userId: String,
        memberIds: Set<String>
    ) {
        val newAssignments = memberIds.filter(String::isNotBlank).distinct().map { memberId ->
            NewRehearsalAssignment(organizationId, rehearsalId, memberId, userId)
        }
        if (newAssignments.isNotEmpty()) {
            client.from("rehearsal_assignments").insert(newAssignments)
        }
    }

    private suspend fun syncAssignments(
        rehearsalId: String,
        organizationId: String,
        userId: String,
        selectedMemberIds: Set<String>
    ) {
        val current = loadAssignments(rehearsalId, organizationId)
        val currentByMember = current.associateBy { it.memberId }
        val selected = selectedMemberIds.filter(String::isNotBlank).toSet()

        current.filter { it.memberId !in selected }.forEach { assignment ->
            client.from("rehearsal_assignments").delete {
                filter {
                    eq("id", assignment.id)
                    eq("organization_id", organizationId)
                }
            }
        }

        insertAssignments(
            rehearsalId = rehearsalId,
            organizationId = organizationId,
            userId = userId,
            memberIds = selected - currentByMember.keys
        )
    }
}
