package com.unityhub.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.OfflineRehearsalCache
import com.unityhub.app.data.PendingServiceAssignmentResponseQueue
import com.unityhub.app.data.PendingServiceAssignmentResponseRules
import com.unityhub.app.data.PendingRehearsalAvailabilityResponseQueue
import com.unityhub.app.data.PendingRehearsalAvailabilityResponseRules
import com.unityhub.app.data.RehearsalRepository
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
                PendingServiceAssignmentResponseQueue(applicationContext)
                    .clearOrganization(requestedUserId, requestedOrganizationId)
                PendingRehearsalAvailabilityResponseQueue(applicationContext)
                    .clearOrganization(requestedUserId, requestedOrganizationId)
                OfflineRehearsalCache(applicationContext)
                    .clearOrganization(requestedUserId, requestedOrganizationId)
                OfflineSyncStatusStore.markFailure(
                    applicationContext,
                    requestedUserId,
                    requestedOrganizationId,
                    "This account no longer has access to the selected team. Saved offline responses for this team were discarded."
                )
                return Result.success()
            }

            OfflineSyncStatusStore.markStarted(applicationContext, requestedUserId, requestedOrganizationId)
            setProgress(workDataOf(PROGRESS_STAGE_KEY to "downloading_songs"))

            val songRepository = SongRepository(client)
            val servicePlanRepository = ServicePlanRepository(client)
            val assignmentRepository = ServiceAssignmentRepository(client)
            val responseQueue = PendingServiceAssignmentResponseQueue(applicationContext)
            val rehearsalRepository = RehearsalRepository(client)
            val rehearsalResponseQueue = PendingRehearsalAvailabilityResponseQueue(applicationContext)
            var discardedPendingResponses = 0

            // Send member responses saved offline before refreshing the cached snapshot.
            // Each row is checked against the live session, organization, and membership ID.
            val queuedResponses = responseQueue.loadPending(requestedUserId, requestedOrganizationId)
            for (pending in queuedResponses) {
                val liveUserId = client.auth.currentUserOrNull()?.id
                if (liveUserId != requestedUserId) return Result.success()

                if (!PendingServiceAssignmentResponseRules.maySync(
                        authenticatedUserId = liveUserId,
                        requestedUserId = requestedUserId,
                        requestedOrganizationId = requestedOrganizationId,
                        membershipOrganizationId = initialMembership?.organizationId,
                        membershipId = initialMembership?.id,
                        pending = pending
                    )
                ) {
                    responseQueue.remove(requestedUserId, pending.assignmentId)
                    discardedPendingResponses += 1
                    continue
                }

                setProgress(workDataOf(PROGRESS_STAGE_KEY to "uploading_responses"))
                val submitted = assignmentRepository.respond(
                    assignmentId = pending.assignmentId,
                    organizationId = pending.organizationId,
                    memberId = pending.memberId,
                    response = pending.responseStatus,
                    responseNote = pending.responseNote
                )
                if (submitted) {
                    responseQueue.remove(requestedUserId, pending.assignmentId)
                } else {
                    // A zero-row update means the assignment no longer belongs to this
                    // membership or was removed. Do not retry an invalid stale response.
                    responseQueue.remove(requestedUserId, pending.assignmentId)
                    discardedPendingResponses += 1
                }
            }

            // Rehearsal availability uses the same authenticated membership scope.
            // The database RLS policy additionally requires the rehearsal to remain scheduled.
            val queuedRehearsalResponses = rehearsalResponseQueue.loadPending(
                requestedUserId,
                requestedOrganizationId
            )
            for (pending in queuedRehearsalResponses) {
                val liveUserId = client.auth.currentUserOrNull()?.id
                if (liveUserId != requestedUserId) return Result.success()

                if (!PendingRehearsalAvailabilityResponseRules.maySync(
                        authenticatedUserId = liveUserId,
                        requestedUserId = requestedUserId,
                        requestedOrganizationId = requestedOrganizationId,
                        membershipOrganizationId = initialMembership?.organizationId,
                        membershipId = initialMembership?.id,
                        pending = pending
                    )
                ) {
                    rehearsalResponseQueue.remove(requestedUserId, pending.assignmentId)
                    discardedPendingResponses += 1
                    continue
                }

                setProgress(workDataOf(PROGRESS_STAGE_KEY to "uploading_rehearsal_responses"))
                val submitted = rehearsalRepository.respond(
                    assignmentId = pending.assignmentId,
                    rehearsalId = pending.rehearsalId,
                    organizationId = pending.organizationId,
                    memberId = pending.memberId,
                    response = pending.responseStatus
                )
                // A zero-row update is stale or no longer permitted (for example, the
                // rehearsal was completed/cancelled). Never replay that response later.
                rehearsalResponseQueue.remove(requestedUserId, pending.assignmentId)
                if (!submitted) discardedPendingResponses += 1
            }

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

            setProgress(workDataOf(PROGRESS_STAGE_KEY to "downloading_rehearsals"))
            val rehearsals = rehearsalRepository.loadRehearsals(requestedOrganizationId)
            val rehearsalMembers = if (rehearsals.isEmpty()) {
                emptyList()
            } else if (members.isNotEmpty()) {
                members
            } else {
                rehearsalRepository.loadTeamMembers(requestedOrganizationId)
            }
            val rehearsalAssignments = linkedMapOf<String, List<com.unityhub.app.data.RehearsalAssignmentDto>>()
            for (rehearsal in rehearsals) {
                rehearsalAssignments[rehearsal.id] = rehearsalRepository.loadAssignments(
                    rehearsalId = rehearsal.id,
                    organizationId = requestedOrganizationId
                )
            }

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
            require(rehearsals.all { it.organizationId == requestedOrganizationId }) {
                "A rehearsal belongs to a different organization."
            }

            // Keep device-local reminders aligned with schedule changes made elsewhere.
            // Only the current member's own reminders are touched; no server writes occur.
            val rehearsalCache = OfflineRehearsalCache(applicationContext)
            val previousRehearsals = runCatching {
                rehearsalCache.loadRehearsals(requestedUserId, requestedOrganizationId)
            }.getOrDefault(emptyList())
            val currentRehearsalIds = rehearsals.mapTo(mutableSetOf()) { it.id }
            previousRehearsals
                .filter { it.id !in currentRehearsalIds }
                .forEach { removed ->
                    RehearsalReminderScheduler.cancel(
                        applicationContext,
                        requestedUserId,
                        removed.id
                    )
                }

            for (rehearsal in rehearsals) {
                val reminderMinutes = RehearsalReminderScheduler.getMinutesBefore(
                    applicationContext,
                    requestedUserId,
                    rehearsal.id
                ) ?: continue
                if (rehearsal.status == "scheduled") {
                    val rescheduled = runCatching {
                        RehearsalReminderScheduler.schedule(
                            context = applicationContext,
                            userId = requestedUserId,
                            organizationId = requestedOrganizationId,
                            rehearsal = rehearsal,
                            minutesBefore = reminderMinutes
                        )
                    }.getOrDefault(false)
                    if (!rescheduled) {
                        RehearsalReminderScheduler.cancel(
                            applicationContext,
                            requestedUserId,
                            rehearsal.id
                        )
                    }
                } else {
                    RehearsalReminderScheduler.cancel(
                        applicationContext,
                        requestedUserId,
                        rehearsal.id
                    )
                }
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

            val rehearsalCache = OfflineRehearsalCache(applicationContext)
            rehearsalCache.saveRehearsals(
                userId = requestedUserId,
                organizationId = requestedOrganizationId,
                rehearsals = rehearsals
            )
            for (rehearsal in rehearsals) {
                rehearsalCache.saveRoster(
                    userId = requestedUserId,
                    organizationId = requestedOrganizationId,
                    rehearsalId = rehearsal.id,
                    assignments = rehearsalAssignments[rehearsal.id].orEmpty(),
                    members = rehearsalMembers
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
            if (discardedPendingResponses > 0) {
                OfflineSyncStatusStore.markFailure(
                    applicationContext,
                    requestedUserId,
                    requestedOrganizationId,
                    "$discardedPendingResponses saved response(s) could not be sent because an assignment, rehearsal, or team access changed. Refresh your service and rehearsal details."
                )
            }
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
