package com.unityhub.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.unityhub.app.BuildConfig
import io.github.jan.supabase.auth.auth
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
    themeMode: String = "system",
    onThemeModeChanged: (String) -> Unit = {},
    fontScale: Float = 1f,
    onFontScaleChanged: (Float) -> Unit = {},
    onOpenProfileDetails: () -> Unit = {},
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
    var signOutAction by rememberSaveable { mutableStateOf("sign_out") }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var showFaqDialog by rememberSaveable { mutableStateOf(false) }
    var showLegalDialog by rememberSaveable { mutableStateOf(false) }
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordError by rememberSaveable { mutableStateOf("") }
    var nameDraft by rememberSaveable { mutableStateOf("") }
    var editErrorMessage by rememberSaveable { mutableStateOf("") }

    val notificationPreferences = remember(context) {
        context.getSharedPreferences("unityhub_notification_categories", android.content.Context.MODE_PRIVATE)
    }
    var chatsPushEnabled by remember(context) { mutableStateOf(notificationPreferences.getBoolean("chats", true)) }
    var updatesPushEnabled by remember(context) { mutableStateOf(notificationPreferences.getBoolean("app_updates", true)) }
    var assignmentsPushEnabled by remember(context) { mutableStateOf(notificationPreferences.getBoolean("assignments", true)) }
    var teamUpdatesPushEnabled by remember(context) { mutableStateOf(notificationPreferences.getBoolean("team_updates", true)) }

    fun updateNotificationCategory(category: String, enabled: Boolean) {
        notificationPreferences.edit().putBoolean(category, enabled).apply()
        when (category) {
            "chats" -> chatsPushEnabled = enabled
            "app_updates" -> updatesPushEnabled = enabled
            "assignments" -> assignmentsPushEnabled = enabled
            "team_updates" -> teamUpdatesPushEnabled = enabled
        }
    }

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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Your account",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
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
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(account.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(account.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = {
                                nameDraft = account.displayName
                                editErrorMessage = ""
                                showEditNameDialog = true
                            },
                            enabled = !savingProfile
                        ) { Text("Edit") }
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
            SettingsSection(title = "ACCOUNT MANAGEMENT", icon = {
                Icon(Icons.Default.Groups, contentDescription = null)
            }) {
                Text("Profile details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                profile?.let { account ->
                    SettingsValueRow(label = "Display name", value = account.displayName)
                    SettingsValueRow(label = "Email", value = account.email.orEmpty())
                }
                SettingsValueRow(label = "Church or team", value = organizationName)
                SettingsValueRow(label = "Team role", value = role)
                OutlinedButton(onClick = onOpenProfileDetails, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Text("Edit profile details", modifier = Modifier.padding(start = 8.dp))
                }

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Login & Security", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Update the password used to sign in to UnityHub.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        passwordError = ""
                        showPasswordDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Change password") }

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Session Controls", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            signOutAction = "switch_account"
                            showSignOutDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Switch account") }
                    OutlinedButton(
                        onClick = {
                            signOutAction = "sign_out"
                            showSignOutDialog = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Text("Sign out", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        item {
            SettingsSection(title = "APPEARANCE", icon = {
                Icon(Icons.Default.DarkMode, contentDescription = null)
            }) {
                Text(
                    "Choose how UnityHub looks on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "system" to "System",
                        "light" to "Light",
                        "dark" to "Dark"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = themeMode == value,
                            onClick = { onThemeModeChanged(value) },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    when (themeMode) {
                        "light" -> "Light appearance is active."
                        "dark" -> "Dark appearance is active."
                        else -> "UnityHub follows your Android system appearance."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Font size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Adjust text throughout the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        0.9f to "Small",
                        1.0f to "Default",
                        1.1f to "Large",
                        1.2f to "Extra large"
                    ).forEach { (scale, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(fontScale - scale) < 0.01f,
                            onClick = { onFontScaleChanged(scale) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsSection(title = "NOTIFICATIONS", icon = {
                Icon(Icons.Default.Notifications, contentDescription = null)
            }) {
                Text(
                    if (notificationsEnabled) "Allowed by Android" else "Blocked by Android",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Choose which UnityHub alerts you receive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NotificationCategoryToggle(
                    title = "Team Chat",
                    description = "New messages from your team.",
                    checked = chatsPushEnabled,
                    onCheckedChange = { updateNotificationCategory("chats", it) }
                )
                NotificationCategoryToggle(
                    title = "Assignments",
                    description = "Service and rehearsal assignments and reminders.",
                    checked = assignmentsPushEnabled,
                    onCheckedChange = { updateNotificationCategory("assignments", it) }
                )
                NotificationCategoryToggle(
                    title = "Team updates",
                    description = "Membership and other team activity.",
                    checked = teamUpdatesPushEnabled,
                    onCheckedChange = { updateNotificationCategory("team_updates", it) }
                )
                NotificationCategoryToggle(
                    title = "App updates",
                    description = "New UnityHub releases and update information.",
                    checked = updatesPushEnabled,
                    onCheckedChange = { updateNotificationCategory("app_updates", it) }
                )
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            SettingsSection(title = "PRIVACY", icon = {
                Icon(Icons.Default.Shield, contentDescription = null)
            }) {
                Text("UnityHub uses Android system permissions for notifications, media selection and network access. Review or revoke permissions in Android settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { context.startActivity(intent) }
                            .onFailure { noticeMessage = "Android app permissions could not be opened." }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Text("App permissions", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        item {
            SettingsSection(title = "ABOUT", icon = {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
            }) {
                Text("UnityHub helps worship teams manage songs, services, schedules and team communication.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsValueRow(label = "App version", value = BuildConfig.VERSION_NAME)
                SettingsValueRow(label = "Build number", value = BuildConfig.VERSION_CODE.toString())
                Text("App updates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                SettingsValueRow(label = "Installed version", value = BuildConfig.VERSION_NAME)
                val latestRelease = updateManifest?.let(AppUpdateRules::latestRelease)
                SettingsValueRow(
                    label = "Latest version",
                    value = latestRelease?.versionName ?: "Not checked"
                )

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Help & Feedback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { showFaqDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Frequently asked questions")
                }
                OutlinedButton(
                    onClick = {
                        val issueUrl = "https://github.com/vcatindig06/UnityHub/issues/new?title=UnityHub%20feedback"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl))) }
                            .onFailure { noticeMessage = "The feedback page could not be opened." }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Send feedback") }
                OutlinedButton(
                    onClick = {
                        val issueUrl = "https://github.com/vcatindig06/UnityHub/issues/new?title=UnityHub%20bug%20report"
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(issueUrl))) }
                            .onFailure { noticeMessage = "The bug report page could not be opened." }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Report a bug") }

                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Legal & Policies", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { showLegalDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Terms, privacy & open-source notices")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = autoDownloadUpdates,
                            role = Role.Switch,
                            onValueChange = { enabled ->
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
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatically download updates", fontWeight = FontWeight.Medium)
                        Text(
                            "Android will ask before installation.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoDownloadUpdates, onCheckedChange = null)
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

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!savingProfile) showPasswordDialog = false },
            title = { Text("Change password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it; passwordError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Current password") },
                        singleLine = true,
                        enabled = !savingProfile,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it; passwordError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New password") },
                        singleLine = true,
                        enabled = !savingProfile,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; passwordError = "" },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm new password") },
                        singleLine = true,
                        enabled = !savingProfile,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (passwordError.isNotBlank()) {
                        Text(passwordError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Use at least 8 characters. Your current password is required to confirm the change.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            currentPassword.isBlank() -> passwordError = "Enter your current password."
                            newPassword.length < 8 -> passwordError = "Use at least 8 characters for your new password."
                            newPassword != confirmPassword -> passwordError = "The new passwords do not match."
                            else -> scope.launch {
                                savingProfile = true
                                passwordError = ""
                                try {
                                    SupabaseClientProvider.client.auth.updateUser {
                                        password = newPassword
                                        currentPassword = this@AccountSettingsScreen.currentPassword
                                    }
                                    showPasswordDialog = false
                                    currentPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    noticeMessage = "Your password has been updated."
                                } catch (exception: Exception) {
                                    passwordError = exception.message ?: "Your password could not be changed. Try again."
                                } finally {
                                    savingProfile = false
                                }
                            }
                        }
                    },
                    enabled = !savingProfile
                ) {
                    if (savingProfile) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Update password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }, enabled = !savingProfile) { Text("Cancel") }
            }
        )
    }

    if (showFaqDialog) {
        AlertDialog(
            onDismissRequest = { showFaqDialog = false },
            title = { Text("UnityHub help") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How do I join a team? Ask an administrator for the workspace name, then use the join-team flow.")
                    Text("Can I view songs offline? Previously synchronized songs and service charts can be read offline.")
                    Text("How do I report a problem? Use Report a bug to open the UnityHub issue tracker.")
                    Text("How do I update? Open About and use Check for updates.")
                }
            },
            confirmButton = { TextButton(onClick = { showFaqDialog = false }) { Text("Close") } }
        )
    }

    if (showLegalDialog) {
        AlertDialog(
            onDismissRequest = { showLegalDialog = false },
            title = { Text("Legal & open-source notices") },
            text = {
                Text("Terms of service and privacy policy documents have not yet been published in this app. Third-party dependency license notices are not bundled in this build. The project source and published notices can be reviewed in the UnityHub repository.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLegalDialog = false
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vcatindig06/UnityHub")))
                        }
                    }
                ) { Text("Open repository") }
            },
            dismissButton = { TextButton(onClick = { showLegalDialog = false }) { Text("Close") } }
        )
    }

    if (showSignOutDialog) {
        val switchingAccount = signOutAction == "switch_account"
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(if (switchingAccount) "Switch account?" else "Sign out of UnityHub?") },
            text = {
                Text(
                    if (switchingAccount) {
                        "UnityHub will sign out this account and return to the sign-in screen so another account can be used. Offline copies and unsynchronized responses will be removed from this device."
                    } else {
                        "Your offline copies will be removed from this device when you sign out. Any service responses saved only on this device and still waiting to sync will also be discarded."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text(if (switchingAccount) "Switch account" else "Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
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
    var expanded by rememberSaveable(title) {
        mutableStateOf(title == "ACCOUNT MANAGEMENT")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Text(
                    title,
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun NotificationCategoryToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
