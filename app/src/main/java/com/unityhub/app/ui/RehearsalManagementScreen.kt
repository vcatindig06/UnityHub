package com.unityhub.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.OfflineRehearsalCache
import com.unityhub.app.data.OfflineServiceCache
import com.unityhub.app.data.PendingRehearsalAvailabilityResponseQueue
import com.unityhub.app.data.RehearsalAssignmentDto
import com.unityhub.app.data.RehearsalCalendarRules
import com.unityhub.app.data.RehearsalTimeFilter
import com.unityhub.app.sync.RehearsalReminderScheduler
import com.unityhub.app.data.RehearsalDto
import com.unityhub.app.data.RehearsalRepository
import com.unityhub.app.data.RehearsalRules
import com.unityhub.app.data.RehearsalRosterFilter
import com.unityhub.app.data.RehearsalRosterRules
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.sync.OfflineSyncScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

private fun hasValidatedInternetConnection(context: Context): Boolean {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun applyPendingAvailabilityResponses(
    assignments: List<RehearsalAssignmentDto>,
    pendingResponses: Map<String, String>
): List<RehearsalAssignmentDto> =
    assignments.map { assignment ->
        val pendingStatus = pendingResponses[assignment.id]
        if (pendingStatus == null) assignment else assignment.copy(responseStatus = pendingStatus)
    }

@Composable
fun RehearsalManagementScreen(
    organizationId: String,
    userId: String,
    role: String?,
    onBack: () -> Unit,
    openRehearsalId: String? = null,
    openRehearsalRequestId: String? = null,
    onOpenRehearsalHandled: () -> Unit = {},
    onOpenServicePlan: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = remember { RehearsalRepository(SupabaseClientProvider.client) }
    val context = LocalContext.current
    val offlineCache = remember(context) { OfflineRehearsalCache(context) }
    val responseQueue = remember(context) { PendingRehearsalAvailabilityResponseQueue(context) }
    val scope = rememberCoroutineScope()
    val canManage = role == "admin" || role == "worship_leader"

    var rehearsals by remember(organizationId) { mutableStateOf<List<RehearsalDto>>(emptyList()) }
    var members by remember(organizationId) { mutableStateOf<List<ServiceMemberOption>>(emptyList()) }
    var servicePlans by remember(organizationId) { mutableStateOf<List<ServicePlanDto>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<RehearsalAssignmentDto>>(emptyList()) }
    var selected by remember { mutableStateOf<RehearsalDto?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RehearsalDto?>(null) }
    var editorMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var assignmentsLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var respondingId by remember { mutableStateOf<String?>(null) }
    var pendingResponseStatuses by remember(userId, organizationId) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var offlineSnapshot by remember(userId, organizationId) { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var consumedRequestId by remember { mutableStateOf<String?>(null) }
    var timeFilter by rememberSaveable(organizationId) {
        mutableStateOf(RehearsalTimeFilter.UPCOMING)
    }
    var calendarViewMode by rememberSaveable(organizationId) { mutableStateOf("calendar") }
    var selectedCalendarDate by rememberSaveable(organizationId) {
        mutableStateOf(LocalDate.now().toString())
    }
    var displayedCalendarMonth by rememberSaveable(organizationId) {
        mutableStateOf(YearMonth.now().toString())
    }

    fun updateTimeFilter(filter: RehearsalTimeFilter) {
        timeFilter = filter
        val matching = RehearsalCalendarRules.filter(rehearsals, filter)
        val targetDateText = if (filter == RehearsalTimeFilter.ALL) {
            matching.firstOrNull {
                it.status == "scheduled" && it.rehearsalDate >= LocalDate.now().toString()
            }?.rehearsalDate
        } else {
            matching.firstOrNull()?.rehearsalDate
        } ?: LocalDate.now().toString()
        val targetDate = runCatching { LocalDate.parse(targetDateText) }.getOrDefault(LocalDate.now())
        selectedCalendarDate = targetDate.toString()
        displayedCalendarMonth = YearMonth.from(targetDate).toString()
    }

    BackHandler(enabled = selected != null) {
        selected = null
        assignments = emptyList()
        error = ""
    }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                pendingResponseStatuses = responseQueue.loadPending(userId, organizationId)
                    .associate { it.assignmentId to it.responseStatus }
                val loadedRehearsals = repository.loadRehearsals(organizationId)
                val loadedMembers = repository.loadTeamMembers(organizationId)
                val loadedPlans = repository.loadServicePlans(organizationId)
                rehearsals = loadedRehearsals
                members = loadedMembers
                servicePlans = loadedPlans
                runCatching { offlineCache.saveRehearsals(userId, organizationId, loadedRehearsals) }
                offlineSnapshot = false
            } catch (_: Exception) {
                pendingResponseStatuses = runCatching {
                    responseQueue.loadPending(userId, organizationId)
                        .associate { it.assignmentId to it.responseStatus }
                }.getOrDefault(emptyMap())
                val cachedRehearsals = runCatching {
                    offlineCache.loadRehearsals(userId, organizationId)
                }.getOrDefault(emptyList())
                if (cachedRehearsals.isNotEmpty()) {
                    rehearsals = cachedRehearsals
                    members = emptyList()
                    servicePlans = runCatching {
                        OfflineServiceCache(context).loadServicePlans(userId, organizationId).plans
                    }.getOrDefault(emptyList())
                    offlineSnapshot = true
                    error = ""
                } else {
                    error = "Rehearsals could not be loaded. Connect to the internet and try again."
                }
            } finally {
                loading = false
            }
        }
    }

    fun loadAssignments(rehearsal: RehearsalDto) {
        scope.launch {
            assignmentsLoading = true
            error = ""
            pendingResponseStatuses = runCatching {
                responseQueue.loadPending(userId, organizationId)
                    .associate { it.assignmentId to it.responseStatus }
            }.getOrDefault(pendingResponseStatuses)
            try {
                val loadedAssignments = repository.loadAssignments(rehearsal.id, organizationId)
                val loadedMembers = repository.loadTeamMembers(organizationId)
                members = loadedMembers
                assignments = applyPendingAvailabilityResponses(
                    loadedAssignments,
                    pendingResponseStatuses
                )
                runCatching {
                    offlineCache.saveRoster(
                        userId = userId,
                        organizationId = organizationId,
                        rehearsalId = rehearsal.id,
                        assignments = loadedAssignments,
                        members = loadedMembers
                    )
                }
                offlineSnapshot = false
            } catch (_: Exception) {
                val cachedRoster = runCatching {
                    offlineCache.loadRoster(userId, organizationId, rehearsal.id)
                }.getOrNull()
                if (cachedRoster != null && cachedRoster.assignments.isNotEmpty()) {
                    members = cachedRoster.members
                    assignments = applyPendingAvailabilityResponses(
                        cachedRoster.assignments,
                        pendingResponseStatuses
                    )
                    offlineSnapshot = true
                    error = ""
                } else {
                    error = "The rehearsal team list could not be loaded. Refresh when you are online."
                }
            } finally {
                assignmentsLoading = false
            }
        }
    }

    fun openDetails(rehearsal: RehearsalDto) {
        selected = rehearsal
        loadAssignments(rehearsal)
    }

    fun startCreate() {
        editing = null
        editorMemberIds = emptySet()
        error = ""
        editorOpen = true
    }

    fun startEdit(rehearsal: RehearsalDto) {
        editing = rehearsal
        editorMemberIds = assignments.map { it.memberId }.toSet()
        error = ""
        editorOpen = true
    }

    fun saveRehearsal(
        title: String,
        date: String,
        start: String,
        end: String,
        location: String,
        notes: String,
        servicePlanId: String?
    ) {
        scope.launch {
            saving = true
            error = ""
            try {
                val existing = editing
                if (existing == null) {
                    val created = repository.createRehearsal(
                        organizationId = organizationId,
                        userId = userId,
                        title = title,
                        date = date,
                        startTime = start,
                        endTime = end,
                        location = location,
                        notes = notes,
                        servicePlanId = servicePlanId,
                        memberIds = editorMemberIds
                    )
                    rehearsals = repository.loadRehearsals(organizationId)
                    runCatching { offlineCache.saveRehearsals(userId, organizationId, rehearsals) }
                    editorOpen = false
                    openDetails(created)
                } else {
                    repository.updateRehearsal(
                        rehearsalId = existing.id,
                        organizationId = organizationId,
                        userId = userId,
                        title = title,
                        date = date,
                        startTime = start,
                        endTime = end,
                        location = location,
                        notes = notes,
                        servicePlanId = servicePlanId,
                        memberIds = editorMemberIds
                    )
                    editorOpen = false
                    val updated = repository.loadRehearsals(organizationId)
                    rehearsals = updated
                    runCatching { offlineCache.saveRehearsals(userId, organizationId, updated) }
                    val updatedRehearsal = updated.firstOrNull { it.id == existing.id }
                    val reminderMinutes = RehearsalReminderScheduler.getMinutesBefore(
                        context,
                        userId,
                        existing.id
                    )
                    if (reminderMinutes != null && updatedRehearsal != null) {
                        val reminderRescheduled = runCatching {
                            RehearsalReminderScheduler.schedule(
                                context = context,
                                userId = userId,
                                organizationId = organizationId,
                                rehearsal = updatedRehearsal,
                                minutesBefore = reminderMinutes
                            )
                        }.getOrDefault(false)
                        if (!reminderRescheduled) {
                            error = "The rehearsal was updated, but its old reminder time had passed. Set a new reminder."
                        }
                    }
                    updatedRehearsal?.let(::openDetails)
                }
            } catch (exception: Exception) {
                error = exception.message ?: "The rehearsal could not be saved."
            } finally {
                saving = false
            }
        }
    }

    fun updateState(rehearsal: RehearsalDto, status: String) {
        scope.launch {
            saving = true
            error = ""
            try {
                repository.updateStatus(rehearsal.id, organizationId, status)
                if (status == "completed" || status == "cancelled") {
                    RehearsalReminderScheduler.cancel(context, userId, rehearsal.id)
                }
                val updated = repository.loadRehearsals(organizationId)
                rehearsals = updated
                runCatching { offlineCache.saveRehearsals(userId, organizationId, updated) }
                selected = updated.firstOrNull { it.id == rehearsal.id }
                selected?.let(::loadAssignments)
            } catch (exception: Exception) {
                error = exception.message ?: "The rehearsal status could not be updated."
            } finally {
                saving = false
            }
        }
    }

    fun deleteSelected(rehearsal: RehearsalDto) {
        scope.launch {
            saving = true
            error = ""
            try {
                repository.deleteRehearsal(rehearsal.id, organizationId)
                RehearsalReminderScheduler.cancel(context, userId, rehearsal.id)
                selected = null
                assignments = emptyList()
                rehearsals = repository.loadRehearsals(organizationId)
                runCatching { offlineCache.saveRehearsals(userId, organizationId, rehearsals) }
            } catch (exception: Exception) {
                error = exception.message ?: "The rehearsal could not be deleted."
            } finally {
                saving = false
            }
        }
    }

    fun respond(assignment: RehearsalAssignmentDto, response: String) {
        val rehearsal = selected ?: return
        if (!RehearsalRules.canRespond(rehearsal.status)) {
            error = "This rehearsal is no longer accepting availability responses."
            return
        }
        scope.launch {
            respondingId = assignment.id
            error = ""
            try {
                if (!hasValidatedInternetConnection(context)) {
                    responseQueue.enqueue(
                        userId = userId,
                        organizationId = organizationId,
                        rehearsalId = rehearsal.id,
                        assignmentId = assignment.id,
                        memberId = assignment.memberId,
                        responseStatus = response
                    )
                    pendingResponseStatuses = pendingResponseStatuses + (assignment.id to response)
                    assignments = assignments.map {
                        if (it.id == assignment.id) it.copy(responseStatus = response) else it
                    }
                    OfflineSyncScheduler.schedule(context, userId, organizationId)
                } else {
                    val submitted = repository.respond(
                        assignmentId = assignment.id,
                        rehearsalId = rehearsal.id,
                        organizationId = organizationId,
                        memberId = assignment.memberId,
                        response = response
                    )
                    if (!submitted) {
                        throw IllegalStateException(
                            "This rehearsal is no longer accepting availability responses. Refresh and check its current status."
                        )
                    }
                    responseQueue.remove(userId, assignment.id)
                    pendingResponseStatuses = pendingResponseStatuses - assignment.id
                    val refreshedAssignments =
                        repository.loadAssignments(rehearsal.id, organizationId)
                    assignments = refreshedAssignments
                    runCatching {
                        offlineCache.saveRoster(
                            userId = userId,
                            organizationId = organizationId,
                            rehearsalId = rehearsal.id,
                            assignments = refreshedAssignments,
                            members = members
                        )
                    }
                    offlineSnapshot = false
                }
            } catch (exception: Exception) {
                error = exception.message ?: "Your availability could not be saved."
            } finally {
                respondingId = null
            }
        }
    }

    fun markAttendance(assignment: RehearsalAssignmentDto, attendance: String) {
        scope.launch {
            respondingId = assignment.id
            error = ""
            try {
                repository.markAttendance(
                    assignmentId = assignment.id,
                    organizationId = organizationId,
                    status = attendance
                )
                assignments = repository.loadAssignments(assignment.rehearsalId, organizationId)
                runCatching {
                    offlineCache.saveRoster(
                        userId = userId,
                        organizationId = organizationId,
                        rehearsalId = assignment.rehearsalId,
                        assignments = assignments,
                        members = members
                    )
                }
            } catch (exception: Exception) {
                error = exception.message ?: "Attendance could not be saved."
            } finally {
                respondingId = null
            }
        }
    }

    LaunchedEffect(userId, organizationId) { reload() }

    LaunchedEffect(openRehearsalId, openRehearsalRequestId, rehearsals, loading) {
        if (openRehearsalRequestId.isNullOrBlank()) {
            consumedRequestId = null
        } else if (consumedRequestId != openRehearsalRequestId && !loading) {
            consumedRequestId = openRehearsalRequestId
            rehearsals.firstOrNull { it.id == openRehearsalId }?.let(::openDetails)
                ?: run { error = "The rehearsal linked to this notification is no longer available." }
            onOpenRehearsalHandled()
        }
    }

    if (editorOpen) {
        RehearsalEditorDialog(
            rehearsal = editing,
            members = members,
            servicePlans = servicePlans,
            selectedMemberIds = editorMemberIds,
            onSelectedMemberIdsChange = { editorMemberIds = it },
            saving = saving,
            onDismiss = { if (!saving) editorOpen = false },
            onSave = ::saveRehearsal
        )
    }

    selected?.let { rehearsal ->
        RehearsalDetailContent(
            rehearsal = rehearsal,
            assignments = assignments,
            members = members,
            servicePlans = servicePlans,
            currentUserId = userId,
            canManage = canManage,
            loading = assignmentsLoading,
            saving = saving,
            respondingId = respondingId,
            pendingResponseIds = pendingResponseStatuses.keys,
            offlineSnapshot = offlineSnapshot,
            error = error,
            onBack = {
                selected = null
                assignments = emptyList()
                error = ""
            },
            onRefresh = { loadAssignments(rehearsal) },
            onEdit = {
                if (!assignmentsLoading) startEdit(rehearsal)
            },
            onStatusChange = { status -> updateState(rehearsal, status) },
            onDelete = { deleteSelected(rehearsal) },
            onRespond = ::respond,
            onMarkAttendance = ::markAttendance,
            onOpenServicePlan = onOpenServicePlan,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to services")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Rehearsals", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Schedule practice, coordinate availability, and track attendance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = ::reload, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh rehearsals")
                }
            }
        }

        if (offlineSnapshot) {
            item {
                Text(
                    "Offline copy · schedule and roster changes are read-only. Member availability can be saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (canManage) {
            item {
                Button(
                    onClick = ::startCreate,
                    enabled = !saving && !offlineSnapshot,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Schedule rehearsal")
                }
            }
        }

        if (error.isNotBlank()) {
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        if (loading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("Loading rehearsals", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (rehearsals.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Text("No rehearsals scheduled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (canManage) "Create your first rehearsal and invite the team members who will attend."
                            else "Your worship leader can schedule rehearsals and invite you here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val filteredRehearsals = RehearsalCalendarRules.filter(rehearsals, timeFilter)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RehearsalTimeFilter.values().forEach { filter ->
                        FilterChip(
                            selected = timeFilter == filter,
                            onClick = { updateTimeFilter(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = calendarViewMode == "calendar",
                        onClick = { calendarViewMode = "calendar" },
                        label = { Text("Calendar") },
                        leadingIcon = if (calendarViewMode == "calendar") {
                            { Icon(Icons.Default.CalendarMonth, contentDescription = null) }
                        } else null
                    )
                    FilterChip(
                        selected = calendarViewMode == "list",
                        onClick = { calendarViewMode = "list" },
                        label = { Text("List") },
                        leadingIcon = if (calendarViewMode == "list") {
                            { Icon(Icons.Default.Groups, contentDescription = null) }
                        } else null
                    )
                }
            }

            if (calendarViewMode == "calendar") {
                item {
                    RehearsalCalendarContent(
                        rehearsals = filteredRehearsals,
                        servicePlans = servicePlans,
                        timeFilter = timeFilter,
                        selectedDateText = selectedCalendarDate,
                        displayedMonthText = displayedCalendarMonth,
                        onSelectedDateChange = { selectedCalendarDate = it },
                        onDisplayedMonthChange = { displayedCalendarMonth = it },
                        onOpenRehearsal = ::openDetails
                    )
                }
            } else if (filteredRehearsals.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Text(
                            when (timeFilter) {
                                RehearsalTimeFilter.UPCOMING -> "No upcoming rehearsals."
                                RehearsalTimeFilter.PAST -> "No past rehearsals yet."
                                RehearsalTimeFilter.ALL -> "No rehearsals are available."
                            },
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    Text(
                        if (timeFilter == RehearsalTimeFilter.ALL) {
                            "All rehearsals · ${filteredRehearsals.size}"
                        } else {
                            "${timeFilter.label} · ${filteredRehearsals.size}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(filteredRehearsals, key = { it.id }) { rehearsal ->
                    RehearsalListCard(
                        rehearsal = rehearsal,
                        servicePlan = servicePlans.firstOrNull { it.id == rehearsal.servicePlanId }
                    ) {
                        openDetails(rehearsal)
                    }
                }
            }
            item {
                Text(
                    "Rehearsal invitations are sent to each assigned member through UnityHub notifications.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        }
    }
}

@Composable
internal fun RehearsalListCard(
    rehearsal: RehearsalDto,
    servicePlan: ServicePlanDto?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(rehearsal.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                RehearsalStatusPill(rehearsal.status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                Text(RehearsalRules.formatDate(rehearsal.rehearsalDate), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    rehearsalTimeLabel(rehearsal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (rehearsal.location.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                    Text(rehearsal.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (servicePlan != null) {
                Text(
                    "For service: ${servicePlan.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RehearsalDetailContent(
    rehearsal: RehearsalDto,
    assignments: List<RehearsalAssignmentDto>,
    members: List<ServiceMemberOption>,
    servicePlans: List<ServicePlanDto>,
    currentUserId: String,
    canManage: Boolean,
    loading: Boolean,
    saving: Boolean,
    respondingId: String?,
    pendingResponseIds: Set<String>,
    offlineSnapshot: Boolean,
    error: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    onRespond: (RehearsalAssignmentDto, String) -> Unit,
    onMarkAttendance: (RehearsalAssignmentDto, String) -> Unit,
    onOpenServicePlan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var confirmDeleteOpen by rememberSaveable(rehearsal.id) { mutableStateOf(false) }
    var reminderDialogOpen by rememberSaveable(rehearsal.id) { mutableStateOf(false) }
    var reminderSetMinutes by remember(rehearsal.id, currentUserId) {
        mutableStateOf(
            RehearsalReminderScheduler.getMinutesBefore(context, currentUserId, rehearsal.id)
        )
    }
    var reminderChoice by remember(rehearsal.id, currentUserId) {
        mutableStateOf(
            reminderSetMinutes ?: RehearsalReminderScheduler.ONE_HOUR_BEFORE_MINUTES
        )
    }
    var calendarReminderMessage by remember(rehearsal.id) { mutableStateOf("") }
    LaunchedEffect(rehearsal.updatedAt, currentUserId) {
        reminderSetMinutes = RehearsalReminderScheduler.getMinutesBefore(
            context,
            currentUserId,
            rehearsal.id
        )
    }
    var rosterSearch by rememberSaveable(rehearsal.id) { mutableStateOf("") }
    var rosterFilter by rememberSaveable(rehearsal.id, rehearsal.status) {
        mutableStateOf(RehearsalRosterFilter.ALL)
    }
    if (confirmDeleteOpen) {
        AlertDialog(
            onDismissRequest = { if (!saving) confirmDeleteOpen = false },
            title = { Text("Delete rehearsal?") },
            text = { Text("This permanently deletes the rehearsal invitation roster and associated rehearsal notifications.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteOpen = false
                        onDelete()
                    },
                    enabled = !saving
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteOpen = false }, enabled = !saving) { Text("Keep rehearsal") }
            }
        )
    }

    if (reminderDialogOpen) {
        AlertDialog(
            onDismissRequest = { reminderDialogOpen = false },
            title = { Text("Rehearsal reminder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Choose when this device should remind you.")
                    listOf(
                        RehearsalReminderScheduler.ONE_DAY_BEFORE_MINUTES,
                        RehearsalReminderScheduler.ONE_HOUR_BEFORE_MINUTES,
                        RehearsalReminderScheduler.FIFTEEN_MINUTES_BEFORE_MINUTES
                    ).forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reminderChoice = option },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = reminderChoice == option,
                                onClick = { reminderChoice = option }
                            )
                            Text(RehearsalReminderScheduler.labelFor(option))
                        }
                    }
                    Text(
                        "UnityHub uses an on-device reminder. Android notification permission and the Rehearsal reminders channel must be enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val scheduled = runCatching {
                            RehearsalReminderScheduler.schedule(
                                context = context,
                                userId = currentUserId,
                                organizationId = rehearsal.organizationId,
                                rehearsal = rehearsal,
                                minutesBefore = reminderChoice
                            )
                        }.getOrDefault(false)
                        if (scheduled) {
                            reminderSetMinutes = reminderChoice
                            calendarReminderMessage =
                                "Reminder set · ${RehearsalReminderScheduler.labelFor(reminderChoice)}"
                            reminderDialogOpen = false
                        } else {
                            calendarReminderMessage =
                                "That reminder time has already passed. Choose a later reminder."
                        }
                    },
                    enabled = rehearsal.status == "scheduled"
                ) { Text("Set reminder") }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reminderSetMinutes != null) {
                        TextButton(
                            onClick = {
                                RehearsalReminderScheduler.cancel(
                                    context,
                                    currentUserId,
                                    rehearsal.id
                                )
                                reminderSetMinutes = null
                                calendarReminderMessage = "Reminder removed."
                                reminderDialogOpen = false
                            }
                        ) { Text("Remove") }
                    }
                    TextButton(onClick = { reminderDialogOpen = false }) { Text("Cancel") }
                }
            }
        )
    }

    val membersById = remember(members) { members.associateBy { it.memberId } }
    val memberNames = remember(members) { members.associate { it.memberId to it.displayName } }
    val myMemberId = members.firstOrNull { it.userId == currentUserId }?.memberId
    val myAssignment = assignments.firstOrNull { it.memberId == myMemberId }
    val linkedPlan = servicePlans.firstOrNull { it.id == rehearsal.servicePlanId }
    val rosterSummary = remember(assignments) { RehearsalRosterRules.summary(assignments) }
    val visibleAssignments = remember(assignments, memberNames, rosterSearch, rosterFilter) {
        RehearsalRosterRules.filter(assignments, memberNames, rosterFilter, rosterSearch)
    }
    val rosterFilters = remember(rehearsal.status) {
        RehearsalRosterRules.filtersFor(rehearsal.status)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to rehearsals") }
                Column(Modifier.weight(1f)) {
                    Text("Rehearsal details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(rehearsal.status.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Default.Refresh, contentDescription = "Refresh team responses") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rehearsal.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        RehearsalStatusPill(rehearsal.status)
                    }
                    RehearsalDetailLine(Icons.Default.CalendarMonth, RehearsalRules.formatDate(rehearsal.rehearsalDate))
                    RehearsalDetailLine(Icons.Default.Schedule, rehearsalTimeLabel(rehearsal))
                    if (rehearsal.location.isNotBlank()) RehearsalDetailLine(Icons.Default.LocationOn, rehearsal.location)
                    if (linkedPlan != null) RehearsalDetailLine(Icons.Default.Groups, "Linked service: ${linkedPlan.title}")
                    if (linkedPlan != null) {
                        TextButton(onClick = { onOpenServicePlan(linkedPlan.id) }) {
                            Text("Open linked service setlist")
                        }
                    }
                    if (rehearsal.notes.isNotBlank()) {
                        HorizontalDivider()
                        Text("Notes", fontWeight = FontWeight.SemiBold)
                        Text(rehearsal.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val opened = runCatching {
                                    context.startActivity(RehearsalCalendarIntent.create(rehearsal))
                                    true
                                }.getOrDefault(false)
                                calendarReminderMessage = if (opened) {
                                    "Calendar opened · review and save the event there."
                                } else {
                                    "No calendar app is available to add this rehearsal."
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Text("Add to calendar")
                        }
                        if (rehearsal.status == "scheduled") {
                            OutlinedButton(
                                onClick = {
                                    reminderChoice = reminderSetMinutes
                                        ?: RehearsalReminderScheduler.ONE_HOUR_BEFORE_MINUTES
                                    reminderDialogOpen = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null)
                                Text(if (reminderSetMinutes == null) "Set reminder" else "Reminder set")
                            }
                        }
                    }
                    if (reminderSetMinutes != null && rehearsal.status == "scheduled") {
                        Text(
                            "Reminder · ${RehearsalReminderScheduler.labelFor(reminderSetMinutes!!)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (calendarReminderMessage.isNotBlank()) {
                        Text(
                            calendarReminderMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canManage) {
                        if (rehearsal.status == "scheduled") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onEdit, enabled = !saving && !loading && !offlineSnapshot, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Text("Edit")
                                }
                                Button(onClick = { onStatusChange("completed") }, enabled = !saving && !offlineSnapshot, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Text("Complete")
                                }
                            }
                        }
                        if (rehearsal.status == "scheduled") {
                            TextButton(onClick = { onStatusChange("cancelled") }, enabled = !saving && !offlineSnapshot) {
                                Text("Cancel rehearsal", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { confirmDeleteOpen = true }, enabled = !saving && !offlineSnapshot) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Text("Delete rehearsal")
                        }
                    }
                }
            }
        }

        if (myAssignment != null && rehearsal.status == "scheduled") {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Your availability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Current response: ${RehearsalRules.responseLabel(myAssignment.responseStatus)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (myAssignment.id in pendingResponseIds) {
                            Text(
                                "Saved on this device · waiting to sync",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (offlineSnapshot) {
                            Text(
                                "Showing the last synced rehearsal details. Your response will be sent when the connection returns.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRespond(myAssignment, "available") },
                                enabled = respondingId != myAssignment.id && myAssignment.responseStatus != "available"
                            ) { Text("Available") }
                            OutlinedButton(
                                onClick = { onRespond(myAssignment, "unavailable") },
                                enabled = respondingId != myAssignment.id && myAssignment.responseStatus != "unavailable"
                            ) { Text("Can't attend") }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Team attendance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${assignments.size} invited members", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        if (assignments.isNotEmpty()) {
            item {
                RehearsalRosterSummaryCard(
                    total = rosterSummary.invited,
                    responded = if (rehearsal.status == "completed") {
                        rosterSummary.attended + rosterSummary.absent
                    } else {
                        rosterSummary.available + rosterSummary.unavailable
                    },
                    responseLabel = if (rehearsal.status == "completed") {
                        "Attendance recorded"
                    } else {
                        "Availability responses"
                    },
                    metrics = if (rehearsal.status == "completed") {
                        listOf(
                            RehearsalRosterMetric("Attended", rosterSummary.attended, MaterialTheme.colorScheme.primary),
                            RehearsalRosterMetric("Absent", rosterSummary.absent, MaterialTheme.colorScheme.error),
                            RehearsalRosterMetric("Not recorded", rosterSummary.notRecorded, MaterialTheme.colorScheme.tertiary)
                        )
                    } else {
                        listOf(
                            RehearsalRosterMetric("Available", rosterSummary.available, MaterialTheme.colorScheme.primary),
                            RehearsalRosterMetric("Can't attend", rosterSummary.unavailable, MaterialTheme.colorScheme.error),
                            RehearsalRosterMetric("Awaiting", rosterSummary.awaiting, MaterialTheme.colorScheme.tertiary)
                        )
                    }
                )
            }
            item {
                OutlinedTextField(
                    value = rosterSearch,
                    onValueChange = { rosterSearch = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search invited members") },
                    placeholder = { Text("Enter a member name") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (rosterSearch.isNotBlank()) {
                            IconButton(onClick = { rosterSearch = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear member search")
                            }
                        }
                    },
                    singleLine = true
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rosterFilters.forEach { filter ->
                        FilterChip(
                            selected = rosterFilter == filter,
                            onClick = { rosterFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }
            item {
                Text(
                    "${visibleAssignments.size} of ${assignments.size} invited members",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (error.isNotBlank()) {
            item { Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        if (!loading && assignments.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Text(
                        "No members have been invited to this rehearsal yet.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (!loading && visibleAssignments.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No invited members match this search or filter.")
                        TextButton(onClick = {
                            rosterSearch = ""
                            rosterFilter = RehearsalRosterFilter.ALL
                        }) {
                            Text("Clear search and filters")
                        }
                    }
                }
            }
        }

        items(visibleAssignments, key = { it.id }) { assignment ->
            val memberName = membersById[assignment.memberId]?.displayName ?: "Team member"
            RehearsalAssignmentCard(
                assignment = assignment,
                memberName = memberName,
                canManage = canManage && !offlineSnapshot,
                attendanceEnabled = rehearsal.status == "completed",
                busy = respondingId == assignment.id,
                onMarkAttendance = { status -> onMarkAttendance(assignment, status) }
            )
        }
    }
}

private data class RehearsalRosterMetric(
    val label: String,
    val value: Int,
    val color: androidx.compose.ui.graphics.Color
)

@Composable
private fun RehearsalRosterSummaryCard(
    metrics: List<RehearsalRosterMetric>,
    total: Int,
    responded: Int,
    responseLabel: String
) {
    val responseRate = if (total > 0) (responded * 100) / total else 0
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                metrics.forEach { metric ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 11.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                metric.value.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = metric.color
                            )
                            Text(
                                metric.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(10.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                metrics.filter { it.value > 0 }.forEach { metric ->
                    Box(
                        modifier = Modifier
                            .weight(metric.value.toFloat())
                            .fillMaxHeight()
                            .background(metric.color)
                    )
                }
            }
            Text(
                "$responseLabel · $responded of $total · $responseRate%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RehearsalAssignmentCard(
    assignment: RehearsalAssignmentDto,
    memberName: String,
    canManage: Boolean,
    attendanceEnabled: Boolean,
    busy: Boolean,
    onMarkAttendance: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(memberName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(
                    RehearsalRules.responseLabel(assignment.responseStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (assignment.responseStatus) {
                        "available", "attended" -> MaterialTheme.colorScheme.primary
                        "unavailable", "absent" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (assignment.responseNote.isNotBlank()) {
                Text(assignment.responseNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canManage && attendanceEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMarkAttendance("attended") }, enabled = !busy && assignment.responseStatus != "attended") {
                        Text("Attended")
                    }
                    OutlinedButton(onClick = { onMarkAttendance("absent") }, enabled = !busy && assignment.responseStatus != "absent") {
                        Text("Absent")
                    }
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun RehearsalEditorDialog(
    rehearsal: RehearsalDto?,
    members: List<ServiceMemberOption>,
    servicePlans: List<ServicePlanDto>,
    selectedMemberIds: Set<String>,
    onSelectedMemberIdsChange: (Set<String>) -> Unit,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String?) -> Unit
) {
    var title by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.title.orEmpty()) }
    var date by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.rehearsalDate ?: LocalDate.now().toString()) }
    var startTime by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.startTime?.take(5) ?: "18:00") }
    var endTime by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.endTime?.take(5).orEmpty()) }
    var location by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.location.orEmpty()) }
    var notes by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.notes.orEmpty()) }
    var selectedPlanId by rememberSaveable(rehearsal?.id) { mutableStateOf(rehearsal?.servicePlanId.orEmpty()) }
    var planMenuExpanded by remember { mutableStateOf(false) }
    var membersExpanded by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rehearsal == null) "Schedule rehearsal" else "Edit rehearsal") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Rehearsal title") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startTime, { startTime = it }, label = { Text("Start (24h)") }, singleLine = true, modifier = Modifier.weight(1f), enabled = !saving)
                    OutlinedTextField(endTime, { endTime = it }, label = { Text("End (optional)") }, singleLine = true, modifier = Modifier.weight(1f), enabled = !saving)
                }
                OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !saving)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Linked service", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { planMenuExpanded = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text(servicePlans.firstOrNull { it.id == selectedPlanId }?.title ?: "No linked service")
                    }
                    DropdownMenu(expanded = planMenuExpanded, onDismissRequest = { planMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("No linked service") },
                            onClick = { selectedPlanId = ""; planMenuExpanded = false }
                        )
                        servicePlans.filter { it.status != "cancelled" }.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text(plan.title) },
                                onClick = { selectedPlanId = plan.id; planMenuExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    notes, { notes = it },
                    label = { Text("Rehearsal notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !saving
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { membersExpanded = !membersExpanded }
                        .semantics {
                            role = Role.Button
                            stateDescription = if (membersExpanded) "Expanded" else "Collapsed"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Invite team members", fontWeight = FontWeight.SemiBold)
                        Text("${selectedMemberIds.size} selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (membersExpanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
                }
                if (membersExpanded) {
                    if (members.isEmpty()) {
                        Text("No team members found.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        members.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .toggleable(
                                        value = member.memberId in selectedMemberIds,
                                        enabled = !saving,
                                        role = Role.Checkbox
                                    ) { checked ->
                                        onSelectedMemberIdsChange(
                                            if (checked) selectedMemberIds + member.memberId
                                            else selectedMemberIds - member.memberId
                                        )
                                    }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = member.memberId in selectedMemberIds,
                                    onCheckedChange = null,
                                    enabled = !saving
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(member.teamRole.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, date, startTime, endTime, location, notes, selectedPlanId.takeIf(String::isNotBlank)) }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(if (rehearsal == null) "Schedule" else "Save changes")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
private fun RehearsalStatusPill(status: String) {
    val label = when (status) {
        "completed" -> "Completed"
        "cancelled" -> "Cancelled"
        else -> "Scheduled"
    }
    Surface(
        color = when (status) {
            "completed" -> MaterialTheme.colorScheme.secondaryContainer
            "cancelled" -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(30.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = when (status) {
                "completed" -> MaterialTheme.colorScheme.onSecondaryContainer
                "cancelled" -> MaterialTheme.colorScheme.onErrorContainer
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}

@Composable
private fun RehearsalDetailLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun rehearsalTimeLabel(rehearsal: RehearsalDto): String =
    if (rehearsal.endTime.isNullOrBlank()) {
        RehearsalRules.formatTime(rehearsal.startTime)
    } else {
        "${RehearsalRules.formatTime(rehearsal.startTime)} – ${RehearsalRules.formatTime(rehearsal.endTime)}"
    }
