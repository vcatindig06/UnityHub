package com.unityhub.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.RehearsalAssignmentDto
import com.unityhub.app.data.RehearsalDto
import com.unityhub.app.data.RehearsalRepository
import com.unityhub.app.data.RehearsalRules
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
fun RehearsalManagementScreen(
    organizationId: String,
    userId: String,
    role: String?,
    onBack: () -> Unit,
    openRehearsalId: String? = null,
    openRehearsalRequestId: String? = null,
    onOpenRehearsalHandled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val repository = remember { RehearsalRepository(SupabaseClientProvider.client) }
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
    var error by remember { mutableStateOf("") }
    var consumedRequestId by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                rehearsals = repository.loadRehearsals(organizationId)
                members = repository.loadTeamMembers(organizationId)
                servicePlans = repository.loadServicePlans(organizationId)
            } catch (_: Exception) {
                error = "Rehearsals could not be loaded. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    fun loadAssignments(rehearsal: RehearsalDto) {
        scope.launch {
            assignmentsLoading = true
            error = ""
            try {
                assignments = repository.loadAssignments(rehearsal.id, organizationId)
            } catch (_: Exception) {
                error = "The rehearsal team list could not be loaded. Refresh and try again."
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
                    updated.firstOrNull { it.id == existing.id }?.let(::openDetails)
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
                val updated = repository.loadRehearsals(organizationId)
                rehearsals = updated
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
                selected = null
                assignments = emptyList()
                rehearsals = repository.loadRehearsals(organizationId)
            } catch (exception: Exception) {
                error = exception.message ?: "The rehearsal could not be deleted."
            } finally {
                saving = false
            }
        }
    }

    fun respond(assignment: RehearsalAssignmentDto, response: String) {
        scope.launch {
            respondingId = assignment.id
            error = ""
            try {
                repository.respond(
                    assignmentId = assignment.id,
                    memberId = assignment.memberId,
                    response = response
                )
                assignments = repository.loadAssignments(assignment.rehearsalId, organizationId)
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
            } catch (exception: Exception) {
                error = exception.message ?: "Attendance could not be saved."
            } finally {
                respondingId = null
            }
        }
    }

    LaunchedEffect(organizationId) { reload() }

    LaunchedEffect(openRehearsalId, openRehearsalRequestId, rehearsals) {
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

        if (canManage) {
            item {
                Button(onClick = ::startCreate, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
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
            val today = LocalDate.now().toString()
            val upcoming = rehearsals.filter { it.rehearsalDate >= today && it.status != "cancelled" }
            val past = rehearsals.filter { it.rehearsalDate < today || it.status != "scheduled" && it !in upcoming }

            if (upcoming.isNotEmpty()) {
                item {
                    Text("Upcoming", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(upcoming, key = { it.id }) { rehearsal ->
                    RehearsalListCard(rehearsal = rehearsal, servicePlan = servicePlans.firstOrNull { it.id == rehearsal.servicePlanId }) {
                        openDetails(rehearsal)
                    }
                }
            }
            if (past.isNotEmpty()) {
                item {
                    Text("Past and completed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                items(past, key = { it.id }) { rehearsal ->
                    RehearsalListCard(rehearsal = rehearsal, servicePlan = servicePlans.firstOrNull { it.id == rehearsal.servicePlanId }) {
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

@Composable
private fun RehearsalListCard(
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
    error: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    onRespond: (RehearsalAssignmentDto, String) -> Unit,
    onMarkAttendance: (RehearsalAssignmentDto, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDeleteOpen by rememberSaveable(rehearsal.id) { mutableStateOf(false) }
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

    val membersById = members.associateBy { it.memberId }
    val myMemberId = members.firstOrNull { it.userId == currentUserId }?.memberId
    val myAssignment = assignments.firstOrNull { it.memberId == myMemberId }
    val linkedPlan = servicePlans.firstOrNull { it.id == rehearsal.servicePlanId }

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
                    if (rehearsal.notes.isNotBlank()) {
                        HorizontalDivider()
                        Text("Notes", fontWeight = FontWeight.SemiBold)
                        Text(rehearsal.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (canManage) {
                        if (rehearsal.status == "scheduled") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onEdit, enabled = !saving && !loading, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Text("Edit")
                                }
                                Button(onClick = { onStatusChange("completed") }, enabled = !saving, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Text("Complete")
                                }
                            }
                        }
                        if (rehearsal.status == "scheduled") {
                            TextButton(onClick = { onStatusChange("cancelled") }, enabled = !saving) {
                                Text("Cancel rehearsal", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        TextButton(onClick = { confirmDeleteOpen = true }, enabled = !saving) {
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
        }

        items(assignments, key = { it.id }) { assignment ->
            val memberName = membersById[assignment.memberId]?.displayName ?: "Team member"
            RehearsalAssignmentCard(
                assignment = assignment,
                memberName = memberName,
                canManage = canManage,
                attendanceEnabled = rehearsal.status == "completed",
                busy = respondingId == assignment.id,
                onMarkAttendance = { status -> onMarkAttendance(assignment, status) }
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
                Row(Modifier.fillMaxWidth().clickable { membersExpanded = !membersExpanded }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
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
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) {
                                    onSelectedMemberIdsChange(
                                        if (member.memberId in selectedMemberIds) selectedMemberIds - member.memberId
                                        else selectedMemberIds + member.memberId
                                    )
                                }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = member.memberId in selectedMemberIds,
                                    onCheckedChange = { checked ->
                                        onSelectedMemberIdsChange(
                                            if (checked) selectedMemberIds + member.memberId
                                            else selectedMemberIds - member.memberId
                                        )
                                    },
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
