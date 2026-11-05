package com.unityhub.app.data

import android.content.Context
import com.unityhub.app.data.cache.CachedServiceAssignmentDao
import com.unityhub.app.data.cache.CachedServiceAssignmentEntity
import com.unityhub.app.data.cache.CachedServicePlanDao
import com.unityhub.app.data.cache.CachedServicePlanEntity
import com.unityhub.app.data.cache.CachedServicePlanItemEntity
import com.unityhub.app.data.cache.UnityHubCacheDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OfflineServiceSnapshot(
    val plans: List<ServicePlanDto>,
    val syncedAtMillis: Long?
)

data class OfflineServiceAssignmentEntry(
    val assignment: ServiceAssignmentDto,
    val isCurrentUser: Boolean,
    val memberDisplayName: String
)

data class OfflineServiceAssignmentSnapshot(
    val assignments: List<OfflineServiceAssignmentEntry>,
    val syncedAtMillis: Long?
)

internal object OfflineServiceCacheRules {
    fun orderedItems(items: List<ServicePlanItemDto>): List<ServicePlanItemDto> =
        items.sortedWith(compareBy<ServicePlanItemDto> { it.itemOrder }.thenBy { it.createdAt })

    fun orderedAssignments(
        assignments: List<OfflineServiceAssignmentEntry>
    ): List<OfflineServiceAssignmentEntry> =
        assignments.sortedWith(
            compareBy<OfflineServiceAssignmentEntry> { it.assignment.roleLabel.lowercase() }
                .thenBy { it.assignment.createdAt }
        )
}

