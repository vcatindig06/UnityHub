package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.unityhub.app.data.TeamAccessRules
import com.unityhub.app.data.TeamDirectoryRules
import com.unityhub.app.data.TeamManagementRepository
import kotlinx.coroutines.launch

@Composable
fun TeamManagementScreen(
    workspace: OrganizationWorkspace,
    currentUserId: String,
    onReviewRequest: (String, Boolean) -> Unit,
    errorMessage: String,
    onWorkspaceChanged: () -> Unit = {},
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
    var editingPosition by remember { mutableStateOf<MinistryPositionDto?>(null) }
    var deletingPosition by remember { mutableStateOf<MinistryPositionDto?>(null) }
    var selectedMember by remember { mutableStateOf<ServiceMemberOption?>(null) }
    var memberAccessTarget by remember { mutableStateOf<ServiceMemberOption?>(null) }
    var memberRemovalTarget by remember { mutableStateOf<ServiceMemberOption?>(null) }
    var ownershipTransferOpen by rememberSaveable { mutableStateOf(false) }
    var memberSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPositionFilter by rememberSaveable { mutableStateOf("") }

    val assignedPositionsByMember = remember(memberPositions, positions) {
        memberPositions.groupBy { it.memberId }.mapValues { (_, assignments) ->
            assignments.mapNotNull { assignment ->
                positions.firstOrNull { it.id == assignment.positionId }?.name
            }.sortedBy { it.lowercase() }
        }
    }
    val filteredMembers = remember(
        members,
        assignedPositionsByMember,
        memberSearchQuery,
        selectedPositionFilter
    ) {
        TeamDirectoryRules.filterMembers(
            members = members,
            assignedPositionsByMember = assignedPositionsByMember,
            searchQuery = memberSearchQuery,
            selectedPosition = selectedPositionFilter
        )
    }

    val canManagePositions = workspace.role == "admin" || workspace.role == "worship_leader"
    val canManageMembers = workspace.role == "admin"
    val organizationOwnerId = workspace.organization?.createdBy.orEmpty()
    val administratorCount = members.count { it.teamRole == "admin" }

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
                onWorkspaceChanged()
            } catch (exception: Exception) {
                val serverMessage = exception.message.orEmpty().lowercase()
                managementError = when {
                    "already exists" in serverMessage ->
                        "That ministry position already exists. Choose a different name."
                    "must contain 2" in serverMessage ->
                        "Use a ministry position name between 2 and 60 characters."
                    "ministry position no longer exists" in serverMessage ->
                        "That ministry position changed or was removed. Refresh the Team screen and try again."
                    "last administrator" in serverMessage ->
                        "UnityHub must keep at least one administrator. Assign another administrator before changing or removing this member."
                    "organization owner" in serverMessage ->
                        "The organization owner must transfer ownership before their access can be changed or removed."
                    "only an administrator" in serverMessage ->
                        "Only a team administrator can manage member access."
                    "current organization owner" in serverMessage ->
                        "Only the current organization owner can transfer ownership."
                    "must already be a member" in serverMessage ->
                        "Choose someone who is already a member of this team."
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

    editingPosition?.let { position ->
        EditMinistryPositionDialog(
            position = position,
            positions = positions,
            saving = saving,
            onDismiss = { if (!saving) editingPosition = null },
            onSave = { name ->
                editingPosition = null
                runAction {
                    repository.updateMinistryPosition(
                        organizationId = organizationId,
                        positionId = position.id,
                        name = name
                    )
                }
            }
        )
    }

    deletingPosition?.let { position ->
        val assignedCount = memberPositions.count { it.positionId == position.id }
        DeleteMinistryPositionDialog(
            position = position,
            assignedCount = assignedCount,
            saving = saving,
            onDismiss = { if (!saving) deletingPosition = null },
            onDelete = {
                deletingPosition = null
                runAction {
                    repository.deleteMinistryPosition(organizationId, position.id)
                    if (selectedPositionFilter == position.name) {
                        selectedPositionFilter = ""
                    }
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

    memberAccessTarget?.let { member ->
        val isLastAdministrator = member.teamRole == "admin" && administratorCount <= 1
        ManageMemberAccessDialog(
            member = member,
            allowedRoles = if (isLastAdministrator) listOf("admin") else TeamAccessRules.availableRoles,
            canRemove = !isLastAdministrator,
            saving = saving,
            onDismiss = { if (!saving) memberAccessTarget = null },
            onSaveRole = { newRole ->
                memberAccessTarget = null
                runAction { repository.changeMemberRole(member.memberId, newRole) }
            },
            onRemove = {
                memberAccessTarget = null
                memberRemovalTarget = member
            }
        )
    }

    memberRemovalTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { if (!saving) memberRemovalTarget = null },
            title = { Text("Remove team member?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove ${member.displayName} from ${workspace.organization?.name ?: "this team"}?")
                    Text(
                        "Their UnityHub account will remain active, but they will lose access to this team's workspace and resources.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        memberRemovalTarget = null
                        runAction { repository.removeMember(member.memberId) }
                    },
                    enabled = !saving
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Remove member")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberRemovalTarget = null }, enabled = !saving) {
                    Text("Cancel")
                }
            }
        )
    }

    if (ownershipTransferOpen) {
        TransferOrganizationOwnershipDialog(
            currentUserId = currentUserId,
            members = members,
            saving = saving,
            onDismiss = { if (!saving) ownershipTransferOpen = false },
            onTransfer = { newOwner ->
                ownershipTransferOpen = false
                runAction {
                    repository.transferOrganizationOwnership(organizationId, newOwner.userId)
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

        item {
            OutlinedTextField(
                value = memberSearchQuery,
                onValueChange = { memberSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search members") },
                placeholder = { Text("Name, team role, or ministry position") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                singleLine = true
            )
        }

        if (positions.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPositionFilter.isBlank(),
                        onClick = { selectedPositionFilter = "" },
                        label = { Text("All positions") }
                    )
                    positions.forEach { position ->
                        FilterChip(
                            selected = selectedPositionFilter == position.name,
                            onClick = {
                                selectedPositionFilter = if (selectedPositionFilter == position.name) {
                                    ""
                                } else {
                                    position.name
                                }
                            },
                            label = { Text(position.name) }
                        )
                    }
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
        } else if (filteredMembers.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No members match these filters.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Try another name or ministry position, or clear the filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            memberSearchQuery = ""
                            selectedPositionFilter = ""
                        }
                    ) {
                        Text("Clear filters")
                    }
                }
            }
        } else {
            item {
                Text(
                    "Showing ${filteredMembers.size} of ${members.size} members",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(filteredMembers, key = { it.memberId }) { member ->
                TeamMemberProfileCard(
                    member = member,
                    isCurrentUser = member.userId == currentUserId,
                    assignedPositions = assignedPositionsByMember[member.memberId].orEmpty(),
                    canManagePositions = canManagePositions,
                    canManageMembers = TeamAccessRules.canManageMember(
                        isAdmin = canManageMembers,
                        currentUserId = currentUserId,
                        organizationOwnerId = organizationOwnerId,
                        targetUserId = member.userId
                    ),
                    isOrganizationOwner = member.userId == organizationOwnerId,
                    saving = saving,
                    onManagePositions = { selectedMember = member },
                    onManageAccess = { memberAccessTarget = member }
                )
            }
        }

        if (TeamAccessRules.canTransferOwnership(
                currentUserId = currentUserId,
                organizationOwnerId = organizationOwnerId,
                targetUserId = members.firstOrNull { it.userId != currentUserId }?.userId.orEmpty()
            )) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Organization ownership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Transfer the organization to another existing team member. The new owner becomes an administrator, and your account remains in the team as a Team Member.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { ownershipTransferOpen = true }, enabled = !saving) {
                            Text("Transfer ownership")
                        }
                    }
                }
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
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { editingPosition = position },
                                    enabled = !saving,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit ${position.name}"
                                    )
                                }
                                IconButton(
                                    onClick = { deletingPosition = position },
                                    enabled = !saving,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete ${position.name}",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
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
    canManageMembers: Boolean,
    isOrganizationOwner: Boolean,
    saving: Boolean,
    onManagePositions: () -> Unit,
    onManageAccess: () -> Unit
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
                if (isOrganizationOwner) {
                    Text(
                        "Organization owner",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                if (canManageMembers && !isOrganizationOwner && !isCurrentUser) {
                    TextButton(onClick = onManageAccess, enabled = !saving) {
                        Text("Manage access")
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageMemberAccessDialog(
    member: ServiceMemberOption,
    allowedRoles: List<String>,
    canRemove: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSaveRole: (String) -> Unit,
    onRemove: () -> Unit
) {
    var selectedRole by rememberSaveable(member.memberId) { mutableStateOf(member.teamRole) }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage team access") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(member.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Choose the member's access role.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { roleMenuExpanded = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                        Text(TeamAccessRules.roleLabel(selectedRole))
                    }
                    DropdownMenu(expanded = roleMenuExpanded, onDismissRequest = { roleMenuExpanded = false }) {
                        allowedRoles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(TeamAccessRules.roleLabel(role)) },
                                onClick = { selectedRole = role; roleMenuExpanded = false },
                                enabled = !saving
                            )
                        }
                    }
                }
                if (selectedRole == "admin" && member.teamRole != "admin") {
                    Text("Administrator access includes team-wide member management and approval controls.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (!canRemove) {
                    Text(
                        "This is the organization's last administrator. Assign another administrator before changing or removing this access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = onRemove, enabled = !saving && canRemove) { Text("Remove member from team") }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveRole(selectedRole) }, enabled = !saving && selectedRole != member.teamRole) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save role")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
}

@Composable
private fun TransferOrganizationOwnershipDialog(
    currentUserId: String,
    members: List<ServiceMemberOption>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onTransfer: (ServiceMemberOption) -> Unit
) {
    val candidates = remember(members, currentUserId) { members.filter { it.userId != currentUserId } }
    var selectedUserId by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedMember = candidates.firstOrNull { it.userId == selectedUserId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer organization ownership") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose an existing member to become the new organization owner.", style = MaterialTheme.typography.bodyMedium)
                Box {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        enabled = !saving && candidates.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedMember?.let {
                            "${it.displayName} · ${TeamAccessRules.roleLabel(it.teamRole)}"
                        } ?: "Choose a team member")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        candidates.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(member.displayName)
                                        Text(TeamAccessRules.roleLabel(member.teamRole), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { selectedUserId = member.userId; menuExpanded = false },
                                enabled = !saving
                            )
                        }
                    }
                }
                Text(
                    "The selected member becomes an Administrator and the new owner. Your account remains in the team as a Team Member. Only the new owner can transfer ownership again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { selectedMember?.let(onTransfer) }, enabled = !saving && selectedMember != null) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Transfer ownership")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } }
    )
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
private fun EditMinistryPositionDialog(
    position: MinistryPositionDto,
    positions: List<MinistryPositionDto>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable(position.id) { mutableStateOf(position.name) }
    val normalizedName = name.trim()
    val normalizedLength = normalizedName.length
    val duplicate = positions.any { other ->
        other.id != position.id &&
            other.name.trim().equals(normalizedName, ignoreCase = true)
    }
    val canSave = !saving && normalizedLength in 2..60 && !duplicate && normalizedName != position.name

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Edit ministry position") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Position name") },
                    singleLine = true,
                    enabled = !saving,
                    isError = normalizedLength !in 2..60 || duplicate,
                    supportingText = {
                        Text(
                            when {
                                duplicate -> "Another ministry position already uses this name."
                                else -> "$normalizedLength/60 characters"
                            }
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words
                    )
                )
                Text(
                    "Renaming a position updates the label everywhere it appears in the team directory and service assignment lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(normalizedName) }, enabled = canSave) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteMinistryPositionDialog(
    position: MinistryPositionDto,
    assignedCount: Int,
    saving: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val assignedMemberLabel = if (assignedCount == 1) "member" else "members"
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Delete ministry position?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Delete \"" + position.name + "\" from this team's ministry positions?")
                Text(
                    if (assignedCount == 0) {
                        "This position is not currently assigned to any team member."
                    } else {
                        "This position is assigned to $assignedCount team $assignedMemberLabel. Deleting it will also remove it from those member profiles."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (assignedCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "This action cannot be undone. Existing service assignments keep their saved role text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Delete position", color = MaterialTheme.colorScheme.error)
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

private fun teamAccessLabel(role: String): String = TeamAccessRules.roleLabel(role)
