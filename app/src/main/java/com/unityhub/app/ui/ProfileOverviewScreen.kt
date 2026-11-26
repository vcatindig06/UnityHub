package com.unityhub.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unityhub.app.data.AccountProfile
import com.unityhub.app.data.AccountRepository
import com.unityhub.app.data.ActivityHistoryRepository
import com.unityhub.app.data.ActivityHistoryRules
import com.unityhub.app.data.MemberProfileData
import com.unityhub.app.data.MemberProfileRepository
import com.unityhub.app.data.OrganizationRepository
import com.unityhub.app.data.SupabaseClientProvider
import com.unityhub.app.data.UserActivityDto
import kotlinx.coroutines.launch

private val editableSocialFields = listOf(
    "facebook" to "Facebook",
    "instagram" to "Instagram",
    "youtube" to "YouTube",
    "tiktok" to "TikTok",
    "website" to "Website"
)

@Composable
fun ProfileOverviewScreen(
    organizationId: String,
    organizationName: String,
    userId: String,
    role: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AccountRepository(SupabaseClientProvider.client) }
    val organizationRepository = remember { OrganizationRepository(SupabaseClientProvider.client) }
    val memberRepository = remember { MemberProfileRepository(SupabaseClientProvider.client) }
    val activityRepository = remember { ActivityHistoryRepository(SupabaseClientProvider.client) }

    var profile by remember { mutableStateOf<AccountProfile?>(null) }
    var contributions by remember { mutableStateOf<MemberProfileData?>(null) }
    var activity by remember { mutableStateOf<List<UserActivityDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activityLoading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var imageUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var activityError by remember { mutableStateOf("") }
    var activityFilter by rememberSaveable { mutableStateOf("All") }
    var showAllActivity by rememberSaveable { mutableStateOf(false) }
    var showAboutEditor by rememberSaveable { mutableStateOf(false) }
    var showSocialEditor by rememberSaveable { mutableStateOf(false) }
    var bioDraft by rememberSaveable { mutableStateOf("") }
    var socialDrafts by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var aboutError by remember { mutableStateOf("") }
    var socialError by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                imageUploading = true
                errorMessage = ""
                try {
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("The selected image could not be opened.")
                    repository.uploadProfileImage("avatar", bytes, mimeType)
                    profile = repository.loadProfile()
                } catch (exception: Exception) {
                    errorMessage = exception.message ?: "The profile photo could not be uploaded."
                } finally {
                    imageUploading = false
                }
            }
        }
    }

    LaunchedEffect(userId, organizationId) {
        loading = true
        activityLoading = true
        errorMessage = ""
        activityError = ""
        try {
            profile = repository.loadProfile()
            val workspace = organizationRepository.loadWorkspace(userId)
            val membership = workspace.members.firstOrNull { it.userId == userId }
            if (membership != null) {
                contributions = runCatching {
                    memberRepository.loadProfile(organizationId, membership.id)
                }.getOrNull()
            }
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Profile details could not be loaded."
        } finally {
            loading = false
        }
        try {
            activity = activityRepository.loadOwnHistory(userId, organizationId, limit = 100)
        } catch (_: Exception) {
            activityError = "Activity history could not be loaded. Check your connection and try again."
        } finally {
            activityLoading = false
        }
    }

    val avatarUrl = remember(profile?.avatarPath) { repository.publicMediaUrl(profile?.avatarPath) }
    val coverUrl = remember(profile?.coverPath) { repository.publicMediaUrl(profile?.coverPath) }
    val initials = profileInitials(profile?.displayName.orEmpty())
    val filteredActivity = activity.filter { entry ->
        when (activityFilter) {
            "Songs" -> entry.entityType in setOf("songs", "song_setlists", "song_setlist_items", "personal_song_favorites")
            "Services" -> entry.entityType.startsWith("service_") || entry.entityType in setOf("rehearsals", "rehearsal_assignments")
            "Team" -> entry.entityType in setOf("team_messages", "organization_members", "organization_join_requests", "member_positions", "ministry_positions")
            "Account" -> entry.entityType == "profiles"
            else -> true
        }
    }
    val displayedActivity = if (showAllActivity) filteredActivity else filteredActivity.take(8)
    val visibleSocialLinks = profile?.socialLinks.orEmpty()
        .filterValues(String::isNotBlank)
        .toList()
        .sortedBy { it.first.lowercase() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))
                ) {
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "Profile cover photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF183D2B), Color(0xFF35684B), Color(0xFFC37A54))
                                )
                            )
                        ) {
                            Text(
                                "WORSHIP · SERVE · GROW",
                                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
                                color = Color(0xFFFFF7E8),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(82.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Profile avatar",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                initials,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            profile?.displayName.orEmpty().ifBlank { "Your profile" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            profile?.email.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "$role · $organizationName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { imagePicker.launch("image/*") },
                        enabled = !imageUploading
                    ) {
                        if (imageUploading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text("Photo", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Loading profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            item { Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("About Me", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                bioDraft = profile?.bio.orEmpty()
                                aboutError = ""
                                showAboutEditor = true
                            },
                            enabled = !saving
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text("Edit", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Text(
                        profile?.bio?.takeIf(String::isNotBlank) ?: "No introduction has been added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (profile?.bio.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    DividerLine()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Ministry positions", fontWeight = FontWeight.SemiBold)
                    }
                    val ministryPositions = contributions?.ministryPositions.orEmpty()
                    if (ministryPositions.isEmpty()) {
                        Text("No ministry positions assigned.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(ministryPositions.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Team role · $role", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Social links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                socialDrafts = editableSocialFields.associate { (key, _) ->
                                    key to profile?.socialLinks?.get(key).orEmpty()
                                }
                                socialError = ""
                                showSocialEditor = true
                            },
                            enabled = !saving
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text(
                                if (visibleSocialLinks.isEmpty()) "Add social links" else "Edit",
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (visibleSocialLinks.isEmpty()) {
                        Text(
                            "No social links added yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        visibleSocialLinks.forEach { (key, value) ->
                            val label = editableSocialFields.firstOrNull { it.first == key }?.second
                                ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val target = if (value.startsWith("http://") || value.startsWith("https://")) {
                                            value
                                        } else {
                                            "https://$value"
                                        }
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Contribution Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val summary = contributions?.summary
                    if (summary == null) {
                        Text(
                            "Your team contributions will appear here when activity is recorded.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContributionMetric("Songs created", summary.songsCreated)
                            ContributionMetric("Rehearsals attended", summary.attendedRehearsals)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Actions history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Your activity in this team, visible only to you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("All", "Songs", "Services", "Team", "Account").forEach { filter ->
                            FilterChip(
                                selected = activityFilter == filter,
                                onClick = { activityFilter = filter; showAllActivity = false },
                                label = { Text(filter) }
                            )
                        }
                    }
                    when {
                        activityLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Loading activity…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        activityError.isNotBlank() -> Text(activityError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        filteredActivity.isEmpty() -> Text(
                            "Actions you take in UnityHub will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> displayedActivity.forEachIndexed { index, entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (entry.description.isNotBlank()) {
                                    Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    ActivityHistoryRules.formatTimestamp(entry.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (index < displayedActivity.lastIndex) DividerLine()
                        }
                        if (filteredActivity.size > 8) {
                            TextButton(
                                onClick = { showAllActivity = !showAllActivity },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showAllActivity) "Show less" else "Show all ${filteredActivity.size} activities")
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButtonFullWidth(
                onClick = onOpenSettings,
                text = "Account settings"
            )
        }
    }

    if (showAboutEditor) {
        AlertDialog(
            onDismissRequest = { if (!saving) showAboutEditor = false },
            title = { Text("Edit About Me") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bioDraft,
                        onValueChange = { bioDraft = it.take(300); aboutError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bio") },
                        placeholder = { Text("Share a little about yourself and how you serve.") },
                        minLines = 3,
                        maxLines = 5,
                        supportingText = { Text("${bioDraft.length}/300") },
                        enabled = !saving
                    )
                    if (aboutError.isNotBlank()) Text(aboutError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            aboutError = ""
                            try {
                                profile = repository.updateProfileDetails(
                                    bio = bioDraft,
                                    socialLinks = profile?.socialLinks.orEmpty()
                                )
                                showAboutEditor = false
                            } catch (exception: Exception) {
                                aboutError = exception.message ?: "Your bio could not be saved."
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { showAboutEditor = false }, enabled = !saving) { Text("Cancel") } }
        )
    }

    if (showSocialEditor) {
        AlertDialog(
            onDismissRequest = { if (!saving) showSocialEditor = false },
            title = { Text("Social links") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editableSocialFields.forEach { (key, label) ->
                        OutlinedTextField(
                            value = socialDrafts[key].orEmpty(),
                            onValueChange = { value -> socialDrafts = socialDrafts + (key to value.take(250)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(label) },
                            singleLine = true,
                            enabled = !saving
                        )
                    }
                    if (socialError.isNotBlank()) Text(socialError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            socialError = ""
                            try {
                                val otherLinks = profile?.socialLinks.orEmpty()
                                    .filterKeys { key -> editableSocialFields.none { it.first == key } }
                                profile = repository.updateProfileDetails(
                                    bio = profile?.bio.orEmpty(),
                                    socialLinks = otherLinks + socialDrafts
                                )
                                showSocialEditor = false
                            } catch (exception: Exception) {
                                socialError = exception.message ?: "Your social links could not be saved."
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Save links")
                }
            },
            dismissButton = { TextButton(onClick = { showSocialEditor = false }, enabled = !saving) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DividerLine() {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun OutlinedButtonFullWidth(onClick: () -> Unit, text: String) {
    androidx.compose.material3.OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Settings, contentDescription = null)
        Text(text, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RowScope.ContributionMetric(label: String, count: Int) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

private fun profileInitials(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifBlank { "UH" }
}
