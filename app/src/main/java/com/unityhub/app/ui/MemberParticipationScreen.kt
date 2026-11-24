package com.unityhub.app.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.MemberParticipationRepository
import com.unityhub.app.data.MemberParticipationRules
import com.unityhub.app.data.MemberParticipationSnapshot
import com.unityhub.app.data.TeamAccessRules
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MemberParticipationScreen(
    organizationId: String,
    organizationName: String,
    member: ServiceMemberOption,
    assignedPositions: List<String>,
    canViewParticipation: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { MemberParticipationRepository(SupabaseClientProvider.client) }
    var snapshot by remember(organizationId, member.memberId) {
        mutableStateOf<MemberParticipationSnapshot?>(null)
    }
    var loading by remember(organizationId, member.memberId) {
        mutableStateOf(canViewParticipation)
    }
    var error by remember(organizationId, member.memberId) { mutableStateOf("") }

    LaunchedEffect(organizationId, member.memberId, canViewParticipation) {
        snapshot = null
        error = ""
        if (!canViewParticipation) {
            loading = false
            return@LaunchedEffect
        }

        loading = true
        try {
            snapshot = repository.load(organizationId, member.memberId)
        } catch (_: Exception) {
            error = "Participation history could not be loaded. Refresh and try again."
        } finally {
            loading = false
        }
    }

    val serviceCount = snapshot?.services?.size ?: 0
    val rehearsalCount = snapshot?.rehearsals?.size ?: 0
    val attendedCount = snapshot?.rehearsals?.count {
        it.assignment.responseStatus == "attended"
    } ?: 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to team")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Member profile",
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        organizationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            member.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            TeamAccessRules.roleLabel(member.teamRole),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            if (assignedPositions.isEmpty()) {
                                "No ministry position assigned"
                            } else {
                                assignedPositions.joinToString(" · ")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        "Loading participation history",
                        modifier = Modifier.padding(start = 10.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (!canViewParticipation) {
            item {
                Text(
                    "Service and rehearsal history is visible to this member and authorized team leaders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (error.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (snapshot != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MemberMetricCard("Service slots", serviceCount, Modifier.weight(1f))
                    MemberMetricCard("Rehearsals", rehearsalCount, Modifier.weight(1f))
                    MemberMetricCard("Attended", attendedCount, Modifier.weight(1f))
                }
            }

            item {
                Text(
                    "Service history",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (snapshot!!.services.isEmpty()) {
                item {
                    Text(
                        "No service assignments recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(snapshot!!.services, key = { it.assignment.id }) { entry ->
                    MemberHistoryCard(
                        title = entry.servicePlan.title,
                        detail = listOfNotNull(
                            formatMemberDate(entry.servicePlan.serviceDate),
                            entry.servicePlan.startTime?.take(5)?.takeIf(String::isNotBlank),
                            entry.assignment.roleLabel
                        ).joinToString(" · "),
                        status = MemberParticipationRules.serviceStatusLabel(
                            entry.assignment.responseStatus
                        ),
                        statusColor = serviceStatusColor(entry.assignment.responseStatus)
                    )
                }
            }

            item {
                Text(
                    "Rehearsal history",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (snapshot!!.rehearsals.isEmpty()) {
                item {
                    Text(
                        "No rehearsal invitations recorded yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(snapshot!!.rehearsals, key = { it.assignment.id }) { entry ->
                    MemberHistoryCard(
                        title = entry.rehearsal.title,
                        detail = listOfNotNull(
                            formatMemberDate(entry.rehearsal.rehearsalDate),
                            entry.rehearsal.startTime.take(5).takeIf(String::isNotBlank),
                            entry.rehearsal.location.takeIf(String::isNotBlank)
                        ).joinToString(" · "),
                        status = MemberParticipationRules.rehearsalStatusLabel(
                            entry.assignment.responseStatus
                        ),
                        statusColor = rehearsalStatusColor(entry.assignment.responseStatus)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun MemberMetricCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
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
private fun MemberHistoryCard(
    title: String,
    detail: String,
    status: String,
    statusColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

@Composable
private fun serviceStatusColor(status: String) = when (status) {
    "confirmed" -> MaterialTheme.colorScheme.primary
    "declined" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun rehearsalStatusColor(status: String) = when (status) {
    "attended", "available" -> MaterialTheme.colorScheme.primary
    "absent", "unavailable" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
}

private fun formatMemberDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        LocalDate.parse(value).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }.getOrDefault(value)
}
