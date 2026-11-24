package com.unityhub.app.data

import android.content.Context
import com.unityhub.app.data.cache.PendingRehearsalAvailabilityResponseDao
import com.unityhub.app.data.cache.PendingRehearsalAvailabilityResponseEntity
import com.unityhub.app.data.cache.UnityHubCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingRehearsalAvailabilityResponse(
    val assignmentId: String,
    val ownerUserId: String,
    val organizationId: String,
    val rehearsalId: String,
    val memberId: String,
    val responseStatus: String,
    val queuedAt: Long
)

internal object PendingRehearsalAvailabilityResponseRules {
    fun isValidResponse(response: String): Boolean =
        RehearsalRules.isAvailabilityResponse(response)

    fun maySync(
        authenticatedUserId: String?,
        requestedUserId: String,
        requestedOrganizationId: String,
        membershipOrganizationId: String?,
        membershipId: String?,
        pending: PendingRehearsalAvailabilityResponse
    ): Boolean =
        authenticatedUserId == requestedUserId &&
            requestedUserId.isNotBlank() &&
            requestedOrganizationId.isNotBlank() &&
            membershipOrganizationId == requestedOrganizationId &&
            membershipId?.isNotBlank() == true &&
            pending.ownerUserId == requestedUserId &&
            pending.organizationId == requestedOrganizationId &&
            pending.memberId == membershipId &&
            pending.assignmentId.isNotBlank() &&
            pending.rehearsalId.isNotBlank() &&
            isValidResponse(pending.responseStatus)
}

class PendingRehearsalAvailabilityResponseQueue(context: Context) {
    private val dao: PendingRehearsalAvailabilityResponseDao =
        UnityHubCacheDatabase.getInstance(context).pendingRehearsalAvailabilityResponseDao()

    suspend fun enqueue(
        userId: String,
        organizationId: String,
        rehearsalId: String,
        assignmentId: String,
        memberId: String,
        responseStatus: String
    ) = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        require(rehearsalId.isNotBlank() && assignmentId.isNotBlank() && memberId.isNotBlank())
        require(PendingRehearsalAvailabilityResponseRules.isValidResponse(responseStatus))

        dao.upsert(
            PendingRehearsalAvailabilityResponseEntity().apply {
                ownerUserId = userId
                this.organizationId = organizationId
                this.rehearsalId = rehearsalId
                this.assignmentId = assignmentId
                this.memberId = memberId
                this.responseStatus = responseStatus
                queuedAt = System.currentTimeMillis()
            }
        )
    }

    suspend fun loadPending(
        userId: String,
        organizationId: String
    ): List<PendingRehearsalAvailabilityResponse> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        dao.loadPending(userId, organizationId).map { item ->
            PendingRehearsalAvailabilityResponse(
                assignmentId = item.assignmentId,
                ownerUserId = item.ownerUserId,
                organizationId = item.organizationId,
                rehearsalId = item.rehearsalId,
                memberId = item.memberId,
                responseStatus = item.responseStatus,
                queuedAt = item.queuedAt
            )
        }
    }

    suspend fun remove(userId: String, assignmentId: String) = withContext(Dispatchers.IO) {
        if (userId.isNotBlank() && assignmentId.isNotBlank()) {
            dao.remove(userId, assignmentId)
        }
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
