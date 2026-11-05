package com.unityhub.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.OfflineSongCache
import com.unityhub.app.data.OfflineWorkspaceCache
import com.unityhub.app.data.OrganizationDto
import com.unityhub.app.data.OrganizationMemberDto
import com.unityhub.app.data.ServiceAssignmentDto
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServicePlanItemDto
import com.unityhub.app.data.ServicePlanRepository
import com.unityhub.app.data.SongRepository
import com.unityhub.app.data.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException

class OfflineSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val requestedUserId = inputData.getString(KEY_USER_ID).orEmpty()
        val requestedOrganizationId = inputData.getString(KEY_ORGANIZATION_ID).orEmpty()

        if (requestedUserId.isBlank() || requestedOrganizationId.isBlank()) return Result.failure()
        if (!SupabaseClientProvider.isConfigured) return Result.success()

        return try {
            setProgress(workDataOf(PROGRESS_STAGE_KEY to "checking_access"))
            val client = SupabaseClientProvider.client
            val initialUserId = client.auth.currentUserOrNull()?.id
            if (initialUserId != requestedUserId) return Result.success()

            val initialMembership = loadMembership(client, requestedUserId, requestedOrganizationId)
            if (!OfflineSyncScopeRules.maySync(
                    requestedUserId,
                    initialUserId,
                    requestedOrganizationId,
                    initialMembership?.organizationId
                )
            ) {
                OfflineSyncStatusStore.markFailure(
                    applicationContext,
                    requestedUserId,
                    requestedOrganizationId,
                    "This account no longer has access to the selected team. Refresh your workspace to sync again."
                )
                return Result.success()
            }

            OfflineSyncStatusStore.markStarted(applicationContext, requestedUserId, requestedOrganizationId)
            setProgress(workDataOf(PROGRESS_STAGE_KEY to "downloading_songs"))

            val songRepository = SongRepository(client)
            val servicePlanRepository = ServicePlanRepository(client)
            val assignmentRepository = ServiceAssignmentRepository(client)

            // Read the full remote snapshot before replacing any local cache.
            val songs = songRepository.loadSongs(requestedOrganizationId)
            setProgress(workDataOf(PROGRESS_STAGE_KEY to "downloading_services"))
            val plans = servicePlanRepository.loadPlans(requestedOrganizationId)
            val items = mutableListOf<ServicePlanItemDto>()
            val assignmentsByPlan = linkedMapOf<String, List<ServiceAssignmentDto>>()

            setProgress(workDataOf(PROGRESS_STAGE_KEY to "downloading_assignments"))
            for (plan in plans) {
                items += servicePlanRepository.loadPlanItems(plan.id, requestedOrganizationId)
                assignmentsByPlan[plan.id] = assignmentRepository.loadAssignments(
                    servicePlanId = plan.id,
                    organizationId = requestedOrganizationId
                )
            }

            val members = if (plans.isEmpty()) {
                emptyList()
            } else {
                assignmentRepository.loadTeamMembers(requestedOrganizationId)
            }

            val organizationName = loadOrganizationName(client, requestedOrganizationId)

            // Re-check both the live session and membership after network reads.
            // A sign-out or membership change must never write data to another account's cache.
            val finalUserId = client.auth.currentUserOrNull()?.id
            if (finalUserId != requestedUserId) return Result.success()

            val finalMembership = loadMembership(client, requestedUserId, requestedOrganizationId)
            if (!OfflineSyncScopeRules.maySync(
                    requestedUserId,
                    finalUserId,
                    requestedOrganizationId,
                    finalMembership?.organizationId
                )
            ) {
                OfflineSyncStatusStore.markFailure(
                    applicationContext,
                    requestedUserId,
                    requestedOrganizationId,
                    "Team access changed during sync. Refresh your workspace and try again."
                )
                return Result.success()
            }

            require(songs.all { it.organizationId == requestedOrganizationId }) {
                "A song belongs to a different organization."
            }

            setProgress(workDataOf(PROGRESS_STAGE_KEY to "saving_offline_copy"))
            OfflineSongCache(applicationContext).saveSongs(
                requestedUserId,
                requestedOrganizationId,
                songs
            )
            val serviceCache = OfflineServiceCache(applicationContext)
            serviceCache.saveServiceData(
                userId = requestedUserId,
                organizationId = requestedOrganizationId,
                plans = plans,
                items = items
            )

            for (plan in plans) {
                serviceCache.saveAssignments(
                    userId = requestedUserId,
                    organizationId = requestedOrganizationId,
                    servicePlanId = plan.id,
                    assignments = assignmentsByPlan[plan.id].orEmpty(),
                    members = members,
                    currentUserId = requestedUserId
                )
            }

            OfflineWorkspaceCache(applicationContext).save(
                userId = requestedUserId,
                organizationId = requestedOrganizationId,
                organizationName = organizationName
            )

            OfflineSyncStatusStore.markSuccess(
                applicationContext,
                requestedUserId,
                requestedOrganizationId
            )
            setProgress(workDataOf(PROGRESS_STAGE_KEY to "completed"))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            OfflineSyncStatusStore.markFailure(
                applicationContext,
                requestedUserId,
                requestedOrganizationId,
                "UnityHub couldn't refresh all offline data. It will retry when possible."
            )
            Result.retry()
        }
    }

    private suspend fun loadMembership(
        client: SupabaseClient,
        userId: String,
        organizationId: String
    ): OrganizationMemberDto? =
        client.from("organization_members").select {
            filter {
                eq("user_id", userId)
                eq("organization_id", organizationId)
            }
        }.decodeList<OrganizationMemberDto>().firstOrNull()

    private suspend fun loadOrganizationName(
        client: SupabaseClient,
        organizationId: String
    ): String =
        client.from("organizations").select {
            filter { eq("id", organizationId) }
        }.decodeList<OrganizationDto>().firstOrNull()?.name
            ?: error("The active organization is no longer available.")

    companion object {
        const val PROGRESS_STAGE_KEY = "progress_stage"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ORGANIZATION_ID = "organization_id"
    }
}