class OfflineServiceCache(context: Context) {
    private val database = UnityHubCacheDatabase.getInstance(context)
    private val dao: CachedServicePlanDao = database.cachedServicePlanDao()
    private val assignmentDao: CachedServiceAssignmentDao = database.cachedServiceAssignmentDao()
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    suspend fun saveServiceData(
        userId: String,
        organizationId: String,
        plans: List<ServicePlanDto>,
        items: List<ServicePlanItemDto>
    ): Long = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        require(plans.all { it.organizationId == organizationId }) {
            "A service plan belongs to a different organization."
        }
        require(items.all { item ->
            item.organizationId == organizationId && plans.any { plan -> plan.id == item.servicePlanId }
        }) {
            "A service item does not belong to this organization's cached plans."
        }
        val timestamp = System.currentTimeMillis()
        val cachedPlans = plans.map { it.toCachedEntity(userId, timestamp) }
        val cachedItems = items.map { it.toCachedEntity(userId, timestamp) }
        dao.replaceOrganization(userId, organizationId, cachedPlans, cachedItems)
        preferences.edit().putLong(cacheKey(userId, organizationId), timestamp).apply()
        timestamp
    }

    suspend fun saveServicePlan(
        userId: String,
        organizationId: String,
        plan: ServicePlanDto,
        items: List<ServicePlanItemDto>
    ) = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank())
        require(plan.organizationId == organizationId)
        require(items.all { it.organizationId == organizationId && it.servicePlanId == plan.id }) {
            "A setlist item does not belong to this service plan."
        }
        val timestamp = System.currentTimeMillis()
        dao.replacePlanItems(
            plan.toCachedEntity(userId, timestamp),
            items.map { it.toCachedEntity(userId, timestamp) }
        )
        preferences.edit().putLong(cacheKey(userId, organizationId), timestamp).apply()
    }

    suspend fun saveAssignments(
        userId: String,
        organizationId: String,
        servicePlanId: String,
        assignments: List<ServiceAssignmentDto>,
        members: List<ServiceMemberOption>,
        currentUserId: String
    ): Long = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank() && servicePlanId.isNotBlank())
        require(assignments.all {
            it.organizationId == organizationId && it.servicePlanId == servicePlanId
        }) {
            "An assignment does not belong to this organization's service plan."
        }

        val timestamp = System.currentTimeMillis()
        val cachedAssignments = assignments.map { assignment ->
            val member = members.firstOrNull { it.memberId == assignment.memberId }
            CachedServiceAssignmentEntity().apply {
                id = assignment.id
                ownerUserId = userId
                this.organizationId = assignment.organizationId
                this.servicePlanId = assignment.servicePlanId
                memberId = assignment.memberId
                memberIsCurrentUser = member?.userId == currentUserId
                memberDisplayName = member?.displayName?.takeIf(String::isNotBlank) ?: "Team member"
                roleLabel = assignment.roleLabel
                responseStatus = assignment.responseStatus
                respondedAt = assignment.respondedAt
                createdAt = assignment.createdAt
                cachedAt = timestamp
            }
        }
        assignmentDao.replacePlanAssignments(
            userId, organizationId, servicePlanId, cachedAssignments
        )
        preferences.edit()
            .putLong(assignmentCacheKey(userId, organizationId, servicePlanId), timestamp)
            .apply()
        timestamp
    }

    suspend fun loadAssignments(
        userId: String,
        organizationId: String,
        servicePlanId: String
    ): OfflineServiceAssignmentSnapshot = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank() && servicePlanId.isNotBlank())
        val assignments = assignmentDao.loadAssignments(userId, organizationId, servicePlanId)
            .map { cached ->
                OfflineServiceAssignmentEntry(
                    assignment = ServiceAssignmentDto(
                        id = cached.id,
                        organizationId = cached.organizationId,
                        servicePlanId = cached.servicePlanId,
                        memberId = cached.memberId,
                        roleLabel = cached.roleLabel,
                        responseStatus = cached.responseStatus,
                        responseNote = "",
                        assignedBy = "",
                        respondedAt = cached.respondedAt,
                        createdAt = cached.createdAt
                    ),
                    isCurrentUser = cached.memberIsCurrentUser,
                    memberDisplayName = cached.memberDisplayName
                )
            }
        val syncedAt = preferences.getLong(
            assignmentCacheKey(userId, organizationId, servicePlanId), 0L
        ).takeIf { it > 0L }
        OfflineServiceAssignmentSnapshot(
            assignments = OfflineServiceCacheRules.orderedAssignments(assignments),
            syncedAtMillis = syncedAt
        )
    }

    suspend fun loadServicePlans(userId: String, organizationId: String): OfflineServiceSnapshot =
        withContext(Dispatchers.IO) {
            require(userId.isNotBlank() && organizationId.isNotBlank())
            val plans = dao.loadPlans(userId, organizationId).map { it.toDto() }
            val syncedAt = preferences.getLong(cacheKey(userId, organizationId), 0L)
                .takeIf { it > 0L }
            OfflineServiceSnapshot(plans = plans, syncedAtMillis = syncedAt)
        }

    suspend fun loadPlanItems(
        userId: String,
        organizationId: String,
        servicePlanId: String
    ): List<ServicePlanItemDto> = withContext(Dispatchers.IO) {
        require(userId.isNotBlank() && organizationId.isNotBlank() && servicePlanId.isNotBlank())
        OfflineServiceCacheRules.orderedItems(
            dao.loadItems(userId, organizationId, servicePlanId).map { it.toDto() }
        )
    }

    suspend fun clearUser(userId: String) {
        if (userId.isBlank()) return
        withContext(Dispatchers.IO) {
            dao.clearUser(userId)
            assignmentDao.clearUser(userId)
            val editor = preferences.edit()
            preferences.all.keys
                .filter { it.startsWith("$userId:") }
                .forEach { key -> editor.remove(key) }
            editor.apply()
        }
    }

    private fun cacheKey(userId: String, organizationId: String) = "$userId:$organizationId"

    private fun assignmentCacheKey(userId: String, organizationId: String, servicePlanId: String) =
        "$userId:$organizationId:assignments:$servicePlanId"

    private fun ServicePlanDto.toCachedEntity(userId: String, timestamp: Long) =
        CachedServicePlanEntity().apply {
            id = this@toCachedEntity.id
            ownerUserId = userId
            organizationId = this@toCachedEntity.organizationId
            title = this@toCachedEntity.title
            serviceDate = this@toCachedEntity.serviceDate
            startTime = this@toCachedEntity.startTime
            notes = this@toCachedEntity.notes
            status = this@toCachedEntity.status
            createdBy = this@toCachedEntity.createdBy
            createdAt = this@toCachedEntity.createdAt
            updatedAt = this@toCachedEntity.updatedAt
            cachedAt = timestamp
        }

    private fun ServicePlanItemDto.toCachedEntity(userId: String, timestamp: Long) =
        CachedServicePlanItemEntity().apply {
            id = this@toCachedEntity.id
            ownerUserId = userId
            organizationId = this@toCachedEntity.organizationId
            servicePlanId = this@toCachedEntity.servicePlanId
            songId = this@toCachedEntity.songId
            title = this@toCachedEntity.title
            itemType = this@toCachedEntity.itemType
            chordproSnapshot = this@toCachedEntity.chordproSnapshot
            itemOrder = this@toCachedEntity.itemOrder
            durationMinutes = this@toCachedEntity.durationMinutes
            notes = this@toCachedEntity.notes
            createdAt = this@toCachedEntity.createdAt
            cachedAt = timestamp
        }

    private fun CachedServicePlanEntity.toDto() = ServicePlanDto(
        id = id,
        organizationId = organizationId,
        title = title,
        serviceDate = serviceDate,
        startTime = startTime,
        notes = notes,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun CachedServicePlanItemEntity.toDto() = ServicePlanItemDto(
        id = id,
        organizationId = organizationId,
        servicePlanId = servicePlanId,
        songId = songId,
        title = title,
        itemType = itemType,
        chordproSnapshot = chordproSnapshot,
        itemOrder = itemOrder,
        durationMinutes = durationMinutes,
        notes = notes,
        createdAt = createdAt
    )

    private companion object {
        const val PREFERENCES_NAME = "unityhub_offline_service_sync"
    }
}
