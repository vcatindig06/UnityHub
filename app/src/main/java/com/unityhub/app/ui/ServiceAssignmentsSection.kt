package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.ServiceAssignmentDto
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch

@Composable
fun ServiceAssignmentsSection(
    servicePlanId: String,
    organizationId: String,
    currentUserId: String,
    canManage: Boolean,
    allowResponses: Boolean
) {
    val repository = remember { ServiceAssignmentRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    var assignments by remember(servicePlanId) { mutableStateOf<List<ServiceAssignmentDto>>(emptyList()) }
    var members by remember(servicePlanId) { mutableStateOf<List<ServiceMemberOption>>(emptyList()) }
    var suggestedRoles by remember(servicePlanId) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember(servicePlanId) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var addDialogOpen by rememberSaveable(servicePlanId) { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            try {
                assignments = repository.loadAssignments(servicePlanId, organizationId)
                members = repository.loadTeamMembers(organizationId)
                suggestedRoles = repository.loadSuggestedRoles(organizationId)
                error = ""
            } catch (_: Exception) {
                error = "Could not load service assignments. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            saving = true
            error = ""
            try {
                action()
                assignments = repository.loadAssignments(servicePlanId, organizationId)
            } catch (exception: Exception) {
                error = exception.message ?: "The assignment could not be updated."
            } finally {
                saving = false
            }
        }
    }

    LaunchedEffect(servicePlanId, organizationId) { reload() }

    if (addDialogOpen) {
        AddServiceAssignmentDialog(
            members = members,
            suggestedRoles = suggestedRoles,
            saving = saving,
            onDismiss = { addDialogOpen = false },
            onAssign = { member, role ->
                addDialogOpen = false
                runAction {
                    repository.assignMember(
                        organizationId = organizationId,
                        servicePlanId = servicePlanId,
                        memberId = member.memberId,
                        roleLabel = role,
                        assignedBy = currentUserId
                    )
                }
            }
        )
    }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Team assignments", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${assignments.size} assigned roles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canManage && allowResponses) {
                    Button(
                        onClick = { addDialogOpen = true },
                        enabled = !saving && !loading && members.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null)
                        Text("Assign")
                    }
                }
            }

            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (assignments.isEmpty()) {
                Text(
                    if (canManage) "No one has been assigned yet. Assign team members to their ministry roles for this service."
                    else "No team members have been assigned to this service yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                assignments.forEach { assignment ->
                    val member = members.firstOrNull { it.memberId == assignment.memberId }
                    val isCurrentUser = member?.userId == currentUserId
                    ServiceAssignmentCard(
                        assignment = assignment,
                        memberName = member?.displayName ?: "Team member",
                        isCurrentUser = isCurrentUser,
                        canManage = canManage,
                        canRespond = allowResponses && isCurrentUser,
                        saving = saving,
                        onConfirm = {
                            runAction {
                                repository.respond(assignment.id, assignment.memberId, "confirmed")
                            }
                        },
                        onDecline = {
                            runAction {
                                repository.respond(assignment.id, assignment.memberId, "declined")
                            }
                        },
                        onRemove = {
                            runAction {
                                repository.removeAssignment(assignment.id, organizationId)
                            }
                        }
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { reload() }, enabled = !loading && !saving) {
                Text("Refresh assignments")
            }
        }
    }
}

@Composable
private fun ServiceAssignmentCard(
    assignment: ServiceAssignmentDto,
    memberName: String,
    isCurrentUser: Boolean,
    canManage: Boolean,
    canRespond: Boolean,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(assignment.roleLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isCurrentUser) "You · $memberName" else memberName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        responseLabel(assignment.responseStatus),
                        style = MaterialTheme.typography.labelMedium,
                        color = responseColor(assignment.responseStatus)
                    )
                }
                if (canManage) {
                    IconButton(onClick = onRemove, enabled = !saving) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove assignment", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (canRespond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirm, enabled = !saving && assignment.responseStatus != "confirmed") {
                        Text(if (assignment.responseStatus == "confirmed") "Confirmed" else "Confirm")
                    }
                    TextButton(onClick = onDecline, enabled = !saving && assignment.responseStatus != "declined") {
                        Text(if (assignment.responseStatus == "declined") "Declined" else "Can't serve")
                    }
                }
            }
        }
    }
}

@Composable
private fun AddServiceAssignmentDialog(
    members: List<ServiceMemberOption>,
    suggestedRoles: List<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onAssign: (ServiceMemberOption, String) -> Unit
) {
    var selectedMember by remember { mutableStateOf<ServiceMemberOption?>(null) }
    var memberMenuOpen by remember { mutableStateOf(false) }
    var roleMenuOpen by remember { mutableStateOf(false) }
    var role by rememberSaveable { mutableStateOf("") }
    val availableRoles = (suggestedRoles + listOf(
        "Worship Leader",
        "Main Vocal",
        "Backup Vocal",
        "Acoustic Guitar",
        "Electric Guitar",
        "Keys",
        "Bass",
        "Drums",
        "Audio / Media"
    )).distinctBy { it.lowercase() }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Assign a team member") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Team member", style = MaterialTheme.typography.labelLarge)
                Box {
                    TextButton(
                        onClick = { memberMenuOpen = true },
                        enabled = !saving
                    ) {
                        Text(selectedMember?.displayName ?: "Choose a team member")
                    }
                    DropdownMenu(
                        expanded = memberMenuOpen,
                        onDismissRequest = { memberMenuOpen = false }
                    ) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(member.displayName)
                                        Text(
                                            member.teamRole.replace('_', ' ').replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedMember = member
                                    memberMenuOpen = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ministry role *") },
                    placeholder = { Text("Example: Electric Guitar") },
                    singleLine = true,
                    enabled = !saving
                )
                Box {
                    TextButton(
                        onClick = { roleMenuOpen = true },
                        enabled = !saving
                    ) {
                        Text("Choose a suggested role")
                    }
                    DropdownMenu(
                        expanded = roleMenuOpen,
                        onDismissRequest = { roleMenuOpen = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        availableRoles.forEach { suggested ->
                            DropdownMenuItem(
                                text = { Text(suggested) },
                                onClick = {
                                    role = suggested
                                    roleMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val member = selectedMember ?: return@Button
                    onAssign(member, role.trim())
                },
                enabled = !saving && selectedMember != null && role.trim().length in 2..80
            ) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Save assignment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

private fun responseLabel(status: String): String = when (status) {
    "confirmed" -> "Confirmed"
    "declined" -> "Unable to serve"
    else -> "Awaiting response"
}

@Composable
private fun responseColor(status: String) = when (status) {
    "confirmed" -> androidx.compose.ui.graphics.Color(0xFF28745D)
    "declined" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
