package com.unityhub.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.unityhub.app.data.MemberProfileData
import com.unityhub.app.data.MemberProfileRepository
import com.unityhub.app.data.OrganizationRepository
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.launch

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

    var profile by remember { mutableStateOf<AccountProfile?>(null) }
    var contributions by remember { mutableStateOf<MemberProfileData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var imageUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var facebook by rememberSaveable { mutableStateOf("") }
    var instagram by rememberSaveable { mutableStateOf("") }
    var youtube by rememberSaveable { mutableStateOf("") }
    var tiktok by rememberSaveable { mutableStateOf("") }
    var website by rememberSaveable { mutableStateOf("") }
    var imageKind by remember { mutableStateOf("avatar") }

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
                    repository.uploadProfileImage(imageKind, bytes, mimeType)
                    profile = repository.loadProfile()
                } catch (exception: Exception) {
                    errorMessage = exception.message ?: "The profile image could not be uploaded."
                } finally {
                    imageUploading = false
                }
            }
        }
    }

    LaunchedEffect(userId, organizationId) {
        loading = true
        errorMessage = ""
        try {
            val loadedProfile = repository.loadProfile()
            profile = loadedProfile
            bio = loadedProfile.bio
            facebook = loadedProfile.socialLinks["facebook"].orEmpty()
            instagram = loadedProfile.socialLinks["instagram"].orEmpty()
            youtube = loadedProfile.socialLinks["youtube"].orEmpty()
            tiktok = loadedProfile.socialLinks["tiktok"].orEmpty()
            website = loadedProfile.socialLinks["website"].orEmpty()

            runCatching {
                val workspace = organizationRepository.loadWorkspace(userId)
                val membership = workspace.members.firstOrNull { it.userId == userId }
                if (membership != null) {
                    contributions = memberRepository.loadProfile(organizationId, membership.id)
                }
            }
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Profile details could not be loaded."
        } finally {
            loading = false
        }
    }

    val avatarUrl = remember(profile?.avatarPath) { repository.publicMediaUrl(profile?.avatarPath) }
    val coverUrl = remember(profile?.coverPath) { repository.publicMediaUrl(profile?.coverPath) }
    val initials = profileInitials(profile?.displayName.orEmpty())

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(172.dp).clip(RoundedCornerShape(22.dp))
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
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(18.dp).size(116.dp)
                                    .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            )
                            Box(
                                Modifier.align(Alignment.BottomStart).padding(24.dp).size(70.dp)
                                    .background(Color(0xFFF3E9D5).copy(alpha = 0.14f), CircleShape)
                            )
                            Text(
                                "WORSHIP · SERVE · GROW",
                                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
                                color = Color(0xFFFFF7E8),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        color = Color.Black.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        TextButton(
                            onClick = { imageKind = "cover"; imagePicker.launch("image/*") },
                            enabled = !imageUploading,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(5.dp))
                            Text("Change cover", color = Color.White)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(86.dp).clip(CircleShape)
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
                        Text(profile?.displayName.orEmpty().ifBlank { "Your profile" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(profile?.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { imageKind = "avatar"; imagePicker.launch("image/*") },
                            enabled = !imageUploading,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                        ) { Text(if (imageUploading) "Uploading…" else "Change profile photo") }
                    }
                }
                Text(organizationName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (loading || imageUploading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(if (imageUploading) "Uploading profile image…" else "Loading profile…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("About Me", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it.take(300) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bio") },
                        placeholder = { Text("Share a little about yourself and how you serve.") },
                        minLines = 3,
                        maxLines = 4,
                        supportingText = { Text("${bio.length}/300") }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Ministry roles", fontWeight = FontWeight.SemiBold)
                    }
                    val roles = contributions?.ministryPositions.orEmpty()
                    if (roles.isEmpty()) {
                        Text("No ministry roles assigned yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        roles.forEach { ministryRole ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (ministryRole.contains("vocal", true) || ministryRole.contains("choir", true)) Icons.Default.Mic else Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(ministryRole, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Text("Workspace role", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(role, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Team", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(organizationName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                errorMessage = ""
                                try {
                                    profile = repository.updateProfileDetails(
                                        bio = bio,
                                        socialLinks = mapOf(
                                            "facebook" to facebook,
                                            "instagram" to instagram,
                                            "youtube" to youtube,
                                            "tiktok" to tiktok,
                                            "website" to website
                                        )
                                    )
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Profile details could not be saved."
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving && !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (saving) "Saving profile…" else "Save About Me")
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Contribution Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val summary = contributions?.summary
                    if (summary == null) {
                        Text("Your service and rehearsal activity will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContributionMetric("Upcoming services", summary.upcomingServices)
                            ContributionMetric("Confirmed", summary.confirmedServices)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContributionMetric("Upcoming rehearsals", summary.upcomingRehearsals)
                            ContributionMetric("Attended", summary.attendedRehearsals)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContributionMetric("Unable to serve", summary.declinedServices)
                            ContributionMetric("Absent", summary.absentRehearsals)
                        }
                    }
                    Text("Activity is based on your assigned services and rehearsals in this team.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Social links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    SocialLinkField("Facebook", facebook, { facebook = it }, "facebook.com/…")
                    SocialLinkField("Instagram", instagram, { instagram = it }, "instagram.com/…")
                    SocialLinkField("YouTube", youtube, { youtube = it }, "youtube.com/…")
                    SocialLinkField("TikTok", tiktok, { tiktok = it }, "tiktok.com/@…")
                    SocialLinkField("Website", website, { website = it }, "https://…")
                    TextButton(
                        onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    profile = repository.updateProfileDetails(
                                        bio = bio,
                                        socialLinks = mapOf(
                                            "facebook" to facebook,
                                            "instagram" to instagram,
                                            "youtube" to youtube,
                                            "tiktok" to tiktok,
                                            "website" to website
                                        )
                                    )
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Social links could not be saved."
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving
                    ) { Text("Save social links") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account and preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Manage your sign-in, appearance, notifications and app updates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Account settings")
                    }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            item { Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
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

@Composable
private fun SocialLinkField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Link, contentDescription = "$label link", tint = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true
        )
    }
}

private fun profileInitials(fullName: String): String {
    val parts = fullName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return parts.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifBlank { "UH" }
}
