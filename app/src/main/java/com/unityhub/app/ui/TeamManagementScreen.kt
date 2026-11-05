package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.MemberMinistryPositionDto
import com.unityhub.app.data.MinistryPositionDto
import com.unityhub.app.data.OrganizationJoinRequestDto
import com.unityhub.app.data.OrganizationWorkspace
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.TeamManagementRepository
import kotlinx.coroutines.launch

@Composable
fun TeamManagementScreen(
    workspace: OrganizationWorkspace,
    currentUserId: String,
    onReviewRequest: (String, Boolean) -> Unit,
    errorMessage: String,
    modifier: Modifier = Modifier
) {
    val organizationId = workspace.organization?.id ?: return
    val client = remember { SupabaseClientProvider.client }
    val assignmentRepository = remember { ServiceAssignmentRepository(client) }
    val repository = remember { TeamManagementRepository(client) }
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<ServiceMemberOption>>(emptyList()) }
    var positions by remember { mutableStateOf<List<MinistryPositionDto>>(emptyList()) }
    var memberPositions by remember { mutableStateOf<List<MemberMinistryPositionDto>>(emptyList()) }
    var dataLoading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var managementError by remember { mutableStateOf("") }
    var addPositionOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<ServiceMemberOption?>(null) }

    val canManagePositions = workspace.role == "admin" || workspace.role == "worship_leader"

    suspend fun loadTeamData() {
        members = assignmentRepository.loadTeamMembers(organizationId)
        positions = repository.loadMinistryPositions(organizationId)
        memberPositions = repository.loadMemberPositions(organizationId)
    }

    fun refresh() {
        scope.launch {
            dataLoading = true
            managementError = ""
            try {
                loadTeamData()
            } catch (_: Exception) {
                managementError = "UnityHub could not load member profiles and ministry positions. Refresh the Team screen and try again."
            } finally {
                dataLoading = false
            }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            saving = true
            managementError = ""
            try {
                action()
                loadTeamData()
            } catch (exception: Exception) {
                managementError = when {
                    "already exists" in exception.message.orEmpty().lowercase() ->
                        "That ministry position already exists. Choose a different name."
                    "must contain 2" in exception.message.orEmpty().lowercase() ->
                        "Use a ministry position name between 2 and 60 characters."
                    else -> "The team update could not be saved. Check your connection and permissions, then try again."
                }
            } finally {
                saving = false
                dataLoading = false
            }
        }
    }

    LaunchedEffect(organizationId) {
        refresh()
    }

    if (addPositionOpen) {
        CreateMinistryPositionDialog(
            saving = saving,
            onDismiss = { addPositionOpen = false },
            onCreate = { name ->
                addPositionOpen = false
                runAction {
                    repository.createMinistryPosition(organizationId, name)
                }
            }
        )
    }

    selectedMember?.let { member ->
        ManageMemberPositionsDialog(
            member = member,
            positions = positions,
            currentPositionIds = memberPositions
                .filter { it.memberId == member.memberId }
                .map { it.positionId }
                .toSet(),
            saving = saving,
            onDismiss = { if (!saving) selectedMember = null },
            onSave = { selectedIds ->
                selectedMember = null
                runAction {
                    val currentIds = memberPositions
                        .filter { it.memberId == member.memberId }
                        .map { it.positionId }
                        .toSet()
                    val addIds = selectedIds - currentIds
                    val removeIds = currentIds - selectedIds

                    addIds.forEach { positionId ->
                        repository.assignPosition(
                            organizationId = organizationId,
                            memberId = member.memberId,
                            positionId = positionId,
                            assignedBy = currentUserId
                        )
                    }
                    removeIds.forEach { positionId ->
                        repository.removePosition(
                            organizationId = organizationId,
                            memberId = member.memberId,
                            positionId = positionId
                        )
                    }
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Team", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Member profiles, ministry roles, and join requests for your worship team.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TeamSummaryCard("Members", members.size.toString(), Modifier.weight(1f))
                TeamSummaryCard(
                    "Ministry roles",
                    positions.size.toString(),
                    Modifier.weight(1f)
                )
                if (workspace.role == "admin") {
                    TeamSummaryCard(
                        "Requests",
                        workspace.pendingRequests.size.toString(),
                        Modifier.weight(1f),
                        emphasize = workspace.pendingRequests.isNotEmpty()
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
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Names and ministry responsibilities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { refresh() }, enabled = !dataLoading && !saving) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refresh")
                }
            }
        }

        if (dataLoading && members.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Loading team profiles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (members.isEmpty()) {
            item {
                Text("No team members are available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(members, key = { it.memberId }) { member ->
                val memberPositionNames = memberPositions
                    .filter { it.memberId == member.memberId }
                    .mapNotNull { assigned ->
                        positions.firstOrNull { it.id == assigned.positionId }?.name
                    }
                    .sortedBy { it.lowercase() }

                TeamMemberProfileCard(
                    member = member,
                    isCurrentUser = member.userId == currentUserId,
                    assignedPositions = memberPositionNames,
                    canManagePositions = canManagePositions,
                    saving = saving,
                    onManagePositions = { selectedMember = member }
                )
            }
        }

        if (canManagePositions) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ministry positions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Create reusable roles for service assignments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { addPositionOpen = true }, enabled = !saving) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add")
                    }
                }
            }

            if (positions.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(15.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("No ministry positions yet", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Create roles such as Worship Leader, Vocalist, Guitarist, Keys, Bass, or Drums. You can then assign one or more positions to each member.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(positions, key = { it.id }) { position ->
                    val assignedCount = memberPositions.count { it.positionId == position.id }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(position.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "$assignedCount assigned member${if (assignedCount == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (workspace.role == "admin") {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Join requests (${workspace.pendingRequests.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Approve a request to add the person as a Team Member, or decline it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (workspace.pendingRequests.isEmpty()) {
                item {
                    Text("There are no pending join requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(workspace.pendingRequests, key = { it.id }) { request ->
                    TeamJoinRequestCard(
                        request = request,
                        loading = saving,
                        onApprove = { onReviewRequest(request.id, true) },
                        onDecline = { onReviewRequest(request.id, false) }
                    )
                }
            }
        }

        if (errorMessage.isNotBlank() || managementError.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        listOf(errorMessage, managementError).filter(String::isNotBlank).joinToString("\n"),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamMemberProfileCard(
    member: ServiceMemberOption,
    isCurrentUser: Boolean,
    assignedPositions: List<String>,
    canManagePositions: Boolean,
    saving: Boolean,
    onManagePositions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (isCurrentUser) "${member.displayName} (You)" else member.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    teamAccessLabel(member.teamRole),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (assignedPositions.isEmpty()) "No ministry position assigned"
                    else assignedPositions.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (canManagePositions) {
                    TextButton(onClick = onManagePositions, enabled = !saving) {
                        Text("Manage ministry positions")
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateMinistryPositionDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val normalizedLength = name.trim().length
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add ministry position") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Position name") },
                    placeholder = { Text("Example: Electric Guitar") },
                    singleLine = true,
                    enabled = !saving,
                    isError = normalizedLength > 0 && normalizedLength !in 2..60,
                    supportingText = { Text("$normalizedLength/60 characters") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                Text(
                    "Use a name your team will recognize. You can assign multiple positions to a member.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = !saving && normalizedLength in 2..60
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Create position")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManageMemberPositionsDialog(
    member: ServiceMemberOption,
    positions: List<MinistryPositionDto>,
    currentPositionIds: Set<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selectedIds by remember(member.memberId, currentPositionIds) {
        mutableStateOf(currentPositionIds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ministry positions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    member.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (positions.isEmpty()) {
                    Text(
                        "Create at least one ministry position before assigning roles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    positions.forEach { position ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !saving) {
                                selectedIds = if (position.id in selectedIds) {
                                    selectedIds - position.id
                                } else {
                                    selectedIds + position.id
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = position.id in selectedIds,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + position.id
                                    else selectedIds - position.id
                                },
                                enabled = !saving
                            )
                            Text(position.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedIds) },
                enabled = !saving && positions.isNotEmpty()
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save positions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun TeamJoinRequestCard(
    request: OrganizationJoinRequestDto,
    loading: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                request.requesterDisplayName.ifBlank { "UnityHub member" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (request.message.isNotBlank()) {
                Text(request.message, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("Approve")
                }
                TextButton(onClick = onDecline, enabled = !loading, modifier = Modifier.weight(1f)) {
                    Text("Decline")
                }
            }
        }
    }
}

@Composable
private fun TeamSummaryCard(
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun teamAccessLabel(role: String): String = when (role) {
    "admin" -> "Administrator"
    "worship_leader" -> "Worship Leader"
    "song_editor" -> "Song Editor"
    "viewer" -> "Viewer"
    else -> "Team Member"
}
