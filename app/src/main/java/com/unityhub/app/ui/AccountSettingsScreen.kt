package com.unityhub.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.unityhub.app.BuildConfig
import com.unityhub.app.data.AccountProfile
import com.unityhub.app.data.AccountProfileRules
import com.unityhub.app.data.AccountRepository
import com.unityhub.app.data.AppUpdateManager
import com.unityhub.app.data.AppUpdateRelease
import com.unityhub.app.data.AppUpdateManifest
import com.unityhub.app.data.AppUpdateRules
import com.unityhub.app.sync.AppUpdateWorkScheduler
import com.unityhub.app.data.SupabaseClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AccountSettingsScreen(
    organizationName: String,
    role: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { AccountRepository(SupabaseClientProvider.client) }
    val scope = rememberCoroutineScope()
    val updateManager = remember(context) { AppUpdateManager(context) }
    var updateManifest by remember { mutableStateOf<AppUpdateManifest?>(null) }
    var checkingUpdates by remember { mutableStateOf(false) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedUpdate by remember { mutableStateOf<File?>(null) }
    var canInstallDownloadedUpdate by remember { mutableStateOf(false) }
    var pendingUpdateExport by remember { mutableStateOf<File?>(null) }
    var autoDownloadUpdates by remember(context) {
        mutableStateOf(updateManager.isAutoDownloadEnabled())
    }
    var updateMessage by remember { mutableStateOf("") }

    val exportUpdateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AppUpdateManager.APK_MIME_TYPE)
    ) { destination ->
        val sourceFile = pendingUpdateExport
        pendingUpdateExport = null
        if (destination != null && sourceFile != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            sourceFile.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("The selected destination could not be opened.")
                    }
                }
                updateMessage = if (result.isSuccess) {
                    "The verified APK was saved. Sync your team data, uninstall the older UnityHub app, then open the saved APK to install it. Future updates can install in place."
                } else {
                    result.exceptionOrNull()?.message ?: "UnityHub could not save the update file."
                }
            }
        }
    }

    fun saveUpdateForReinstall(file: File, release: AppUpdateRelease) {
        pendingUpdateExport = file
        updateMessage = "This release uses a different signing key. Save the APK to Downloads for the one-time reinstall."
        exportUpdateLauncher.launch("UnityHub-${release.versionName}-update.apk")
    }

    fun checkForUpdates() {
        scope.launch {
            checkingUpdates = true
            updateMessage = ""
            try {
                val loadedManifest = updateManager.fetchManifest()
                updateManifest = loadedManifest
                val latest = AppUpdateRules.latestRelease(loadedManifest)
                downloadedUpdate = latest?.let { updateManager.findVerifiedDownload(it) }
                canInstallDownloadedUpdate = if (latest != null && downloadedUpdate != null) {
                    updateManager.hasMatchingInstalledSigner(downloadedUpdate!!, latest)
                } else {
                    false
                }
                updateMessage = when {
                    latest == null -> "No published UnityHub releases were found."
                    latest.versionCode > BuildConfig.VERSION_CODE ->
                        "Version ${latest.versionName} is available."
                    else -> "UnityHub is up to date."
                }
            } catch (exception: Exception) {
                updateMessage = exception.message
                    ?: "UnityHub could not check for updates. Try again when you are online."
            } finally {
                checkingUpdates = false
            }
        }
    }

    fun downloadUpdate(release: AppUpdateRelease) {
        if (downloadingUpdate) return
        scope.launch {
            downloadingUpdate = true
            downloadProgress = 0f
            updateMessage = ""
            downloadedUpdate = null
            try {
                val verifiedFile = updateManager.downloadRelease(release) { progress ->
                    downloadProgress = progress
                }
                downloadedUpdate = verifiedFile
                canInstallDownloadedUpdate = updateManager.hasMatchingInstalledSigner(verifiedFile, release)
                updateMessage = if (canInstallDownloadedUpdate) {
                    "The update is downloaded and verified. Android will ask you to approve installation."
                } else {
                    "The update is verified, but its signing key differs from this installation. Save it for the one-time reinstall."
                }
            } catch (exception: Exception) {
                updateMessage = exception.message ?: "The UnityHub update could not be downloaded."
            } finally {
                downloadingUpdate = false
            }
        }
    }

    fun installDownloadedUpdate(release: AppUpdateRelease) {
        val file = downloadedUpdate ?: return
        if (!updateManager.hasMatchingInstalledSigner(file, release)) {
            saveUpdateForReinstall(file, release)
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            updateMessage = "Allow UnityHub to install updates in Android settings, then return and tap Install update."
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }.onFailure {
                updateMessage = "Open Android Settings and allow UnityHub to install unknown apps."
            }
            return
        }
        runCatching {
            context.startActivity(updateManager.createInstallIntent(file, release))
        }.onFailure {
            updateMessage = it.message ?: "Android could not open the update installer."
        }
    }

    LaunchedEffect(updateManager) {
        checkForUpdates()
    }

    var profile by remember { mutableStateOf<AccountProfile?>(null) }
    var profileLoading by remember { mutableStateOf(true) }
    var savingProfile by remember { mutableStateOf(false) }
    var notificationsEnabled by remember(context) {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var errorMessage by rememberSaveable { mutableStateOf("") }
    var noticeMessage by rememberSaveable { mutableStateOf("") }
    var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var editErrorMessage by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(repository) {
        profileLoading = true
        errorMessage = ""
        try {
            profile = repository.loadProfile()
        } catch (_: Exception) {
            errorMessage = "Your account details could not be loaded. Check your connection and try again."
        } finally {
            profileLoading = false
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat
                    .from(context)
                    .areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Your account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Manage your profile and UnityHub app preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (profileLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        profile?.let { account ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initials(account.displayName),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    account.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    account.email ?: "No email address available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                nameDraft = account.displayName
                                editErrorMessage = ""
                                showEditNameDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !savingProfile
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Text("Edit display name", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        if (errorMessage.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        errorMessage,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (noticeMessage.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        noticeMessage,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Workspace", icon = {
                Icon(Icons.Default.Groups, contentDescription = null)
            }) {
                SettingsValueRow(label = "Church or team", value = organizationName)
                SettingsValueRow(label = "Your role", value = role)
            }
        }

        item {
            SettingsSection(title = "Appearance", icon = {
                Icon(Icons.Default.DarkMode, contentDescription = null)
            }) {
                Text(
                    "UnityHub automatically follows your device's light or dark appearance. Change your Android system theme to switch the app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Appearance preference", fontWeight = FontWeight.Medium)
                    Text(
                        "System default",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        item {
            SettingsSection(title = "Notifications", icon = {
                Icon(Icons.Default.Notifications, contentDescription = null)
            }) {
                Text(
                    if (notificationsEnabled) {
                        "UnityHub notifications are allowed by Android."
                    } else {
                        "UnityHub notifications are currently blocked by Android."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                noticeMessage = "Android notification settings could not be opened on this device."
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manage notification settings")
                }
            }
        }

        item {
            SettingsSection(title = "Account security", icon = {
                Icon(Icons.Default.Shield, contentDescription = null)
            }) {
                Text(
                    "Your sign-in and session are managed securely by Supabase Auth. Display names are profile information only; team roles and access are controlled separately by your worship team membership.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SettingsSection(title = "App updates", icon = {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
            }) {
                SettingsValueRow(label = "Installed version", value = BuildConfig.VERSION_NAME)
                val latestRelease = updateManifest?.let(AppUpdateRules::latestRelease)
                SettingsValueRow(
                    label = "Latest version",
                    value = latestRelease?.versionName ?: "Not checked"
                )
                Text(
                    "Check for new versions and review the complete release history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatically download updates", fontWeight = FontWeight.Medium)
                        Text(
                            "Downloads can happen in the background. Android will still ask you to confirm installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoDownloadUpdates,
                        onCheckedChange = { enabled ->
                            autoDownloadUpdates = enabled
                            updateManager.setAutoDownloadEnabled(enabled)
                            AppUpdateWorkScheduler.setEnabled(context, enabled)
                            if (enabled && latestRelease != null &&
                                latestRelease.versionCode > BuildConfig.VERSION_CODE &&
                                AppUpdateRules.hasValidDownloadMetadata(latestRelease)
                            ) {
                                downloadUpdate(latestRelease)
                            }
                        }
                    )
                }
                OutlinedButton(
                    onClick = { checkForUpdates() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !checkingUpdates && !downloadingUpdate
                ) {
                    if (checkingUpdates) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                    }
                    Text(
                        if (checkingUpdates) "Checking…" else "Check for updates",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (latestRelease != null &&
                    latestRelease.versionCode > BuildConfig.VERSION_CODE
                ) {
                    Button(
                        onClick = {
                            val file = downloadedUpdate
                            if (file == null) {
                                downloadUpdate(latestRelease)
                            } else if (canInstallDownloadedUpdate) {
                                installDownloadedUpdate(latestRelease)
                            } else {
                                saveUpdateForReinstall(file, latestRelease)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !downloadingUpdate &&
                            (downloadedUpdate != null ||
                                AppUpdateRules.hasValidDownloadMetadata(latestRelease))
                    ) {
                        Text(
                            when {
                                downloadedUpdate == null -> "Download update"
                                canInstallDownloadedUpdate -> "Install update"
                                else -> "Save APK for reinstall"
                            }
                        )
                    }
                }
                if (downloadingUpdate) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Downloading update… ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (updateMessage.isNotBlank()) {
                    Text(
                        updateMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (downloadedUpdate != null ||
                            updateMessage == "UnityHub is up to date."
                        ) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                Text("Release history", fontWeight = FontWeight.SemiBold)
                val history = updateManifest?.releases.orEmpty()
                    .sortedByDescending(AppUpdateRelease::versionCode)
                if (history.isEmpty()) {
                    Text(
                        "Release notes will appear here after UnityHub connects to its update service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    history.forEach { release ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Version ${release.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (release.releasedAt.isNotBlank()) {
                                Text(
                                    release.releasedAt,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (release.summary.isNotBlank()) {
                                Text(release.summary, style = MaterialTheme.typography.bodySmall)
                            }
                            release.changes.forEach { change ->
                                Text("• $change", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !savingProfile
            ) {
                Icon(Icons.Default.Logout, contentDescription = null)
                Text("Sign out", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!savingProfile) showEditNameDialog = false
            },
            title = { Text("Edit display name") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = {
                            nameDraft = it
                            editErrorMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Display name") },
                        singleLine = true,
                        enabled = !savingProfile,
                        isError = nameDraft.isNotBlank() &&
                            !AccountProfileRules.isValidDisplayName(nameDraft),
                        supportingText = {
                            Text("Use 2–80 characters.")
                        }
                    )
                    if (editErrorMessage.isNotBlank()) {
                        Text(
                            editErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            savingProfile = true
                            editErrorMessage = ""
                            try {
                                profile = repository.updateDisplayName(nameDraft)
                                noticeMessage = "Your display name has been updated."
                                showEditNameDialog = false
                            } catch (_: Exception) {
                                editErrorMessage = "Your profile could not be updated. Check your connection and try again."
                            } finally {
                                savingProfile = false
                            }
                        }
                    },
                    enabled = !savingProfile &&
                        AccountProfileRules.isValidDisplayName(nameDraft)
                ) {
                    if (savingProfile) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Save changes")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditNameDialog = false },
                    enabled = !savingProfile
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out of UnityHub?") },
            text = {
                Text("Your offline copies will be removed from this device when you sign out. Any service responses saved only on this device and still waiting to sync will also be discarded.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Stay signed in")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

private fun initials(displayName: String): String =
    displayName.trim()
        .split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifBlank { "U" }
