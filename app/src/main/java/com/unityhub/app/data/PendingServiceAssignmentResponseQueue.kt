package com.unityhub.app.data

import android.content.Context
import com.unityhub.app.data.cache.PendingServiceAssignmentResponseDao
import com.unityhub.app.data.cache.PendingServiceAssignmentResponseEntity
import com.unityhub.app.data.cache.UnityHubCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingServiceAssignmentResponse(
    val assignmentId: String,
    val ownerUserId: String,
    val organizationId: String,
    val memberId: String,
    val responseStatus: String,
    val responseNote: String = "",
    val queuedAt: Long
)

internal object PendingServiceAssignmentResponseRules {
    fun isValidResponse(response: String): Boolean =
        response == "confirmed" || response == "declined"

    fun maySync(
        authenticatedUserId: String?,
        requestedUserId: String,
        requestedOrganizationId: String,
        membershipOrganizationId: String?,
        membershipId: String?,
        pending: PendingServiceAssignmentResponse
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
            isValidResponse(pending.responseStatus) &&
            ServiceAssignmentRules.isValidResponseNote(pending.responseNote) &&
            (pending.responseStatus != "confirmed" || pending.responseNote.isBlank())
}

class PendingServiceAssignmentResponseQueue(context: Context) {
    private val dao: PendingServiceAssignmentResponseDao =
        UnityHubCacheDatabase.getInstance(context).pendingServiceAssignmentResponseDao()

    suspend fun enqueue(
        userId: String,
        organizationId: String,
        assignmentId: String,
        memberId: String,
        responseStatus: String,
        responseNote: String = ""
    ) = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        require(assignmentId.isNotBlank() && memberId.isNotBlank())
        require(PendingServiceAssignmentResponseRules.isValidResponse(responseStatus))
        val normalizedNote = if (responseStatus == "declined") {
            ServiceAssignmentRules.normalizeResponseNote(responseNote)
        } else {
            ""
        }

        dao.upsert(
            PendingServiceAssignmentResponseEntity().apply {
                ownerUserId = userId
                this.organizationId = organizationId
                this.assignmentId = assignmentId
                this.memberId = memberId
                this.responseStatus = responseStatus
                this.responseNote = normalizedNote
                queuedAt = System.currentTimeMillis()
            }
        )
    }

    suspend fun loadPending(
        userId: String,
        organizationId: String
    ): List<PendingServiceAssignmentResponse> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        dao.loadPending(userId, organizationId).map { item ->
            PendingServiceAssignmentResponse(
                assignmentId = item.assignmentId,
                ownerUserId = item.ownerUserId,
                organizationId = item.organizationId,
                memberId = item.memberId,
                responseStatus = item.responseStatus,
                responseNote = item.responseNote,
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
