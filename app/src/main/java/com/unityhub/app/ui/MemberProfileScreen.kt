package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Person
import com.unityhub.app.data.AccountProfile
import com.unityhub.app.data.AccountRepository
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unityhub.app.data.MemberProfileData
import com.unityhub.app.data.MemberProfileRepository
import com.unityhub.app.data.MemberProfileRules
import com.unityhub.app.data.MemberRehearsalActivity
import com.unityhub.app.data.MemberServiceActivity
import com.unityhub.app.data.ServiceMemberOption
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MemberProfileScreen(
    organizationId: String,
    member: ServiceMemberOption,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember(organizationId) {
        MemberProfileRepository(SupabaseClientProvider.client)
    }
    val accountRepository = remember { AccountRepository(SupabaseClientProvider.client) }
    var publicProfile by remember(organizationId, member.userId) {
        mutableStateOf<AccountProfile?>(null)
    }
    val scope = rememberCoroutineScope()
    var profile by remember(organizationId, member.memberId) {
        mutableStateOf<MemberProfileData?>(null)
    }
    var loading by remember(organizationId, member.memberId) { mutableStateOf(true) }
    var error by remember(organizationId, member.memberId) { mutableStateOf("") }

    fun reload() {
        scope.launch {
            loading = true
            error = ""
            try {
                profile = repository.loadProfile(organizationId, member.memberId)
                publicProfile = runCatching {
                    accountRepository.loadPublicProfile(member.userId)
                }.getOrNull()
            } catch (_: Exception) {
                error = "This member's activity could not be loaded. Check your connection and try again."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(organizationId, member.memberId) {
        reload()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
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
                    Text("Team member", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        member.displayName,
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { reload() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh member profile")
                }
            }
        }

        if (loading && profile == null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading member activity", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (error.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = { reload() }, enabled = !loading) {
                            Text("Try again")
                        }
                    }
                }
            }
        }

        profile?.let { data ->
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val avatarUrl = accountRepository.publicMediaUrl(publicProfile?.avatarPath)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "${member.displayName} profile photo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(member.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(memberRoleLabel(member.teamRole), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Member since ${formatMemberDate(data.joinedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!publicProfile?.bio.isNullOrBlank()) {
                            Text(publicProfile?.bio.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                        val socialLinks = publicProfile?.socialLinks.orEmpty()
                        if (socialLinks.isNotEmpty()) {
                            Text(
                                socialLinks.entries.sortedBy { it.key }.joinToString(" · ") { (key, value) -> "${key.replaceFirstChar { it.uppercase() }}: $value" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            "Team role: ${memberRoleLabel(member.teamRole)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (data.ministryPositions.isEmpty()) "No ministry positions assigned"
                            else data.ministryPositions.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            item {
                Text(
                    "Participation overview",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberProfileMetricCard("Upcoming services", data.summary.upcomingServices, Modifier.weight(1f))
                        MemberProfileMetricCard("Confirmed", data.summary.confirmedServices, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberProfileMetricCard("Unable to serve", data.summary.declinedServices, Modifier.weight(1f))
                        MemberProfileMetricCard("Awaiting service reply", data.summary.awaitingServiceResponses, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberProfileMetricCard("Upcoming rehearsals", data.summary.upcomingRehearsals, Modifier.weight(1f))
                        MemberProfileMetricCard("Attended rehearsals", data.summary.attendedRehearsals, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemberProfileMetricCard("Absent", data.summary.absentRehearsals, Modifier.weight(1f))
                        MemberProfileMetricCard("Attendance not recorded", data.summary.unrecordedRehearsals, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    "Recent service assignments",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (data.recentServices.isEmpty()) {
                item {
                    Text(
                        "No service assignments have been recorded for this member yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(data.recentServices, key = { it.assignmentId }) { activity ->
                    MemberServiceActivityCard(activity)
                }
            }

            item {
                Text(
                    "Recent rehearsals",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (data.recentRehearsals.isEmpty()) {
                item {
                    Text(
                        "No rehearsal invitations have been recorded for this member yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(data.recentRehearsals, key = { it.assignmentId }) { activity ->
                    MemberRehearsalActivityCard(activity)
                }
            }
        }
    }
}

@Composable
private fun MemberProfileMetricCard(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
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
private fun MemberServiceActivityCard(activity: MemberServiceActivity) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(activity.serviceTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                activity.roleLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                activityDateTime(activity.serviceDate, activity.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${MemberProfileRules.serviceResponseLabel(activity.responseStatus)} · ${MemberProfileRules.serviceStatusLabel(activity.serviceStatus)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun MemberRehearsalActivityCard(activity: MemberRehearsalActivity) {
    Card(shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(activity.rehearsalTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                activityDateTime(activity.rehearsalDate, activity.startTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (activity.location.isNotBlank()) {
                Text(
                    activity.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                MemberProfileRules.rehearsalResponseLabel(activity.rehearsalStatus, activity.responseStatus),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun memberRoleLabel(role: String): String = when (role) {
    "admin" -> "Administrator"
    "worship_leader" -> "Worship Leader"
    "song_editor" -> "Song Editor"
    else -> "Team Member"
}

private fun formatMemberDate(value: String): String {
    val date = runCatching { LocalDate.parse(value.take(10)) }.getOrNull() ?: return value.take(10)
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun activityDateTime(date: String?, time: String?): String {
    val formattedDate = date?.takeIf(String::isNotBlank)?.let(::formatMemberDate) ?: "Date not set"
    val formattedTime = time?.takeIf(String::isNotBlank)?.take(5)
    return if (formattedTime == null) formattedDate else "$formattedDate · $formattedTime"
}
