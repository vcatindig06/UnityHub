package com.unityhub.app.data

import android.content.Context
import com.unityhub.app.data.cache.CachedRehearsalAssignmentEntity
import com.unityhub.app.data.cache.CachedRehearsalDao
import com.unityhub.app.data.cache.CachedRehearsalEntity
import com.unityhub.app.data.cache.UnityHubCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflineRehearsalRosterSnapshot(
    val assignments: List<RehearsalAssignmentDto>,
    val members: List<ServiceMemberOption>,
    val syncedAtMillis: Long?
)

class OfflineRehearsalCache(context: Context) {
    private val dao: CachedRehearsalDao =
        UnityHubCacheDatabase.getInstance(context).cachedRehearsalDao()

    suspend fun saveRehearsals(
        userId: String,
        organizationId: String,
        rehearsals: List<RehearsalDto>
    ) = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        require(rehearsals.all { it.organizationId == organizationId }) {
            "A rehearsal belongs to a different organization."
        }
        val timestamp = System.currentTimeMillis()
        dao.replaceRehearsals(
            userId,
            organizationId,
            rehearsals.map { rehearsal ->
                CachedRehearsalEntity().apply {
                    id = rehearsal.id
                    ownerUserId = userId
                    this.organizationId = rehearsal.organizationId
                    title = rehearsal.title
                    rehearsalDate = rehearsal.rehearsalDate
                    startTime = rehearsal.startTime
                    endTime = rehearsal.endTime
                    location = rehearsal.location
                    notes = rehearsal.notes
                    status = rehearsal.status
                    servicePlanId = rehearsal.servicePlanId
                    createdBy = rehearsal.createdBy
                    createdAt = rehearsal.createdAt
                    updatedAt = rehearsal.updatedAt
                    cachedAt = timestamp
                }
            }
        )
    }

    suspend fun saveRoster(
        userId: String,
        organizationId: String,
        rehearsalId: String,
        assignments: List<RehearsalAssignmentDto>,
        members: List<ServiceMemberOption>
    ) = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank() && rehearsalId.isNotBlank())
        require(assignments.all {
            it.organizationId == organizationId && it.rehearsalId == rehearsalId
        }) {
            "A rehearsal assignment belongs to a different rehearsal or organization."
        }

        val membersById = members.associateBy { it.memberId }
        val timestamp = System.currentTimeMillis()
        val cachedAssignments = assignments.map { assignment ->
            val member = membersById[assignment.memberId]
            CachedRehearsalAssignmentEntity().apply {
                id = assignment.id
                ownerUserId = userId
                this.organizationId = assignment.organizationId
                this.rehearsalId = assignment.rehearsalId
                memberId = assignment.memberId
                memberUserId = member?.userId.orEmpty()
                memberDisplayName = member?.displayName?.takeIf(String::isNotBlank) ?: "Team member"
                memberTeamRole = member?.teamRole?.takeIf(String::isNotBlank) ?: "team_member"
                responseStatus = assignment.responseStatus
                responseNote = assignment.responseNote
                assignedBy = assignment.assignedBy
                respondedAt = assignment.respondedAt
                createdAt = assignment.createdAt
                cachedAt = timestamp
            }
        }
        dao.replaceRoster(userId, organizationId, rehearsalId, cachedAssignments)
    }

    suspend fun loadRehearsals(
        userId: String,
        organizationId: String
    ): List<RehearsalDto> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        dao.loadRehearsals(userId, organizationId).map { cached ->
            RehearsalDto(
                id = cached.id,
                organizationId = cached.organizationId,
                title = cached.title,
                rehearsalDate = cached.rehearsalDate,
                startTime = cached.startTime,
                endTime = cached.endTime,
                location = cached.location,
                notes = cached.notes,
                status = cached.status,
                servicePlanId = cached.servicePlanId,
                createdBy = cached.createdBy,
                createdAt = cached.createdAt,
                updatedAt = cached.updatedAt
            )
        }
    }

    suspend fun loadRoster(
        userId: String,
        organizationId: String,
        rehearsalId: String
    ): OfflineRehearsalRosterSnapshot = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank() && rehearsalId.isNotBlank())
        val cached = dao.loadAssignments(userId, organizationId, rehearsalId)
        val assignments = cached.map { item ->
            RehearsalAssignmentDto(
                id = item.id,
                organizationId = item.organizationId,
                rehearsalId = item.rehearsalId,
                memberId = item.memberId,
                responseStatus = item.responseStatus,
                responseNote = item.responseNote,
                assignedBy = item.assignedBy,
                respondedAt = item.respondedAt,
                createdAt = item.createdAt
            )
        }
        val members = cached.distinctBy { it.memberId }.map { item ->
            ServiceMemberOption(
                memberId = item.memberId,
                userId = item.memberUserId,
                displayName = item.memberDisplayName,
                teamRole = item.memberTeamRole
            )
        }
        OfflineRehearsalRosterSnapshot(
            assignments = assignments,
            members = members,
            syncedAtMillis = cached.maxOfOrNull { it.cachedAt }
        )
    }

    suspend fun clearOrganization(userId: String, organizationId: String) =
        withContext(Dispatchers.IO) {
            if (userId.isNotBlank() && organizationId.isNotBlank()) {
                dao.clearOrganization(userId, organizationId)
            }
        }

    suspend fun clearUser(userId: String) = withContext(Dispatchers.IO) {
        if (userId.isNotBlank()) dao.clearUser(userId)
    }
}
