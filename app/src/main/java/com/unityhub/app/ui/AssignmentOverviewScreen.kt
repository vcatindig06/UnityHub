package com.unityhub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.unityhub.app.data.ServiceAssignmentOverviewEntry
import com.unityhub.app.data.ServiceAssignmentRepository
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AssignmentOverviewScreen(
    organizationId: String,
    onBack: () -> Unit,
    onOpenServicePlan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { ServiceAssignmentRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ServiceAssignmentOverviewEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(AssignmentResponseFilter.ALL) }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                entries = repository.loadOrganizationAssignments(organizationId)
            } catch (_: Exception) {
                error = "The team assignment overview could not be loaded. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(organizationId) { reload() }

    val today = LocalDate.now()
    val upcomingEntries = remember(entries, today) {
        AssignmentOverviewRules.upcoming(entries, today)
    }
    val visibleEntries = remember(upcomingEntries, selectedFilter, searchQuery) {
        AssignmentOverviewRules.filter(upcomingEntries, selectedFilter, searchQuery)
    }
    val awaitingCount = upcomingEntries.count { it.assignment.responseStatus == "pending" }
    val confirmedCount = upcomingEntries.count { it.assignment.responseStatus == "confirmed" }
    val declinedCount = upcomingEntries.count { it.assignment.responseStatus == "declined" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to service planning")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Assignment overview",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Track every member's response for upcoming services",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { reload() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh assignments")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssignmentSummaryCard(
                    label = "Awaiting",
                    value = awaitingCount,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.tertiary
                )
                AssignmentSummaryCard(
                    label = "Confirmed",
                    value = confirmedCount,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary
                )
                AssignmentSummaryCard(
                    label = "Can't serve",
                    value = declinedCount,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search member, role or service") },
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssignmentResponseFilter.values().forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        if (loading && entries.isEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        } else if (error.isNotBlank() && entries.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { reload() }, enabled = !loading) {
                            Text("Try again")
                        }
                    }
                }
            }
        } else if (upcomingEntries.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("No upcoming assignments", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Assignments for current and future services will appear here with each member's response.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (visibleEntries.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "No assignments match this search or response filter.",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(visibleEntries, key = { it.assignment.id }) { entry ->
                AssignmentOverviewCard(
                    entry = entry,
                    onClick = { onOpenServicePlan(entry.servicePlan.id) }
                )
            }
        }

        if (error.isNotBlank() && entries.isNotEmpty()) {
            item {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AssignmentSummaryCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AssignmentOverviewCard(
    entry: ServiceAssignmentOverviewEntry,
    onClick: () -> Unit
) {
    val responseStatus = entry.assignment.responseStatus
    val responseLabel = when (responseStatus) {
        "confirmed" -> "Confirmed"
        "declined" -> "Can't serve"
        else -> "Awaiting response"
    }
    val responseColor = when (responseStatus) {
        "confirmed" -> MaterialTheme.colorScheme.primary
        "declined" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.servicePlan.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    responseLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = responseColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "${entry.memberDisplayName} · ${entry.assignment.roleLabel}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                assignmentDateTimeLabel(entry.servicePlan.serviceDate, entry.servicePlan.startTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (responseStatus == "declined" && entry.assignment.responseNote.isNotBlank()) {
                Text(
                    "Member note",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(entry.assignment.responseNote, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Open service plan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun assignmentDateTimeLabel(dateValue: String?, timeValue: String?): String {
    val date = dateValue?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return "Date to be confirmed"
    val dateLabel = date.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    val timeLabel = timeValue?.let {
        runCatching {
            LocalTime.parse(it).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
        }.getOrNull()
    }
    return if (timeLabel.isNullOrBlank()) dateLabel else "$dateLabel · $timeLabel"
}
