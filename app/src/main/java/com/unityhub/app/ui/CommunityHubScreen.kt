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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.MinistryPositionDto
import com.unityhub.app.data.MemberMinistryPositionDto
import com.unityhub.app.data.ServiceAssignmentOverviewEntry
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.ServicePlanRepository
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.TeamManagementRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityHubScreen(
    organizationId: String,
    memberCount: Int,
    onOpenTeam: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenServicePlan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val client = remember { SupabaseClientProvider.client }
    val assignmentRepository = remember { ServiceAssignmentRepository(client) }
    val teamRepository = remember { TeamManagementRepository(client) }
    val serviceRepository = remember { ServicePlanRepository(client) }
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<ServiceMemberOption>>(emptyList()) }
    var positions by remember { mutableStateOf<List<MinistryPositionDto>>(emptyList()) }
    var memberPositions by remember { mutableStateOf<List<MemberMinistryPositionDto>>(emptyList()) }
    var upcomingServices by remember { mutableStateOf<List<ServicePlanDto>>(emptyList()) }
    var serviceAssignments by remember { mutableStateOf<List<ServiceAssignmentOverviewEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf("") }

    fun refresh(showIndicator: Boolean = false) {
        scope.launch {
            refreshing = showIndicator
            loading = true
            loadingError = ""
            try {
                members = assignmentRepository.loadTeamMembers(organizationId)
                positions = teamRepository.loadMinistryPositions(organizationId)
                memberPositions = teamRepository.loadMemberPositions(organizationId)
                val todayValue = LocalDate.now().toString()
                upcomingServices = serviceRepository.loadPlans(organizationId).filter { plan ->
                    plan.status != "completed" && plan.status != "cancelled" &&
                        (plan.serviceDate.isNullOrBlank() || plan.serviceDate >= todayValue)
                }.take(4)
                serviceAssignments = assignmentRepository.loadOrganizationAssignments(organizationId)
            } catch (_: Exception) {
                loadingError = "Some community information could not be loaded. Check your connection and refresh."
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(organizationId) { refresh() }

    val assignedMemberIds = remember(memberPositions) { memberPositions.map { it.memberId }.toSet() }
    val assignedPositionIds = remember(memberPositions) { memberPositions.map { it.positionId }.toSet() }
    val teamSize = if (members.isEmpty()) memberCount else members.size
    val unassignedMembers = (teamSize - assignedMemberIds.size).coerceAtLeast(0)
    val servicesById = remember(serviceAssignments) { serviceAssignments.groupBy { it.servicePlan.id } }
    val today = LocalDate.now()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refresh(showIndicator = true) },
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Your community", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("A snapshot of your worship team and what is coming up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CommunityMetric("Members", if (loading) "—" else teamSize.toString(), Modifier.weight(1f))
                    CommunityMetric("Ministries", if (loading) "—" else positions.size.toString(), Modifier.weight(1f))
                    CommunityMetric("Upcoming", if (loading) "—" else upcomingServices.size.toString(), Modifier.weight(1f))
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ministry coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        when {
                            loading -> Text("Loading team coverage…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            positions.isEmpty() -> Text("No ministry positions have been created yet. Add positions from Team to start organizing ministry coverage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> {
                                val assignedCount = assignedMemberIds.size.coerceAtMost(teamSize)
                                Text(assignedCount.toString() + " of " + teamSize + " members have at least one ministry position.", style = MaterialTheme.typography.bodyMedium)
                                Text(unassignedMembers.toString() + if (unassignedMembers == 1) " member has no ministry position assigned." else " members have no ministry position assigned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(assignedPositionIds.size.toString() + " of " + positions.size + " ministry positions have at least one member.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (positions.isNotEmpty() && !loading) {
                            positions.take(5).forEach { position ->
                                val count = memberPositions.count { it.positionId == position.id }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(position.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text(if (count == 0) "Needs coverage" else count.toString() + if (count == 1) " member" else " members", style = MaterialTheme.typography.labelMedium, color = if (count == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (positions.size > 5) Text((positions.size - 5).toString() + " more ministries in Team", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = onOpenTeam, modifier = Modifier.fillMaxWidth()) { Text("Manage team and ministries") }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Upcoming services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onOpenServices) { Text("View all") }
                }
            }
            if (loading && upcomingServices.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Loading service schedule…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (upcomingServices.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Text("No upcoming services scheduled.", modifier = Modifier.fillMaxWidth().padding(15.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(upcomingServices, key = { it.id }) { service ->
                    val entries = servicesById[service.id].orEmpty()
                    val confirmed = entries.count { it.assignment.responseStatus == "confirmed" }
                    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenServicePlan(service.id) }, shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(service.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(communityDateLabel(service.serviceDate, service.startTime, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(entries.size.toString() + " assigned " + (if (entries.size == 1) "role" else "roles") + " · " + confirmed + " confirmed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (loadingError.isNotBlank()) {
                item {
                    Text(loadingError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = null); Text("Try again") }
                }
            }
            item {
                Spacer(Modifier.height(2.dp))
                CommunityDestinationCard("Team", "Members, ministry roles, requests and team access", Icons.Default.Groups, onOpenTeam)
            }
            item { CommunityDestinationCard("Team Chat", "Conversations, updates and team coordination", Icons.Default.ChatBubbleOutline, onOpenChat) }
            item { CommunityDestinationCard("My Profile", "Your personal information, contributions and links", Icons.Default.Person, onOpenProfile) }
        }
    }
}

@Composable
private fun CommunityMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommunityDestinationCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun communityDateLabel(dateValue: String?, timeValue: String?, today: LocalDate): String {
    val date = dateValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "Date to be confirmed"
    val dateLabel = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }
    val timeLabel = timeValue?.let { runCatching { java.time.LocalTime.parse(it).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())) }.getOrNull() }
    return if (timeLabel.isNullOrBlank()) dateLabel else dateLabel + " · " + timeLabel
}
