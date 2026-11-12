package com.unityhub.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkInfo
import com.unityhub.app.data.MyServiceAssignment
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.PendingServiceAssignmentResponseQueue
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceAssignmentRules
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.ServicePlanRepository
import com.unityhub.app.data.RehearsalDto
import com.unityhub.app.data.RehearsalRepository
import com.unityhub.app.data.RehearsalRules
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.sync.OfflineSyncScheduler
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    organizationId: String,
    organizationName: String,
    userId: String,
    role: String?,
    memberCount: Int,
    pendingCount: Int,
    onOpenSongs: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenServicePlan: (String) -> Unit,
    onOpenTeam: () -> Unit,
    onOpenRehearsal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { ServicePlanRepository(SupabaseClientProvider.client) }
    val assignmentRepository = remember { ServiceAssignmentRepository(SupabaseClientProvider.client) }
    val rehearsalRepository = remember { RehearsalRepository(SupabaseClientProvider.client) }
    val context = LocalContext.current
    val offlineServiceCache = remember(context) { OfflineServiceCache(context) }
    val responseQueue = remember(context) { PendingServiceAssignmentResponseQueue(context) }
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<ServicePlanDto>>(emptyList()) }
    var rehearsals by remember { mutableStateOf<List<RehearsalDto>>(emptyList()) }
    var rehearsalsLoading by remember { mutableStateOf(true) }
    var rehearsalErrorMessage by remember { mutableStateOf("") }
    var myAssignments by remember { mutableStateOf<List<MyServiceAssignment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var assignmentsLoading by remember { mutableStateOf(true) }
    var respondingAssignmentId by remember { mutableStateOf<String?>(null) }
    var decliningAssignment by remember { mutableStateOf<MyServiceAssignment?>(null) }
    var pendingResponseIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var offlineDataMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var assignmentErrorMessage by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            assignmentsLoading = true
            errorMessage = ""
            assignmentErrorMessage = ""
            offlineDataMessage = ""
            try {
                plans = repository.loadPlans(organizationId)
            } catch (_: Exception) {
                val cached = runCatching {
                    offlineServiceCache.loadServicePlans(userId, organizationId)
                }.getOrNull()
                plans = cached?.plans.orEmpty()
                if (plans.isEmpty()) {
                    errorMessage = "Upcoming services could not be loaded and no saved copy is available."
                } else {
                    offlineDataMessage = "Showing saved service information. Updates will sync when your internet connection returns."
                }
            } finally {
                loading = false
            }

            try {
                rehearsalsLoading = true
                rehearsals = rehearsalRepository.loadRehearsals(organizationId)
                rehearsalErrorMessage = ""
            } catch (_: Exception) {
                rehearsalErrorMessage = "Upcoming rehearsals could not be loaded."
            } finally {
                rehearsalsLoading = false
            }

            try {
                myAssignments = assignmentRepository.loadMyAssignments(organizationId, userId)
                assignmentErrorMessage = ""
            } catch (_: Exception) {
                val cached = runCatching {
                    offlineServiceCache.loadServicePlans(userId, organizationId)
                }.getOrNull()
                val cachedAssignments = mutableListOf<MyServiceAssignment>()
                cached?.plans.orEmpty().forEach { plan ->
                    runCatching {
                        offlineServiceCache.loadAssignments(userId, organizationId, plan.id)
                    }.getOrNull()?.assignments.orEmpty()
                        .filter { it.isCurrentUser }
                        .forEach { entry ->
                            cachedAssignments += MyServiceAssignment(
                                assignment = entry.assignment,
                                servicePlan = plan
                            )
                        }
                }
                myAssignments = cachedAssignments
                if (cachedAssignments.isEmpty()) {
                    assignmentErrorMessage = "Your assignments could not be loaded. Check your connection and sync your offline data."
                } else if (offlineDataMessage.isBlank()) {
                    offlineDataMessage = "Showing your last saved assignments. Responses can be saved here and sent when you're online."
                }
            } finally {
                assignmentsLoading = false
            }

            val pending = runCatching {
                responseQueue.loadPending(userId, organizationId)
            }.getOrDefault(emptyList())
            val pendingByAssignment = pending.associateBy { it.assignmentId }
            pendingResponseIds = pendingByAssignment.keys
            myAssignments = myAssignments.map { item ->
                val savedResponse = pendingByAssignment[item.assignment.id] ?: return@map item
                item.copy(
                    assignment = item.assignment.copy(
                        responseStatus = savedResponse.responseStatus,
                        responseNote = savedResponse.responseNote,
                        respondedAt = Instant.ofEpochMilli(savedResponse.queuedAt).toString()
                    )
                )
            }
        }
    }

    fun applyLocalResponse(
        item: MyServiceAssignment,
        response: String,
        responseNote: String,
        queued: Boolean
    ) {
        val respondedAt = Instant.now().toString()
        myAssignments = myAssignments.map { current ->
            if (current.assignment.id == item.assignment.id) {
                current.copy(
                    assignment = current.assignment.copy(
                        responseStatus = response,
                        responseNote = if (response == "declined") responseNote.trim() else "",
                        respondedAt = respondedAt
                    )
                )
            } else current
        }
        pendingResponseIds = if (queued) {
            pendingResponseIds + item.assignment.id
        } else {
            pendingResponseIds - item.assignment.id
        }
    }

    fun respondToAssignment(
        item: MyServiceAssignment,
        response: String,
        responseNote: String = ""
    ) {
        scope.launch {
            respondingAssignmentId = item.assignment.id
            assignmentErrorMessage = ""
            try {
                if (!hasValidatedNetwork(context)) {
                    responseQueue.enqueue(
                        userId = userId,
                        organizationId = organizationId,
                        assignmentId = item.assignment.id,
                        memberId = item.assignment.memberId,
                        responseStatus = response,
                        responseNote = responseNote
                    )
                    applyLocalResponse(item, response, responseNote, queued = true)
                    OfflineSyncScheduler.syncNow(context, userId, organizationId)
                    return@launch
                }

                val applied = assignmentRepository.respond(
                    assignmentId = item.assignment.id,
                    organizationId = organizationId,
                    memberId = item.assignment.memberId,
                    response = response,
                    responseNote = responseNote
                )
                if (!applied) {
                    assignmentErrorMessage = "This assignment is no longer available to your team membership. Refresh the dashboard and try again."
                } else {
                    responseQueue.remove(userId, item.assignment.id)
                    applyLocalResponse(item, response, responseNote, queued = false)
                }
            } catch (exception: Exception) {
                if (!hasValidatedNetwork(context) || isNetworkFailure(exception)) {
                    runCatching {
                        responseQueue.enqueue(
                            userId = userId,
                            organizationId = organizationId,
                            assignmentId = item.assignment.id,
                            memberId = item.assignment.memberId,
                            responseStatus = response,
                            responseNote = responseNote
                        )
                    }.onSuccess {
                        applyLocalResponse(item, response, responseNote, queued = true)
                        OfflineSyncScheduler.syncNow(context, userId, organizationId)
                    }.onFailure {
                        assignmentErrorMessage = "Your response could not be saved on this device. Please try again."
                    }
                } else {
                    assignmentErrorMessage = "Your response could not be saved. Check your team access and try again."
                }
            } finally {
                respondingAssignmentId = null
            }
        }
    }

    LaunchedEffect(organizationId, userId) {
        refresh()
    }

    // A completed response-upload worker can update Supabase while the dashboard
    // remains open. Refresh once for each completed immediate sync so the local
    // "waiting to sync" label is replaced by the server-confirmed response.
    LaunchedEffect(context, userId, organizationId) {
        val handledWorkIds = mutableSetOf<String>()
        OfflineSyncScheduler.observeWork(context, userId).collect { workInfos ->
            workInfos
                .filter { info ->
                    info.state == WorkInfo.State.SUCCEEDED &&
                        OfflineSyncScheduler.immediateTagFor(userId) in info.tags
                }
                .forEach { info ->
                    if (handledWorkIds.add(info.id.toString())) refresh()
                }
        }
    }

    val today = LocalDate.now()
    val allUpcomingPlans = remember(plans, today) {
        HomeDashboardRules.upcomingPlans(plans, today)
    }
    val upcomingPlans = allUpcomingPlans.take(4)
    val nextRehearsal = remember(rehearsals, today) {
        rehearsals.filter { it.status == "scheduled" && it.rehearsalDate >= today.toString() }
            .minWithOrNull(compareBy<RehearsalDto> { it.rehearsalDate }.thenBy { it.startTime })
    }
    val allUpcomingAssignments = remember(myAssignments, today) {
        HomeDashboardRules.upcomingAssignments(myAssignments, today)
    }
    val upcomingAssignments = allUpcomingAssignments.take(3)
    val isAdmin = role == "admin"
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val dateLabel = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "YOUR WORSHIP TEAM",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.76f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            organizationName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${dashboardRoleLabel(role)} workspace",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }

        item {
            Text("Team at a glance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                DashboardMetricCard(
                    label = "Members",
                    value = memberCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                DashboardMetricCard(
                    label = "Upcoming",
                    value = if (loading) "—" else allUpcomingPlans.size.toString(),
                    modifier = Modifier.weight(1f)
                )
                if (isAdmin) {
                    DashboardMetricCard(
                        label = "Requests",
                        value = pendingCount.toString(),
                        modifier = Modifier.weight(1f),
                        emphasize = pendingCount > 0
                    )
                }
            }
        }

        item {
            Text("Quick access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                DashboardQuickAction(
                    label = "Songs",
                    icon = Icons.Default.LibraryMusic,
                    onClick = onOpenSongs,
                    modifier = Modifier.weight(1f)
                )
                DashboardQuickAction(
                    label = "Services",
                    icon = Icons.Default.CalendarMonth,
                    onClick = onOpenServices,
                    modifier = Modifier.weight(1f)
                )
                DashboardQuickAction(
                    label = "Team",
                    icon = Icons.Default.Groups,
                    onClick = onOpenTeam,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OfflineSyncStatusCard(
                userId = userId,
                organizationId = organizationId
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Next rehearsal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Practice together and prepare for the next service",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { nextRehearsal?.let { onOpenRehearsal(it.id) } }, enabled = nextRehearsal != null) {
                        Text("Open")
                    }
                }
                when {
                    rehearsalsLoading -> Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading your rehearsal schedule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    nextRehearsal != null -> Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenRehearsal(nextRehearsal.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(nextRehearsal.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                RehearsalRules.formatDate(nextRehearsal.rehearsalDate) + " · " +
                                    RehearsalRules.formatTime(nextRehearsal.startTime),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (nextRehearsal.location.isNotBlank()) {
                                Text(nextRehearsal.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    rehearsalErrorMessage.isNotBlank() -> Text(rehearsalErrorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else -> Card(shape = RoundedCornerShape(14.dp)) {
                        Text(
                            "No upcoming rehearsals. Your worship leader can schedule one from Services → Rehearsals.",
                            modifier = Modifier.fillMaxWidth().padding(15.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (offlineDataMessage.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        offlineDataMessage,
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (pendingResponseIds.isNotEmpty()) {
            item {
                Text(
                    "${pendingResponseIds.size} service response(s) saved on this device and waiting to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("My upcoming assignments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "The ministry roles your team has assigned to you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (assignmentsLoading && myAssignments.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Loading your assignments", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (assignmentErrorMessage.isNotBlank() && myAssignments.isEmpty()) {
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(assignmentErrorMessage, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { refresh() }, enabled = !assignmentsLoading) {
                                Text("Try again")
                            }
                        }
                    }
                } else if (upcomingAssignments.isEmpty()) {
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text("No upcoming roles assigned to you", fontWeight = FontWeight.SemiBold)
                            Text(
                                "When a worship leader assigns you to a service, your role and response status will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    upcomingAssignments.forEach { assignment ->
                        MyUpcomingAssignmentCard(
                            item = assignment,
                            today = today,
                            responding = respondingAssignmentId == assignment.assignment.id,
                            pendingSync = assignment.assignment.id in pendingResponseIds,
                            onOpen = { onOpenServicePlan(assignment.servicePlan.id) },
                            onConfirm = { respondToAssignment(assignment, "confirmed") },
                            onDecline = { decliningAssignment = assignment }
                        )
                    }
                    if (allUpcomingAssignments.size > upcomingAssignments.size) {
                        TextButton(onClick = onOpenServices) {
                            Text("View all ${allUpcomingAssignments.size} upcoming roles")
                        }
                    }
                }

                if (assignmentErrorMessage.isNotBlank() && myAssignments.isNotEmpty()) {
                    Text(
                        assignmentErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Upcoming services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your next worship gatherings and setlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenServices) { Text("View all") }
            }
        }

        if (loading && plans.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Loading your service schedule", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else if (errorMessage.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Text("Try again")
                        }
                    }
                }
            }
        } else if (upcomingPlans.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("No upcoming services yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "When your team schedules a gathering, it will appear here with its date and setlist.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onOpenServices) { Text("Open service planning") }
                    }
                }
            }
        } else {
            items(upcomingPlans, key = { it.id }) { plan ->
                UpcomingServiceCard(
                    plan = plan,
                    today = today,
                    onClick = { onOpenServicePlan(plan.id) }
                )
            }
            item {
                TextButton(onClick = onOpenServices) { Text("Open full service schedule") }
            }
        }

        item {
            TextButton(
                onClick = { refresh() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Refresh dashboard")
            }
        }
    }

    decliningAssignment?.let { item ->
        DeclineServiceAssignmentDialog(
            assignment = item.assignment,
            saving = respondingAssignmentId == item.assignment.id,
            onDismiss = {
                if (respondingAssignmentId != item.assignment.id) decliningAssignment = null
            },
            onSubmit = { note ->
                decliningAssignment = null
                respondToAssignment(item, "declined", note)
            }
        )
    }
}

@Composable
private fun MyUpcomingAssignmentCard(
    item: MyServiceAssignment,
    today: LocalDate,
    responding: Boolean,
    pendingSync: Boolean,
    onOpen: () -> Unit,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    val status = item.assignment.responseStatus
    val canRespond = ServiceAssignmentRules.canRespondToService(item.servicePlan.status)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    item.assignment.roleLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (pendingSync) "Saved · waiting to sync" else when (status) {
                        "confirmed" -> "Confirmed"
                        "declined" -> "Unable to serve"
                        else -> "Awaiting response"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (pendingSync) MaterialTheme.colorScheme.secondary else when (status) {
                        "confirmed" -> MaterialTheme.colorScheme.primary
                        "declined" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(item.servicePlan.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                dashboardDateTimeLabel(item.servicePlan.serviceDate, item.servicePlan.startTime, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status == "declined" && item.assignment.responseNote.isNotBlank()) {
                Text(
                    if (pendingSync) "Note saved · waiting to sync" else "Note sent to worship leader",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(item.assignment.responseNote, style = MaterialTheme.typography.bodySmall)
            }
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirm,
                        enabled = !responding && status != "confirmed"
                    ) {
                        if (responding) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (status == "confirmed") "Confirmed" else "Confirm")
                        }
                    }
                    TextButton(
                        onClick = onDecline,
                        enabled = !responding
                    ) {
                        Text(if (status == "declined") "Edit note" else "Can't serve")
                    }
                }
            } else {
                Text(
                    if (item.servicePlan.status == "completed") "Service completed"
                    else "Service cancelled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasize) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (emphasize) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UpcomingServiceCard(
    plan: ServicePlanDto,
    today: LocalDate,
    onClick: () -> Unit
) {
    val statusLabel = when (plan.status) {
        "published" -> "Published"
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        else -> "Draft"
    }
    val statusColor = when (plan.status) {
        "published" -> MaterialTheme.colorScheme.primary
        "cancelled" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    dashboardDateTimeLabel(plan.serviceDate, plan.startTime, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Open ${plan.title}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun dashboardDateTimeLabel(dateValue: String?, timeValue: String?, today: LocalDate): String {
    val date = dateValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return "Date to be confirmed"
    val dateLabel = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }
    val timeLabel = timeValue?.let {
        runCatching {
            LocalTime.parse(it).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        }.getOrNull()
    }
    return if (timeLabel.isNullOrBlank()) dateLabel else "$dateLabel · $timeLabel"
}


private fun hasValidatedNetwork(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun isNetworkFailure(exception: Throwable): Boolean =
    generateSequence(exception) { it.cause }
        .take(8)
        .any {
        it is IOException || it.javaClass.simpleName in setOf(
            "HttpRequestTimeoutException",
            "ConnectTimeoutException"
        )
    }

private fun dashboardRoleLabel(role: String?): String = when (role) {
    "admin" -> "Administrator"
    "worship_leader" -> "Worship Leader"
    "song_editor" -> "Song Editor"
    "viewer" -> "Viewer"
    "team_member" -> "Team Member"
    else -> "Team Member"
}
