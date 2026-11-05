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
import com.unityhub.app.data.ServicePlanDto
import com.unityhub.app.data.ServicePlanRepository
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    organizationId: String,
    organizationName: String,
    role: String?,
    memberCount: Int,
    pendingCount: Int,
    onOpenSongs: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenServicePlan: (String) -> Unit,
    onOpenTeam: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { ServicePlanRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<ServicePlanDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            errorMessage = ""
            try {
                plans = repository.loadPlans(organizationId)
            } catch (_: Exception) {
                errorMessage = "Upcoming services could not be loaded. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(organizationId) {
        refresh()
    }

    val today = LocalDate.now()
    val allUpcomingPlans = remember(plans, today) {
        HomeDashboardRules.upcomingPlans(plans, today)
    }
    val upcomingPlans = allUpcomingPlans.take(4)
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


private fun dashboardRoleLabel(role: String?): String = when (role) {
    "admin" -> "Administrator"
    "worship_leader" -> "Worship Leader"
    "song_editor" -> "Song Editor"
    "viewer" -> "Viewer"
    "team_member" -> "Team Member"
    else -> "Team Member"
}
